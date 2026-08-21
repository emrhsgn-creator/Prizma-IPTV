package com.prizma.iptv.data.remote

import android.util.Xml
import com.prizma.iptv.R
import com.prizma.iptv.core.Http
import com.prizma.iptv.core.appError
import com.prizma.iptv.data.model.EpgProgram
import com.prizma.iptv.data.model.Profile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedInputStream
import java.io.InputStream
import java.io.PushbackInputStream
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale
import java.util.zip.GZIPInputStream
import kotlin.coroutines.coroutineContext

/**
 * XMLTV yayın akışı ayrıştırıcısı.
 *
 * XMLTV dosyaları onlarca megabayt olabildiği için belge belleğe alınmaz:
 * akış hâlinde okunur, ilgilenilmeyen kanallar ve zaman aralığı dışındaki
 * programlar daha ayrıştırma sırasında elenir.
 */
object XmltvParser {

    data class Result(
        val programCount: Int,
        val channelNames: Map<String, String>
    )

    /** Panelin xmltv.php çıktısını indirip [sink] üzerinden programları akıtır. */
    suspend fun download(
        profile: Profile,
        acceptChannel: (String) -> Boolean,
        fromMs: Long,
        toMs: Long,
        sink: (EpgProgram) -> Unit
    ): Result = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url(XtreamApi.xmltvUrl(profile))
            .header("User-Agent", profile.userAgent.ifBlank { Http.DEFAULT_USER_AGENT })
            .header("Accept-Encoding", "gzip")
            .build()
        Http.bulk.newCall(req).execute().use { res ->
            if (!res.isSuccessful) throw appError(R.string.error_http, res.code)
            val body = res.body ?: throw appError(R.string.error_parse)
            parse(body.byteStream(), acceptChannel, fromMs, toMs, sink)
        }
    }

    /** Gzip ile sıkıştırılmış olabilecek akışı şeffaf biçimde açar. */
    private fun unwrap(input: InputStream): InputStream {
        val pushback = PushbackInputStream(BufferedInputStream(input, 1 shl 16), 2)
        val head = ByteArray(2)
        val read = pushback.read(head, 0, 2)
        if (read > 0) pushback.unread(head, 0, read)
        val gzipped = read == 2 &&
            (head[0].toInt() and 0xFF) == 0x1F &&
            (head[1].toInt() and 0xFF) == 0x8B
        return if (gzipped) GZIPInputStream(pushback, 1 shl 16) else pushback
    }

    suspend fun parse(
        raw: InputStream,
        acceptChannel: (String) -> Boolean,
        fromMs: Long,
        toMs: Long,
        sink: (EpgProgram) -> Unit
    ): Result {
        val channelNames = LinkedHashMap<String, String>()
        var count = 0
        var ticks = 0
        val preferredLang = Locale.getDefault().language

        unwrap(raw).use { stream ->
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(stream, null)

            var channelId = ""
            var haveDisplayName = false

            var progChannel = ""
            var progStart = 0L
            var progStop = 0L
            var title = ""
            var titleLang = ""
            var desc = ""
            var descLang = ""
            var inProgramme = false
            var pending: String? = null
            var pendingLang = ""

            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> when (parser.name) {
                        "channel" -> {
                            channelId = parser.getAttributeValue(null, "id").orEmpty()
                            haveDisplayName = false
                        }

                        "programme" -> {
                            progChannel = parser.getAttributeValue(null, "channel").orEmpty()
                            progStart = parseTime(parser.getAttributeValue(null, "start"))
                            progStop = parseTime(parser.getAttributeValue(null, "stop"))
                            title = ""
                            titleLang = ""
                            desc = ""
                            descLang = ""
                            inProgramme = true
                        }

                        "display-name" -> if (channelId.isNotEmpty() && !haveDisplayName) {
                            pending = "display-name"
                            pendingLang = ""
                        }

                        "title" -> if (inProgramme) {
                            pending = "title"
                            pendingLang = parser.getAttributeValue(null, "lang").orEmpty()
                        }

                        "desc" -> if (inProgramme) {
                            pending = "desc"
                            pendingLang = parser.getAttributeValue(null, "lang").orEmpty()
                        }
                    }

                    XmlPullParser.TEXT -> if (pending != null) {
                        val text = parser.text?.trim().orEmpty()
                        if (text.isNotEmpty()) {
                            when (pending) {
                                "display-name" -> {
                                    channelNames[channelId] = text
                                    haveDisplayName = true
                                }
                                // Aynı program için birden çok dilde başlık gelebilir;
                                // cihaz diline uyan sürüm tercih edilir.
                                "title" -> if (title.isEmpty() || betterLang(
                                        titleLang, pendingLang, preferredLang
                                    )
                                ) {
                                    title = text
                                    titleLang = pendingLang
                                }

                                "desc" -> if (desc.isEmpty() || betterLang(
                                        descLang, pendingLang, preferredLang
                                    )
                                ) {
                                    desc = text
                                    descLang = pendingLang
                                }
                            }
                        }
                    }

                    XmlPullParser.END_TAG -> {
                        when (parser.name) {
                            "display-name", "title", "desc" -> pending = null

                            "channel" -> channelId = ""

                            "programme" -> {
                                inProgramme = false
                                val stop = if (progStop > progStart) progStop else progStart + 1_800_000L
                                val inWindow = progStart in fromMs..toMs || stop in fromMs..toMs ||
                                    (progStart < fromMs && stop > toMs)
                                if (progStart > 0L && inWindow &&
                                    progChannel.isNotEmpty() && acceptChannel(progChannel)
                                ) {
                                    sink(
                                        EpgProgram(
                                            channelId = progChannel,
                                            start = progStart,
                                            stop = stop,
                                            title = title,
                                            description = desc
                                        )
                                    )
                                    count++
                                }
                                progChannel = ""
                                progStart = 0L
                                progStop = 0L
                                // Uzun dosyalarda iptal isteğine cevap verebilmek için.
                                ticks++
                                if ((ticks and 0x1FF) == 0) coroutineContext.ensureActive()
                            }
                        }
                    }
                }
                event = parser.next()
            }
        }
        return Result(count, channelNames)
    }

    private fun betterLang(current: String, candidate: String, preferred: String): Boolean =
        candidate.startsWith(preferred, true) && !current.startsWith(preferred, true)

    /**
     * XMLTV zaman biçimi: yyyyMMddHHmmss ardından isteğe bağlı " +0300".
     * Saat dilimi verilmemişse cihazın yerel dilimi varsayılır.
     */
    fun parseTime(raw: String?): Long {
        if (raw == null) return 0L
        val s = raw.trim()
        if (s.length < 14) return 0L
        return try {
            val local = LocalDateTime.of(
                s.substring(0, 4).toInt(),
                s.substring(4, 6).toInt(),
                s.substring(6, 8).toInt(),
                s.substring(8, 10).toInt(),
                s.substring(10, 12).toInt(),
                s.substring(12, 14).toInt()
            )
            val tail = s.substring(14).trim()
            val offset = parseOffset(tail) ?: ZoneId.systemDefault().rules.getOffset(local)
            local.toInstant(offset).toEpochMilli()
        } catch (e: Exception) {
            0L
        }
    }

    private fun parseOffset(tail: String): ZoneOffset? {
        if (tail.length < 5) return null
        val sign = when (tail[0]) {
            '+' -> 1
            '-' -> -1
            else -> return null
        }
        val hours = tail.substring(1, 3).toIntOrNull() ?: return null
        val minutes = tail.substring(3, 5).toIntOrNull() ?: return null
        return runCatching {
            ZoneOffset.ofTotalSeconds(sign * (hours * 3600 + minutes * 60))
        }.getOrNull()
    }
}
