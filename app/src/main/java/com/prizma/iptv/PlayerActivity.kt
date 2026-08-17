package com.prizma.iptv

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay

class PlayerActivity : ComponentActivity() {

    companion object {
        fun start(
            ctx: Context, url: String, title: String, section: String,
            id: String, icon: String, ext: String, resumable: Boolean
        ) {
            val i = Intent(ctx, PlayerActivity::class.java)
            i.putStringArrayListExtra("urls", arrayListOf(url))
            i.putStringArrayListExtra("titles", arrayListOf(title))
            i.putStringArrayListExtra("ids", arrayListOf(id))
            i.putStringArrayListExtra("icons", arrayListOf(icon))
            i.putStringArrayListExtra("exts", arrayListOf(ext))
            i.putExtra("section", section)
            i.putExtra("startIndex", 0)
            i.putExtra("resumable", resumable)
            ctx.startActivity(i)
        }

        fun startPlaylist(
            ctx: Context,
            urls: ArrayList<String>,
            titles: ArrayList<String>,
            ids: ArrayList<String>,
            icons: ArrayList<String>,
            exts: ArrayList<String>,
            startIndex: Int
        ) {
            val i = Intent(ctx, PlayerActivity::class.java)
            i.putStringArrayListExtra("urls", urls)
            i.putStringArrayListExtra("titles", titles)
            i.putStringArrayListExtra("ids", ids)
            i.putStringArrayListExtra("icons", icons)
            i.putStringArrayListExtra("exts", exts)
            i.putExtra("section", "EPISODE")
            i.putExtra("startIndex", startIndex)
            i.putExtra("resumable", true)
            ctx.startActivity(i)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            PlayerScreen(
                urls = intent.getStringArrayListExtra("urls") ?: arrayListOf(),
                titles = intent.getStringArrayListExtra("titles") ?: arrayListOf(),
                ids = intent.getStringArrayListExtra("ids") ?: arrayListOf(),
                icons = intent.getStringArrayListExtra("icons") ?: arrayListOf(),
                exts = intent.getStringArrayListExtra("exts") ?: arrayListOf(),
                section = intent.getStringExtra("section").orEmpty(),
                startIndex = intent.getIntExtra("startIndex", 0),
                resumable = intent.getBooleanExtra("resumable", false)
            ) { finish() }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    urls: List<String>,
    titles: List<String>,
    ids: List<String>,
    icons: List<String>,
    exts: List<String>,
    section: String,
    startIndex: Int,
    resumable: Boolean,
    onBack: () -> Unit
) {
    val ctx = LocalContext.current
    var error by remember { mutableStateOf("") }
    var resizeIndex by remember { mutableIntStateOf(0) }
    var current by remember { mutableIntStateOf(startIndex) }
    var notice by remember { mutableStateOf("") }

    val resizeModes = listOf(
        AspectRatioFrameLayout.RESIZE_MODE_FIT to "Sığdır",
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM to "Kırp",
        AspectRatioFrameLayout.RESIZE_MODE_FILL to "Ger"
    )

    fun idAt(i: Int) = ids.getOrNull(i).orEmpty()
    fun titleAt(i: Int) = titles.getOrNull(i).orEmpty()
    fun iconAt(i: Int) = icons.getOrNull(i).orEmpty()
    fun extAt(i: Int) = exts.getOrNull(i).orEmpty()

    val startAt = remember {
        if (resumable) Store.resumePosition(ctx, section, idAt(startIndex)) else 0L
    }

    val player = remember {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent("PrizmaIPTV/1.0")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(20000)
        ExoPlayer.Builder(ctx)
            .setMediaSourceFactory(DefaultMediaSourceFactory(http))
            .build().apply {
                setMediaItems(urls.map { MediaItem.fromUri(it) })
                playWhenReady = true
                seekTo(startIndex, if (startAt > 0) startAt else 0L)
                prepare()
            }
    }

    LaunchedEffect(Unit) {
        if (startAt > 0) notice = "Kaldığın yerden devam ediliyor"
        while (true) {
            delay(5000)
            val i = player.currentMediaItemIndex
            val d = player.duration
            if (resumable && d > 0) {
                Store.record(
                    ctx, section, idAt(i), titleAt(i), iconAt(i), extAt(i),
                    player.currentPosition, d
                )
            }
        }
    }

    LaunchedEffect(notice) {
        if (notice.isNotEmpty()) {
            delay(3000)
            notice = ""
        }
    }

    DisposableEffect(Unit) {
        val listener = object : Player.Listener {
            override fun onPlayerError(e: PlaybackException) {
                error = "Oynatılamadı (${e.errorCodeName})"
            }

            override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
                val i = player.currentMediaItemIndex
                current = i
                error = ""
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && urls.size > 1) {
                    notice = "Sonraki bölüm: ${titleAt(i)}"
                }
            }
        }
        player.addListener(listener)
        onDispose {
            val i = player.currentMediaItemIndex
            val d = player.duration
            if (resumable && d > 0) {
                Store.record(
                    ctx, section, idAt(i), titleAt(i), iconAt(i), extAt(i),
                    player.currentPosition, d
                )
            }
            player.removeListener(listener)
            player.release()
        }
    }

    BackHandler { onBack() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { c ->
                PlayerView(c).apply {
                    this.player = player
                    useController = true
                    setShowSubtitleButton(true)
                    setShowNextButton(urls.size > 1)
                    setShowPreviousButton(urls.size > 1)
                    controllerShowTimeoutMs = 3500
                    keepScreenOn = true
                }
            },
            update = { it.resizeMode = resizeModes[resizeIndex].first }
        )

        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "‹",
                color = Color.White,
                fontSize = 26.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { onBack() }
                    .padding(horizontal = 10.dp)
            )
            Text(
                titleAt(current),
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = 4.dp)
            )
            Text(
                resizeModes[resizeIndex].second,
                color = PrizmaAccent,
                fontSize = 13.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0x66000000))
                    .clickable { resizeIndex = (resizeIndex + 1) % resizeModes.size }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }

        if (notice.isNotEmpty()) {
            Text(
                notice,
                color = Color.White,
                fontSize = 12.sp,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xAA000000))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }

        if (error.isNotEmpty()) {
            Text(
                error,
                color = Color(0xFFFF6B6B),
                fontSize = 14.sp,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}
