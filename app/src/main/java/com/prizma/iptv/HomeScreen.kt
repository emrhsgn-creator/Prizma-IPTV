package com.prizma.iptv

import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.material.icons.filled.Settings

private val BarStart = Color(0xFF23306E)
private val BarEnd = Color(0xFF5B3FA8)
private val SideBg = Color(0xFF0C0F1D)
private val Muted = Color(0xFF8A90A0)
private const val FAV_CAT = "__FAV__"

private data class SectionData(val categories: List<Category>, val items: List<StreamItem>)

private data class Tile(
    val id: String,
    val name: String,
    val icon: String,
    val ext: String,
    val rating: String,
    val sectionName: String,
    val progress: Float = 0f,
    val order: Long = 0L
)

private enum class SortMode(val label: String) {
    MANUAL("Elle"), NAME("A-Z"), RATING("Puan"), ADDED("Eklenme")
}

private fun ratingOf(s: String): Double = s.replace(',', '.').toDoubleOrNull() ?: 0.0

private fun StreamItem.toTile(sec: String) =
    Tile(id, name, icon, extension, rating, sec, 0f, added)

private fun SavedItem.toTile() =
    Tile(id, name, icon, extension, rating, section, 0f, savedAt)

private fun WatchState.toTile(): Tile {
    val p = if (duration > 0) (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f
    return Tile(id, name, icon, extension, "", section, p, lastSeen)
}

private fun launchTile(ctx: Context, host: String, user: String, pass: String, t: Tile) {
    when (t.sectionName) {
        Section.SERIES.name -> {
            SeriesActivity.start(ctx, host, user, pass, t.id, t.name, t.icon)
            return
        }
        Section.VOD.name -> {
            MovieActivity.start(ctx, host, user, pass, t.id, t.name, t.icon, t.ext, t.rating)
            return
        }
    }
    val live = t.sectionName == Section.LIVE.name
    val e = if (t.ext.isNotEmpty()) t.ext else if (live) "ts" else "mp4"
    val folder = when {
        live -> "live"
        t.sectionName == "EPISODE" -> "series"
        else -> "movie"
    }
    PlayerActivity.start(
        ctx, "$host/$folder/$user/$pass/${t.id}.$e", t.name,
        t.sectionName, t.id, t.icon, e, !live
    )
}

@Composable
private fun Modifier.tvFocus(
    shape: Shape = RoundedCornerShape(10.dp),
    scaleUp: Float = 1.06f
): Modifier {
    var focused by remember { mutableStateOf(false) }
    return this
        .onFocusChanged { focused = it.isFocused }
        .scale(if (focused) scaleUp else 1f)
        .border(
            width = if (focused) 2.dp else 0.dp,
            color = if (focused) Color.White else Color.Transparent,
            shape = shape
        )
}

@Composable
fun HomeScreen(
    host: String,
    user: String,
    pass: String,
    account: Account,
    cacheEpoch: Int,
    onSettings: () -> Unit,
    onLogout: () -> Unit
) {
    val ctx = LocalContext.current
    val isTv = remember {
        ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    }
    val firstTab = remember { FocusRequester() }
    val cache = remember { mutableStateMapOf<Section, SectionData>() }

    var tab by remember { mutableIntStateOf(0) }
    var selectedCat by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var reload by remember { mutableIntStateOf(0) }
    var localRev by remember { mutableIntStateOf(0) }
    var sortMode by remember { mutableStateOf(SortMode.MANUAL) }

    LaunchedEffect(Unit) {
        Creds.host = host
        Creds.user = user
        Creds.pass = pass
        if (isTv) runCatching { firstTab.requestFocus() }
    }

    val section: Section? = when (tab) {
        1 -> Section.LIVE
        2 -> Section.VOD
        3 -> Section.SERIES
        else -> null
    }

    val favs = remember(localRev, Refresh.tick) { Store.favorites(ctx) }
    val hist = remember(localRev, Refresh.tick) { Store.history(ctx) }

    LaunchedEffect(reload, cacheEpoch) {
        if (reload > 0 || cacheEpoch > 0) cache.clear()
    }

    LaunchedEffect(section, reload) {
        val sec = section ?: return@LaunchedEffect
        selectedCat = ""
        if (cache.containsKey(sec)) return@LaunchedEffect
        loading = true
        error = ""
        try {
            val cats = XtreamApi.categories(host, user, pass, sec)
            val items = XtreamApi.allStreams(host, user, pass, sec)
            val counts = items.groupingBy { it.categoryId }.eachCount()
            cache[sec] = SectionData(cats.map { it.copy(count = counts[it.id] ?: 0) }, items)
        } catch (e: Exception) {
            error = e.message ?: "İçerik alınamadı"
        }
        loading = false
    }

    LaunchedEffect(tab, reload) {
        if (tab != 0) return@LaunchedEffect
        for (sec in listOf(Section.VOD, Section.SERIES)) {
            if (cache.containsKey(sec)) continue
            try {
                val cats = XtreamApi.categories(host, user, pass, sec)
                val items = XtreamApi.allStreams(host, user, pass, sec)
                val counts = items.groupingBy { it.categoryId }.eachCount()
                cache[sec] = SectionData(cats.map { it.copy(count = counts[it.id] ?: 0) }, items)
            } catch (e: Exception) {
                // ana sayfa rafları sessizce boş kalır
            }
        }
    }

    BackHandler(enabled = tab != 0 || selectedCat.isNotEmpty() || query.isNotEmpty()) {
        when {
            query.isNotEmpty() || searching -> {
                query = ""
                searching = false
            }
            selectedCat.isNotEmpty() -> selectedCat = ""
            else -> tab = 0
        }
    }

    Surface(color = PrizmaBg, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.horizontalGradient(listOf(BarStart, BarEnd)))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0x33FFFFFF)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("▶", color = Color.White, fontSize = 14.sp)
                }
                Spacer(Modifier.width(8.dp))

                if (searching) {
                    TextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Ara...", color = Color(0xB3FFFFFF), fontSize = 14.sp) },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color(0x26FFFFFF),
                            unfocusedContainerColor = Color(0x26FFFFFF),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    )
                    IconButton(onClick = { searching = false; query = "" }) {
                        Icon(Icons.Default.Close, null, tint = Color.White)
                    }
                } else {
                    LazyRow(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val labels = listOf(
                            "Ana Sayfa", "Canlı TV", "Filmler", "Diziler", "Son İzlenenler"
                        )
                        itemsIndexed(labels) { i, label ->
                            val sel = i == tab
                            Text(
                                label,
                                color = if (sel) Color.White else Color(0xCCFFFFFF),
                                fontSize = 13.sp,
                                fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1,
                                modifier = Modifier
                                    .padding(end = 4.dp)
                                    .then(if (i == 0) Modifier.focusRequester(firstTab) else Modifier)
                                    .tvFocus(RoundedCornerShape(20.dp), 1.0f)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(if (sel) Color(0x40FFFFFF) else Color.Transparent)
                                    .clickable { tab = i }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                    if (section != null) {
                        IconButton(onClick = { searching = true }) {
                            Icon(Icons.Default.Search, null, tint = Color.White)
                        }
                    }
                    IconButton(onClick = { reload++ }) {
                        Icon(Icons.Default.Refresh, null, tint = Color.White)
                    }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, null, tint = Color.White)
                    }
                    IconButton(onClick = onLogout) {
                        Icon(Icons.Default.ExitToApp, null, tint = Color.White)
                    }
                }
            }

            if (tab == 0) {
                HomeTab(ctx, host, user, pass, favs, hist, cache)
                return@Column
            }

            if (tab == 4) {
                HistoryTab(
                    ctx, host, user, pass, hist,
                    onRemove = { t ->
                        Store.removeHistory(ctx, t.sectionName, t.id)
                        localRev++
                    },
                    onClear = {
                        Store.clearHistory(ctx)
                        localRev++
                    }
                )
                return@Column
            }

            val sec = section ?: return@Column
            val data = cache[sec]

            BoxWithConstraints(Modifier.fillMaxSize()) {
                val wide = maxWidth >= 620.dp

                if (loading || data == null) {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        if (error.isNotEmpty()) {
                            Text(error, color = Color(0xFFFF6B6B), fontSize = 13.sp)
                        } else {
                            CircularProgressIndicator(color = PrizmaAccent)
                        }
                    }
                    return@BoxWithConstraints
                }

                val sectionFavs = favs.filter { it.section == sec.name }
                val favIds = remember(sectionFavs) { sectionFavs.map { it.id }.toSet() }
                val showingFav = selectedCat == FAV_CAT
                val q = query.trim()

                val tiles: List<Tile> = when {
                    showingFav -> {
                        val base = sectionFavs.map { it.toTile() }
                        when (sortMode) {
                            SortMode.MANUAL -> base
                            SortMode.NAME -> base.sortedBy { it.name.lowercase() }
                            SortMode.RATING -> base.sortedByDescending { ratingOf(it.rating) }
                            SortMode.ADDED -> base.sortedByDescending { it.order }
                        }.filter { q.isEmpty() || it.name.contains(q, true) }
                    }
                    else -> data.items
                        .filter { s ->
                            (selectedCat.isEmpty() || s.categoryId == selectedCat) &&
                                (q.isEmpty() || s.name.contains(q, true))
                        }
                        .map { it.toTile(sec.name) }
                }

                val body: @Composable () -> Unit = {
                    Column(Modifier.fillMaxSize()) {
                        if (showingFav) {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(SortMode.values().toList()) { m ->
                                    Chip(m.label, m == sortMode) { sortMode = m }
                                }
                            }
                        }
                        TileGrid(
                            tiles = tiles,
                            favIds = favIds,
                            showMove = showingFav && sortMode == SortMode.MANUAL,
                            onClick = { t ->
                                if (t.sectionName == Section.LIVE.name) {
                                    val liveTiles = tiles.filter {
                                        it.sectionName == Section.LIVE.name
                                    }
                                    val idx = liveTiles.indexOfFirst { it.id == t.id }
                                    PlayerActivity.startPlaylist(
                                        ctx = ctx,
                                        urls = ArrayList(liveTiles.map {
                                            val e = if (it.ext.isNotEmpty()) it.ext else "ts"
                                            "$host/live/$user/$pass/${it.id}.$e"
                                        }),
                                        titles = ArrayList(liveTiles.map { it.name }),
                                        ids = ArrayList(liveTiles.map { it.id }),
                                        icons = ArrayList(liveTiles.map { it.icon }),
                                        exts = ArrayList(liveTiles.map { it.ext }),
                                        startIndex = if (idx >= 0) idx else 0,
                                        section = Section.LIVE.name,
                                        resumable = false
                                    )
                                } else {
                                    launchTile(ctx, host, user, pass, t)
                                }
                            },
                            onLong = { t ->
                                val added = Store.toggleFavorite(
                                    ctx, t.sectionName, t.id, t.name, t.icon, t.ext, t.rating
                                )
                                localRev++
                                Toast.makeText(
                                    ctx,
                                    if (added) "Favorilere eklendi" else "Favorilerden çıkarıldı",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            onMove = { t, d ->
                                Store.moveFavorite(ctx, sec.name, t.id, d)
                                localRev++
                            }
                        )
                    }
                }

                if (wide) {
                    Row(Modifier.fillMaxSize()) {
                        Column(
                            Modifier
                                .width(230.dp)
                                .fillMaxHeight()
                                .background(SideBg)
                        ) {
                            Text(
                                "Kategoriler",
                                color = PrizmaAccent,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(16.dp)
                            )
                            LazyColumn(Modifier.weight(1f)) {
                                item {
                                    SideRow("FAVORİLER", sectionFavs.size, showingFav) {
                                        selectedCat = FAV_CAT
                                    }
                                }
                                item {
                                    SideRow("TÜMÜ", data.items.size, selectedCat.isEmpty()) {
                                        selectedCat = ""
                                    }
                                }
                                items(data.categories) { c ->
                                    SideRow(c.name, c.count, selectedCat == c.id) {
                                        selectedCat = c.id
                                    }
                                }
                            }
                            Text(
                                "${account.username} · ${account.expiry}",
                                color = Muted,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                        body()
                    }
                } else {
                    Column(Modifier.fillMaxSize()) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            item {
                                Chip("FAVORİLER (${sectionFavs.size})", showingFav) {
                                    selectedCat = FAV_CAT
                                }
                            }
                            item {
                                Chip("TÜMÜ", selectedCat.isEmpty()) { selectedCat = "" }
                            }
                            items(data.categories) { c ->
                                Chip("${c.name} (${c.count})", selectedCat == c.id) {
                                    selectedCat = c.id
                                }
                            }
                        }
                        body()
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryTab(
    ctx: Context,
    host: String,
    user: String,
    pass: String,
    hist: List<WatchState>,
    onRemove: (Tile) -> Unit,
    onClear: () -> Unit
) {
    if (hist.isEmpty()) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            Text("Henüz bir şey izlemedin.", color = Muted, fontSize = 13.sp)
        }
        return
    }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "${hist.size} kayıt",
                color = Muted,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f)
            )
            Row(
                Modifier
                    .tvFocus(RoundedCornerShape(16.dp), 1.0f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(PrizmaSurface)
                    .clickable(onClick = onClear)
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Delete, null,
                    tint = Color(0xFFFF6B6B),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text("Geçmişi temizle", color = Color(0xFFC3C8D4), fontSize = 12.sp)
            }
        }
        Text(
            "Bir kaydı uzun basarak silebilirsin.",
            color = Muted,
            fontSize = 10.sp,
            modifier = Modifier.padding(start = 14.dp, bottom = 6.dp)
        )
        TileGrid(
            tiles = hist.map { it.toTile() },
            favIds = emptySet(),
            showMove = false,
            onClick = { t -> launchTile(ctx, host, user, pass, t) },
            onLong = { t ->
                onRemove(t)
                Toast.makeText(ctx, "Geçmişten silindi", Toast.LENGTH_SHORT).show()
            },
            onMove = { _, _ -> }
        )
    }
}

