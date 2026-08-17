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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import java.util.Locale

private data class TrackOption(
    val label: String,
    val groupIndex: Int,
    val trackIndex: Int,
    val selected: Boolean
)

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
            MaterialTheme(colorScheme = darkColorScheme(primary = PrizmaAccent)) {
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
}

@OptIn(UnstableApi::class)
private fun collectTracks(tracks: Tracks, type: Int): List<TrackOption> {
    val out = ArrayList<TrackOption>()
    var gi = 0
    for (group in tracks.groups) {
        if (group.type == type) {
            for (ti in 0 until group.length) {
                val f = group.getTrackFormat(ti)
                val lang = f.language
                val name = when {
                    !f.label.isNullOrBlank() -> f.label!!
                    !lang.isNullOrBlank() && lang != "und" ->
                        Locale(lang).getDisplayLanguage(Locale.getDefault())
                            .replaceFirstChar { it.uppercase() }
                    else -> "Kanal ${ti + 1}"
                }
                val extra = when (type) {
                    C.TRACK_TYPE_AUDIO ->
                        if (f.channelCount > 0) " · ${f.channelCount}ch" else ""
                    else -> ""
                }
                out.add(TrackOption(name + extra, gi, ti, group.isTrackSelected(ti)))
            }
        }
        gi++
    }
    return out
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
    var showMenu by remember { mutableStateOf(false) }
    var locked by remember { mutableStateOf(false) }
    var speed by remember { mutableFloatStateOf(1f) }
    var subSize by remember { mutableFloatStateOf(0.06f) }
    var tracks by remember { mutableStateOf<Tracks?>(null) }

    val hasList = urls.size > 1
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
        val sec = Prefs.bufferSeconds(ctx)
        val loadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                sec * 1000,
                sec * 2000,
                1500,
                3000
            )
            .build()
        ExoPlayer.Builder(ctx)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(DefaultMediaSourceFactory(http))
            .build().apply {
                setMediaItems(urls.map { MediaItem.fromUri(it) })
                playWhenReady = true
                seekTo(startIndex, if (startAt > 0) startAt else 0L)
                prepare()
            }
    }

    fun flash(msg: String) {
        notice = msg
    }

    LaunchedEffect(Unit) {
        if (startAt > 0) flash("Kaldığın yerden devam ediliyor")
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
            delay(2200)
            notice = ""
        }
    }

    LaunchedEffect(speed) {
        player.playbackParameters = PlaybackParameters(speed)
    }

    DisposableEffect(Unit) {
        val listener = object : Player.Listener {
            override fun onPlayerError(e: PlaybackException) {
                error = "Oynatılamadı (${e.errorCodeName})"
            }

            override fun onTracksChanged(t: Tracks) {
                tracks = t
                error = ""
            }

            override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
                val i = player.currentMediaItemIndex
                current = i
                error = ""
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && hasList) {
                    flash("Sonraki: ${titleAt(i)}")
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

    BackHandler {
        when {
            showMenu -> showMenu = false
            locked -> locked = false
            else -> onBack()
        }
    }

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
                    setShowNextButton(hasList)
                    setShowPreviousButton(hasList)
                    controllerShowTimeoutMs = 3500
                    keepScreenOn = true
                    isFocusable = true
                    isFocusableInTouchMode = false
                    requestFocus()
                    subtitleView?.setStyle(
                        CaptionStyleCompat(
                            android.graphics.Color.WHITE,
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT,
                            CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                            android.graphics.Color.BLACK,
                            null
                        )
                    )
                }
            },
            update = { v ->
                v.resizeMode = resizeModes[resizeIndex].first
                v.useController = !locked
                v.subtitleView?.setFractionalTextSize(subSize)
            }
        )

        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(locked) {
                    if (locked) return@pointerInput
                    detectTapGestures(
                        onDoubleTap = { off ->
                            if (off.x < size.width / 3f) {
                                player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0))
                                flash("−10 sn")
                            } else if (off.x > size.width * 2f / 3f) {
                                player.seekTo(player.currentPosition + 10_000)
                                flash("+10 sn")
                            }
                        }
                    )
                }
        )

        if (!locked) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TopBtn("‹", 24.sp) { onBack() }
                Text(
                    titleAt(current),
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 6.dp)
                )
                if (speed != 1f) {
                    Text(
                        "${speed}x",
                        color = PrizmaAccent,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
                TopBtn("🔒", 14.sp) { locked = true; flash("Kilitlendi") }
                Spacer(Modifier.width(6.dp))
                TopBtn("⋮", 18.sp) { showMenu = true }
            }
        } else {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .clip(CircleShape)
                    .background(Color(0x88000000))
                    .clickable { locked = false; flash("Kilit açıldı") }
                    .size(40.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("🔓", fontSize = 15.sp)
            }
        }

        if (notice.isNotEmpty()) {
            Text(
                notice,
                color = Color.White,
                fontSize = 12.sp,
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xAA000000))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            )
        }

        if (error.isNotEmpty()) {
            Text(
                error,
                color = Color(0xFFFF6B6B),
                fontSize = 14.sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 70.dp)
            )
        }

        if (showMenu) {
            SettingsPanel(
                player = player,
                tracks = tracks,
                speed = speed,
                subSize = subSize,
                resizeLabel = resizeModes[resizeIndex].second,
                hasList = hasList,
                onSpeed = { speed = it },
                onSubSize = { subSize = it },
                onResize = { resizeIndex = (resizeIndex + 1) % resizeModes.size },
                onDismiss = { showMenu = false }
            )
        }
    }
}

