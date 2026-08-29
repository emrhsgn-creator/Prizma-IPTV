package com.prizma.iptv

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

    private suspend fun request(
        host: String, user: String, pass: String, params: String
    ): String = withContext(Dispatchers.IO) {
        val url = host + "/player_api.php?username=" + enc(user) + "&password=" + enc(pass) + params
        val req = Request.Builder().url(url).header("User-Agent", "PrizmaIPTV/1.0").build()
        client.newCall(req).execute().use { res ->
            if (!res.isSuccessful) throw Exception("Sunucu hatası: HTTP ${res.code}")
            res.body?.string().orEmpty()
        }
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
        if (info.opt("auth")?.toString() != "1") throw Exception("Kullanıcı adı veya şifre hatalı.")
        val status = info.optString("status", "-")
        if (!status.equals("Active", true)) throw Exception("Hesap aktif değil (durum: $status).")
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
        val body = request(host, user, pass, "&action=" + section.categoryAction)
        val arr = try { JSONArray(body) } catch (e: Exception) { JSONArray() }
        val out = ArrayList<Category>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out.add(
                Category(
                    o.opt("category_id")?.toString().orEmpty(),
                    o.optString("category_name", "Kategori")
                )
            )
        }
        out
    }

    suspend fun allStreams(
        host: String, user: String, pass: String, section: Section
    ): List<StreamItem> = withContext(Dispatchers.IO) {
        val body = request(host, user, pass, "&action=" + section.streamAction)
        val arr = try { JSONArray(body) } catch (e: Exception) { JSONArray() }
        val out = ArrayList<StreamItem>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out.add(
                StreamItem(
                    id = o.opt(section.idKey)?.toString().orEmpty(),
                    name = o.optString("name", "Adsız"),
                    icon = o.optString(section.iconKey, ""),
                    extension = o.optString("container_extension", ""),
                    categoryId = o.opt("category_id")?.toString().orEmpty(),
                    rating = parseRating(o),
                    added = parseAdded(o)
                )
            )
        }
        out
    }

    private fun parseRating(o: JSONObject): String {
        val five = o.opt("rating_5based")?.toString()?.toDoubleOrNull()
        val ten = o.opt("rating")?.toString()?.toDoubleOrNull()
        val v = when {
            five != null && five > 0.0 -> five
            ten != null && ten > 0.0 -> ten / 2.0
            else -> return ""
        }
        return String.format(Locale.getDefault(), "%.1f", v)
    }

    private fun parseAdded(o: JSONObject): Long {
        val a = o.opt("added")?.toString()?.toLongOrNull()
        if (a != null) return a
        return o.opt("last_modified")?.toString()?.toLongOrNull() ?: 0L
    }
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
        val arr = root.optJSONArray("epg_listings") ?: return@withContext emptyList()
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
