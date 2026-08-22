package com.prizma.iptv.ui.home

import android.content.Context
import com.prizma.iptv.data.local.Settings
import com.prizma.iptv.data.model.PlayItem
import com.prizma.iptv.data.model.Section
import com.prizma.iptv.data.model.StreamItem
import com.prizma.iptv.data.model.WatchState
import com.prizma.iptv.data.repo.Session
import com.prizma.iptv.player.PlayerActivity
import com.prizma.iptv.ui.detail.MovieActivity
import com.prizma.iptv.ui.detail.SeriesActivity

/**
 * Bir katalog öğesinin nereye gideceğini tek yerden karara bağlar:
 * canlı kanal doğrudan oynatıcıya, film ve dizi ise detay ekranına.
 */
object Launch {

    fun open(
        ctx: Context,
        session: Session,
        section: Section,
        item: StreamItem,
        siblings: List<StreamItem> = emptyList()
    ) {
        when (section) {
            Section.LIVE -> openLive(ctx, session, siblings.ifEmpty { listOf(item) }, item)
            Section.VOD -> MovieActivity.start(ctx, item.id, item.name, item.icon, item.extension)
            Section.SERIES -> SeriesActivity.start(ctx, item.id, item.name, item.icon)
        }
    }

    /**
     * Canlı yayında kumandayla kanal gezinmesi için tüm liste oynatıcıya
     * verilir. Liste Intent yerine bellek üzerinden aktarılır; on binlerce
     * kanal Intent sınırını (1 MB) rahatlıkla aşıyor.
     */
    fun openLive(
        ctx: Context,
        session: Session,
        channels: List<StreamItem>,
        picked: StreamItem
    ) {
        val found = channels.indexOfFirst { it.id == picked.id }
        // Secilen kanal listede yoksa sessizce ilk kanali acmak yerine
        // yalnizca o kanali oynat; yanlis yayin acilmasi en kotu sonuc.
        val items: List<PlayItem>
        val index: Int
        if (found >= 0) {
            items = channels.map { session.liveItem(it) }
            index = found
        } else {
            items = listOf(session.liveItem(picked))
            index = 0
        }
        Settings.lastLiveItem = items[index].toJson()
        PlayerActivity.start(ctx, items, index)
    }

    fun openSingle(ctx: Context, item: PlayItem) {
        PlayerActivity.start(ctx, listOf(item), 0)
    }

    /**
     * Geçmiş kaydından yeniden açma.
     *
     * [direct] verildiğinde ("Devam Et" rafı) detay ekranı atlanır ve
     * içerik kaldığı yerden doğrudan oynatılır.
     */
    fun openWatchState(
        ctx: Context,
        session: Session,
        state: WatchState,
        direct: Boolean = false
    ) {
        if (direct && resumeDirectly(ctx, session, state)) return
        when (state.section) {
            Section.LIVE.name -> {
                val channel = session.catalog.get(Section.LIVE)?.items
                    ?.firstOrNull { it.id == state.id }
                if (channel != null) {
                    openLive(
                        ctx,
                        session,
                        session.catalog.get(Section.LIVE)?.items.orEmpty(),
                        channel
                    )
                }
            }

            Section.VOD.name ->
                MovieActivity.start(ctx, state.id, state.name, state.icon, state.extension)

            PlayItem.EPISODE_SECTION -> {
                if (state.parentId.isNotBlank()) {
                    SeriesActivity.start(ctx, state.parentId, state.name, state.icon)
                }
            }

            Section.SERIES.name ->
                SeriesActivity.start(ctx, state.id, state.name, state.icon)
        }
    }

    /** Film ve dizi bölümlerini detay ekranına uğramadan oynatır. */
    private fun resumeDirectly(ctx: Context, session: Session, state: WatchState): Boolean {
        val item = when (state.section) {
            Section.VOD.name -> session.movieItem(
                state.id, state.name, state.icon, state.extension
            )
            PlayItem.EPISODE_SECTION -> session.episodeItemFromHistory(state)
            else -> null
        } ?: return false
        PlayerActivity.start(ctx, listOf(item), 0)
        return true
    }

    @Volatile
    private var autoplayConsumed = false

    /**
     * Açılışta son izlenen kanalı başlatır. Süreç ömrü boyunca yalnızca
     * bir kez çalışır; ayarlara girip çıkınca tekrar tetiklenmez.
     */
    fun resumeLastChannelOnce(ctx: Context, session: Session): Boolean {
        if (autoplayConsumed) return false
        autoplayConsumed = true
        return resumeLastChannel(ctx, session)
    }

    /** Açılışta son izlenen kanalı başlatma ayarı. */
    fun resumeLastChannel(ctx: Context, session: Session): Boolean {
        if (!Settings.autoplayLastChannel) return false
        val item = PlayItem.fromJson(Settings.lastLiveItem) ?: return false
        val channels = session.catalog.get(Section.LIVE)?.items.orEmpty()
        if (channels.isEmpty()) {
            PlayerActivity.start(ctx, listOf(item), 0)
            return true
        }
        val match = channels.firstOrNull { it.id == item.id } ?: return false
        openLive(ctx, session, channels, match)
        return true
    }
}
