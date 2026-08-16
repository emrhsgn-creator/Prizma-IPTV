package com.prizma.iptv

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class SavedItem(
    val section: String,
    val id: String,
    val name: String,
    val icon: String,
    val extension: String,
    val rating: String,
    val savedAt: Long
)

data class WatchState(
    val section: String,
    val id: String,
    val name: String,
    val icon: String,
    val extension: String,
    val position: Long,
    val duration: Long,
    val lastSeen: Long
)

object Store {
    private const val FILE = "prizma_store"
    private const val K_FAV = "favorites"
    private const val K_HIST = "history"
    private const val HIST_LIMIT = 80

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun favorites(ctx: Context): List<SavedItem> {
        val raw = prefs(ctx).getString(K_FAV, "[]") ?: "[]"
        val arr = try { JSONArray(raw) } catch (e: Exception) { JSONArray() }
        val out = ArrayList<SavedItem>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out.add(
                SavedItem(
                    o.optString("section"),
                    o.optString("id"),
                    o.optString("name"),
                    o.optString("icon"),
                    o.optString("ext"),
                    o.optString("rating"),
                    o.optLong("savedAt")
                )
            )
        }
        return out
    }

    private fun writeFavorites(ctx: Context, list: List<SavedItem>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().apply {
                put("section", it.section)
                put("id", it.id)
                put("name", it.name)
                put("icon", it.icon)
                put("ext", it.extension)
                put("rating", it.rating)
                put("savedAt", it.savedAt)
            })
        }
        prefs(ctx).edit().putString(K_FAV, arr.toString()).apply()
    }

    fun toggleFavorite(ctx: Context, section: Section, s: StreamItem): Boolean {
        val list = favorites(ctx).toMutableList()
        val idx = list.indexOfFirst { it.section == section.name && it.id == s.id }
        return if (idx >= 0) {
            list.removeAt(idx)
            writeFavorites(ctx, list)
            false
        } else {
            list.add(
                SavedItem(
                    section.name, s.id, s.name, s.icon,
                    s.extension, s.rating, System.currentTimeMillis()
                )
            )
            writeFavorites(ctx, list)
            true
        }
    }

    fun moveFavorite(ctx: Context, section: Section, id: String, delta: Int) {
        val all = favorites(ctx).toMutableList()
        val idxs = all.indices.filter { all[it].section == section.name }
        val pos = idxs.indexOfFirst { all[it].id == id }
        if (pos < 0) return
        val target = pos + delta
        if (target < 0 || target >= idxs.size) return
        val a = idxs[pos]
        val b = idxs[target]
        val tmp = all[a]
        all[a] = all[b]
        all[b] = tmp
        writeFavorites(ctx, all)
    }

    fun history(ctx: Context): List<WatchState> {
        val raw = prefs(ctx).getString(K_HIST, "[]") ?: "[]"
        val arr = try { JSONArray(raw) } catch (e: Exception) { JSONArray() }
        val out = ArrayList<WatchState>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out.add(
                WatchState(
                    o.optString("section"),
                    o.optString("id"),
                    o.optString("name"),
                    o.optString("icon"),
                    o.optString("ext"),
                    o.optLong("position"),
                    o.optLong("duration"),
                    o.optLong("lastSeen")
                )
            )
        }
        return out.sortedByDescending { it.lastSeen }
    }

    private fun writeHistory(ctx: Context, list: List<WatchState>) {
        val arr = JSONArray()
        list.take(HIST_LIMIT).forEach {
            arr.put(JSONObject().apply {
                put("section", it.section)
                put("id", it.id)
                put("name", it.name)
                put("icon", it.icon)
                put("ext", it.extension)
                put("position", it.position)
                put("duration", it.duration)
                put("lastSeen", it.lastSeen)
            })
        }
        prefs(ctx).edit().putString(K_HIST, arr.toString()).apply()
    }

    fun record(
        ctx: Context, section: String, id: String, name: String,
        icon: String, ext: String, position: Long, duration: Long
    ) {
        if (id.isEmpty()) return
        val list = history(ctx).toMutableList()
        val idx = list.indexOfFirst { it.section == section && it.id == id }
        val entry = WatchState(
            section, id, name, icon, ext,
            if (position > 0) position else 0L,
            if (duration > 0) duration else 0L,
            System.currentTimeMillis()
        )
        if (idx >= 0) list[idx] = entry else list.add(entry)
        writeHistory(ctx, list.sortedByDescending { it.lastSeen })
    }

    fun resumePosition(ctx: Context, section: String, id: String): Long {
        val w = history(ctx).firstOrNull { it.section == section && it.id == id } ?: return 0L
        if (w.duration <= 0L) return 0L
        if (w.position < 30_000L) return 0L
        if (w.position > (w.duration * 95 / 100)) return 0L
        return w.position
    }

    fun clearHistory(ctx: Context) {
        prefs(ctx).edit().remove(K_HIST).apply()
    }
}
