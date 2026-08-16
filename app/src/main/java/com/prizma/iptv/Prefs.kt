package com.prizma.iptv

import android.content.Context

object Prefs {
    private const val FILE = "prizma_prefs"

    fun save(ctx: Context, host: String, user: String, pass: String) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString("host", host)
            .putString("user", user)
            .putString("pass", pass)
            .apply()
    }

    fun load(ctx: Context): Triple<String, String, String>? {
        val p = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val h = p.getString("host", "") ?: ""
        val u = p.getString("user", "") ?: ""
        val s = p.getString("pass", "") ?: ""
        return if (h.isNotEmpty() && u.isNotEmpty()) Triple(h, u, s) else null
    }

    fun clear(ctx: Context) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
