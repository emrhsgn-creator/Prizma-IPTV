package com.prizma.iptv.data.remote

import android.util.Base64
import com.prizma.iptv.R
import com.prizma.iptv.core.Http
import com.prizma.iptv.core.appError
import com.prizma.iptv.data.model.Account
import com.prizma.iptv.data.model.Category
import com.prizma.iptv.data.model.EpgProgram
import com.prizma.iptv.data.model.Episode
import com.prizma.iptv.data.model.Profile
import com.prizma.iptv.data.model.SeasonBundle
import com.prizma.iptv.data.model.Section
import com.prizma.iptv.data.model.SeriesInfo
import com.prizma.iptv.data.model.ServerInfo
import com.prizma.iptv.data.model.StreamItem
import com.prizma.iptv.data.model.VodInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Xtream Codes panel istemcisi. Tum cagrilar askiya alinabilir ve IO dispatcher
 * uzerinde calisir; gecici ag hatalarinda ustel bekleme ile yeniden dener.
 */
object XtreamApi {

    private const val MAX_ATTEMPTS = 3

    private data class Endpoint(
        val categories: String,
        val streams: String,
        val idKey: String,
        val iconKey: String
    )

    private fun endpoint(section: Section) = when (section) {
        Section.LIVE ->
            Endpoint("get_live_categories", "get_live_streams", "stream_id", "stream_icon")
        Section.VOD ->
            Endpoint("get_vod_categories", "get_vod_streams", "stream_id", "stream_icon")
        Section.SERIES ->
            Endpoint("get_series_categories", "get_series", "series_id", "cover")
    }

    // ------------------------------------------------------------ yardimcilar

    /** ornek.com:8080/player_api.php gibi girdileri http://ornek.com:8080 haline getirir. */
    fun normalizeHost(raw: String): String {
        var h = raw.trim()
        if (h.isEmpty()) return h
        if (!h.startsWith("http://", true) && !h.startsWith("https://", true)) h = "http://$h"
        h = h.substringBefore("/player_api.php")
            .substringBefore("/get.php")
            .substringBefore("/xmltv.php")
            .substringBefore("?")
        while (h.endsWith("/")) h = h.dropLast(1)
        return h
    }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    private fun client(bulk: Boolean): OkHttpClient = if (bulk) Http.bulk else Http.api

    private fun userAgent(profile: Profile): String =
        profile.userAgent.ifBlank { Http.DEFAULT_USER_AGENT }

    private suspend fun request(
        profile: Profile,
        params: String,
        bulk: Boolean = false
    ): String = withContext(Dispatchers.IO) {
        val url = profile.host + "/player_api.php?username=" + enc(profile.user) +
            "&password=" + enc(profile.pass) + params
        var lastError: Exception? = null
        var attempt = 0
        while (attempt < MAX_ATTEMPTS) {
            try {
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", userAgent(profile))
                    .header("Accept", "application/json, text/plain, */*")
                    .build()
                client(bulk).newCall(req).execute().use { res ->
                    if (res.code == 401 || res.code == 403) throw appError(R.string.error_auth)
                    if (!res.isSuccessful) throw appError(R.string.error_http, res.code)
                    return@withContext res.body?.string().orEmpty()
                }
            } catch (e: IOException) {
                lastError = e
                attempt++
                if (attempt < MAX_ATTEMPTS) delay(600L * attempt)
            }
        }
        throw lastError ?: appError(R.string.error_network)
    }

    private fun asArray(body: String): JSONArray {
        runCatching { return JSONArray(body) }
        // Bos sonuclarda bazi paneller dizi yerine nesne ya da duz metin donuyor.
        if (runCatching { JSONObject(body) }.isSuccess) return JSONArray()
        throw appError(R.string.error_parse)
    }

    /** Puani her zaman 10 uzerinden normalize eder. */
    private fun rating(o: JSONObject): Double {
        val five = o.opt("rating_5based")?.toString()?.replace(',', '.')?.toDoubleOrNull()
        if (five != null && five > 0.0) return (five * 2.0).coerceAtMost(10.0)
        val ten = o.opt("rating")?.toString()?.replace(',', '.')?.toDoubleOrNull()
        if (ten != null && ten > 0.0) return ten.coerceAtMost(10.0)
        return 0.0
    }

