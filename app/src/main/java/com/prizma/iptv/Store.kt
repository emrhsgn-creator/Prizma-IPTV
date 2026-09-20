package com.prizma.iptv

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

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

/**
 * Favoriler ve izleme gecmisi.
 *
 * Listeler artik bellekte tutuluyor. Onceden her okuma SharedPreferences'tan
 * string alip 120 JSON nesnesini bastan ayristiriyor ve siraliyordu; ustelik
 * bu okumalar kompozisyonun icinden, yani ana is parcacigindan yapiliyordu:
 *
 *  - HomeScreen her onResume'da (Refresh.tick) favorites() + history()
 *  - PlayerActivity acilirken resumePosition()
 *  - PlayerActivity kapanirken onDispose icinde record()
 *
 * Cihazda olculen Choreographer atlamalari bu yolda birikiyordu: kategori
 * degisiminde 31 ve 37 kare, canli TV ilk acilisinda 94 kare (~1,6 sn).
 *
 * Simdi ayristirma surec basina bir kez yapiliyor. Yazmalar once bellegi
 * gunceller, diske yazim ayri bir is parcaciginda surer; bu sayede record()
 * ana is parcacigindan cagrilsa bile bloklamaz.
 */
object Store {
    private const val FILE = "prizma_store"
    private const val K_FAV = "favorites"
    private const val K_HIST = "history"
    private const val HIST_LIMIT = 120

    @Volatile
    private var favCache: List<SavedItem>? = null

    @Volatile
    private var histCache: List<WatchState>? = null

    // Tek is parcacigi: yazimlarin sirasi korunur, boylece bellekteki son
    // durum ile diskteki son durum ayni olur.
    private val io = Executors.newSingleThreadExecutor { r ->
        Thread(r, "prizma-store").apply { isDaemon = true }
    }

    // Uygulama baglami kullaniliyor: arka plandaki yazim isi bir Activity'yi
    // hayatta tutmasin.
    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /**
     * Ilk okumayi acilista arka planda yapar, boylece ilk kompozisyon bile
     * ayristirma maliyeti odemez. PrizmaApplication'dan cagriliyor.
     */
    fun warm(ctx: Context) {
        val app = ctx.applicationContext
        io.execute {
            runCatching { favorites(app) }
            runCatching { history(app) }
        }
    }

    /**
     * Bekleyen yazimlarin diske inmesini bekler.
     *
     * SharedPreferences.apply() kullanilirken Android, surec olurken
     * bekleyen yazimlari QueuedWork uzerinden bosaltmayi garanti
     * ediyordu. Yazimi kendi is parcacigimiza alinca o garanti kalkti:
     * bir favori degisikliginin hemen ardindan uygulama oldurulurse
     * (ornegin yeniden kurulum sirasinda) degisiklik kaybolabilirdi.
     *
     * Kuyruk sirali calistigi icin bos bir is gonderip onun bitmesini
     * beklemek, oncesindeki butun yazimlarin tamamlandigini garanti
     * eder. Bekleme sinirli: bir sekilde tikanirsa ana is parcacigini
     * tutup ANR'a yol acmasin. Pratikte kuyruk bos oldugu icin aninda
     * doner.
     */
    fun flush() {
        val done = CountDownLatch(1)
        runCatching { io.execute { done.countDown() } }
        runCatching { done.await(500, TimeUnit.MILLISECONDS) }
    }

    // --------------------------------------------------------------- favoriler

