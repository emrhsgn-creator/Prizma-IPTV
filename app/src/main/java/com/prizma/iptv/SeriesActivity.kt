package com.prizma.iptv

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.foundation.border
import androidx.compose.ui.focus.onFocusChanged

class SeriesActivity : ComponentActivity() {

    companion object {
        fun start(
            ctx: Context, host: String, user: String, pass: String,
            seriesId: String, name: String, cover: String
        ) {
            val i = Intent(ctx, SeriesActivity::class.java)
            i.putExtra("host", host)
            i.putExtra("user", user)
            i.putExtra("pass", pass)
            i.putExtra("seriesId", seriesId)
            i.putExtra("name", name)
            i.putExtra("cover", cover)
            ctx.startActivity(i)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = PrizmaAccent,
                    background = PrizmaBg,
                    surface = PrizmaSurface
                )
            ) {
                SeriesScreen(
                    host = intent.getStringExtra("host").orEmpty(),
                    user = intent.getStringExtra("user").orEmpty(),
                    pass = intent.getStringExtra("pass").orEmpty(),
                    seriesId = intent.getStringExtra("seriesId").orEmpty(),
                    name = intent.getStringExtra("name").orEmpty(),
                    cover = intent.getStringExtra("cover").orEmpty()
                ) { finish() }
            }
        }
    }
}

@Composable
fun SeriesScreen(
    host: String,
    user: String,
    pass: String,
    seriesId: String,
    name: String,
    cover: String,
    onBack: () -> Unit
) {
    val ctx = LocalContext.current
    var info by remember { mutableStateOf<SeriesInfo?>(null) }
    var error by remember { mutableStateOf("") }
    var season by remember { mutableIntStateOf(-1) }

    LaunchedEffect(seriesId) {
        try {
            val r = XtreamApi.seriesInfo(host, user, pass, seriesId)
            info = r
            season = r.seasons.keys.firstOrNull() ?: -1
        } catch (e: Exception) {
            error = e.message ?: "Yüklenemedi"
        }
    }

    Surface(color = PrizmaBg, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {

            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1B2350))
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "‹",
                    color = Color.White,
                    fontSize = 24.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = onBack)
                        .padding(horizontal = 10.dp)
                )
                Text(
                    name,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 6.dp)
                )
            }

            val data = info
            when {
                error.isNotEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text(error, color = Color(0xFFFF6B6B), fontSize = 13.sp)
                }

                data == null -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator(color = PrizmaAccent)
                }

                else -> {
                    val eps = data.seasons[season].orEmpty()
                    LazyColumn(Modifier.fillMaxSize()) {

                        item {
                            Row(Modifier.padding(14.dp)) {
                                Box(
                                    Modifier
                                        .width(96.dp)
                                        .height(144.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(PrizmaSurface)
                                ) {
                                    val img = if (data.cover.isNotEmpty()) data.cover else cover
                                    if (img.isNotEmpty()) {
                                        AsyncImage(
                                            model = img,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    val meta = listOf(data.genre, data.releaseDate, data.rating)
                                        .filter { it.isNotBlank() }
                                        .joinToString(" · ")
                                    if (meta.isNotEmpty()) {
                                        Text(meta, color = PrizmaAccent, fontSize = 11.sp)
                                        Spacer(Modifier.height(6.dp))
                                    }
                                    if (data.plot.isNotBlank()) {
                                        Text(
                                            data.plot,
                                            color = Color(0xFFB9BFCC),
                                            fontSize = 11.sp,
                                            lineHeight = 15.sp,
                                            maxLines = 7,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }

                        item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 14.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(data.seasons.keys.toList()) { s ->
                                    val sel = s == season
                                    Text(
                                        "Sezon $s",
                                        color = if (sel) Color.White else Color(0xFFC3C8D4),
                                        fontSize = 12.sp,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(
                                                if (sel) PrizmaAccent.copy(alpha = 0.35f)
                                                else PrizmaSurface
                                            )
                                            .clickable { season = s }
                                            .padding(horizontal = 14.dp, vertical = 8.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                        }

                        items(eps) { ep ->
                            val idx = eps.indexOf(ep)
                            EpisodeRow(ep) {
                                PlayerActivity.startPlaylist(
                                    ctx = ctx,
                                    urls = ArrayList(eps.map {
                                        "$host/series/$user/$pass/${it.id}.${it.extension}"
                                    }),
                                    titles = ArrayList(eps.map {
                                        "$name · S${it.season}B${it.episodeNum} ${it.title}"
                                    }),
                                    ids = ArrayList(eps.map { it.id }),
                                    icons = ArrayList(eps.map { it.icon.ifEmpty { cover } }),
                                    exts = ArrayList(eps.map { it.extension }),
                                    startIndex = if (idx >= 0) idx else 0
                                )
                            }
                        }

                        item { Spacer(Modifier.height(24.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeRow(ep: Episode, onClick: () -> Unit) {
    val ctx = LocalContext.current
    var focused by remember { mutableStateOf(false) }
    val progress = remember(ep.id) {
        val w = Store.history(ctx).firstOrNull { it.section == "EPISODE" && it.id == ep.id }
        if (w != null && w.duration > 0) {
            (w.position.toFloat() / w.duration.toFloat()).coerceIn(0f, 1f)
        } else 0f
    }

   Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .border(
                width = if (focused) 2.dp else 0.dp,
                color = if (focused) Color.White else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .width(112.dp)
                .height(63.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(PrizmaSurface)
        ) {
            if (ep.icon.isNotEmpty()) {
                AsyncImage(
                    model = ep.icon,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Text(
                "${ep.episodeNum}",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(4.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xCC000000))
                    .padding(horizontal = 6.dp, vertical = 1.dp)
            )
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
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                ep.title,
                color = Color(0xFFE6E8EB),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (ep.duration.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(ep.duration, color = Color(0xFF8A90A0), fontSize = 10.sp)
            }
            if (ep.plot.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    ep.plot,
                    color = Color(0xFF8A90A0),
                    fontSize = 10.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 13.sp
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text("▶", color = PrizmaAccent, fontSize = 16.sp, modifier = Modifier.size(20.dp))
    }
}