@Composable
private fun HomeTab(
    ctx: Context,
    host: String,
    user: String,
    pass: String,
    favs: List<SavedItem>,
    hist: List<WatchState>,
    cache: Map<Section, SectionData>
) {
    val devam = hist.filter {
        it.duration > 0 && it.position > 60_000 && it.position < it.duration * 95 / 100
    }
    val vod = cache[Section.VOD]?.items.orEmpty()
    val series = cache[Section.SERIES]?.items.orEmpty()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {

        if (devam.isNotEmpty()) item {
            Shelf("Devam Et") {
                items(devam) { w ->
                    val t = w.toTile()
                    ShelfTile(t) { launchTile(ctx, host, user, pass, t) }
                }
            }
        }

        if (favs.isNotEmpty()) item {
            Shelf("Favoriler") {
                items(favs.reversed()) { f ->
                    val t = f.toTile()
                    ShelfTile(t) { launchTile(ctx, host, user, pass, t) }
                }
            }
        }

        if (hist.isNotEmpty()) item {
            Shelf("Son İzlenenler") {
                items(hist.take(25)) { w ->
                    val t = w.toTile()
                    ShelfTile(t) { launchTile(ctx, host, user, pass, t) }
                }
            }
        }

        if (vod.isNotEmpty()) {
            item {
                Shelf("Son Eklenen Filmler") {
                    items(vod.sortedByDescending { it.added }.take(20)) { s ->
                        val t = s.toTile(Section.VOD.name)
                        ShelfTile(t) { launchTile(ctx, host, user, pass, t) }
                    }
                }
            }
            item {
                Shelf("En İyi 10 Film") {
                    items(vod.sortedByDescending { ratingOf(it.rating) }.take(10)) { s ->
                        val t = s.toTile(Section.VOD.name)
                        ShelfTile(t) { launchTile(ctx, host, user, pass, t) }
                    }
                }
            }
        }

        if (series.isNotEmpty()) {
            item {
                Shelf("Son Eklenen Diziler") {
                    items(series.sortedByDescending { it.added }.take(20)) { s ->
                        val t = s.toTile(Section.SERIES.name)
                        ShelfTile(t) { launchTile(ctx, host, user, pass, t) }
                    }
                }
            }
            item {
                Shelf("En İyi 10 Dizi") {
                    items(series.sortedByDescending { ratingOf(it.rating) }.take(10)) { s ->
                        val t = s.toTile(Section.SERIES.name)
                        ShelfTile(t) { launchTile(ctx, host, user, pass, t) }
                    }
                }
            }
        }

        if (devam.isEmpty() && favs.isEmpty() && hist.isEmpty() &&
            vod.isEmpty() && series.isEmpty()
        ) {
            item {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(60.dp),
                    Alignment.Center
                ) {
                    CircularProgressIndicator(color = PrizmaAccent)
                }
            }
        }
    }
}

