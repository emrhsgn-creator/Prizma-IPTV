package com.prizma.iptv

import android.content.Context
import java.io.File

/**
 * Katalog (kategoriler + akışlar) için disk önbelleği.
 *
 * Uygulama her açılışta tüm kanal, film ve dizi listesini sunucudan baştan
 * indiriyordu; açılışın en uzun adımı buydu. Artık liste diske yazılıyor:
 * açılışta anında diskten gösteriliyor, ağdan gelen taze sürüm arkadan gelip
 * üstüne yazıyor.
 *
 * Biçim JSON değil satır tabanlı. On binlerce kayıtta ayrıştırma belirgin
 * biçimde daha hızlı ve dosya daha küçük oluyor.
 */
internal object Catalog {

    private const val VERSION = "v1"

    private fun file(ctx: Context, host: String, user: String, sec: Section): File {
        val key = Integer.toHexString("$host|$user".hashCode())
        return File(ctx.cacheDir, "catalog_${key}_${sec.name}.tsv")
    }

    private fun esc(s: String): String =
        if (s.indexOf('\\') < 0 && s.indexOf('\t') < 0 && s.indexOf('\n') < 0) s
        else s.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n")

    private fun unesc(s: String): String {
        if (s.indexOf('\\') < 0) return s
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (s[i + 1]) {
                    't' -> { sb.append('\t'); i += 2 }
                    'n' -> { sb.append('\n'); i += 2 }
                    '\\' -> { sb.append('\\'); i += 2 }
                    else -> { sb.append(c); i++ }
                }
            } else {
                sb.append(c); i++
            }
        }
        return sb.toString()
    }

    fun save(ctx: Context, host: String, user: String, sec: Section, data: SectionData) {
        val target = file(ctx, host, user, sec)
        val tmp = File(target.parentFile, target.name + ".tmp")
        try {
            tmp.bufferedWriter().use { w ->
                w.append(VERSION).append('\t')
                    .append(System.currentTimeMillis().toString()).append('\n')
                for (c in data.categories) {
                    w.append("C\t").append(esc(c.id))
                        .append('\t').append(esc(c.name))
                        .append('\t').append(c.count.toString()).append('\n')
                }
                for (s in data.items) {
                    w.append("I\t").append(esc(s.id))
                        .append('\t').append(esc(s.name))
                        .append('\t').append(esc(s.icon))
                        .append('\t').append(esc(s.extension))
                        .append('\t').append(esc(s.categoryId))
                        .append('\t').append(esc(s.rating))
                        .append('\t').append(s.added.toString()).append('\n')
                }
            }
            // Yarım kalan bir yazım bozuk dosya bırakmasın diye tek adımda yerine koy
            if (!tmp.renameTo(target)) tmp.delete()
        } catch (e: Exception) {
            runCatching { tmp.delete() }
        }
    }

    fun load(ctx: Context, host: String, user: String, sec: Section): SectionData? {
        val f = file(ctx, host, user, sec)
        if (!f.exists()) return null
        return try {
            val cats = ArrayList<Category>()
            val items = ArrayList<StreamItem>()
            f.bufferedReader().useLines { lines ->
                var first = true
                for (line in lines) {
                    if (first) {
                        first = false
                        // Biçim değişirse eski dosyayı sessizce yok say
                        if (!line.startsWith(VERSION + "\t")) return null
                        continue
                    }
                    val p = line.split('\t')
                    when (p.getOrNull(0)) {
                        "C" -> if (p.size >= 4) cats.add(
                            Category(unesc(p[1]), unesc(p[2]), p[3].toIntOrNull() ?: 0)
                        )

                        "I" -> if (p.size >= 8) items.add(
                            StreamItem(
                                id = unesc(p[1]),
                                name = unesc(p[2]),
                                icon = unesc(p[3]),
                                extension = unesc(p[4]),
                                categoryId = unesc(p[5]),
                                rating = unesc(p[6]),
                                added = p[7].toLongOrNull() ?: 0L
                            )
                        )
                    }
                }
            }
            if (cats.isEmpty() && items.isEmpty()) null else SectionData(cats, items)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Önbelleğin taze sayıldığı süre.
     *
     * save() ilk satıra bir zaman damgası yazıyordu ama hiçbir yer onu
     * okumuyordu. Sonuç: disk kopyası varken bile her açılışta bütün VOD ve
     * SERIES kataloğu sunucudan baştan iniyor, ayrıştırılıyor ve diske geri
     * yazılıyordu. Damga artık okunuyor.
     */
    const val TTL_MS = 6L * 60 * 60 * 1000

    /** Onbellegin ne zaman yazildigi; dosya yoksa ya da baslik bozuksa 0. */
    private fun savedAt(ctx: Context, host: String, user: String, sec: Section): Long {
        val f = file(ctx, host, user, sec)
        if (!f.exists()) return 0L
        return try {
            val head = f.bufferedReader().use { it.readLine() } ?: return 0L
            if (!head.startsWith(VERSION + "\t")) return 0L
            head.substringAfter('\t').trim().toLongOrNull() ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * Disk kopyasi aga hic cikmadan kullanilabilir mi.
     *
     * Saat geri alinirsa yas negatif cikabilir; o durumda taze saymiyoruz,
     * yani en kotu ihtimalle eski davranisa (her acilista indir) doneriz.
     */
    fun isFresh(ctx: Context, host: String, user: String, sec: Section): Boolean {
        val t = savedAt(ctx, host, user, sec)
        if (t <= 0L) return false
        return (System.currentTimeMillis() - t) in 0 until TTL_MS
    }

    /** Ayarlardaki "önbelleği temizle" ve elle yenileme için. */
    fun clear(ctx: Context) {
        runCatching {
            ctx.cacheDir.listFiles()?.forEach {
                if (it.name.startsWith("catalog_")) it.delete()
            }
        }
    }
}
