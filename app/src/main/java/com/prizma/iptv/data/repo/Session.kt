package com.prizma.iptv.data.repo

import android.app.Application
import android.content.Context
import com.prizma.iptv.data.local.FavoritesStore
import com.prizma.iptv.data.local.HistoryStore
import com.prizma.iptv.data.local.Paths
import com.prizma.iptv.data.local.ProfileStore
import com.prizma.iptv.data.local.Settings
import com.prizma.iptv.data.model.Account
import com.prizma.iptv.data.model.Category
import com.prizma.iptv.data.model.Episode
import com.prizma.iptv.data.model.PlayItem
import com.prizma.iptv.data.model.PlayKind
import com.prizma.iptv.data.model.Profile
import com.prizma.iptv.data.model.SavedItem
import com.prizma.iptv.data.model.Section
import com.prizma.iptv.data.model.ServerInfo
import com.prizma.iptv.data.model.SourceType
import com.prizma.iptv.data.model.StreamItem
import com.prizma.iptv.data.model.WatchState
import com.prizma.iptv.data.remote.XtreamApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Etkin abonelik ve ona bağlı tüm depolar. Profil değiştirildiğinde bu nesne
 * tümüyle atılır; favori, geçmiş ve önbellek profil kimliğine bağlı olduğu için
 * hesaplar arasında veri sızmaz.
 */
class Session(val profile: Profile) {

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val favorites = FavoritesStore(profile.id)
    val history = HistoryStore(profile.id)
    val catalog = CatalogRepository(profile)
    val epg = EpgRepository(profile)

    private val _account = MutableStateFlow(Account(username = profile.user))
    val account: StateFlow<Account> = _account

    private val _server = MutableStateFlow(ServerInfo())
    val server: StateFlow<ServerInfo> = _server

    fun updateAccount(account: Account, server: ServerInfo) {
        _account.value = account
        _server.value = server
    }

    fun close() {
        scope.cancel()
    }

    // ---------------------------------------------------------------- adresler

    /**
     * Hazir adres yalnizca M3U kaynaklarinda anlamli. Xtream profillerinde
     * adres her zaman kanal kimliginden kurulur; boylece onbellekten gelen
     * bozuk bir alan yanlis yayin acilmasina yol acamaz.
     */
    private fun liveUrl(item: StreamItem, extension: String = "ts"): String =
        if (profile.type == SourceType.M3U && item.url.isNotBlank()) item.url
        else XtreamApi.liveUrl(profile, item.id, extension)

    fun liveItem(item: StreamItem): PlayItem = PlayItem(
        kind = PlayKind.LIVE,
        id = item.id,
        title = item.name,
        url = liveUrl(item),
        icon = item.icon,
        number = item.number,
        extension = item.extension.ifBlank { "ts" },
        epgChannelId = item.epgChannelId,
        archiveDays = item.archiveDays
    )

    fun movieItem(item: StreamItem, extensionOverride: String = ""): PlayItem {
        val ext = extensionOverride.ifBlank { item.extension }.ifBlank { "mp4" }
        return PlayItem(
            kind = PlayKind.MOVIE,
            id = item.id,
            title = item.name,
            url = if (profile.type == SourceType.M3U && item.url.isNotBlank()) item.url
            else XtreamApi.movieUrl(profile, item.id, ext),
            icon = item.icon,
            extension = ext
        )
    }

    fun movieItem(id: String, name: String, icon: String, extension: String): PlayItem {
        val ext = extension.ifBlank { "mp4" }
        return PlayItem(
            kind = PlayKind.MOVIE,
            id = id,
            title = name,
            url = XtreamApi.movieUrl(profile, id, ext),
            icon = icon,
            extension = ext
        )
    }

