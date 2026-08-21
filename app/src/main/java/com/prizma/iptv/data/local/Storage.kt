package com.prizma.iptv.data.local

import android.content.Context
import java.io.BufferedReader
import java.io.File

/** Profil başına dosya yerleşimi: filesDir/prizma/{profileId}/… */
object Paths {

    private lateinit var root: File

    fun init(ctx: Context) {
        if (::root.isInitialized) return
        root = File(ctx.applicationContext.filesDir, "prizma")
        root.mkdirs()
    }

    fun profileDir(profileId: String): File {
        val safe = profileId.ifBlank { "default" }.replace(Regex("[^A-Za-z0-9_-]"), "_")
        return File(root, safe).apply { mkdirs() }
    }

    fun file(profileId: String, name: String): File = File(profileDir(profileId), name)

    fun totalSize(): Long = if (::root.isInitialized) sizeOf(root) else 0L

    /** Profile ait her şeyi siler (favori ve geçmiş dahil). */
    fun clearProfile(profileId: String) {
        profileDir(profileId).listFiles()?.forEach { runCatching { it.delete() } }
    }

    /**
     * Yalnızca yeniden indirilebilir verileri siler: katalog ve EPG.
     * Favoriler ile izleme geçmişi korunur.
     */
    fun clearCache(profileId: String) {
        profileDir(profileId).listFiles()?.forEach { file ->
            val name = file.name
            val disposable = name.startsWith("catalog_") ||
                name.startsWith("cats_") ||
                name.startsWith("epg")
            if (disposable) runCatching { file.delete() }
        }
    }

    fun clearAll() {
        if (!::root.isInitialized) return
        root.listFiles()?.forEach { dir ->
            dir.listFiles()?.forEach { runCatching { it.delete() } }
            runCatching { dir.delete() }
        }
    }

    private fun sizeOf(f: File): Long =
        if (f.isDirectory) (f.listFiles()?.sumOf { sizeOf(it) } ?: 0L) else f.length()
}

/**
 * Basit, satır tabanlı depolama. On binlerce kanalı JSON ile ayrıştırmak
 * TV kutularında saniyeler sürüyordu; sekmeyle ayrılmış düz metin
 * aynı veriyi ~20 kat hızlı okuyor.
 */
object Tsv {

    const val SEP = "\t"

    /** Alan içindeki ayırıcı karakterleri temizler. */
    fun clean(s: String?): String {
        if (s.isNullOrEmpty()) return ""
        var out = s
        if (out.indexOf('\t') >= 0) out = out.replace('\t', ' ')
        if (out.indexOf('\n') >= 0) out = out.replace('\n', ' ')
        if (out.indexOf('\r') >= 0) out = out.replace('\r', ' ')
        return out
    }

    fun row(vararg fields: Any?): String =
        fields.joinToString(SEP) { clean(it?.toString()) }

    data class Data(val version: Int, val savedAt: Long, val rows: List<Array<String>>)

    /** Önce geçici dosyaya yazıp yeniden adlandırır; yarım kalmış dosya oluşmaz. */
    fun write(file: File, version: Int, lines: List<String>) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        runCatching {
            tmp.bufferedWriter(Charsets.UTF_8).use { w ->
                w.write("#" + version + SEP + System.currentTimeMillis() + SEP + lines.size)
                w.newLine()
                lines.forEach {
                    w.write(it)
                    w.newLine()
                }
            }
            if (file.exists()) file.delete()
            tmp.renameTo(file)
        }.onFailure { runCatching { tmp.delete() } }
    }

    fun read(file: File, expectedVersion: Int, expectedColumns: Int): Data? {
        if (!file.exists() || file.length() == 0L) return null
        return runCatching {
            file.bufferedReader(Charsets.UTF_8).use { r ->
                val header = r.readLine() ?: return@use null
                if (!header.startsWith("#")) return@use null
                val parts = header.substring(1).split(SEP)
                val version = parts.getOrNull(0)?.toIntOrNull() ?: -1
                if (version != expectedVersion) return@use null
                val savedAt = parts.getOrNull(1)?.toLongOrNull() ?: 0L
                Data(version, savedAt, readRows(r, expectedColumns))
            }
        }.getOrNull()
    }

    private fun readRows(r: BufferedReader, columns: Int): List<Array<String>> {
        val out = ArrayList<Array<String>>(1024)
        while (true) {
            val line = r.readLine() ?: break
            if (line.isEmpty()) continue
            val cells = line.split(SEP)
            val row = Array(columns) { i -> cells.getOrElse(i) { "" } }
            out.add(row)
        }
        return out
    }

    fun str(row: Array<String>, i: Int): String = row.getOrElse(i) { "" }
    fun int(row: Array<String>, i: Int): Int = str(row, i).toIntOrNull() ?: 0
    fun long(row: Array<String>, i: Int): Long = str(row, i).toLongOrNull() ?: 0L
    fun dbl(row: Array<String>, i: Int): Double = str(row, i).toDoubleOrNull() ?: 0.0
    fun bool(row: Array<String>, i: Int): Boolean = str(row, i) == "1"
}
