package com.prizma.iptv.ui.home

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.prizma.iptv.R
import com.prizma.iptv.core.Fmt
import com.prizma.iptv.data.model.Profile
import com.prizma.iptv.data.model.SavedItem
import com.prizma.iptv.data.model.Section
import com.prizma.iptv.data.model.WatchState
import com.prizma.iptv.data.repo.CatalogRepository
import com.prizma.iptv.data.repo.Session
import com.prizma.iptv.ui.common.focusHighlight
import com.prizma.iptv.ui.detail.EpgDialog
import com.prizma.iptv.ui.guide.GuideScreen
import com.prizma.iptv.ui.settings.SettingsScreen
import com.prizma.iptv.ui.theme.Ink
import com.prizma.iptv.ui.theme.accent
import com.prizma.iptv.ui.theme.ui

/** Ekranların paylaştığı, akışlardan toplanmış veri. */
data class HomeData(
    val sections: Map<Section, CatalogRepository.SectionData>,
    val loading: Set<Section>,
    val favorites: List<SavedItem>,
    val history: List<WatchState>,
    val epgRevision: Int
) {
    fun section(section: Section): CatalogRepository.SectionData? = sections[section]
    fun isLoading(section: Section) = loading.contains(section)
}

@Composable
fun HomeScreen(
    session: Session,
    warning: String?,
    onDismissWarning: () -> Unit,
    onAddProfile: () -> Unit,
    onSwitchProfile: (Profile) -> Unit,
    onRemoveProfile: (Profile) -> Unit,
    onSignOut: () -> Unit
) {
    val profile = ui()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val state = remember(session) { HomeState(session, scope) }

    val sections by session.catalog.sections.collectAsStateWithLifecycle()
    val loading by session.catalog.loading.collectAsStateWithLifecycle()
    val favorites by session.favorites.items.collectAsStateWithLifecycle()
    val history by session.history.items.collectAsStateWithLifecycle()
    val epgRevision by session.epg.revision.collectAsStateWithLifecycle()
    val epgRefreshing by session.epg.refreshing.collectAsStateWithLifecycle()

    val data = HomeData(sections, loading, favorites, history, epgRevision)

    LaunchedEffect(session) { state.warmUp() }

    // Kumandadaki MENU / INFO tusu, odaktaki kutucugun baglam menusunu acar.
    DisposableEffect(state) {
        HomeBus.onKey = handler@{ code ->
            if (code != KeyEvent.KEYCODE_MENU && code != KeyEvent.KEYCODE_INFO) {
                return@handler false
            }
            val focused = state.focusedItem
            val busy = state.contextItem != null ||
                state.pendingPinCategory != null ||
                state.epgDialogItem != null
            if (focused == null || busy) return@handler false
            state.contextItem = focused
            true
        }
        onDispose { HomeBus.onKey = null }
    }

    BackHandler(enabled = state.route != Route.HOME || state.searchActive) {
        state.handleBack()
    }

    Surface(color = Ink.Bg, modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = profile.isTv || maxWidth >= 680.dp

            Column(Modifier.fillMaxSize()) {

                if (warning != null) {
                    WarningStrip(warning, onDismissWarning)
                }

                if (epgRefreshing) {
                    LinearProgressIndicator(
                        color = accent(),
                        trackColor = Ink.SurfaceHigh,
                        modifier = Modifier.fillMaxWidth().height(2.dp)
                    )
                }

                if (wide) {
                    Row(Modifier.fillMaxSize()) {
                        NavRail(state, session, onSignOut)
                        Column(Modifier.fillMaxSize()) {
                            TopBar(state, compact = false)
                            RouteContent(
                                state, data, session,
                                onAddProfile, onSwitchProfile, onRemoveProfile, onSignOut,
                                Modifier.weight(1f)
                            )
                        }
                    }
                } else {
                    TopBar(state, compact = true)
                    RouteContent(
                        state, data, session,
                        onAddProfile, onSwitchProfile, onRemoveProfile, onSignOut,
                        Modifier.weight(1f)
                    )
                    BottomBar(state)
                }
            }

            // Katman hâlindeki diyaloglar
            state.pendingPinCategory?.let {
                PinPrompt(
                    onSuccess = { state.unlockPending() },
                    onDismiss = { state.dismissPin() }
                )
            }

            state.epgDialogItem?.let { item ->
                EpgDialog(
                    session = session,
                    item = item,
                    onDismiss = { state.epgDialogItem = null }
                )
            }

            state.contextItem?.let { (section, item) ->
                ItemContextSheet(
                    state = state,
                    session = session,
                    section = section,
                    item = item,
                    onDismiss = { state.contextItem = null }
                )
            }
        }
    }
}

@Composable
private fun RouteContent(
    state: HomeState,
    data: HomeData,
    session: Session,
    onAddProfile: () -> Unit,
    onSwitchProfile: (Profile) -> Unit,
    onRemoveProfile: (Profile) -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier.fillMaxSize()) {
        when (state.route) {
            Route.HOME -> DashboardTab(state, data)
            Route.LIVE -> CatalogTab(state, data, Section.LIVE)
            Route.MOVIES -> CatalogTab(state, data, Section.VOD)
            Route.SERIES -> CatalogTab(state, data, Section.SERIES)
            Route.GUIDE -> GuideScreen(state, data)
            Route.SEARCH -> SearchTab(state, data)
            Route.RECENT -> RecentTab(state, data)
            Route.SETTINGS -> SettingsScreen(
                state = state,
                session = session,
                onAddProfile = onAddProfile,
                onSwitchProfile = onSwitchProfile,
                onRemoveProfile = onRemoveProfile,
                onSignOut = onSignOut
            )
        }
    }
}

