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

data class Category(val id: String, val name: String)

data class StreamItem(
    val id: String,
    val name: String,
    val icon: String,
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
        .readTimeout(30, TimeUnit.SECONDS)
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

    suspend fun login(host: String, user: String, pass: String): Account {
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
        return Account(
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
    ): List<Category> {
        val body = request(host, user, pass, "&action=" + section.categoryAction)
        val arr = try { JSONArray(body) } catch (e: Exception) { JSONArray() }
        val out = ArrayList<Category>()
        out.add(Category("", "Tümü"))
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out.add(
                Category(
                    o.opt("category_id")?.toString().orEmpty(),
                    o.optString("category_name", "Kategori")
                )
            )
        }
        return out
    }

    suspend fun streams(
        host: String, user: String, pass: String, section: Section, categoryId: String
    ): List<StreamItem> {
        val extra = if (categoryId.isEmpty()) "" else "&category_id=" + enc(categoryId)
        val body = request(host, user, pass, "&action=" + section.streamAction + extra)
        val arr = try { JSONArray(body) } catch (e: Exception) { JSONArray() }
        val out = ArrayList<StreamItem>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out.add(
                StreamItem(
                    id = o.opt(section.idKey)?.toString().orEmpty(),
                    name = o.optString("name", "Adsız"),
                    icon = o.optString(section.iconKey, ""),
                    extension = o.optString("container_extension", "")
                )
            )
        }
        return out
    }
}
