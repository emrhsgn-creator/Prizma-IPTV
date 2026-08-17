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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

class MovieActivity : ComponentActivity() {

    companion object {
        fun start(
            ctx: Context, host: String, user: String, pass: String,
            vodId: String, name: String, icon: String, ext: String, rating: String
        ) {
            val i = Intent(ctx, MovieActivity::class.java)
            i.putExtra("host", host)
            i.putExtra("user", user)
            i.putExtra("pass", pass)
            i.putExtra("vodId", vodId)
            i.putExtra("name", name)
            i.putExtra("icon", icon)
            i.putExtra("ext", ext)
            i.putExtra("rating", rating)
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
                MovieScreen(
                    host = intent.getStringExtra("host").orEmpty(),
                    user = intent.getStringExtra("user").orEmpty(),
                    pass = intent.getStringExtra("pass").orEmpty(),
                    vodId = intent.getStringExtra("vodId").orEmpty(),
                    name = intent.getStringExtra("name").orEmpty(),
                    icon = intent.getStringExtra("icon").orEmpty(),
                    ext = intent.getStringExtra("ext").orEmpty(),
                    rating = intent.getStringExtra("rating").orEmpty()
                ) { finish() }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Refresh.tick++
    }
}

@Composable
fun MovieScreen(
    host: String,
    user: String,
    pass: String,
    vodId: String,
    name: String,
    icon: String,
    ext: String,
    rating: String,
    onBack: () -> Unit
) {
    val ctx = LocalContext.current
    var info by remember { mutableStateOf<VodInfo?>(null) }
    var loading by remember { mutableStateOf(true) }
    var fav by remember { mutableStateOf(false) }
    var rev by remember { mutableIntStateOf(0) }

    LaunchedEffect(vodId) {
        fav = Store.favorites(ctx).any { it.section == Section.VOD.name && it.id == vodId }
        try {
            info = XtreamApi.vodInfo(host, user, pass, vodId)
        } catch (e: Exception) {
            info = null
        }
        loading = false
    }

    LaunchedEffect(rev) {
        if (rev > 0) {
            fav = Store.favorites(ctx).any { it.section == Section.VOD.name && it.id == vodId }
        }
    }

    val d = info
    val realExt = d?.extension?.ifBlank { ext } ?: ext
    val resume = remember(Refresh.tick) { Store.resumePosition(ctx, Section.VOD.name, vodId) }

    fun play() {
        val e = if (realExt.isNotEmpty()) realExt else "mp4"
        PlayerActivity.start(
            ctx, "$host/movie/$user/$pass/$vodId.$e", name,
            Section.VOD.name, vodId, icon, e, true
        )
    }

    Surface(color = PrizmaBg, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(220.dp)
            ) {
                val bg = when {
                    d?.backdrop?.isNotBlank() == true -> d.backdrop
                    d?.cover?.isNotBlank() == true -> d.cover
                    else -> icon
                }
                if (bg.isNotEmpty()) {
                    AsyncImage(
                        model = bg,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0x99000000), Color(0xE6101014), PrizmaBg)
                            )
                        )
                )
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp)
                        .clip(CircleShape)
                        .background(Color(0x66000000))
                        .clickable(onClick = onBack)
                        .size(36.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("‹", color = Color.White, fontSize = 22.sp)
                }
            }

            Column(Modifier.padding(horizontal = 16.dp)) {

                Text(
                    name,
                    color = Color.White,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    val r = d?.rating?.ifBlank { rating } ?: rating
                    if (r.isNotBlank()) {
                        Icon(
                            Icons.Default.Star, null,
                            tint = Color(0xFFF5C518),
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(r, color = Color.White, fontSize = 12.sp)
                        Spacer(Modifier.width(10.dp))
                    }
                    val meta = listOfNotNull(
                        d?.releaseDate?.take(10)?.ifBlank { null },
                        d?.duration?.ifBlank { null },
                        d?.country?.ifBlank { null }
                    ).joinToString(" · ")
                    if (meta.isNotEmpty()) {
                        Text(meta, color = Color(0xFF8A90A0), fontSize = 12.sp)
                    }
                }

                if (d?.genre?.isNotBlank() == true) {
                    Spacer(Modifier.height(6.dp))
                    Text(d.genre, color = PrizmaAccent, fontSize = 11.sp)
                }

                Spacer(Modifier.height(16.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(PrizmaAccent)
                            .clickable { play() }
                            .padding(vertical = 13.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("▶", color = Color.White, fontSize = 14.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (resume > 0) "Devam et" else "Oynat",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Box(
                        Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(PrizmaSurface)
                            .clickable {
                                Store.toggleFavorite(
                                    ctx, Section.VOD.name, vodId, name, icon, realExt, rating
                                )
                                rev++
                            }
                            .padding(horizontal = 18.dp, vertical = 13.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Star, null,
                            tint = if (fav) Color(0xFFF5C518) else Color(0xFF6E7686),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                if (resume > 0) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Kaldığın yer: ${resume / 60000} dk",
                        color = Color(0xFF8A90A0),
                        fontSize = 11.sp
                    )
                }

                Spacer(Modifier.height(18.dp))

                when {
                    loading -> Box(Modifier.fillMaxWidth(), Alignment.Center) {
                        CircularProgressIndicator(color = PrizmaAccent)
                    }

                    d == null -> Text(
                        "Bu film için ek bilgi bulunamadı.",
                        color = Color(0xFF6E7686),
                        fontSize = 12.sp
                    )

                    else -> {
                        if (d.plot.isNotBlank()) {
                            Text("Özet", color = PrizmaAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                d.plot,
                                color = Color(0xFFC3C8D4),
                                fontSize = 12.sp,
                                lineHeight = 18.sp
                            )
                            Spacer(Modifier.height(14.dp))
                        }
                        if (d.director.isNotBlank()) {
                            InfoLine("Yönetmen", d.director)
                        }
                        if (d.cast.isNotBlank()) {
                            InfoLine("Oyuncular", d.cast)
                        }
                    }
                }

                Spacer(Modifier.height(30.dp))
            }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Column(Modifier.padding(bottom = 10.dp)) {
        Text(label, color = PrizmaAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(3.dp))
        Text(value, color = Color(0xFFC3C8D4), fontSize = 12.sp, lineHeight = 17.sp)
    }
}
