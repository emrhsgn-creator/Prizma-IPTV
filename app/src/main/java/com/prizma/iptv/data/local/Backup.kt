package com.prizma.iptv.data.local

import com.prizma.iptv.data.model.SavedItem
import com.prizma.iptv.data.model.WatchState
import org.json.JSONArray
import org.json.JSONObject

/**
 * Favori ve izleme geçmişinin dışa/içe aktarımı.
 *
 * Elle düzenlenmiş favori sırası kullanıcının en pahalı emeği; cihaz
 * değiştirirken ya da uygulamayı silerken kaybolmaması gerekiyor.
 * Kanal listesi ve EPG yedeklenmez, ikisi de yeniden indirilebilir.
 */
object Backup {

    private const val VERSION = 1

    fun export(favorites: List<SavedItem>, history: List<WatchState>): String {
        val root = JSONObject()
        root.put("version", VERSION)
        root.put("exportedAt", System.currentTimeMillis())

        val favArray = JSONArray()
        favorites.forEach { item ->
            favArray.put(
                JSONObject().apply {
                    put("section", item.section)
                    put("id", item.id)
                    put("name", item.name)
                    put("icon", item.icon)
                    put("ext", item.extension)
                    put("rating", item.rating)
                    put("num", item.number)
                    put("savedAt", item.savedAt)
                }
            )
        }
        root.put("favorites", favArray)

        val histArray = JSONArray()
        history.forEach { item ->
            histArray.put(
                JSONObject().apply {
                    put("section", item.section)
                    put("id", item.id)
                    put("name", item.name)
                    put("icon", item.icon)
                    put("ext", item.extension)
                    put("position", item.position)
                    put("duration", item.duration)
                    put("lastSeen", item.lastSeen)
                    put("parent", item.parentId)
                }
            )
        }
        root.put("history", histArray)

        return root.toString(2)
    }

    data class Restored(val favorites: List<SavedItem>, val history: List<WatchState>)

    /** Geçersiz ya da tanınmayan yedekte null döner. */
    fun parse(raw: String): Restored? {
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        if (root.optInt("version", 0) != VERSION) return null

        val favArray = root.optJSONArray("favorites") ?: JSONArray()
        val favorites = ArrayList<SavedItem>(favArray.length())
        for (i in 0 until favArray.length()) {
            val o = favArray.optJSONObject(i) ?: continue
            val id = o.optString("id")
            if (id.isBlank()) continue
            favorites.add(
                SavedItem(
                    section = o.optString("section"),
                    id = id,
                    name = o.optString("name"),
                    icon = o.optString("icon"),
                    extension = o.optString("ext"),
                    rating = o.optDouble("rating", 0.0),
                    number = o.optInt("num"),
                    savedAt = o.optLong("savedAt")
                )
            )
        }

        val histArray = root.optJSONArray("history") ?: JSONArray()
        val history = ArrayList<WatchState>(histArray.length())
        for (i in 0 until histArray.length()) {
            val o = histArray.optJSONObject(i) ?: continue
            val id = o.optString("id")
            if (id.isBlank()) continue
            history.add(
                WatchState(
                    section = o.optString("section"),
                    id = id,
                    name = o.optString("name"),
                    icon = o.optString("icon"),
                    extension = o.optString("ext"),
                    position = o.optLong("position"),
                    duration = o.optLong("duration"),
                    lastSeen = o.optLong("lastSeen"),
                    parentId = o.optString("parent")
                )
            )
        }

        if (favorites.isEmpty() && history.isEmpty()) return null
        return Restored(favorites, history)
    }

    fun suggestedFileName(): String {
        val stamp = java.time.LocalDateTime.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"))
        return "prizma-yedek-$stamp.json"
    }
}
