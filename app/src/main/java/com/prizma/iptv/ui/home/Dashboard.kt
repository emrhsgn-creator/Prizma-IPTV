package com.prizma.iptv.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.prizma.iptv.R
import com.prizma.iptv.data.model.SavedItem
import com.prizma.iptv.data.model.Section
import com.prizma.iptv.data.model.StreamItem
import com.prizma.iptv.data.model.WatchState
import com.prizma.iptv.ui.common.LoadingBox
import com.prizma.iptv.ui.common.MediaTile
import com.prizma.iptv.ui.common.SectionHeader
import com.prizma.iptv.ui.theme.ui

fun SavedItem.asStreamItem() = StreamItem(
    id = id,
    name = name,
    icon = icon,
    extension = extension,
    rating = rating,
    number = number
)

fun WatchState.asStreamItem() = StreamItem(
    id = id,
    name = name,
    icon = icon,
    extension = extension
)

private data class LiveFavoriteRow(
    val channel: StreamItem,
    val name: String,
    val icon: String,
    val nowTitle: String
)

fun sectionOfKey(key: String): Section? = when (key) {
    Section.LIVE.name -> Section.LIVE
    Section.VOD.name -> Section.VOD
    Section.SERIES.name -> Section.SERIES
    else -> null
}

/**
 * Ana sayfa: izlemeye devam, favoriler ve katalogdan türetilmiş öneri rafları.
 * Raflar veri geldikçe sırayla belirir; hiçbiri hazır değilse yükleniyor gösterilir.
 */
