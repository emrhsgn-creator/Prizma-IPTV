package com.prizma.iptv

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

private val BarStart = Color(0xFF23306E)
private val BarEnd = Color(0xFF5B3FA8)
private val SideBg = Color(0xFF0C0F1D)
private val Muted = Color(0xFF8A90A0)
private const val FAV_CAT = "__FAV__"

private data class SectionData(val categories: List<Category>, val items: List<StreamItem>)

private enum class SortMode(val label: String) {
    MANUAL("Elle"), NAME("A-Z"), RATING("Puan"), ADDED("Eklenme")
}

private fun ratingOf(s: String): Double =
    s.replace(',', '.').toDoubleOrNull() ?: 0.0

private fun launchItem(
    ctx: Context, host: String, user: String, pass: String,
    sectionName: String, id: String, name: String, icon: String, ext: String
) {
    if (sectionName == Section.SERIES.name) {
        Toast.makeText(ctx, "Dizi bölümleri sonraki adımda", Toast.LENGTH_SHORT).show()
        return
    }
    val live = sectionName == Section.LIVE.name
    val e = if (ext.isNotEmpty()) ext else "ts"
    val folder = if (live) "live" else "movie"
    PlayerActivity.start(
        ctx, "$host/$folder/$user/$pass/$id.$e", name,
        sectionName, id, icon, e, !live
    )
}