    fun episodeItem(episode: Episode, seriesName: String, seriesId: String, cover: String) =
        PlayItem(
            kind = PlayKind.EPISODE,
            id = episode.id,
            title = seriesName,
            subtitle = "S" + episode.season + "B" + episode.episodeNum +
                (if (episode.title.isBlank()) "" else " · " + episode.title),
            url = XtreamApi.episodeUrl(profile, episode.id, episode.extension),
            icon = episode.icon.ifBlank { cover },
            extension = episode.extension,
            parentId = seriesId
        )

    /**
     * Geçmiş kaydından bölümü yeniden kurar. Dizi bilgisini yeniden çekmeye
     * gerek yok: bölüm kimliği ve uzantısı adres için yeterli.
     */
    fun episodeItemFromHistory(state: WatchState): PlayItem? {
        if (state.id.isBlank()) return null
        val ext = state.extension.ifBlank { "mp4" }
        return PlayItem(
            kind = PlayKind.EPISODE,
            id = state.id,
            title = state.name,
            url = XtreamApi.episodeUrl(profile, state.id, ext),
            icon = state.icon,
            extension = ext,
            parentId = state.parentId
        )
    }

    /**
     * Geçmişe dönük (catch-up) oynatma. Kanalın arşiv penceresi dışındaki
     * istekler için null döner.
     */
    fun catchupItem(
        channel: StreamItem,
        startMs: Long,
        durationMinutes: Int,
        title: String
    ): PlayItem? {
        if (channel.archiveDays <= 0) return null
        val oldest = System.currentTimeMillis() - channel.archiveDays * 86_400_000L
        if (startMs < oldest) return null
        return PlayItem(
            kind = PlayKind.CATCHUP,
            id = channel.id,
            title = channel.name,
            subtitle = title,
            url = XtreamApi.catchupUrl(profile, channel.id, startMs, durationMinutes),
            icon = channel.icon,
            number = channel.number,
            extension = "ts",
            epgChannelId = channel.epgChannelId,
            archiveDays = channel.archiveDays
        )
    }

    // ---------------------------------------------------------------- favoriler

    fun favoriteOf(section: Section, item: StreamItem) = SavedItem(
        section = section.name,
        id = item.id,
        name = item.name,
        icon = item.icon,
        extension = item.extension,
        rating = item.rating,
        number = item.number
    )

    // ---------------------------------------------------------------- ebeveyn

    /** Kategori yetişkin içerikliyse ve ayarlar gizlemeyi istiyorsa true. */
    fun isHidden(category: Category): Boolean =
        category.adult && Settings.hideAdult

    /** Kategori açılırken PIN sorulmalı mı? */
    fun needsPin(category: Category): Boolean =
        category.adult && Settings.lockAdult && Settings.hasPin
}

/**
 * Basit servis konumlandırıcı. Uygulama tek modüllü olduğu için ayrı bir
 * bağımlılık enjeksiyon çatısı yerine bu yeterli ve okunaklı.
 */
object App {

    lateinit var context: Context
        private set

    private val _session = MutableStateFlow<Session?>(null)
    val session: StateFlow<Session?> = _session

    fun init(application: Application) {
        context = application.applicationContext
        Settings.init(context)
        Paths.init(context)
        ProfileStore.init(context)
    }

    fun open(profile: Profile): Session {
        val current = _session.value
        if (current != null && current.profile.id == profile.id) {
            return current
        }
        current?.close()
        ProfileStore.setActive(profile)
        val fresh = Session(profile)
        _session.value = fresh
        return fresh
    }

    fun close() {
        _session.value?.close()
        _session.value = null
    }

    /**
     * Süreç öldürülüp oynatıcı doğrudan geri yüklendiğinde oturum boş olabilir;
     * kayıtlı etkin profilden ağ isteği yapmadan yeniden kurulur.
     */
    fun ensureSession(): Session? {
        _session.value?.let { return it }
        val profile = ProfileStore.active() ?: return null
        val fresh = Session(profile)
        _session.value = fresh
        return fresh
    }
}