    /** Panel added alanini saniye verir; icerde her yerde milisaniye kullanilir. */
    private fun addedMs(o: JSONObject): Long {
        val raw = o.opt("added")?.toString()?.toLongOrNull()
            ?: o.opt("last_modified")?.toString()?.toLongOrNull()
            ?: return 0L
        return if (raw > 100_000_000_000L) raw else raw * 1000L
    }

    private fun decodeB64(s: String): String {
        if (s.isBlank()) return ""
        return runCatching {
            String(Base64.decode(s, Base64.DEFAULT), Charsets.UTF_8)
        }.getOrDefault(s)
    }

    private val adultPattern = Regex(
        "(^|[^a-z0-9])(xxx|adult|porn|erotic|hardcore|18\\+|\\+18|yetiskin)([^a-z0-9]|$)",
        RegexOption.IGNORE_CASE
    )

    fun isAdultName(name: String): Boolean = adultPattern.containsMatchIn(name)

    // ------------------------------------------------------------ hesap

    suspend fun login(profile: Profile): Pair<Account, ServerInfo> {
        val body = request(profile, "")
        val root = runCatching { JSONObject(body) }
            .getOrElse { throw appError(R.string.error_parse) }
        val info = root.optJSONObject("user_info") ?: throw appError(R.string.error_no_account_info)

        if (info.opt("auth")?.toString() != "1") throw appError(R.string.error_auth)

        val status = info.optString("status", "-")
        val expirySecs = info.opt("exp_date")?.toString()?.toLongOrNull() ?: 0L
        val expiryMs = if (expirySecs > 0L) expirySecs * 1000L else 0L

        if (!status.equals("Active", true)) {
            if (status.equals("Expired", true)) throw appError(R.string.error_account_expired)
            throw appError(R.string.error_account_inactive, status)
        }
        if (expiryMs in 1 until System.currentTimeMillis()) {
            throw appError(R.string.error_account_expired)
        }

        val account = Account(
            username = info.optString("username", profile.user),
            status = status,
            expiryMs = expiryMs,
            maxConnections = info.opt("max_connections")?.toString() ?: "-",
            activeConnections = info.opt("active_cons")?.toString() ?: "-",
            isTrial = info.opt("is_trial")?.toString() == "1",
            message = info.optString("message", "")
        )

        val srv = root.optJSONObject("server_info")
        val server = ServerInfo(
            url = srv?.optString("url").orEmpty(),
            port = srv?.optString("port").orEmpty(),
            httpsPort = srv?.optString("https_port").orEmpty(),
            protocol = srv?.optString("server_protocol", "http").orEmpty().ifBlank { "http" },
            timezone = srv?.optString("timezone").orEmpty()
        )
        return account to server
    }

    // ------------------------------------------------------------ katalog