@Composable
fun DashboardTab(state: HomeState, data: HomeData) {
    val ctx = LocalContext.current
    val session = state.session

    val continueWatching = data.history.filter {
        it.duration > 0L && it.position > 20_000L && !it.finished
    }
    val liveFavorites = data.favorites.filter { it.section == Section.LIVE.name }
    val vodFavorites = data.favorites.filter { it.section != Section.LIVE.name }

    // On binlerce ogeli kataloglarda suzme her bestelemede tekrarlanmasin.
    val movies = remember(data.sections) { state.visibleItems(data.section(Section.VOD)) }
    val series = remember(data.sections) { state.visibleItems(data.section(Section.SERIES)) }
    val channels = remember(data.sections) { state.visibleItems(data.section(Section.LIVE)) }

    // Şu an yayında bilgisi EPG sürümüne bağlı; raf içinde hesaplanırsa
    // liste yeniden bestelenmediği için bayat kalıyor.
    val liveFavoriteRows = remember(liveFavorites, channels, data.epgRevision) {
        liveFavorites.map { fav ->
            val channel = channels.firstOrNull { it.id == fav.id } ?: fav.asStreamItem()
            LiveFavoriteRow(
                channel = channel,
                name = fav.name,
                icon = fav.icon.ifBlank { channel.icon },
                nowTitle = session.epg.nowNextFor(channel).first?.title.orEmpty()
            )
        }
    }

    val empty = continueWatching.isEmpty() && data.favorites.isEmpty() &&
        data.history.isEmpty() && movies.isEmpty() && series.isEmpty() && channels.isEmpty()

    if (empty) {
        LoadingBox(Modifier.fillMaxSize(), stringResource(R.string.home_greeting_hint))
        return
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 28.dp)
    ) {
        if (continueWatching.isNotEmpty()) item {
            Shelf(stringResource(R.string.shelf_continue)) {
                items(continueWatching, key = { it.section + it.id }) { watch ->
                    val wide = watch.section == Section.LIVE.name
                    ShelfTile(
                        state = state,
                        name = watch.name,
                        icon = watch.icon,
                        wide = wide,
                        progress = watch.progress,
                        onClick = { Launch.openWatchState(ctx, session, watch) }
                    )
                }
            }
        }

        if (liveFavoriteRows.isNotEmpty()) item {
            Shelf(stringResource(R.string.shelf_live_favorites)) {
                items(liveFavoriteRows, key = { it.channel.id }) { row ->
                    ShelfTile(
                        state = state,
                        name = row.name,
                        icon = row.icon,
                        wide = true,
                        subtitle = row.nowTitle,
                        onClick = {
                            Launch.openLive(
                                ctx, session,
                                channels.ifEmpty { listOf(row.channel) },
                                row.channel
                            )
                        }
                    )
                }
            }
        }

        if (vodFavorites.isNotEmpty()) item {
            Shelf(stringResource(R.string.shelf_favorites)) {
                items(vodFavorites, key = { it.section + it.id }) { fav ->
                    val section = sectionOfKey(fav.section) ?: Section.VOD
                    ShelfTile(
                        state = state,
                        name = fav.name,
                        icon = fav.icon,
                        wide = false,
                        rating = fav.rating,
                        onClick = {
                            Launch.open(ctx, session, section, fav.asStreamItem())
                        }
                    )
                }
            }
        }

        if (movies.isNotEmpty()) {
            item {
                Shelf(stringResource(R.string.shelf_new_movies)) {
                    items(
                        movies.sortedByDescending { it.added }.take(24),
                        key = { it.id }
                    ) { item ->
                        ShelfTile(
                            state = state,
                            name = item.name,
                            icon = item.icon,
                            wide = false,
                            rating = item.rating,
                            onClick = { Launch.open(ctx, session, Section.VOD, item) }
                        )
                    }
                }
            }
            item {
                Shelf(stringResource(R.string.shelf_top_movies)) {
                    items(
                        movies.filter { it.rating > 0.0 }
                            .sortedByDescending { it.rating }
                            .take(20),
                        key = { it.id }
                    ) { item ->
                        ShelfTile(
                            state = state,
                            name = item.name,
                            icon = item.icon,
                            wide = false,
                            rating = item.rating,
                            onClick = { Launch.open(ctx, session, Section.VOD, item) }
                        )
                    }
                }
            }
        }

        if (series.isNotEmpty()) {
            item {
                Shelf(stringResource(R.string.shelf_new_series)) {
                    items(
                        series.sortedByDescending { it.added }.take(24),
                        key = { it.id }
                    ) { item ->
                        ShelfTile(
                            state = state,
                            name = item.name,
                            icon = item.icon,
                            wide = false,
                            rating = item.rating,
                            onClick = { Launch.open(ctx, session, Section.SERIES, item) }
                        )
                    }
                }
            }
            item {
                Shelf(stringResource(R.string.shelf_top_series)) {
                    items(
                        series.filter { it.rating > 0.0 }
                            .sortedByDescending { it.rating }
                            .take(20),
                        key = { it.id }
                    ) { item ->
                        ShelfTile(
                            state = state,
                            name = item.name,
                            icon = item.icon,
                            wide = false,
                            rating = item.rating,
                            onClick = { Launch.open(ctx, session, Section.SERIES, item) }
                        )
                    }
                }
            }
        }

        if (data.history.isNotEmpty()) item {
            Shelf(stringResource(R.string.shelf_recent)) {
                items(data.history.take(25), key = { it.section + it.id }) { watch ->
                    ShelfTile(
                        state = state,
                        name = watch.name,
                        icon = watch.icon,
                        wide = watch.section == Section.LIVE.name,
                        progress = watch.progress,
                        onClick = { Launch.openWatchState(ctx, session, watch) }
                    )
                }
            }
        }
    }
}

@Composable
private fun Shelf(
    title: String,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit
) {
    Column {
        SectionHeader(title)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}

@Composable
private fun ShelfTile(
    state: HomeState,
    name: String,
    icon: String,
    wide: Boolean,
    rating: Double = 0.0,
    progress: Float = 0f,
    subtitle: String = "",
    onClick: () -> Unit
) {
    val profile = ui()
    MediaTile(
        title = name,
        imageUrl = icon,
        wide = wide,
        rating = rating,
        progress = progress,
        subtitle = subtitle,
        onClick = onClick,
        modifier = Modifier.width(
            if (wide) profile.channelWidth else profile.posterWidth
        )
    )
}
