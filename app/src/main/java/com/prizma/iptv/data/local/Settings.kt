package com.prizma.iptv.data.local

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.security.MessageDigest
import java.util.UUID

enum class DecoderMode { HARDWARE, SOFTWARE }

/** Izgara yoğunluğu — telefon/tablet/TV'de sütun genişliğini belirler (dp). */
enum class GridDensity(val posterWidthDp: Int, val channelWidthDp: Int) {
    COMPACT(100, 132),
    NORMAL(124, 164),
    LARGE(156, 208)
}

enum class AspectMode { FIT, CROP, STRETCH, RATIO_16_9, RATIO_4_3 }

/**
 * Uygulama genelindeki ayarlar. Yazma işlemleri [revision] akışını ilerletir,
 * böylece Compose ekranları ayar değişince kendini yeniler.
 */
object Settings {

    private const val FILE = "prizma_settings"

    private lateinit var sp: SharedPreferences

    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision

    fun init(ctx: Context) {
        if (::sp.isInitialized) return
        sp = ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    }

    private fun bump() {
        _revision.value = _revision.value + 1
    }

    private fun putInt(k: String, v: Int) = sp.edit().putInt(k, v).apply().also { bump() }
    private fun putLong(k: String, v: Long) = sp.edit().putLong(k, v).apply().also { bump() }
    private fun putBool(k: String, v: Boolean) = sp.edit().putBoolean(k, v).apply().also { bump() }
    private fun putStr(k: String, v: String) = sp.edit().putString(k, v).apply().also { bump() }

    // ---- Oynatma ----

    /** Saniye cinsinden hedef tampon. 10 / 30 / 60 / 120 */
    var bufferSeconds: Int
        get() = sp.getInt("buffer", 30)
        set(v) = putInt("buffer", v.coerceIn(5, 240))

    var autoNext: Boolean
        get() = sp.getBoolean("auto_next", true)
        set(v) = putBool("auto_next", v)

    var resumeEnabled: Boolean
        get() = sp.getBoolean("resume", true)
        set(v) = putBool("resume", v)

    var autoplayLastChannel: Boolean
        get() = sp.getBoolean("autoplay_last", false)
        set(v) = putBool("autoplay_last", v)

    var decoderMode: DecoderMode
        get() = runCatching { DecoderMode.valueOf(sp.getString("decoder", "") ?: "") }
            .getOrDefault(DecoderMode.HARDWARE)
        set(v) = putStr("decoder", v.name)

    var tunneling: Boolean
        get() = sp.getBoolean("tunneling", false)
        set(v) = putBool("tunneling", v)

    var autoReconnect: Boolean
        get() = sp.getBoolean("auto_reconnect", true)
        set(v) = putBool("auto_reconnect", v)

    var backgroundAudio: Boolean
        get() = sp.getBoolean("bg_audio", true)
        set(v) = putBool("bg_audio", v)

    var autoPip: Boolean
        get() = sp.getBoolean("auto_pip", true)
        set(v) = putBool("auto_pip", v)

    var aspectMode: AspectMode
        get() = runCatching { AspectMode.valueOf(sp.getString("aspect", "") ?: "") }
            .getOrDefault(AspectMode.FIT)
        set(v) = putStr("aspect", v.name)

    var subtitleScale: Float
        get() = sp.getFloat("sub_scale", 0.06f)
        set(v) = sp.edit().putFloat("sub_scale", v).apply().also { bump() }

    var subtitleBackground: Boolean
        get() = sp.getBoolean("sub_bg", false)
        set(v) = putBool("sub_bg", v)

    // ---- Görünüm ----

    var gridDensity: GridDensity
        get() = runCatching { GridDensity.valueOf(sp.getString("grid", "") ?: "") }
            .getOrDefault(GridDensity.NORMAL)
        set(v) = putStr("grid", v.name)

    var accentColor: Long
        get() = sp.getLong("accent", 0xFF4F8DF7L)
        set(v) = putLong("accent", v)

    /** "" = sistem dili, "tr", "en" */
    var language: String
        get() = sp.getString("lang", "") ?: ""
        set(v) = putStr("lang", v)

    var forceTvUi: Boolean
        get() = sp.getBoolean("force_tv", false)
        set(v) = putBool("force_tv", v)

    // ---- Katalog / EPG ----

    var catalogTtlHours: Int
        get() = sp.getInt("catalog_ttl", 12)
        set(v) = putInt("catalog_ttl", v.coerceIn(1, 168))

    var epgDays: Int
        get() = sp.getInt("epg_days", 3)
        set(v) = putInt("epg_days", v.coerceIn(1, 14))

    var epgAutoRefresh: Boolean
        get() = sp.getBoolean("epg_auto", true)
        set(v) = putBool("epg_auto", v)

    // ---- Ebeveyn kontrolü ----

    var hideAdult: Boolean
        get() = sp.getBoolean("hide_adult", false)
        set(v) = putBool("hide_adult", v)

    var lockAdult: Boolean
        get() = sp.getBoolean("lock_adult", false)
        set(v) = putBool("lock_adult", v)

    val hasPin: Boolean get() = !(sp.getString("pin_hash", "") ?: "").isBlank()

    private fun salt(): String {
        val existing = sp.getString("pin_salt", "") ?: ""
        if (existing.isNotBlank()) return existing
        val fresh = UUID.randomUUID().toString()
        sp.edit().putString("pin_salt", fresh).apply()
        return fresh
    }

    private fun hash(pin: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest((salt() + pin).toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun setPin(pin: String) = putStr("pin_hash", hash(pin))

    fun clearPin() = putStr("pin_hash", "")

    fun checkPin(pin: String): Boolean {
        val stored = sp.getString("pin_hash", "") ?: ""
        if (stored.isBlank()) return true
        return stored == hash(pin)
    }

    // ---- Oturum durumu ----

    var lastProfileId: String
        get() = sp.getString("last_profile", "") ?: ""
        set(v) = putStr("last_profile", v)

    /**
     * Kullanıcı bilerek çıkış yaptıysa açılışta profiller dursa bile
     * giriş ekranı gösterilir.
     */
    var signedOut: Boolean
        get() = sp.getBoolean("signed_out", false)
        set(v) = putBool("signed_out", v)

    /** En son izlenen canlı kanal (PlayItem JSON). */
    var lastLiveItem: String
        get() = sp.getString("last_live", "") ?: ""
        set(v) = sp.edit().putString("last_live", v).apply()

    fun epgUpdatedAt(profileId: String): Long = sp.getLong("epg_at_" + profileId, 0L)

    fun setEpgUpdatedAt(profileId: String, ts: Long) = putLong("epg_at_" + profileId, ts)
}