@Composable
private fun Shelf(title: String, content: LazyListScope.() -> Unit) {
    Column(Modifier.padding(top = 14.dp)) {
        Text(
            title,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 14.dp, bottom = 8.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            content = content
        )
    }
}

@Composable
private fun ShelfTile(t: Tile, onClick: () -> Unit) {
    val live = t.sectionName == Section.LIVE.name
    Column(Modifier.width(if (live) 165.dp else 112.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(if (live) 16f / 9f else 2f / 3f)
                .tvFocus(RoundedCornerShape(8.dp))
                .clip(RoundedCornerShape(8.dp))
                .background(PrizmaSurface)
                .clickable(onClick = onClick)
        ) {
            if (t.icon.isNotEmpty()) {
                AsyncImage(
                    model = t.icon,
                    contentDescription = null,
                    contentScale = if (live) ContentScale.Fit else ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(if (live) 6.dp else 0.dp)
                )
            }
            if (t.rating.isNotEmpty()) RatingBadge(Modifier.align(Alignment.TopEnd), t.rating)
            if (t.progress > 0f) {
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(t.progress)
                        .height(3.dp)
                        .background(PrizmaAccent)
                )
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(
            t.name,
            color = Color(0xFFE6E8EB),
            fontSize = 10.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 13.sp
        )
    }
}

@Composable
private fun SideRow(name: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .tvFocus(RoundedCornerShape(0.dp), 1.0f)
            .background(if (selected) Color(0x2E4F8DF7) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            name.uppercase(),
            color = if (selected) Color.White else Color(0xFFC3C8D4),
            fontSize = 12.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text("$count", color = Muted, fontSize = 11.sp)
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (selected) Color.White else Color(0xFFC3C8D4),
        fontSize = 12.sp,
        maxLines = 1,
        modifier = Modifier
            .tvFocus(RoundedCornerShape(16.dp), 1.0f)
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) PrizmaAccent.copy(alpha = 0.35f) else PrizmaSurface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    )
}

