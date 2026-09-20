package com.prizma.iptv

import android.util.JsonReader
import android.util.JsonToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

data class Account(
    val username: String,
    val status: String,
    val expiry: String,
    val maxConnections: String,
    val activeConnections: String
)

/**
 * Sunucu kimlik bilgilerini reddetti ya da hesap kapali.
 *
 * Ag hatasindan ayri tutuluyor: agdan kaynaklanan bir hatada elimizdeki
 * onbellekle devam edebiliriz, ama kimlik reddedildiyse giris ekranina
 * donmek gerekir. Onceden ikisi de duz Exception'di ve cagiran taraf
 * ayirt edemiyordu.
 */
class AuthRejected(message: String) : Exception(message)

data class Category(val id: String, val name: String, val count: Int = 0)

data class StreamItem(
    val id: String,
    val name: String,
    val icon: String,
    val extension: String,
    val categoryId: String,
    val rating: String,
    val added: Long
)
data class Episode(
    val id: String,
    val title: String,
    val season: Int,
    val episodeNum: Int,
    val extension: String,
    val plot: String,
    val duration: String,
    val icon: String
)

data class SeriesInfo(
    val plot: String,
    val cover: String,
    val genre: String,
    val releaseDate: String,
    val cast: String,
    val rating: String,
    val seasons: Map<Int, List<Episode>>
)
data class EpgItem(
    val title: String,
    val description: String,
    val start: Long,
    val stop: Long
)
data class VodInfo(
    val plot: String,
    val cover: String,
    val backdrop: String,
    val genre: String,
    val releaseDate: String,
    val cast: String,
    val director: String,
    val duration: String,
    val country: String,
    val rating: String,
    val youtube: String,
    val extension: String
)
enum class Section(
    val title: String,
    val categoryAction: String,
    val streamAction: String,
    val idKey: String,
    val iconKey: String
) {
    LIVE("Canlı TV", "get_live_categories", "get_live_streams", "stream_id", "stream_icon"),
    VOD("Filmler", "get_vod_categories", "get_vod_streams", "stream_id", "stream_icon"),
    SERIES("Diziler", "get_series_categories", "get_series", "series_id", "cover")
}

object XtreamApi {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    fun normalizeHost(raw: String): String {
        var h = raw.trim()
        if (h.isEmpty()) return h
        if (!h.startsWith("http://", true) && !h.startsWith("https://", true)) h = "http://$h"
        h = h.substringBefore("/player_api.php").substringBefore("/get.php")
        while (h.endsWith("/")) h = h.dropLast(1)
        return h
    }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    private fun urlFor(host: String, user: String, pass: String, params: String): String =
        host + "/player_api.php?username=" + enc(user) + "&password=" + enc(pass) + params

