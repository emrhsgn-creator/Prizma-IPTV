package com.prizma.iptv.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.prizma.iptv.R
import com.prizma.iptv.core.userMessage
import com.prizma.iptv.data.model.Category
import com.prizma.iptv.data.model.Section
import com.prizma.iptv.data.model.StreamItem
import com.prizma.iptv.data.repo.Session
import com.prizma.iptv.ui.common.Chip
import com.prizma.iptv.ui.common.LoadingBox
import com.prizma.iptv.ui.common.MediaTile
import com.prizma.iptv.ui.common.MessageBox
import com.prizma.iptv.ui.common.focusHighlight
import com.prizma.iptv.ui.theme.Ink
import com.prizma.iptv.ui.theme.accent
import com.prizma.iptv.ui.theme.ui

/**
 * Bir bölümün (canlı / film / dizi) kategori listesi ve içerik ızgarası.
 * Geniş ekranda kategoriler solda sabit sütun, dar ekranda üstte kaydırılabilir
 * etiket şeridi olarak gösterilir.
 */
@Composable
fun CatalogTab(state: HomeState, data: HomeData, section: Section) {
    val ctx = LocalContext.current
    val profile = ui()
    val session = state.session
    val sectionData = data.section(section)
    val error by session.catalog.error.collectAsStateWithLifecycle()

    if (sectionData == null) {
        if (data.isLoading(section)) {
            LoadingBox(Modifier.fillMaxSize(), stringResource(section.titleRes()))
        } else {
            val throwable = error
            MessageBox(
                message = throwable?.userMessage(ctx) ?: stringResource(R.string.empty_content),
                isError = throwable != null,
                actionLabel = stringResource(R.string.retry),
                onAction = {
                    session.catalog.clearError()
                    state.ensure(section, force = true)
                }
            )
        }
        return
    }

    val allItems = remember(sectionData) { state.visibleItems(sectionData) }
    val categories = remember(sectionData) { state.visibleCategories(sectionData) }
    val favorites = data.favorites.filter { it.section == section.name }
    val selected = state.selectedCategory(section)
    val query = if (state.searchActive) state.query.trim() else ""

    val tiles = remember(allItems, favorites, selected, query, state.sortMode, section) {
        buildTiles(allItems, favorites, selected, query, state.sortMode)
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = profile.isTv || maxWidth >= 680.dp

        val grid: @Composable () -> Unit = {
            Column(Modifier.fillMaxSize()) {
                SortRow(state, section)
                ItemGrid(
                    state = state,
                    data = data,
                    section = section,
                    items = tiles,
                    allChannels = allItems,
                    showReorder = selected == CATEGORY_FAVORITES &&
                        state.sortMode == SortMode.DEFAULT
                )
            }
        }

        if (wide) {
            Row(Modifier.fillMaxSize()) {
                CategorySidebar(
                    state = state,
                    section = section,
                    categories = categories,
                    totalCount = allItems.size,
                    favoriteCount = favorites.size,
                    selected = selected
                )
                grid()
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                CategoryStrip(
                    state = state,
                    section = section,
                    categories = categories,
                    totalCount = allItems.size,
                    favoriteCount = favorites.size,
                    selected = selected
                )
                grid()
            }
        }
    }
}

fun Section.titleRes(): Int = when (this) {
    Section.LIVE -> R.string.nav_live
    Section.VOD -> R.string.nav_movies
    Section.SERIES -> R.string.nav_series
}

/** Kategori, favori ve arama süzgeçlerini uygulayıp sıralar. */
private fun buildTiles(
    items: List<StreamItem>,
    favorites: List<com.prizma.iptv.data.model.SavedItem>,
    selected: String,
    query: String,
    sort: SortMode
): List<StreamItem> {
    val base = when (selected) {
        CATEGORY_FAVORITES -> {
            val byId = items.associateBy { it.id }
            favorites.map { fav -> byId[fav.id] ?: fav.asStreamItem() }
        }
        CATEGORY_RECENT -> items.sortedByDescending { it.added }.take(200)
        CATEGORY_ALL -> items
        else -> items.filter { it.categoryId == selected }
    }

    val filtered = if (query.isEmpty()) base
    else base.filter { it.name.contains(query, ignoreCase = true) }

    return when (sort) {
        SortMode.DEFAULT -> filtered
        SortMode.NAME -> filtered.sortedBy { it.name.lowercase() }
        SortMode.NAME_DESC -> filtered.sortedByDescending { it.name.lowercase() }
        SortMode.RATING -> filtered.sortedByDescending { it.rating }
        SortMode.ADDED -> filtered.sortedByDescending { it.added }
        SortMode.NUMBER -> filtered.sortedBy { if (it.number > 0) it.number else Int.MAX_VALUE }
    }
}

@Composable
private fun SortRow(state: HomeState, section: Section) {
    val modes = if (section == Section.LIVE) {
        listOf(SortMode.DEFAULT, SortMode.NAME, SortMode.NUMBER)
    } else {
        listOf(SortMode.DEFAULT, SortMode.NAME, SortMode.NAME_DESC, SortMode.RATING, SortMode.ADDED)
    }
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(modes, key = { it.name }) { mode ->
            Chip(stringResource(mode.labelRes), state.sortMode == mode) {
                state.sortMode = mode
            }
        }
    }
}