@Composable
private fun RatingBadge(modifier: Modifier, rating: String) {
    Row(
        modifier = modifier
            .padding(5.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(Color(0xCC000000))
            .padding(horizontal = 5.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.Star, null,
            tint = Color(0xFFF5C518),
            modifier = Modifier.size(10.dp)
        )
        Spacer(Modifier.width(3.dp))
        Text(rating, color = Color.White, fontSize = 10.sp)
    }
}

@Composable
private fun TileGrid(
    tiles: List<Tile>,
    favIds: Set<String>,
    showMove: Boolean,
    onClick: (Tile) -> Unit,
    onLong: (Tile) -> Unit,
    onMove: (Tile, Int) -> Unit
) {
    if (tiles.isEmpty()) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            Text("İçerik bulunamadı.", color = Muted, fontSize = 13.sp)
        }
        return
    }
    val allLive = tiles.all { it.sectionName == Section.LIVE.name }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(if (allLive) 150.dp else 118.dp),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(tiles, key = { it.sectionName + it.id + it.name }) { t ->
            PosterTile(
                t = t,
                fav = favIds.contains(t.id),
                showMove = showMove,
                onClick = { onClick(t) },
                onLongClick = { onLong(t) },
                onLeft = { onMove(t, -1) },
                onRight = { onMove(t, 1) }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PosterTile(
    t: Tile,
    fav: Boolean,
    showMove: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onLeft: () -> Unit,
    onRight: () -> Unit
) {
    val live = t.sectionName == Section.LIVE.name
    var showEpg by remember { mutableStateOf(false) }
    var nowPlaying by remember { mutableStateOf("") }
    var nowProgress by remember { mutableFloatStateOf(0f) }

    if (live) {
        LaunchedEffect(t.id) {
            try {
                val list = XtreamApi.shortEpg(Creds.host, Creds.user, Creds.pass, t.id, 2)
                val sec = System.currentTimeMillis() / 1000
                val cur = list.firstOrNull { sec in it.start..it.stop }
                if (cur != null) {
                    nowPlaying = cur.title
                    nowProgress = epgProgress(sec, cur.start, cur.stop)
                }
            } catch (e: Exception) {
                // akış yoksa sessizce boş kalır
            }
        }
    }

    if (showEpg) {
        EpgDialog(t.id, t.name) { showEpg = false }
    }

    Column {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(if (live) 16f / 9f else 2f / 3f)
                .tvFocus(RoundedCornerShape(10.dp))
                .clip(RoundedCornerShape(10.dp))
                .background(PrizmaSurface)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
        ) {
            if (t.icon.isNotEmpty()) {
                AsyncImage(
                    model = t.icon,
                    contentDescription = null,
                    contentScale = if (live) ContentScale.Fit else ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(if (live) 8.dp else 0.dp)
                )
            } else {
                Text(
                    t.name.take(1).uppercase(),
                    color = Muted,
                    fontSize = 24.sp,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            if (t.rating.isNotEmpty()) RatingBadge(Modifier.align(Alignment.TopEnd), t.rating)
            if (fav) {
                Icon(
                    Icons.Default.Star, null,
                    tint = Color(0xFFF5C518),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(5.dp)
                        .size(14.dp)
                )
            }
            if (t.progress > 0f) {
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(t.progress)
                        .height(3.dp)
                        .background(PrizmaAccent)
                )
            }
            if (live) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(5.dp)
                        .tvFocus(CircleShape, 1.15f)
                        .clip(CircleShape)
                        .background(Color(0xCC000000))
                        .clickable { showEpg = true }
                        .size(22.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("i", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (showMove) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 3.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Text(
                    "◀", color = PrizmaAccent, fontSize = 14.sp,
                    modifier = Modifier
                        .tvFocus(RoundedCornerShape(4.dp), 1.0f)
                        .clip(RoundedCornerShape(4.dp))
                        .clickable(onClick = onLeft)
                        .padding(horizontal = 12.dp, vertical = 2.dp)
                )
                Text(
                    "▶", color = PrizmaAccent, fontSize = 14.sp,
                    modifier = Modifier
                        .tvFocus(RoundedCornerShape(4.dp), 1.0f)
                        .clip(RoundedCornerShape(4.dp))
                        .clickable(onClick = onRight)
                        .padding(horizontal = 12.dp, vertical = 2.dp)
                )
            }
        }

        Spacer(Modifier.height(6.dp))
        Text(
            t.name,
            color = Color(0xFFE6E8EB),
            fontSize = 11.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 14.sp
        )

        if (live && nowPlaying.isNotEmpty()) {
            Text(
                nowPlaying,
                color = PrizmaAccent,
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(Color(0xFF2A2E3A))
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(nowProgress)
                        .height(2.dp)
                        .background(PrizmaAccent)
                )
            }
        }
    }
}
