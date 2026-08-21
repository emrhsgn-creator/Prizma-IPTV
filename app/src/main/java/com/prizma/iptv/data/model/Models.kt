package com.prizma.iptv.data.model

import org.json.JSONObject

/** Kaynak türü: klasik Xtream Codes paneli ya da düz M3U listesi. */
enum class SourceType { XTREAM, M3U }

/** Katalogdaki üç ana bölüm. */
enum class Section { LIVE, VOD, SERIES }

/**
 * Bir abonelik/profil. Favoriler, geçmiş ve önbellek profil kimliğine göre
 * ayrı tutulur; iki hesap arasında geçiş yapınca veriler karışmaz.
 */
data class Profile(
    val id: String,
    val label: String,
    val type: SourceType,
    val host: String,
    val user: String,
    val pass: String,
    val epgUrl: String = "",
    val userAgent: String = "",
    val createdAt: Long = 0L
) {
    fun displayName(): String = label.ifBlank {
        user.ifBlank { host.substringAfter("://").substringBefore("/") }
    }

    fun serverLabel(): String =
        host.substringAfter("://").substringBefore("/").ifBlank { host }
}

data class Account(
    val username: String = "",
    val status: String = "",
    val expiryMs: Long = 0L,
    val maxConnections: String = "-",
    val activeConnections: String = "-",
    val isTrial: Boolean = false,
    val message: String = ""
)

data class ServerInfo(
    val url: String = "",
    val port: String = "",
    val httpsPort: String = "",
    val protocol: String = "http",
    val timezone: String = ""
)

data class Category(
    val id: String,
    val name: String,
    val count: Int = 0,
    val adult: Boolean = false
)

/**
 * Katalogdaki tek bir öğe: canlı kanal, film ya da dizi.
 * [rating] her zaman 10 üzerinden normalize edilir.
 */
data class StreamItem(
    val id: String,
    val name: String,
    val icon: String = "",
    val extension: String = "",
    val categoryId: String = "",
    val rating: Double = 0.0,
    val added: Long = 0L,
    val number: Int = 0,
    val epgChannelId: String = "",
    val archiveDays: Int = 0,
    /** M3U kaynaklarinda oynatma adresi dogrudan listeden gelir; Xtream'de bos kalir. */
    val url: String = ""
) {
    val hasArchive: Boolean get() = archiveDays > 0
}

data class Episode(
    val id: String,
    val title: String,
    val season: Int,
    val episodeNum: Int,
    val extension: String = "mp4",
    val plot: String = "",
    val durationSecs: Int = 0,
    val icon: String = "",
    val added: Long = 0L,
    val rating: Double = 0.0
)

data class SeasonBundle(val number: Int, val episodes: List<Episode>)

data class SeriesInfo(
    val plot: String = "",
    val cover: String = "",
    val backdrop: String = "",
    val genre: String = "",
    val releaseDate: String = "",
    val cast: String = "",
    val director: String = "",
    val rating: Double = 0.0,
    val seasons: List<SeasonBundle> = emptyList()
) {
    fun allEpisodes(): List<Episode> = seasons.flatMap { it.episodes }
}

data class VodInfo(
    val plot: String = "",
    val cover: String = "",
    val backdrop: String = "",
    val genre: String = "",
    val releaseDate: String = "",
    val cast: String = "",
    val director: String = "",
    val duration: String = "",
    val country: String = "",
    val rating: Double = 0.0,
    val youtube: String = "",
    val extension: String = "mp4"
)

/** XMLTV / Xtream EPG programı. Zamanlar epoch milisaniye. */
data class EpgProgram(
    val channelId: String,
    val start: Long,
    val stop: Long,
    val title: String,
    val description: String = ""
) {
    fun isLiveAt(nowMs: Long): Boolean = nowMs in start until stop.coerceAtLeast(start + 1)

    fun progressAt(nowMs: Long): Float {
        if (stop <= start) return 0f
        if (nowMs <= start) return 0f
        if (nowMs >= stop) return 1f
        return ((nowMs - start).toFloat() / (stop - start).toFloat()).coerceIn(0f, 1f)
    }
}

/** Favori kaydı. [order] elle sıralama için kullanılır. */
data class SavedItem(
    val section: String,
    val id: String,
    val name: String,
    val icon: String = "",
    val extension: String = "",
    val rating: Double = 0.0,
    val number: Int = 0,
    val savedAt: Long = 0L
)

/** İzleme durumu. Canlı kanallarda [duration] 0 olur. */
data class WatchState(
    val section: String,
    val id: String,
    val name: String,
    val icon: String = "",
    val extension: String = "",
    val position: Long = 0L,
    val duration: Long = 0L,
    val lastSeen: Long = 0L,
    val parentId: String = ""
) {
    val progress: Float
        get() = if (duration > 0L) (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f

    val finished: Boolean
        get() = duration > 0L && position > duration * 95 / 100
}

/** Oynatıcıya gönderilen tek bir oynatılabilir öğe. */
enum class PlayKind { LIVE, MOVIE, EPISODE, CATCHUP }

data class PlayItem(
    val kind: PlayKind,
    val id: String,
    val title: String,
    val url: String,
    val subtitle: String = "",
    val icon: String = "",
    val number: Int = 0,
    val extension: String = "",
    val epgChannelId: String = "",
    val archiveDays: Int = 0,
    val parentId: String = ""
) {
    val isLive: Boolean get() = kind == PlayKind.LIVE
    val resumable: Boolean get() = kind == PlayKind.MOVIE || kind == PlayKind.EPISODE

    /** Geçmiş/favori kayıtlarında kullanılan bölüm anahtarı. */
    fun sectionKey(): String = when (kind) {
        PlayKind.LIVE, PlayKind.CATCHUP -> Section.LIVE.name
        PlayKind.MOVIE -> Section.VOD.name
        PlayKind.EPISODE -> EPISODE_SECTION
    }

    fun toJson(): String = JSONObject().apply {
        put("k", kind.name)
        put("i", id)
        put("t", title)
        put("u", url)
        put("s", subtitle)
        put("c", icon)
        put("n", number)
        put("e", extension)
        put("g", epgChannelId)
        put("a", archiveDays)
        put("p", parentId)
    }.toString()

    companion object {
        const val EPISODE_SECTION = "EPISODE"

        fun fromJson(raw: String?): PlayItem? {
            if (raw.isNullOrBlank()) return null
            return try {
                val o = JSONObject(raw)
                PlayItem(
                    kind = runCatching { PlayKind.valueOf(o.optString("k")) }
                        .getOrDefault(PlayKind.LIVE),
                    id = o.optString("i"),
                    title = o.optString("t"),
                    url = o.optString("u"),
                    subtitle = o.optString("s"),
                    icon = o.optString("c"),
                    number = o.optInt("n"),
                    extension = o.optString("e"),
                    epgChannelId = o.optString("g"),
                    archiveDays = o.optInt("a"),
                    parentId = o.optString("p")
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}