@Composable
fun HomeScreen(
    host: String,
    user: String,
    pass: String,
    account: Account,
    onLogout: () -> Unit
) {
    val ctx = LocalContext.current
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

    val section: Section? = when (tab) {
        1 -> Section.LIVE
        2 -> Section.VOD
        3 -> Section.SERIES
        else -> null
    }

    val favs = remember(localRev, Refresh.tick) { Store.favorites(ctx) }
    val hist = remember(localRev, Refresh.tick) { Store.history(ctx) }

    LaunchedEffect(reload) {
        if (reload > 0) cache.clear()
    }

    LaunchedEffect(section, reload) {
        val sec = section ?: return@LaunchedEffect
        selectedCat = ""
        if (cache.containsKey(sec)) return@LaunchedEffect
        loading = true; error = ""
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
            query.isNotEmpty() || searching -> { query = ""; searching = false }
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
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color(0x33FFFFFF)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("▶", color = Color.White, fontSize = 15.sp)
                }
                Spacer(Modifier.width(10.dp))

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
                        modifier = Modifier.weight(1f).height(48.dp)
                    )
                    IconButton(onClick = { searching = false; query = "" }) {
                        Icon(Icons.Default.Close, null, tint = Color.White)
                    }
                } else {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        listOf("Ana Sayfa", "Canlı TV", "Filmler", "Diziler")
                            .forEachIndexed { i, label ->
                                val sel = i == tab
                                Text(
                                    label,
                                    color = if (sel) Color.White else Color(0xCCFFFFFF),
                                    fontSize = 13.sp,
                                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1,
                                    modifier = Modifier
                                        .padding(end = 4.dp)
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(if (sel) Color(0x40FFFFFF) else Color.Transparent)
                                        .clickable { tab = i }
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }
                    }
                    if (tab != 0) {
                        IconButton(onClick = { searching = true }) {
                            Icon(Icons.Default.Search, null, tint = Color.White)
                        }
                    }
                    IconButton(onClick = { reload++ }) {
                        Icon(Icons.Default.Refresh, null, tint = Color.White)
                    }
                    IconButton(onClick = onLogout) {
                        Icon(Icons.Default.ExitToApp, null, tint = Color.White)
                    }
                }
            }

            if (section == null) {
                HomeTab(
                    ctx = ctx, host = host, user = user, pass = pass,
                    favs = favs, hist = hist, cache = cache
                )
                return@Column
            }

            val data = cache[section]

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

                val sectionFavs = favs.filter { it.section == section.name }
                val favAsItems = sectionFavs.map {
                    StreamItem(it.id, it.name, it.icon, it.extension, FAV_CAT, it.rating, it.savedAt)
                }

                val showingFav = selectedCat == FAV_CAT
                val q = query.trim()

                val visible = when {
                    showingFav -> when (sortMode) {
                        SortMode.MANUAL -> favAsItems
                        SortMode.NAME -> favAsItems.sortedBy { it.name.lowercase() }
                        SortMode.RATING -> favAsItems.sortedByDescending { ratingOf(it.rating) }
                        SortMode.ADDED -> favAsItems.sortedByDescending { it.added }
                    }.filter { q.isEmpty() || it.name.contains(q, true) }

                    else -> data.items.filter { s ->
                        (selectedCat.isEmpty() || s.categoryId == selectedCat) &&
                            (q.isEmpty() || s.name.contains(q, true))
                    }
                }

                val favIds = remember(sectionFavs) { sectionFavs.map { it.id }.toSet() }

                val grid: @Composable () -> Unit = {
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
                        Grid(
                            items = visible,
                            live = section == Section.LIVE,
                            favIds = favIds,
                            showMove = showingFav && sortMode == SortMode.MANUAL,
                            onClick = { s ->
                                launchItem(ctx, host, user, pass, section.name, s.id, s.name, s.icon, s.extension)
                            },
                            onLong = { s ->
                                val added = Store.toggleFavorite(ctx, section, s)
                                localRev++
                                Toast.makeText(
                                    ctx,
                                    if (added) "Favorilere eklendi" else "Favorilerden çıkarıldı",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            onMove = { s, d ->
                                Store.moveFavorite(ctx, section, s.id, d)
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
                        grid()
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
                        grid()
                    }
                }
            }
        }
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
            RowSection("Devam Et") {
                items(devam) { w ->
                    RowTile(
                        w.name, w.icon, "",
                        w.section == Section.LIVE.name,
                        (w.position.toFloat() / w.duration.toFloat()).coerceIn(0f, 1f)
                    ) {
                        launchItem(ctx, host, user, pass, w.section, w.id, w.name, w.icon, w.extension)
                    }
                }
            }
        }

        if (favs.isNotEmpty()) item {
            RowSection("Favoriler") {
                items(favs.reversed()) { f ->
                    RowTile(f.name, f.icon, f.rating, f.section == Section.LIVE.name, 0f) {
                        launchItem(ctx, host, user, pass, f.section, f.id, f.name, f.icon, f.extension)
                    }
                }
            }
        }

        if (hist.isNotEmpty()) item {
            RowSection("Son İzlenenler") {
                items(hist) { w ->
                    RowTile(w.name, w.icon, "", w.section == Section.LIVE.name, 0f) {
                        launchItem(ctx, host, user, pass, w.section, w.id, w.name, w.icon, w.extension)
                    }
                }
            }
        }

        if (vod.isNotEmpty()) {
            item {
                RowSection("Son Eklenen Filmler") {
                    items(vod.sortedByDescending { it.added }.take(20)) { s ->
                        RowTile(s.name, s.icon, s.rating, false, 0f) {
                            launchItem(ctx, host, user, pass, Section.VOD.name, s.id, s.name, s.icon, s.extension)
                        }
                    }
                }
            }
            item {
                RowSection("En İyi 10 Film") {
                    items(vod.sortedByDescending { ratingOf(it.rating) }.take(10)) { s ->
                        RowTile(s.name, s.icon, s.rating, false, 0f) {
                            launchItem(ctx, host, user, pass, Section.VOD.name, s.id, s.name, s.icon, s.extension)
                        }
                    }
                }
            }
        }

        if (series.isNotEmpty()) {
            item {
                RowSection("Son Eklenen Diziler") {
                    items(series.sortedByDescending { it.added }.take(20)) { s ->
                        RowTile(s.name, s.icon, s.rating, false, 0f) {
                            launchItem(ctx, host, user, pass, Section.SERIES.name, s.id, s.name, s.icon, s.extension)
                        }
                    }
                }
            }
            item {
                RowSection("En İyi 10 Dizi") {
                    items(series.sortedByDescending { ratingOf(it.rating) }.take(10)) { s ->
                        RowTile(s.name, s.icon, s.rating, false, 0f) {
                            launchItem(ctx, host, user, pass, Section.SERIES.name, s.id, s.name, s.icon, s.extension)
                        }
                    }
                }
            }
        }

        if (devam.isEmpty() && favs.isEmpty() && hist.isEmpty() && vod.isEmpty() && series.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(60.dp), Alignment.Center) {
                    CircularProgressIndicator(color = PrizmaAccent)
                }
            }
        }
    }
}

