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
            ctx: Context,
            url: String,
            title: String,
            section: String,
            id: String,
            icon: String,
            ext: String,
            resumable: Boolean
        ) {
            val i = Intent(ctx, PlayerActivity::class.java)
            i.putExtra("url", url)
            i.putExtra("title", title)
            i.putExtra("section", section)
            i.putExtra("id", id)
            i.putExtra("icon", icon)
            i.putExtra("ext", ext)
            i.putExtra("resumable", resumable)
            ctx.startActivity(i)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            PlayerScreen(
                url = intent.getStringExtra("url").orEmpty(),
                title = intent.getStringExtra("title").orEmpty(),
                section = intent.getStringExtra("section").orEmpty(),
                id = intent.getStringExtra("id").orEmpty(),
                icon = intent.getStringExtra("icon").orEmpty(),
                ext = intent.getStringExtra("ext").orEmpty(),
                resumable = intent.getBooleanExtra("resumable", false)
            ) { finish() }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    url: String,
    title: String,
    section: String,
    id: String,
    icon: String,
    ext: String,
    resumable: Boolean,
    onBack: () -> Unit
) {
    val ctx = LocalContext.current
    var error by remember { mutableStateOf("") }
    var resizeIndex by remember { mutableIntStateOf(0) }
    var resumed by remember { mutableStateOf(false) }

    val resizeModes = listOf(
        AspectRatioFrameLayout.RESIZE_MODE_FIT to "Sığdır",
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM to "Kırp",
        AspectRatioFrameLayout.RESIZE_MODE_FILL to "Ger"
    )

    val startAt = remember {
        if (resumable) Store.resumePosition(ctx, section, id) else 0L
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
                setMediaItem(MediaItem.fromUri(url))
                playWhenReady = true
                prepare()
                if (startAt > 0) seekTo(startAt)
            }
    }

    LaunchedEffect(Unit) {
        Store.record(ctx, section, id, title, icon, ext, startAt, 0L)
        if (startAt > 0) resumed = true
        while (true) {
            delay(5000)
            val d = player.duration
            if (resumable && d > 0) {
                Store.record(ctx, section, id, title, icon, ext, player.currentPosition, d)
            }
        }
    }

    LaunchedEffect(resumed) {
        if (resumed) {
            delay(3000)
            resumed = false
        }
    }

    DisposableEffect(Unit) {
        val listener = object : Player.Listener {
            override fun onPlayerError(e: PlaybackException) {
                error = "Oynatılamadı (${e.errorCodeName})"
            }
        }
        player.addListener(listener)
        onDispose {
            val d = player.duration
            if (resumable && d > 0) {
                Store.record(ctx, section, id, title, icon, ext, player.currentPosition, d)
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
                    setShowNextButton(false)
                    setShowPreviousButton(false)
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
                title,
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

        if (resumed) {
            Text(
                "Kaldığın yerden devam ediliyor",
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
