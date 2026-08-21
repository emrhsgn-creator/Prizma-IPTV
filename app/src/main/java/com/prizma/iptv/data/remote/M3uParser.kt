package com.prizma.iptv.data.remote

import com.prizma.iptv.R
import com.prizma.iptv.core.Http
import com.prizma.iptv.core.appError
import com.prizma.iptv.data.model.Category
import com.prizma.iptv.data.model.Profile
import com.prizma.iptv.data.model.Section
import com.prizma.iptv.data.model.StreamItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.BufferedReader

/** Tek bir #EXTINF kaydı. */
data class M3uEntry(
    val name: String,
    val url: String,
    val logo: String,
    val group: String,
    val tvgId: String,
    val number: Int
)

/** Xtream paneli olmayan, düz M3U listesi veren kaynaklar için ayrıştırıcı. */
object M3uParser {

    private val attrPattern = Regex("([A-Za-z0-9-]+)=\"([^\"]*)\"")

    /** Listeyi indirir ve bölümlere ayırır. */
    suspend fun load(profile: Profile): Map<Section, Pair<List<Category>, List<StreamItem>>> =
        withContext(Dispatchers.IO) {
            val entries = download(profile)
            if (entries.isEmpty()) throw appError(R.string.error_empty_playlist)
            classify(entries)
        }

    suspend fun download(profile: Profile): List<M3uEntry> = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url(profile.host)
            .header("User-Agent", profile.userAgent.ifBlank { Http.DEFAULT_USER_AGENT })
            .build()
        Http.bulk.newCall(req).execute().use { res ->
            if (res.code == 401 || res.code == 403) throw appError(R.string.error_auth)
            if (!res.isSuccessful) throw appError(R.string.error_http, res.code)
            val body = res.body ?: throw appError(R.string.error_empty_playlist)
            parse(body.charStream().buffered())
        }
    }

    fun parse(reader: BufferedReader): List<M3uEntry> {
        val out = ArrayList<M3uEntry>(4096)
        var pendingName = ""
        var pendingLogo = ""
        var pendingGroup = ""
        var pendingTvg = ""
        var pendingNumber = 0
        var haveHeader = false

        reader.use { r ->
            while (true) {
                val raw = r.readLine() ?: break
                val line = raw.trim()
                if (line.isEmpty()) continue

                when {
                    line.startsWith("#EXTM3U", true) -> haveHeader = true

                    line.startsWith("#EXTINF", true) -> {
                        val attrs = attrPattern.findAll(line)
                            .associate { it.groupValues[1].lowercase() to it.groupValues[2] }
                        pendingLogo = attrs["tvg-logo"].orEmpty()
                        pendingGroup = attrs["group-title"].orEmpty()
                        pendingTvg = attrs["tvg-id"].orEmpty()
                        pendingNumber = attrs["tvg-chno"]?.toIntOrNull()
                            ?: attrs["channel-number"]?.toIntOrNull()
                            ?: 0
                        // Görünen ad son virgülden sonrasıdır.
                        val comma = line.lastIndexOf(',')
                        pendingName = if (comma >= 0 && comma < line.length - 1) {
                            line.substring(comma + 1).trim()
                        } else {
                            attrs["tvg-name"].orEmpty()
                        }
                    }

                    line.startsWith("#EXTGRP", true) -> {
                        val v = line.substringAfter(':', "").trim()
                        if (v.isNotEmpty()) pendingGroup = v
                    }

                    line.startsWith("#") -> Unit // diğer yönergeler yok sayılır

                    else -> {
                        if (pendingName.isNotEmpty() || haveHeader) {
                            val name = pendingName.ifBlank { line.substringAfterLast('/') }
                            out.add(
                                M3uEntry(
                                    name = name,
                                    url = line,
                                    logo = pendingLogo,
                                    group = pendingGroup.ifBlank { "Genel" },
                                    tvgId = pendingTvg,
                                    number = if (pendingNumber > 0) pendingNumber else out.size + 1
                                )
                            )
                        }
                        pendingName = ""
                        pendingLogo = ""
                        pendingGroup = ""
                        pendingTvg = ""
                        pendingNumber = 0
                    }
                }
            }
        }
        return out
    }

    private val vodHints = listOf("vod", "film", "movie", "sinema")
    private val seriesHints = listOf("dizi", "seri", "series", "show")

    private fun sectionOf(entry: M3uEntry): Section {
        val url = entry.url.lowercase()
        if (url.contains("/movie/")) return Section.VOD
        if (url.contains("/series/")) return Section.SERIES
        if (url.contains("/live/")) return Section.LIVE

        val group = entry.group.lowercase()
        if (seriesHints.any { group.contains(it) }) return Section.SERIES
        if (vodHints.any { group.contains(it) }) return Section.VOD

        // Dosya uzantısı olan adresler genelde talep üzerine videodur.
        val tail = url.substringAfterLast('/')
        if (tail.endsWith(".mp4") || tail.endsWith(".mkv") || tail.endsWith(".avi")) {
            return Section.VOD
        }
        return Section.LIVE
    }

    /** M3U adresleri sabit kalır, bu yüzden kimlik olarak adresin özeti kullanılır. */
    fun stableId(url: String): String {
        var h = 1125899906842597L
        for (c in url) h = 31 * h + c.code
        return java.lang.Long.toHexString(h)
    }

    private fun classify(
        entries: List<M3uEntry>
    ): Map<Section, Pair<List<Category>, List<StreamItem>>> {
        val bySection = entries.groupBy { sectionOf(it) }
        val result = LinkedHashMap<Section, Pair<List<Category>, List<StreamItem>>>()

        for (section in Section.entries) {
            val list = bySection[section].orEmpty()
            if (list.isEmpty()) {
                result[section] = emptyList<Category>() to emptyList()
                continue
            }
            val groups = LinkedHashMap<String, Int>()
            list.forEach { groups[it.group] = (groups[it.group] ?: 0) + 1 }

            val categories = groups.entries.map { (name, count) ->
                Category(
                    id = stableId("cat:$name"),
                    name = name,
                    count = count,
                    adult = XtreamApi.isAdultName(name)
                )
            }
            val catIdByName = categories.associate { it.name to it.id }

            val items = list.map { e ->
                StreamItem(
                    id = stableId(e.url),
                    name = e.name,
                    icon = e.logo,
                    extension = e.url.substringAfterLast('.', "").take(5),
                    categoryId = catIdByName[e.group].orEmpty(),
                    rating = 0.0,
                    added = 0L,
                    number = e.number,
                    epgChannelId = e.tvgId,
                    archiveDays = 0,
                    url = e.url
                )
            }
            result[section] = categories to items
        }
        return result
    }
}