    suspend fun categories(profile: Profile, section: Section): List<Category> {
        val arr = asArray(request(profile, "&action=" + endpoint(section).categories))
        val out = ArrayList<Category>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val name = o.optString("category_name", "")
            if (name.isBlank()) continue
            out.add(
                Category(
                    id = o.opt("category_id")?.toString().orEmpty(),
                    name = name,
                    adult = isAdultName(name)
                )
            )
        }
        return out
    }

    suspend fun streams(profile: Profile, section: Section): List<StreamItem> {
        val ep = endpoint(section)
        val arr = asArray(request(profile, "&action=" + ep.streams, bulk = true))
        val out = ArrayList<StreamItem>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.opt(ep.idKey)?.toString().orEmpty()
            if (id.isBlank() || id == "null") continue
            val name = o.optString("name", "")
            out.add(
                StreamItem(
                    id = id,
                    name = if (name.isBlank()) "#$id" else name,
                    icon = o.optString(ep.iconKey, ""),
                    extension = o.optString("container_extension", ""),
                    categoryId = o.opt("category_id")?.toString().orEmpty(),
                    rating = rating(o),
                    added = addedMs(o),
                    number = o.opt("num")?.toString()?.toIntOrNull() ?: 0,
                    epgChannelId = o.optString("epg_channel_id", ""),
                    archiveDays = if (o.opt("tv_archive")?.toString() == "1") {
                        (o.opt("tv_archive_duration")?.toString()?.toIntOrNull() ?: 7)
                            .coerceIn(1, 30)
                    } else 0
                )
            )
        }
        return out
    }

    suspend fun seriesInfo(profile: Profile, seriesId: String): SeriesInfo {
        val body = request(profile, "&action=get_series_info&series_id=" + enc(seriesId))
        val root = runCatching { JSONObject(body) }
            .getOrElse { throw appError(R.string.error_parse) }
        val info = root.optJSONObject("info") ?: JSONObject()
        val episodesNode = root.opt("episodes")

        val seasons = ArrayList<SeasonBundle>()

        fun parseSeason(seasonKey: String, arr: JSONArray) {
            val seasonNo = seasonKey.toIntOrNull() ?: 0
            val list = ArrayList<Episode>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.opt("id")?.toString().orEmpty()
                if (id.isBlank()) continue
                val ei = o.optJSONObject("info") ?: JSONObject()
                val title = o.optString("title", "")
                val epNo = o.opt("episode_num")?.toString()?.toIntOrNull() ?: (i + 1)
                list.add(
                    Episode(
                        id = id,
                        title = if (title.isBlank()) "$epNo" else title,
                        season = o.opt("season")?.toString()?.toIntOrNull() ?: seasonNo,
                        episodeNum = epNo,
                        extension = o.optString("container_extension", "mp4").ifBlank { "mp4" },
                        plot = ei.optString("plot", ei.optString("description", "")),
                        durationSecs = ei.opt("duration_secs")?.toString()?.toIntOrNull() ?: 0,
                        icon = ei.optString("movie_image", ei.optString("cover_big", "")),
                        added = addedMs(o),
                        rating = rating(ei)
                    )
                )
            }
            if (list.isNotEmpty()) {
                seasons.add(SeasonBundle(seasonNo, list.sortedBy { it.episodeNum }))
            }
        }

        when (episodesNode) {
            is JSONObject -> episodesNode.keys().asSequence().toList()
                .sortedBy { it.toIntOrNull() ?: Int.MAX_VALUE }
                .forEach { key ->
                    val arr = episodesNode.optJSONArray(key)
                    if (arr != null) parseSeason(key, arr)
                }
            // Bazi paneller sezonlari dizi olarak veriyor.
            is JSONArray -> for (i in 0 until episodesNode.length()) {
                val arr = episodesNode.optJSONArray(i)
                if (arr != null) parseSeason(i.toString(), arr)
            }
        }

        return SeriesInfo(
            plot = info.optString("plot", ""),
            cover = info.optString("cover", ""),
            backdrop = info.optJSONArray("backdrop_path")?.optString(0, "").orEmpty(),
            genre = info.optString("genre", ""),
            releaseDate = info.optString("releaseDate", info.optString("release_date", "")),
            cast = info.optString("cast", info.optString("actors", "")),
            director = info.optString("director", ""),
            rating = rating(info),
            seasons = seasons.sortedBy { it.number }
        )
    }

    suspend fun vodInfo(profile: Profile, vodId: String): VodInfo {
        val body = request(profile, "&action=get_vod_info&vod_id=" + enc(vodId))
        val root = runCatching { JSONObject(body) }
            .getOrElse { throw appError(R.string.error_parse) }
        val info = root.optJSONObject("info") ?: JSONObject()
        val movie = root.optJSONObject("movie_data") ?: JSONObject()

        return VodInfo(
            plot = info.optString("plot", info.optString("description", "")),
            cover = info.optString("movie_image", info.optString("cover_big", "")),
            backdrop = info.optJSONArray("backdrop_path")?.optString(0, "").orEmpty(),
            genre = info.optString("genre", ""),
            releaseDate = info.optString("releasedate", info.optString("release_date", "")),
            cast = info.optString("cast", info.optString("actors", "")),
            director = info.optString("director", ""),
            duration = info.optString("duration", ""),
            country = info.optString("country", ""),
            rating = rating(info),
            youtube = info.optString("youtube_trailer", ""),
            extension = movie.optString("container_extension", "mp4").ifBlank { "mp4" }
        )
    }

    // ------------------------------------------------------------ EPG

    private fun parseEpgArray(arr: JSONArray, channelId: String): List<EpgProgram> {
        val out = ArrayList<EpgProgram>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val start = o.opt("start_timestamp")?.toString()?.toLongOrNull() ?: 0L
            val stop = o.opt("stop_timestamp")?.toString()?.toLongOrNull() ?: 0L
            if (start <= 0L) continue
            out.add(
                EpgProgram(
                    channelId = channelId,
                    start = start * 1000L,
                    stop = (if (stop > start) stop else start + 1800L) * 1000L,
                    title = decodeB64(o.optString("title", "")),
                    description = decodeB64(o.optString("description", ""))
                )
            )
        }
        return out.sortedBy { it.start }
    }

    /** Bir kanalin yakin gelecekteki programlari. */
    suspend fun shortEpg(profile: Profile, streamId: String, limit: Int = 12): List<EpgProgram> {
        val body = request(
            profile,
            "&action=get_short_epg&stream_id=" + enc(streamId) + "&limit=" + limit
        )
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return emptyList()
        val arr = root.optJSONArray("epg_listings") ?: return emptyList()
        return parseEpgArray(arr, streamId)
    }

    /** Bir kanalin panelde bulunan tum akisi. */
    suspend fun fullEpg(profile: Profile, streamId: String): List<EpgProgram> {
        val body = request(profile, "&action=get_simple_data_table&stream_id=" + enc(streamId))
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return emptyList()
        val arr = root.optJSONArray("epg_listings") ?: return emptyList()
        return parseEpgArray(arr, streamId)
    }

    /** Panelin XMLTV ciktisi: tum kanallarin akisini tek indirmede getirir. */
    fun xmltvUrl(profile: Profile): String =
        profile.epgUrl.ifBlank {
            profile.host + "/xmltv.php?username=" + enc(profile.user) +
                "&password=" + enc(profile.pass)
        }

    // ------------------------------------------------------------ akis adresleri

    fun liveUrl(profile: Profile, streamId: String, extension: String = "ts"): String {
        val ext = extension.ifBlank { "ts" }
        return profile.host + "/live/" + enc(profile.user) + "/" + enc(profile.pass) +
            "/" + streamId + "." + ext
    }

    fun movieUrl(profile: Profile, streamId: String, extension: String): String {
        val ext = extension.ifBlank { "mp4" }
        return profile.host + "/movie/" + enc(profile.user) + "/" + enc(profile.pass) +
            "/" + streamId + "." + ext
    }

    fun episodeUrl(profile: Profile, episodeId: String, extension: String): String {
        val ext = extension.ifBlank { "mp4" }
        return profile.host + "/series/" + enc(profile.user) + "/" + enc(profile.pass) +
            "/" + episodeId + "." + ext
    }

    private val catchupStamp: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd:HH-mm", Locale.US)

    /**
     * Catch-up (timeshift) adresi. Paneller birden fazla bicimi destekler;
     * en yaygin olan timeshift.php kullanilir.
     */
    fun catchupUrl(
        profile: Profile,
        streamId: String,
        startMs: Long,
        durationMinutes: Int
    ): String {
        val stamp = catchupStamp.format(
            Instant.ofEpochMilli(startMs).atZone(ZoneId.systemDefault())
        )
        return profile.host + "/streaming/timeshift.php?username=" + enc(profile.user) +
            "&password=" + enc(profile.pass) +
            "&stream=" + streamId +
            "&start=" + stamp +
            "&duration=" + durationMinutes.coerceAtLeast(1)
    }
}