@Composable
private fun CategorySidebar(
    state: HomeState,
    section: Section,
    categories: List<Category>,
    totalCount: Int,
    favoriteCount: Int,
    selected: String
) {
    val profile = ui()
    Column(
        Modifier
            .width(if (profile.isTv) 250.dp else 220.dp)
            .fillMaxHeight()
            .background(Ink.Surface)
    ) {
        Text(
            stringResource(R.string.categories),
            color = accent(),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(16.dp)
        )
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                SidebarRow(stringResource(R.string.cat_favorites), favoriteCount,
                    selected == CATEGORY_FAVORITES) {
                    state.selectCategory(section, CATEGORY_FAVORITES)
                }
            }
            item {
                SidebarRow(stringResource(R.string.cat_all), totalCount,
                    selected == CATEGORY_ALL) {
                    state.selectCategory(section, CATEGORY_ALL)
                }
            }
            if (section != Section.LIVE) {
                item {
                    SidebarRow(stringResource(R.string.cat_recent_added), 0,
                        selected == CATEGORY_RECENT) {
                        state.selectCategory(section, CATEGORY_RECENT)
                    }
                }
            }
            items(categories, key = { it.id }) { category ->
                SidebarRow(
                    label = category.name,
                    count = category.count,
                    selected = selected == category.id,
                    locked = !state.isUnlocked(category)
                ) {
                    state.requestCategory(section, category)
                }
            }
        }
    }
}

@Composable
private fun SidebarRow(
    label: String,
    count: Int,
    selected: Boolean,
    locked: Boolean = false,
    onClick: () -> Unit
) {
    val tint = accent()
    Row(
        Modifier
            .fillMaxWidth()
            .focusHighlight(RoundedCornerShape(0.dp))
            .background(if (selected) tint.copy(alpha = 0.20f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            (if (locked) "🔒 " else "") + label.uppercase(),
            color = if (selected) Color.White else Ink.TextSecondary,
            fontSize = 12.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (count > 0) {
            Spacer(Modifier.width(6.dp))
            Text("$count", color = Ink.TextMuted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun CategoryStrip(
    state: HomeState,
    section: Section,
    categories: List<Category>,
    totalCount: Int,
    favoriteCount: Int,
    selected: String
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Chip(
                stringResource(R.string.cat_favorites) + " ($favoriteCount)",
                selected == CATEGORY_FAVORITES
            ) { state.selectCategory(section, CATEGORY_FAVORITES) }
        }
        item {
            Chip(
                stringResource(R.string.cat_all) + " ($totalCount)",
                selected == CATEGORY_ALL
            ) { state.selectCategory(section, CATEGORY_ALL) }
        }
        if (section != Section.LIVE) {
            item {
                Chip(stringResource(R.string.cat_recent_added), selected == CATEGORY_RECENT) {
                    state.selectCategory(section, CATEGORY_RECENT)
                }
            }
        }
        items(categories, key = { it.id }) { category ->
            val locked = !state.isUnlocked(category)
            Chip(
                (if (locked) "🔒 " else "") + category.name + " (" + category.count + ")",
                selected == category.id
            ) { state.requestCategory(section, category) }
        }
    }
}

@Composable
fun ItemGrid(
    state: HomeState,
    data: HomeData,
    section: Section,
    items: List<StreamItem>,
    allChannels: List<StreamItem>,
    showReorder: Boolean
) {
    val ctx = LocalContext.current
    val profile = ui()
    val session = state.session
    val live = section == Section.LIVE

    if (items.isEmpty()) {
        MessageBox(stringResource(R.string.empty_content))
        return
    }

    val favoriteIds = remember(data.favorites, section) {
        data.favorites.filter { it.section == section.name }.map { it.id }.toSet()
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(if (live) profile.channelWidth else profile.posterWidth),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(items, key = { it.id + "/" + it.categoryId }) { item ->
            Column(
                Modifier.onFocusChanged {
                    if (it.hasFocus) state.focusedItem = section to item
                }
            ) {
                MediaTile(
                    title = item.name,
                    imageUrl = item.icon,
                    wide = live,
                    rating = item.rating,
                    subtitle = if (live) {
                        nowPlayingTitle(session, item, data.epgRevision)
                    } else "",
                    badge = if (live && item.hasArchive) {
                        stringResource(R.string.player_catchup)
                    } else "",
                    favorite = favoriteIds.contains(item.id),
                    onClick = {
                        Launch.open(ctx, session, section, item, allChannels)
                    },
                    onLongClick = { state.contextItem = section to item }
                )
                if (showReorder) {
                    ReorderRow(
                        onLeft = { session.favorites.move(section.name, item.id, -1) },
                        onRight = { session.favorites.move(section.name, item.id, 1) }
                    )
                }
            }
        }
    }
}

@Composable
private fun nowPlayingTitle(session: Session, item: StreamItem, epgRevision: Int): String =
    remember(item.id, epgRevision) {
        session.epg.nowNextFor(item).first?.title.orEmpty()
    }

@Composable
private fun ReorderRow(onLeft: () -> Unit, onRight: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        Text(
            "◀",
            color = accent(),
            fontSize = 14.sp,
            modifier = Modifier
                .focusHighlight(RoundedCornerShape(4.dp))
                .clickable(onClick = onLeft)
                .padding(horizontal = 14.dp, vertical = 2.dp)
        )
        Text(
            "▶",
            color = accent(),
            fontSize = 14.sp,
            modifier = Modifier
                .focusHighlight(RoundedCornerShape(4.dp))
                .clickable(onClick = onRight)
                .padding(horizontal = 14.dp, vertical = 2.dp)
        )
    }
}