@Composable
private fun TopBtn(label: String, size: androidx.compose.ui.unit.TextUnit, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(CircleShape)
            .background(Color(0x66000000))
            .clickable(onClick = onClick)
            .size(36.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color.White, fontSize = size)
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun SettingsPanel(
    player: ExoPlayer,
    tracks: Tracks?,
    speed: Float,
    subSize: Float,
    resizeLabel: String,
    hasList: Boolean,
    onSpeed: (Float) -> Unit,
    onSubSize: (Float) -> Unit,
    onResize: () -> Unit,
    onDismiss: () -> Unit
) {
    val audio = remember(tracks) {
        tracks?.let { collectTracks(it, C.TRACK_TYPE_AUDIO) }.orEmpty()
    }
    val subs = remember(tracks) {
        tracks?.let { collectTracks(it, C.TRACK_TYPE_TEXT) }.orEmpty()
    }
    val subsOff = remember(tracks) { subs.none { it.selected } }

    fun applyTrack(type: Int, opt: TrackOption?) {
        val t = tracks ?: return
        val b = player.trackSelectionParameters.buildUpon()
        b.clearOverridesOfType(type)
        if (opt == null) {
            b.setTrackTypeDisabled(type, true)
        } else {
            b.setTrackTypeDisabled(type, false)
            val group = t.groups.getOrNull(opt.groupIndex) ?: return
            b.addOverride(TrackSelectionOverride(group.mediaTrackGroup, opt.trackIndex))
        }
        player.trackSelectionParameters = b.build()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0x99000000))
            .clickable(onClick = onDismiss)
    ) {
        Column(
            Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(310.dp)
                .background(Color(0xF21A1B23))
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                "Oynatıcı ayarları",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(14.dp))

            GroupTitle("Ses")
            if (audio.isEmpty()) {
                Muted("Ses kanalı bulunamadı")
            } else {
                audio.forEach { a ->
                    OptRow(a.label, a.selected) { applyTrack(C.TRACK_TYPE_AUDIO, a) }
                }
            }

            Spacer(Modifier.height(14.dp))
            GroupTitle("Altyazı")
            OptRow("Kapalı", subsOff) { applyTrack(C.TRACK_TYPE_TEXT, null) }
            subs.forEach { s ->
                OptRow(s.label, s.selected) { applyTrack(C.TRACK_TYPE_TEXT, s) }
            }

            if (subs.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                GroupTitle("Altyazı boyutu")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0.04f to "Küçük", 0.06f to "Orta", 0.09f to "Büyük")
                        .forEach { (v, label) ->
                            Pill(label, subSize == v) { onSubSize(v) }
                        }
                }
            }

            Spacer(Modifier.height(14.dp))
            GroupTitle("Oynatma hızı")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { v ->
                    Pill(if (v == 1f) "Normal" else "${v}x", speed == v) { onSpeed(v) }
                }
            }

            Spacer(Modifier.height(14.dp))
            GroupTitle("Görüntü")
            OptRow("En-boy oranı: $resizeLabel", false) { onResize() }

            if (hasList) {
                Spacer(Modifier.height(14.dp))
                GroupTitle("Bölüm")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("◀ Önceki", false) { player.seekToPreviousMediaItem() }
                    Pill("Sonraki ▶", false) { player.seekToNextMediaItem() }
                }
            }

            Spacer(Modifier.height(20.dp))
            Text(
                "Ekranın soluna/sağına çift dokunarak 10 sn atlayabilirsin.",
                color = Color(0xFF6E7686),
                fontSize = 10.sp,
                lineHeight = 14.sp
            )
        }
    }
}

@Composable
private fun GroupTitle(t: String) {
    Text(t, color = PrizmaAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun Muted(t: String) {
    Text(t, color = Color(0xFF6E7686), fontSize = 12.sp)
}

@Composable
private fun OptRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) Color(0x334F8DF7) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            color = if (selected) Color.White else Color(0xFFC3C8D4),
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (selected) Text("✓", color = PrizmaAccent, fontSize = 12.sp)
    }
}

@Composable
private fun Pill(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (selected) Color.White else Color(0xFFC3C8D4),
        fontSize = 11.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) PrizmaAccent.copy(alpha = 0.4f) else Color(0xFF23242E))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    )
}
