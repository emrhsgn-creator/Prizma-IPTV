package com.prizma.iptv

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Profile(
    val label: String,
    val host: String,
    val user: String,
    val pass: String
)

object Prefs {
    private const val FILE = "prizma_prefs"
    private const val K_PROFILES = "profiles"
    private const val K_ACTIVE = "active"
    private const val K_BUFFER = "buffer"
    private const val K_AUTONEXT = "autonext"

    private fun p(ctx: Context) = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun save(ctx: Context, host: String, user: String, pass: String) {
        val list = profiles(ctx).toMutableList()
        val idx = list.indexOfFirst { it.host == host && it.user == user }
        val label = try {
            host.substringAfter("://").substringBefore(":").substringBefore("/")
        } catch (e: Exception) {
            host
        }
        val prof = Profile(label, host, user, pass)
        if (idx >= 0) list[idx] = prof else list.add(prof)
        writeProfiles(ctx, list)
        p(ctx).edit().putString(K_ACTIVE, host + "|" + user).apply()
    }

    fun load(ctx: Context): Triple<String, String, String>? {
        val active = p(ctx).getString(K_ACTIVE, "") ?: ""
        val list = profiles(ctx)
        val prof = list.firstOrNull { it.host + "|" + it.user == active } ?: list.firstOrNull()
        return prof?.let { Triple(it.host, it.user, it.pass) }
    }

    fun profiles(ctx: Context): List<Profile> {
        val raw = p(ctx).getString(K_PROFILES, "[]") ?: "[]"
        val arr = try { JSONArray(raw) } catch (e: Exception) { JSONArray() }
        val out = ArrayList<Profile>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out.add(
                Profile(
                    o.optString("label"), o.optString("host"),
                    o.optString("user"), o.optString("pass")
                )
            )
        }
        return out
    }

    private fun writeProfiles(ctx: Context, list: List<Profile>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().apply {
                put("label", it.label)
                put("host", it.host)
                put("user", it.user)
                put("pass", it.pass)
            })
        }
        p(ctx).edit().putString(K_PROFILES, arr.toString()).apply()
    }

    fun setActive(ctx: Context, prof: Profile) {
        p(ctx).edit().putString(K_ACTIVE, prof.host + "|" + prof.user).apply()
    }

    fun activeKey(ctx: Context): String = p(ctx).getString(K_ACTIVE, "") ?: ""

    fun removeProfile(ctx: Context, prof: Profile) {
        writeProfiles(ctx, profiles(ctx).filterNot { it.host == prof.host && it.user == prof.user })
    }

    fun clear(ctx: Context) {
        p(ctx).edit().remove(K_ACTIVE).apply()
    }

    fun bufferSeconds(ctx: Context): Int = p(ctx).getInt(K_BUFFER, 30)

    fun setBufferSeconds(ctx: Context, v: Int) {
        p(ctx).edit().putInt(K_BUFFER, v).apply()
    }

    fun autoNext(ctx: Context): Boolean = p(ctx).getBoolean(K_AUTONEXT, true)

    fun setAutoNext(ctx: Context, v: Boolean) {
        p(ctx).edit().putBoolean(K_AUTONEXT, v).apply()
    }
}