    private fun parseFavorites(raw: String): List<SavedItem> {
        val arr = try { JSONArray(raw) } catch (e: Exception) { JSONArray() }
        val out = ArrayList<SavedItem>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out.add(
                SavedItem(
                    o.optString("section"), o.optString("id"), o.optString("name"),
                    o.optString("icon"), o.optString("ext"), o.optString("rating"),
                    o.optLong("savedAt")
                )
            )
        }
        return out
    }

    fun favorites(ctx: Context): List<SavedItem> {
        favCache?.let { return it }
        synchronized(this) {
            favCache?.let { return it }
            val parsed = parseFavorites(prefs(ctx).getString(K_FAV, "[]") ?: "[]")
            favCache = parsed
            return parsed
        }
    }

    private fun writeFavorites(ctx: Context, list: List<SavedItem>) {
        favCache = list
        val app = ctx.applicationContext
        io.execute {
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
            // Arka plandayiz; commit() dayanikli ve yazim sirasini bozmaz.
            runCatching { prefs(app).edit().putString(K_FAV, arr.toString()).commit() }
        }
    }

    fun toggleFavorite(ctx: Context, sectionName: String, id: String, name: String,
                       icon: String, ext: String, rating: String): Boolean {
        val list = favorites(ctx).toMutableList()
        val idx = list.indexOfFirst { it.section == sectionName && it.id == id }
        return if (idx >= 0) {
            list.removeAt(idx); writeFavorites(ctx, list); false
        } else {
            list.add(SavedItem(sectionName, id, name, icon, ext, rating, System.currentTimeMillis()))
            writeFavorites(ctx, list); true
        }
    }

    fun moveFavorite(ctx: Context, sectionName: String, id: String, delta: Int) {
        val all = favorites(ctx).toMutableList()
        val idxs = all.indices.filter { all[it].section == sectionName }
        val pos = idxs.indexOfFirst { all[it].id == id }
        if (pos < 0) return
        val target = pos + delta
        if (target < 0 || target >= idxs.size) return
        val a = idxs[pos]
        val b = idxs[target]
        val tmp = all[a]; all[a] = all[b]; all[b] = tmp
        writeFavorites(ctx, all)
    }

    // ------------------------------------------------------------------ gecmis

    private fun parseHistory(raw: String): List<WatchState> {
        val arr = try { JSONArray(raw) } catch (e: Exception) { JSONArray() }
        val out = ArrayList<WatchState>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out.add(
                WatchState(
                    o.optString("section"), o.optString("id"), o.optString("name"),
                    o.optString("icon"), o.optString("ext"), o.optLong("position"),
                    o.optLong("duration"), o.optLong("lastSeen")
                )
            )
        }
        // Siralama ayristirmada bir kez; her okumada degil.
        return out.sortedByDescending { it.lastSeen }
    }

    fun history(ctx: Context): List<WatchState> {
        histCache?.let { return it }
        synchronized(this) {
            histCache?.let { return it }
            val parsed = parseHistory(prefs(ctx).getString(K_HIST, "[]") ?: "[]")
            histCache = parsed
            return parsed
        }
    }

    private fun writeHistory(ctx: Context, list: List<WatchState>) {
        val trimmed = list.take(HIST_LIMIT)
        histCache = trimmed
        val app = ctx.applicationContext
        io.execute {
            val arr = JSONArray()
            trimmed.forEach {
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
            runCatching { prefs(app).edit().putString(K_HIST, arr.toString()).commit() }
        }
    }

    /**
     * Ana is parcacigindan cagrilabilir: bellek guncellemesi anlik, diske
     * yazim arka planda.
     */
    fun record(ctx: Context, section: String, id: String, name: String,
               icon: String, ext: String, position: Long, duration: Long) {
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

    fun removeHistory(ctx: Context, section: String, id: String) {
        writeHistory(ctx, history(ctx).filterNot { it.section == section && it.id == id })
    }

    fun resumePosition(ctx: Context, section: String, id: String): Long {
        val w = history(ctx).firstOrNull { it.section == section && it.id == id } ?: return 0L
        if (w.duration <= 0L) return 0L
        if (w.position < 30_000L) return 0L
        if (w.position > (w.duration * 95 / 100)) return 0L
        return w.position
    }

    fun clearHistory(ctx: Context) {
        histCache = emptyList()
        val app = ctx.applicationContext
        io.execute { runCatching { prefs(app).edit().remove(K_HIST).commit() } }
    }
}
