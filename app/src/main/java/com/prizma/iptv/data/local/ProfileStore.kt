package com.prizma.iptv.data.local

import android.content.Context
import android.content.SharedPreferences
import com.prizma.iptv.data.model.Profile
import com.prizma.iptv.data.model.SourceType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Kayıtlı aboneliklerin listesi. Şifreler cihazda tutulduğu için bu dosya
 * yedeklemenin dışında bırakılır (bkz. res/xml/backup_rules.xml).
 */
object ProfileStore {

    private const val FILE = "prizma_secure"
    private const val KEY = "profiles"

    private lateinit var sp: SharedPreferences

    private val _profiles = MutableStateFlow<List<Profile>>(emptyList())
    val profiles: StateFlow<List<Profile>> = _profiles

    fun init(ctx: Context) {
        if (::sp.isInitialized) return
        // Application.attachBaseContext icinden cagrildiginda applicationContext
        // henuz null olur; boyle durumlarda verilen context dogrudan kullanilir.
        val target = ctx.applicationContext ?: ctx
        sp = target.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        _profiles.value = read()
        migrateLegacy(ctx)
    }

    private fun read(): List<Profile> {
        val raw = sp.getString(KEY, "[]") ?: "[]"
        val arr = runCatching { JSONArray(raw) }.getOrDefault(JSONArray())
        val out = ArrayList<Profile>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out.add(
                Profile(
                    id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
                    label = o.optString("label"),
                    type = runCatching { SourceType.valueOf(o.optString("type")) }
                        .getOrDefault(SourceType.XTREAM),
                    host = o.optString("host"),
                    user = o.optString("user"),
                    pass = o.optString("pass"),
                    epgUrl = o.optString("epg"),
                    userAgent = o.optString("ua"),
                    createdAt = o.optLong("at")
                )
            )
        }
        return out
    }

    private fun persist(list: List<Profile>) {
        val arr = JSONArray()
        list.forEach { p ->
            arr.put(JSONObject().apply {
                put("id", p.id)
                put("label", p.label)
                put("type", p.type.name)
                put("host", p.host)
                put("user", p.user)
                put("pass", p.pass)
                put("epg", p.epgUrl)
                put("ua", p.userAgent)
                put("at", p.createdAt)
            })
        }
        sp.edit().putString(KEY, arr.toString()).apply()
        _profiles.value = list
    }

    fun all(): List<Profile> = _profiles.value

    fun byId(id: String): Profile? = _profiles.value.firstOrNull { it.id == id }

    /** Aynı sunucu + kullanıcı ikilisi varsa günceller, yoksa ekler. */
    fun upsert(profile: Profile): Profile {
        val list = _profiles.value.toMutableList()
        val idx = list.indexOfFirst {
            it.id == profile.id ||
                (it.host.equals(profile.host, true) &&
                    it.user == profile.user &&
                    it.type == profile.type)
        }
        val saved = if (idx >= 0) {
            val merged = profile.copy(id = list[idx].id, createdAt = list[idx].createdAt)
            list[idx] = merged
            merged
        } else {
            val fresh = profile.copy(
                id = profile.id.ifBlank { UUID.randomUUID().toString() },
                createdAt = System.currentTimeMillis()
            )
            list.add(fresh)
            fresh
        }
        persist(list)
        return saved
    }

    fun remove(id: String) {
        persist(_profiles.value.filterNot { it.id == id })
        if (Settings.lastProfileId == id) Settings.lastProfileId = ""
    }

    fun active(): Profile? {
        val list = _profiles.value
        if (list.isEmpty()) return null
        return list.firstOrNull { it.id == Settings.lastProfileId } ?: list.first()
    }

    fun setActive(profile: Profile) {
        Settings.lastProfileId = profile.id
    }

    /**
     * 1.x sürümünde profiller "prizma_prefs" içinde tutuluyordu.
     * İlk açılışta bir kereye mahsus taşınır ki kullanıcı yeniden giriş yapmasın.
     */
    private fun migrateLegacy(ctx: Context) {
        if (_profiles.value.isNotEmpty()) return
        val old = ctx.getSharedPreferences("prizma_prefs", Context.MODE_PRIVATE)
        val raw = old.getString("profiles", "") ?: ""
        if (raw.isBlank()) return
        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return
        val migrated = ArrayList<Profile>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val host = o.optString("host")
            if (host.isBlank()) continue
            migrated.add(
                Profile(
                    id = UUID.randomUUID().toString(),
                    label = o.optString("label"),
                    type = SourceType.XTREAM,
                    host = host,
                    user = o.optString("user"),
                    pass = o.optString("pass"),
                    createdAt = System.currentTimeMillis()
                )
            )
        }
        if (migrated.isEmpty()) return
        persist(migrated)

        val activeKey = old.getString("active", "") ?: ""
        val match = migrated.firstOrNull { it.host + "|" + it.user == activeKey }
        Settings.lastProfileId = (match ?: migrated.first()).id
        old.edit().clear().apply()
    }
}
