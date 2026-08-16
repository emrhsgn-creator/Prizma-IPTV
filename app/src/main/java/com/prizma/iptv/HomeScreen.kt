package com.prizma.iptv

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

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
    var openCategory by remember { mutableStateOf<Category?>(null) }
    var streams by remember { mutableStateOf<List<StreamItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }

    LaunchedEffect(section) {
        loading = true; error = ""; openCategory = null; categories = emptyList()
        try {
            categories = XtreamApi.categories(host, user, pass, section)
        } catch (e: Exception) {
            error = e.message ?: "Kategoriler alınamadı"
        }
        loading = false
    }

    LaunchedEffect(openCategory) {
        val cat = openCategory ?: return@LaunchedEffect
        loading = true; error = ""; streams = emptyList()
        try {
            streams = XtreamApi.streams(host, user, pass, section, cat.id)
        } catch (e: Exception) {
            error = e.message ?: "İçerik alınamadı"
        }
        loading = false
    }

    BackHandler(enabled = openCategory != null) { openCategory = null }

    Surface(color = PrizmaBg, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {

            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "PRIZMA IPTV",
                        color = PrizmaAccent,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${account.username} · Bitiş: ${account.expiry} · ${account.activeConnections}/${account.maxConnections} bağlantı",
                        color = Color(0xFF7C828A),
                        fontSize = 11.sp
                    )
                }
                TextButton(onClick = onLogout) { Text("Çıkış", fontSize = 13.sp) }
            }

            TabRow(
                selectedTabIndex = section.ordinal,
                containerColor = PrizmaBg,
                contentColor = PrizmaAccent
            ) {
                Section.values().forEach { s ->
                    Tab(
                        selected = section == s,
                        onClick = { section = s },
                        text = { Text(s.title, fontSize = 13.sp) }
                    )
                }
            }

            val cat = openCategory
            if (cat != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { openCategory = null }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "‹  ${cat.name}",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.weight(1f))
                    Text("${streams.size}", color = Color(0xFF7C828A), fontSize = 12.sp)
                }
                Divider(color = Color(0xFF23232B))
            }

            Box(Modifier.fillMaxSize()) {
                when {
                    loading -> CircularProgressIndicator(
                        color = PrizmaAccent,
                        modifier = Modifier.align(Alignment.Center)
                    )

                    error.isNotEmpty() -> Text(
                        error,
                        color = Color(0xFFFF6B6B),
                        fontSize = 13.sp,
                        modifier = Modifier.align(Alignment.Center).padding(24.dp)
                    )

                    cat == null -> LazyColumn(Modifier.fillMaxSize()) {
                        items(categories) { c ->
                            RowItem(title = c.name, icon = null) { openCategory = c }
                        }
                    }

                    streams.isEmpty() -> Text(
                        "Bu kategoride içerik yok.",
                        color = Color(0xFF7C828A),
                        modifier = Modifier.align(Alignment.Center)
                    )

                    else -> LazyColumn(Modifier.fillMaxSize()) {
                        items(streams) { s ->
                            RowItem(title = s.name, icon = s.icon) {
                                if (section == Section.SERIES) {
                                    Toast.makeText(
                                        ctx,
                                        "Dizi bölümleri sonraki adımda",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    val ext = if (s.extension.isNotEmpty()) s.extension else "ts"
                                    val folder = if (section == Section.LIVE) "live" else "movie"
                                    val url = "$host/$folder/$user/$pass/${s.id}.$ext"
                                    PlayerActivity.start(ctx, url, s.name)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RowItem(title: String, icon: String?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(PrizmaSurface),
                contentAlignment = Alignment.Center
            ) {
                if (icon.isNotEmpty()) {
                    AsyncImage(
                        model = icon,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(3.dp)
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
        }
        Text(
            title,
            color = Color(0xFFE6E8EB),
            fontSize = 14.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}
