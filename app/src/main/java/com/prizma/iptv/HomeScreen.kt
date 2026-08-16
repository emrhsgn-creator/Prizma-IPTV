package com.prizma.iptv

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

private val BarStart = Color(0xFF23306E)
private val BarEnd = Color(0xFF5B3FA8)
private val SideBg = Color(0xFF0C0F1D)
private val Muted = Color(0xFF8A90A0)

@Composable
fun HomeScreen(
    host: String,
    user: String,
    pass: String,
    account: Account,
    onLogout: () -> Unit
) {
    val ctx = LocalContext.current
    var section by remember { mutableStateOf(Section.LIVE) }
    var categories by remember { mutableStateOf<List<Category>>(emptyList()) }
    var allItems by remember { mutableStateOf<List<StreamItem>>(emptyList()) }
    var selectedCat by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var reload by remember { mutableIntStateOf(0) }

    LaunchedEffect(section, reload) {
        loading = true; error = ""; selectedCat = ""; query = ""
        categories = emptyList(); allItems = emptyList()
        try {
            val cats = XtreamApi.categories(host, user, pass, section)
            val items = XtreamApi.allStreams(host, user, pass, section)
            val counts = items.groupingBy { it.categoryId }.eachCount()
            categories = cats.map { it.copy(count = counts[it.id] ?: 0) }
            allItems = items
        } catch (e: Exception) {
            error = e.message ?: "İçerik alınamadı"
        }
        loading = false
    }

    BackHandler(enabled = selectedCat.isNotEmpty() || query.isNotEmpty()) {
        selectedCat = ""; query = ""; searching = false
    }

    val visible = remember(allItems, selectedCat, query) {
        val q = query.trim()
        allItems.filter { s ->
            (selectedCat.isEmpty() || s.categoryId == selectedCat) &&
                (q.isEmpty() || s.name.contains(q, ignoreCase = true))
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
                        Section.values().forEach { s ->
                            val sel = s == section
                            Text(
                                s.title,
                                color = if (sel) Color.White else Color(0xCCFFFFFF),
                                fontSize = 14.sp,
                                fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier
                                    .padding(end = 6.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(if (sel) Color(0x40FFFFFF) else Color.Transparent)
                                    .clickable { section = s }
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                    }
                    IconButton(onClick = { searching = true }) {
                        Icon(Icons.Default.Search, null, tint = Color.White)
                    }
                    IconButton(onClick = { reload++ }) {
                        Icon(Icons.Default.Refresh, null, tint = Color.White)
                    }
                    IconButton(onClick = onLogout) {
                        Icon(Icons.Default.ExitToApp, null, tint = Color.White)
                    }
                }
            }

            BoxWithConstraints(Modifier.fillMaxSize()) {
                val wide = maxWidth >= 620.dp

                when {
                    loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                        CircularProgressIndicator(color = PrizmaAccent)
                    }

                    error.isNotEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Text(error, color = Color(0xFFFF6B6B), fontSize = 13.sp)
                    }

                    wide -> Row(Modifier.fillMaxSize()) {
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
                                    SideRow("TÜMÜ", allItems.size, selectedCat.isEmpty()) {
                                        selectedCat = ""
                                    }
                                }
                                items(categories) { c ->
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
                        Grid(visible, section, host, user, pass, ctx)
                    }

                    else -> Column(Modifier.fillMaxSize()) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            item {
                                Chip("TÜMÜ", selectedCat.isEmpty()) { selectedCat = "" }
                            }
                            items(categories) { c ->
                                Chip("${c.name} (${c.count})", selectedCat == c.id) {
                                    selectedCat = c.id
                                }
                            }
                        }
                        Grid(visible, section, host, user, pass, ctx)
                    }
                }
            }
        }
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
private fun Grid(
    items: List<StreamItem>,
    section: Section,
    host: String,
    user: String,
    pass: String,
    ctx: android.content.Context
) {
    if (items.isEmpty()) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            Text("İçerik bulunamadı.", color = Muted, fontSize = 13.sp)
        }
        return
    }
    val live = section == Section.LIVE
    LazyVerticalGrid(
        columns = GridCells.Adaptive(if (live) 150.dp else 118.dp),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(items, key = { it.id + it.name }) { s ->
            PosterTile(s, live) {
                if (section == Section.SERIES) {
                    Toast.makeText(ctx, "Dizi bölümleri sonraki adımda", Toast.LENGTH_SHORT).show()
                } else {
                    val ext = if (s.extension.isNotEmpty()) s.extension else "ts"
                    val folder = if (live) "live" else "movie"
                    PlayerActivity.start(ctx, "$host/$folder/$user/$pass/${s.id}.$ext", s.name)
                }
            }
        }
    }
}

@Composable
private fun PosterTile(s: StreamItem, live: Boolean, onClick: () -> Unit) {
    Column(Modifier.clickable(onClick = onClick)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(if (live) 16f / 9f else 2f / 3f)
                .clip(RoundedCornerShape(10.dp))
                .background(PrizmaSurface)
        ) {
            if (s.icon.isNotEmpty()) {
                AsyncImage(
                    model = s.icon,
                    contentDescription = null,
                    contentScale = if (live) ContentScale.Fit else ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().padding(if (live) 8.dp else 0.dp)
                )
            } else {
                Text(
                    s.name.take(1).uppercase(),
                    color = Muted,
                    fontSize = 24.sp,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            if (s.rating.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(5.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(Color(0xCC000000))
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Star,
                        null,
                        tint = Color(0xFFF5C518),
                        modifier = Modifier.size(10.dp)
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(s.rating, color = Color.White, fontSize = 10.sp)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            s.name,
            color = Color(0xFFE6E8EB),
            fontSize = 11.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Start,
            lineHeight = 14.sp
        )
    }
}