@Composable
private fun RowSection(
    title: String,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit
) {
    Column(Modifier.padding(top = 14.dp)) {
        Text(
            title,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 14.dp, bottom = 8.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            content = content
        )
    }
}

@Composable
private fun RowTile(
    name: String,
    icon: String,
    rating: String,
    live: Boolean,
    progress: Float,
    onClick: () -> Unit
) {
    Column(
        Modifier
            .width(if (live) 165.dp else 112.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(if (live) 16f / 9f else 2f / 3f)
                .clip(RoundedCornerShape(8.dp))
                .background(PrizmaSurface)
        ) {
            if (icon.isNotEmpty()) {
                AsyncImage(
                    model = icon,
                    contentDescription = null,
                    contentScale = if (live) ContentScale.Fit else ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().padding(if (live) 6.dp else 0.dp)
                )
            }
            if (rating.isNotEmpty()) RatingBadge(Modifier.align(Alignment.TopEnd), rating)
            if (progress > 0f) {
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(progress)
                        .height(3.dp)
                        .background(PrizmaAccent)
                )
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(
            name,
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
        Icon(Icons.Default.Star, null, tint = Color(0xFFF5C518), modifier = Modifier.size(10.dp))
        Spacer(Modifier.width(3.dp))
        Text(rating, color = Color.White, fontSize = 10.sp)
    }
}

@Composable
private fun Grid(
    items: List<StreamItem>,
    live: Boolean,
    favIds: Set<String>,
    showMove: Boolean,
    onClick: (StreamItem) -> Unit,
    onLong: (StreamItem) -> Unit,
    onMove: (StreamItem, Int) -> Unit
) {
    if (items.isEmpty()) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            Text("İçerik bulunamadı.", color = Muted, fontSize = 13.sp)
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(if (live) 150.dp else 118.dp),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(items, key = { it.id + it.name }) { s ->
            PosterTile(
                item = s,
                live = live,
                fav = favIds.contains(s.id),
                showMove = showMove,
                width = null,
                onClick = { onClick(s) },
                onLongClick = { onLong(s) },
                onLeft = { onMove(s, -1) },
                onRight = { onMove(s, 1) }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PosterTile(
    item: StreamItem,
    live: Boolean,
    fav: Boolean,
    showMove: Boolean,
    width: Dp?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onLeft: () -> Unit,
    onRight: () -> Unit
) {
    val base = if (width != null) Modifier.width(width) else Modifier
    Column(base) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(if (live) 16f / 9f else 2f / 3f)
                .clip(RoundedCornerShape(10.dp))
                .background(PrizmaSurface)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
        ) {
            if (item.icon.isNotEmpty()) {
                AsyncImage(
                    model = item.icon,
                    contentDescription = null,
                    contentScale = if (live) ContentScale.Fit else ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().padding(if (live) 8.dp else 0.dp)
                )
            } else {
                Text(
                    item.name.take(1).uppercase(),
                    color = Muted,
                    fontSize = 24.sp,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            if (item.rating.isNotEmpty()) {
                RatingBadge(Modifier.align(Alignment.TopEnd), item.rating)
            }

            if (fav) {
                Icon(
                    Icons.Default.Star,
                    null,
                    tint = Color(0xFFF5C518),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(5.dp)
                        .size(14.dp)
                )
            }
        }

        if (showMove) {
            Row(
                Modifier.fillMaxWidth().padding(top = 3.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Text(
                    "◀",
                    color = PrizmaAccent,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable(onClick = onLeft)
                        .padding(horizontal = 12.dp, vertical = 2.dp)
                )
                Text(
                    "▶",
                    color = PrizmaAccent,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable(onClick = onRight)
                        .padding(horizontal = 12.dp, vertical = 2.dp)
                )
            }
        }

        Spacer(Modifier.height(6.dp))
        Text(
            item.name,
            color = Color(0xFFE6E8EB),
            fontSize = 11.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 14.sp
        )
    }
}