@Composable
private fun WarningStrip(message: String, onDismiss: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color(0x33FF6B6B))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            message,
            color = Ink.Danger,
            fontSize = 12.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Default.Close, null, tint = Ink.Danger, modifier = Modifier.size(16.dp))
        }
    }
}

/** TV ve geniş ekranlarda sol menü. */
@Composable
private fun NavRail(state: HomeState, session: Session, onSignOut: () -> Unit) {
    val profile = ui()
    val tint = accent()
    val account by session.account.collectAsStateWithLifecycle()
    val firstItem = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        if (profile.isTv) runCatching { firstItem.requestFocus() }
    }

    Column(
        Modifier
            .width(if (profile.isTv) 230.dp else 200.dp)
            .fillMaxHeight()
            .background(Ink.Surface)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 14.dp)
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(tint),
                contentAlignment = Alignment.Center
            ) {
                Text("▶", color = Color.White, fontSize = 13.sp)
            }
            Spacer(Modifier.width(10.dp))
            Text(
                stringResource(R.string.app_name),
                color = Ink.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(10.dp))

        Route.entries.forEachIndexed { index, route ->
            RailItem(
                route = route,
                selected = state.route == route,
                modifier = if (index == 0) Modifier.focusRequester(firstItem) else Modifier
            ) { state.navigate(route) }
        }

        Spacer(Modifier.height(16.dp))

        Column(Modifier.padding(horizontal = 16.dp)) {
            Text(
                account.username.ifBlank { session.profile.displayName() },
                color = Ink.TextSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                if (account.expiryMs > 0L) Fmt.date(account.expiryMs)
                else stringResource(R.string.s_expiry_never),
                color = Ink.TextMuted,
                fontSize = 10.sp
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.logout),
                color = Ink.TextMuted,
                fontSize = 11.sp,
                modifier = Modifier
                    .focusHighlight(RoundedCornerShape(6.dp))
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onSignOut)
                    .padding(horizontal = 6.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun RailItem(
    route: Route,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val tint = accent()
    Row(
        modifier
            .fillMaxWidth()
            .focusHighlight(RoundedCornerShape(0.dp))
            .background(if (selected) tint.copy(alpha = 0.22f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(route.glyph, fontSize = 14.sp, color = if (selected) tint else Ink.TextMuted)
        Spacer(Modifier.width(12.dp))
        Text(
            stringResource(route.titleRes),
            color = if (selected) Color.White else Ink.TextSecondary,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Telefonda alt gezinme çubuğu. */
@Composable
private fun BottomBar(state: HomeState) {
    val tint = accent()
    val items = listOf(Route.HOME, Route.LIVE, Route.MOVIES, Route.SERIES, Route.GUIDE)
    Row(
        Modifier
            .fillMaxWidth()
            .background(Ink.Surface)
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEach { route ->
            val selected = state.route == route
            Column(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { state.navigate(route) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(route.glyph, fontSize = 15.sp, color = if (selected) tint else Ink.TextMuted)
                Spacer(Modifier.height(2.dp))
                Text(
                    stringResource(route.titleRes),
                    color = if (selected) Color.White else Ink.TextMuted,
                    fontSize = 9.sp,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun TopBar(state: HomeState, compact: Boolean) {
    val tint = accent()
    val searchFocus = remember { FocusRequester() }

    LaunchedEffect(state.searchActive) {
        if (state.searchActive) runCatching { searchFocus.requestFocus() }
    }

    Row(
        Modifier
            .fillMaxWidth()
            .background(Ink.Bg)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (state.searchActive) {
            TextField(
                value = state.query,
                onValueChange = { state.query = it },
                placeholder = {
                    Text(stringResource(R.string.search_hint), fontSize = 13.sp)
                },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Ink.Surface,
                    unfocusedContainerColor = Ink.Surface,
                    focusedTextColor = Ink.TextPrimary,
                    unfocusedTextColor = Ink.TextPrimary,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = tint
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .focusRequester(searchFocus)
            )
            IconButton(onClick = {
                state.query = ""
                state.searchActive = false
                if (state.route == Route.SEARCH) state.navigate(Route.HOME)
            }) {
                Icon(Icons.Default.Close, null, tint = Ink.TextSecondary)
            }
        } else {
            Text(
                stringResource(state.route.titleRes),
                color = Ink.TextPrimary,
                fontSize = if (compact) 16.sp else 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = {
                // Katalog sekmelerinde bulundugun bolumu suzer, digerlerinde
                // uc bolumde birden arayan sekmeye gecer.
                if (state.route.section != null) state.searchActive = true
                else state.navigate(Route.SEARCH)
            }) {
                Icon(Icons.Default.Search, null, tint = Ink.TextSecondary)
            }
            IconButton(onClick = { state.refreshAll() }) {
                Icon(Icons.Default.Refresh, null, tint = Ink.TextSecondary)
            }
            if (compact) {
                IconButton(onClick = { state.navigate(Route.SETTINGS) }) {
                    Icon(Icons.Default.Settings, null, tint = Ink.TextSecondary)
                }
            }
        }
    }
}