    /** Kucuk yanitlar icin: giris, dizi/film detayi, yayin akisi. */
    private suspend fun request(
        host: String, user: String, pass: String, params: String
    ): String = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(urlFor(host, user, pass, params))
            .header("User-Agent", "PrizmaIPTV/1.0").build()
        client.newCall(req).execute().use { res ->
            if (!res.isSuccessful) throw Exception("Sunucu hatası: HTTP ${res.code}")
            res.body?.string().orEmpty()
        }
    }

    /**
     * Buyuk listeler icin akis tabanli okuma.
     *
     * Onceden butun govde once String'e, sonra JSONArray nesne agacina
     * aliniyor, ardindan ucuncu kez veri sinifi listesine kopyalaniyordu;
     * ucu de ayni anda bellekteydi. Buyuk bir saglayicida (on binlerce kanal
     * ve film) tepe kullanim 1-2 GB RAM'li bir TV stick'i zorluyor, GC
     * baskisini yukseltiyordu (cihazda 47 dakikada 327 GC olculdu).
     *
     * JsonReader akisi tek geciste okur: yalnizca sonuc listesi bellekte
     * kalir.
     */
    private fun <T> streamJson(
        host: String, user: String, pass: String, params: String, parse: (JsonReader) -> T
    ): T {
        val req = Request.Builder().url(urlFor(host, user, pass, params))
            .header("User-Agent", "PrizmaIPTV/1.0").build()
        client.newCall(req).execute().use { res ->
            if (!res.isSuccessful) throw Exception("Sunucu hatası: HTTP ${res.code}")
            val body = res.body ?: throw Exception("Sunucu boş yanıt verdi.")
            return JsonReader(body.charStream()).use { r ->
                // Bazi Xtream panelleri basta BOM ya da fazladan bosluk
                // gonderiyor; katı mod bunlarda okumayi bastan kesiyordu.
                r.isLenient = true
                parse(r)
            }
        }
    }

    /**
     * Degeri tipine bakmadan metne cevirir.
     *
     * Xtream panelleri ayni alani kimi zaman sayi kimi zaman metin olarak
     * gonderiyor; eski kod bunu JSONObject.opt().toString() ile asiyordu.
     */
    private fun JsonReader.nextText(): String = when (peek()) {
        JsonToken.NULL -> { nextNull(); "" }
        JsonToken.BOOLEAN -> nextBoolean().toString()
        JsonToken.NUMBER, JsonToken.STRING -> nextString()
        else -> { skipValue(); "" }
    }

    private fun ratingOf(five: String, ten: String): String {
        val f = five.toDoubleOrNull()
        val t = ten.toDoubleOrNull()
        val v = when {
            f != null && f > 0.0 -> f
            t != null && t > 0.0 -> t / 2.0
            else -> return ""
        }
        return String.format(Locale.getDefault(), "%.1f", v)
    }

    suspend fun login(
        host: String, user: String, pass: String
    ): Account = withContext(Dispatchers.IO) {
        val body = request(host, user, pass, "")
        val root = try {
            JSONObject(body)
        } catch (e: Exception) {
            throw Exception("Sunucu geçerli bir yanıt vermedi. Adresi kontrol et.")
        }
        val info = root.optJSONObject("user_info") ?: throw Exception("Hesap bilgisi alınamadı.")
        if (info.opt("auth")?.toString() != "1") {
            throw AuthRejected("Kullanıcı adı veya şifre hatalı.")
        }
        val status = info.optString("status", "-")
        if (!status.equals("Active", true)) {
            throw AuthRejected("Hesap aktif değil (durum: $status).")
        }
        Account(
            username = info.optString("username", user),
            status = status,
            expiry = formatDate(info.opt("exp_date")?.toString()),
            maxConnections = info.opt("max_connections")?.toString() ?: "-",
            activeConnections = info.opt("active_cons")?.toString() ?: "-"
        )
    }

    private fun formatDate(raw: String?): String {
        val secs = raw?.toLongOrNull() ?: return "Süresiz"
        return SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(secs * 1000))
    }

    suspend fun categories(
        host: String, user: String, pass: String, section: Section
    ): List<Category> = withContext(Dispatchers.IO) {
        try {
            streamJson(host, user, pass, "&action=" + section.categoryAction) { r ->
                val out = ArrayList<Category>()
                if (r.peek() != JsonToken.BEGIN_ARRAY) {
                    r.skipValue()
                    return@streamJson out
                }
                r.beginArray()
                while (r.hasNext()) {
                    if (r.peek() != JsonToken.BEGIN_OBJECT) { r.skipValue(); continue }
                    var id = ""
                    var name = ""
                    r.beginObject()
                    while (r.hasNext()) {
                        when (r.nextName()) {
                            "category_id" -> id = r.nextText()
                            "category_name" -> name = r.nextText()
                            else -> r.skipValue()
                        }
                    }
                    r.endObject()
                    out.add(Category(id, name.ifEmpty { "Kategori" }))
                }
                r.endArray()
                out
            }
        } catch (e: Exception) {
            // Sunucu dizi yerine hata nesnesi donebiliyor; eski davranista da
            // bu durumda bos liste donuluyordu.
            emptyList()
        }
    }

    suspend fun allStreams(
        host: String, user: String, pass: String, section: Section
    ): List<StreamItem> = withContext(Dispatchers.IO) {
        try {
            streamJson(host, user, pass, "&action=" + section.streamAction) { r ->
                val out = ArrayList<StreamItem>(512)
                if (r.peek() != JsonToken.BEGIN_ARRAY) {
                    r.skipValue()
                    return@streamJson out
                }
                r.beginArray()
                while (r.hasNext()) {
                    if (r.peek() != JsonToken.BEGIN_OBJECT) { r.skipValue(); continue }
                    var id = ""
                    var name = ""
                    var icon = ""
                    var ext = ""
                    var catId = ""
                    var five = ""
                    var ten = ""
                    var added: Long? = null
                    var lastMod: Long? = null
                    r.beginObject()
                    while (r.hasNext()) {
                        when (r.nextName()) {
                            section.idKey -> id = r.nextText()
                            section.iconKey -> icon = r.nextText()
                            "name" -> name = r.nextText()
                            "container_extension" -> ext = r.nextText()
                            "category_id" -> catId = r.nextText()
                            "rating_5based" -> five = r.nextText()
                            "rating" -> ten = r.nextText()
                            "added" -> added = r.nextText().toLongOrNull()
                            "last_modified" -> lastMod = r.nextText().toLongOrNull()
                            else -> r.skipValue()
                        }
                    }
                    r.endObject()
                    out.add(
                        StreamItem(
                            id = id,
                            name = name.ifEmpty { "Adsız" },
                            icon = icon,
                            extension = ext,
                            categoryId = catId,
                            rating = ratingOf(five, ten),
                            added = added ?: lastMod ?: 0L
                        )
                    )
                }
                r.endArray()
                out
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseRating(o: JSONObject): String =
        ratingOf(
            o.opt("rating_5based")?.toString().orEmpty(),
            o.opt("rating")?.toString().orEmpty()
        )

    suspend fun seriesInfo(
        host: String, user: String, pass: String, seriesId: String
    ): SeriesInfo = withContext(Dispatchers.IO) {
        val body = request(host, user, pass, "&action=get_series_info&series_id=" + enc(seriesId))
        val root = try { JSONObject(body) } catch (e: Exception) {
            throw Exception("Dizi bilgisi alınamadı.")
        }
        val info = root.optJSONObject("info") ?: JSONObject()
        val epsObj = root.optJSONObject("episodes") ?: JSONObject()

        val seasons = LinkedHashMap<Int, List<Episode>>()
        val keys = epsObj.keys().asSequence().toList()
            .sortedBy { it.toIntOrNull() ?: Int.MAX_VALUE }

        for (k in keys) {
            val arr = epsObj.optJSONArray(k) ?: continue
            val list = ArrayList<Episode>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val ei = o.optJSONObject("info") ?: JSONObject()
                list.add(
                    Episode(
                        id = o.opt("id")?.toString().orEmpty(),
                        title = o.optString("title", "Bölüm"),
                        season = k.toIntOrNull() ?: 0,
                        episodeNum = o.opt("episode_num")?.toString()?.toIntOrNull() ?: (i + 1),
                        extension = o.optString("container_extension", "mp4"),
                        plot = ei.optString("plot", ""),
                        duration = ei.optString("duration", ""),
                        icon = ei.optString("movie_image", "")
                    )
                )
            }
            if (list.isNotEmpty()) {
                seasons[k.toIntOrNull() ?: 0] = list.sortedBy { it.episodeNum }
            }
        }

        SeriesInfo(
            plot = info.optString("plot", ""),
            cover = info.optString("cover", ""),
            genre = info.optString("genre", ""),
            releaseDate = info.optString("releaseDate", info.optString("release_date", "")),
            cast = info.optString("cast", ""),
            rating = parseRating(info),
            seasons = seasons
        )
    }

    suspend fun shortEpg(
        host: String, user: String, pass: String, streamId: String, limit: Int = 8
    ): List<EpgItem> = withContext(Dispatchers.IO) {
        val body = request(
            host, user, pass,
            "&action=get_short_epg&stream_id=" + enc(streamId) + "&limit=" + limit
        )
        val root = try {
            JSONObject(body)
        } catch (e: Exception) {
            return@withContext emptyList()
        }
        val arr: JSONArray = root.optJSONArray("epg_listings") ?: return@withContext emptyList()
        val out = ArrayList<EpgItem>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out.add(
                EpgItem(
                    title = decodeB64(o.optString("title", "")),
                    description = decodeB64(o.optString("description", "")),
                    start = o.opt("start_timestamp")?.toString()?.toLongOrNull() ?: 0L,
                    stop = o.opt("stop_timestamp")?.toString()?.toLongOrNull() ?: 0L
                )
            )
        }
        out.sortedBy { it.start }
    }

    private fun decodeB64(s: String): String {
        if (s.isBlank()) return ""
        return try {
            String(android.util.Base64.decode(s, android.util.Base64.DEFAULT), Charsets.UTF_8)
        } catch (e: Exception) {
            s
        }
    }

    suspend fun vodInfo(
        host: String, user: String, pass: String, vodId: String
    ): VodInfo = withContext(Dispatchers.IO) {
        val body = request(host, user, pass, "&action=get_vod_info&vod_id=" + enc(vodId))
        val root = try { JSONObject(body) } catch (e: Exception) {
            throw Exception("Film bilgisi alınamadı.")
        }
        val info = root.optJSONObject("info") ?: JSONObject()
        val movie = root.optJSONObject("movie_data") ?: JSONObject()

        VodInfo(
            plot = info.optString("plot", info.optString("description", "")),
            cover = info.optString("movie_image", info.optString("cover_big", "")),
            backdrop = info.optJSONArray("backdrop_path")?.optString(0, "").orEmpty(),
            genre = info.optString("genre", ""),
            releaseDate = info.optString("releasedate", info.optString("release_date", "")),
            cast = info.optString("cast", info.optString("actors", "")),
            director = info.optString("director", ""),
            duration = info.optString("duration", ""),
            country = info.optString("country", ""),
            rating = parseRating(info),
            youtube = info.optString("youtube_trailer", ""),
            extension = movie.optString("container_extension", "mp4")
        )
    }
}
