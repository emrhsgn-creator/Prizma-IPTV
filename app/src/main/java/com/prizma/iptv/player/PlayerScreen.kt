package com.prizma.iptv.player

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import com.prizma.iptv.R
import com.prizma.iptv.core.Fmt
import com.prizma.iptv.data.local.AspectMode
import com.prizma.iptv.data.model.PlayKind
import com.prizma.iptv.ui.common.GlyphButton
import com.prizma.iptv.ui.common.PrimaryButton
import com.prizma.iptv.ui.theme.Ink
import com.prizma.iptv.ui.theme.accent
import com.prizma.iptv.ui.theme.ui
import kotlinx.coroutines.delay
import kotlin.math.abs

private enum class DragMode { NONE, SEEK, VOLUME, BRIGHTNESS }

@UnstableApi
@Composable
fun PlayerScreen(
    controller: PlayerController,
    inPip: Boolean,
    landscapeLocked: Boolean,
    onToggleOrientation: () -> Unit,
    onEnterPip: () -> Unit,
    onPickSubtitle: () -> Unit,
    onExit: () -> Unit
) {
    val ctx = LocalContext.current
    val profile = ui()
    val tint = accent()
    val activity = ctx as? Activity
    val audio = remember { ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }

    val player = controller.player
    val item = controller.current
    val isLive = item?.isLive == true

    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var buffered by remember { mutableLongStateOf(0L) }
    var scrub by remember { mutableStateOf<Float?>(null) }

    LaunchedEffect(controller.currentIndex) {
        while (true) {
            position = player.currentPosition.coerceAtLeast(0L)
            duration = player.duration.let { if (it > 0L) it else 0L }
            buffered = player.bufferedPosition.coerceAtLeast(0L)
            delay(500)
        }
    }

    BackHandler {
        when {
            controller.showSettings -> controller.showSettings = false
            controller.showChannels -> controller.showChannels = false
            controller.locked -> controller.applyLock(false)
            controller.controlsVisible && profile.isTv -> controller.controlsVisible = false
            else -> onExit()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {

        // ------------------------------------------------------------ video
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(scaleX = controller.zoom, scaleY = controller.zoom),
            factory = { context ->
                PlayerView(context).apply {
                    useController = false
                    setKeepContentOnPlayerReset(true)
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    this.player = controller.player
                    subtitleView?.setApplyEmbeddedStyles(false)
                }
            },
            update = { view ->
                view.applyAspect(controller.aspect, controller.naturalRatio)
                view.subtitleView?.setFractionalTextSize(controller.subtitleScale)
                view.subtitleView?.setStyle(captionStyle(controller.subtitleBackground))
            }
        )

        if (inPip) return@Box

        // ------------------------------------------------------------ jestler
        if (!profile.isTv) {
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(controller.locked) {
                        detectTapGestures(
                            onTap = {
                                if (controller.locked) controller.showNotice(
                                    ctx.getString(R.string.player_locked)
                                ) else controller.toggleControls()
                            },
                            onDoubleTap = { offset ->
                                if (controller.locked || isLive) return@detectTapGestures
                                if (offset.x < size.width / 2f) {
                                    player.seekBack()
                                } else {
                                    player.seekForward()
                                }
                                controller.revealControls()
                            },
                            onLongPress = {
                                if (!controller.locked) controller.applySpeedBoost(true)
                            }
                        )
                    }
                    // Uzun basış bırakıldığında hız normale döner.
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            waitForUpOrCancellation()
                            controller.applySpeedBoost(false)
                        }
                    }
                    .pointerInput(controller.locked) {
                        if (controller.locked) return@pointerInput
                        var mode = DragMode.NONE
                        var startX = 0f
                        var startPosition = 0L
                        var accumulated = 0f

                        detectDragGestures(
                            onDragStart = { offset ->
                                mode = DragMode.NONE
                                startX = offset.x
                                startPosition = player.currentPosition
                                accumulated = 0f
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                if (mode == DragMode.NONE) {
                                    mode = when {
                                        abs(amount.x) > abs(amount.y) -> DragMode.SEEK
                                        startX < size.width / 2f -> DragMode.BRIGHTNESS
                                        else -> DragMode.VOLUME
                                    }
                                }
                                when (mode) {
                                    DragMode.SEEK -> {
                                        if (isLive || duration <= 0L) return@detectDragGestures
                                        accumulated += amount.x
                                        val deltaMs =
                                            (accumulated / size.width.toFloat() * duration).toLong()
                                        controller.seekPreviewMs =
                                            (startPosition + deltaMs).coerceIn(0L, duration)
                                    }

                                    DragMode.BRIGHTNESS -> {
                                        val current = activity?.window?.attributes?.screenBrightness
                                            ?: 0.5f
                                        val base = if (current < 0f) 0.5f else current
                                        val next = (base - amount.y / size.height.toFloat())
                                            .coerceIn(0.02f, 1f)
                                        activity?.window?.let { window ->
                                            window.attributes = window.attributes.apply {
                                                screenBrightness = next
                                            }
                                        }
                                        controller.brightnessOverlay = next
                                    }

                                    DragMode.VOLUME -> {
                                        val manager = audio ?: return@detectDragGestures
                                        val max =
                                            manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                        val currentVolume =
                                            manager.getStreamVolume(AudioManager.STREAM_MUSIC)
                                        val step = -amount.y / size.height.toFloat() * max * 1.6f
                                        val next = (currentVolume + step).toInt()
                                            .coerceIn(0, max)
                                        manager.setStreamVolume(
                                            AudioManager.STREAM_MUSIC, next, 0
                                        )
                                        controller.volumeOverlay =
                                            if (max > 0) next.toFloat() / max else 0f
                                    }

                                    DragMode.NONE -> Unit
                                }
                            },
                            onDragEnd = {
                                controller.seekPreviewMs?.let { target ->
                                    player.seekTo(target)
                                    controller.revealControls()
                                }
                                controller.seekPreviewMs = null
                                mode = DragMode.NONE
                            },
                            onDragCancel = {
                                controller.seekPreviewMs = null
                                mode = DragMode.NONE
                            }
                        )
                    }
            )
        }

        // ------------------------------------------------------------ göstergeler

        if (controller.buffering && controller.errorText.isEmpty()) {
            CircularProgressIndicator(
                color = tint,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(46.dp)
            )
        }

        controller.seekPreviewMs?.let { target ->
            CenterPill(
                Fmt.duration(target) + " / " + Fmt.duration(duration),
                Modifier.align(Alignment.Center)
            )
        }

        controller.volumeOverlay?.let { value ->
            LevelOverlay(
                label = stringResource(R.string.player_volume),
                value = value,
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }

        controller.brightnessOverlay?.let { value ->
            LevelOverlay(
                label = stringResource(R.string.player_brightness),
                value = value,
                modifier = Modifier.align(Alignment.CenterStart)
            )
        }

        LaunchedEffect(controller.volumeOverlay, controller.brightnessOverlay) {
            if (controller.volumeOverlay != null || controller.brightnessOverlay != null) {
                delay(900)
                controller.volumeOverlay = null
                controller.brightnessOverlay = null
            }
        }

        if (controller.channelInput.isNotEmpty()) {
            CenterPill(
                stringResource(R.string.player_channel_number, controller.channelInput),
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 70.dp)
            )
        }

        if (controller.speedBoost) {
            CenterPill("2x ▶▶", Modifier.align(Alignment.TopCenter).padding(top = 24.dp))
        }

        if (controller.notice.isNotEmpty()) {
            CenterPill(
                controller.notice,
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 110.dp)
            )
        }

        if (controller.errorText.isNotEmpty()) {
            Column(
                Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(controller.errorText, color = Ink.Danger, fontSize = 14.sp)
                Spacer(Modifier.height(12.dp))
                PrimaryButton(stringResource(R.string.retry)) { controller.retryNow() }
            }
        }

        // ------------------------------------------------------------ kilit

        if (controller.locked) {
            GlyphButton(
                glyph = "🔓",
                size = 46.dp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(14.dp)
            ) { controller.applyLock(false) }
            return@Box
        }

        // ------------------------------------------------------------ üst çubuk

        AnimatedVisibility(
            visible = controller.controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopStart)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent))
                    )
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlyphButton("‹", fontSize = 24.sp, onClick = onExit)
                Spacer(Modifier.width(8.dp))
                GlyphButton("🔒", fontSize = 14.sp) { controller.applyLock(true) }

                Column(
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp)
                ) {
                    val channelNumber = item?.number ?: 0
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (channelNumber > 0) {
                            Text(
                                channelNumber.toString(),
                                color = tint,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                        }
                        Text(
                            item?.title.orEmpty(),
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    val subtitle = item?.subtitle.orEmpty()
                    if (subtitle.isNotEmpty()) {
                        Text(
                            subtitle,
                            color = Color(0xCCFFFFFF),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (controller.sleepRemainingMs > 0L) {
                    Text(
                        Fmt.duration(controller.sleepRemainingMs),
                        color = tint,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }

                if (!profile.isTv) {
                    GlyphButton("⤢", fontSize = 15.sp, onClick = onEnterPip)
                    Spacer(Modifier.width(6.dp))
                    GlyphButton(
                        if (landscapeLocked) "🔄" else "📱",
                        fontSize = 13.sp,
                        onClick = onToggleOrientation
                    )
                    Spacer(Modifier.width(6.dp))
                }
                if (controller.hasQueue) {
                    GlyphButton("☰", fontSize = 16.sp) { controller.showChannels = true }
                    Spacer(Modifier.width(6.dp))
                }
                GlyphButton("⋮", fontSize = 18.sp) { controller.showSettings = true }
            }
        }

        // ------------------------------------------------------------ alt çubuk

        AnimatedVisibility(
            visible = controller.controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomStart)
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000)))
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                if (!isLive && duration > 0L) {
                    val value = scrub ?: (position.toFloat() / duration.toFloat())
                    Slider(
                        value = value.coerceIn(0f, 1f),
                        onValueChange = { scrub = it },
                        onValueChangeFinished = {
                            scrub?.let { player.seekTo((it * duration).toLong()) }
                            scrub = null
                            controller.revealControls()
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = tint,
                            activeTrackColor = tint,
                            inactiveTrackColor = Color(0x55FFFFFF)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(Modifier.fillMaxWidth()) {
                        Text(
                            Fmt.duration((value * duration).toLong()),
                            color = Color.White,
                            fontSize = 11.sp
                        )
                        Spacer(Modifier.weight(1f))
                        Text(Fmt.duration(duration), color = Color(0xCCFFFFFF), fontSize = 11.sp)
                    }
                    Spacer(Modifier.height(6.dp))
                }

                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (controller.hasQueue) {
                        GlyphButton("⏮", fontSize = 14.sp) { controller.jump(-1) }
                    }
                    if (!isLive) {
                        GlyphButton("⏪", fontSize = 14.sp) { player.seekBack() }
                    }
                    GlyphButton(
                        if (controller.playing) "⏸" else "▶",
                        size = 52.dp,
                        fontSize = 20.sp
                    ) {
                        if (controller.playing) player.pause() else player.play()
                        controller.revealControls()
                    }
                    if (!isLive) {
                        GlyphButton("⏩", fontSize = 14.sp) { player.seekForward() }
                    }
                    if (controller.hasQueue) {
                        GlyphButton("⏭", fontSize = 14.sp) { controller.jump(1) }
                    }

                    Spacer(Modifier.weight(1f))

                    if (isLive) {
                        Text(
                            stringResource(
                                if (item?.kind == PlayKind.CATCHUP) {
                                    R.string.player_catchup
                                } else {
                                    R.string.player_live
                                }
                            ),
                            color = Ink.Live,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0x33FF4D4F))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    if (controller.speed != 1f) {
                        Text(
                            controller.speed.toString() + "x",
                            color = tint,
                            fontSize = 12.sp
                        )
                    }
                }

                if (profile.isTv) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.player_hint_dpad),
                        color = Color(0x99FFFFFF),
                        fontSize = 10.sp
                    )
                }
            }
        }

        // ------------------------------------------------------------ paneller

        if (controller.showChannels) {
            ChannelPanel(controller) { controller.showChannels = false }
        }

        if (controller.showSettings) {
            PlayerSettingsPanel(
                controller = controller,
                onPickSubtitle = onPickSubtitle,
                onDismiss = { controller.showSettings = false }
            )
        }

        if (controller.showStats) {
            StatsOverlay(controller, Modifier.align(Alignment.TopStart))
        }
    }
}

@Composable
private fun CenterPill(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        color = Color.White,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xB3000000))
            .padding(horizontal = 16.dp, vertical = 9.dp)
    )
}

@Composable
private fun LevelOverlay(label: String, value: Float, modifier: Modifier = Modifier) {
    val tint = accent()
    Column(
        modifier
            .padding(24.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xB3000000))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, color = Color.White, fontSize = 11.sp)
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .width(6.dp)
                .height(110.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color(0x44FFFFFF))
        ) {
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .width(6.dp)
                    .height((110 * value.coerceIn(0f, 1f)).dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(tint)
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(Fmt.percent(value), color = Color.White, fontSize = 11.sp)
    }
}

/** En-boy oranını uygular; zorlanmış oranlar içerik çerçevesine yazılır. */
@UnstableApi
private fun PlayerView.applyAspect(mode: AspectMode, naturalRatio: Float) {
    resizeMode = when (mode) {
        AspectMode.CROP -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        AspectMode.STRETCH -> AspectRatioFrameLayout.RESIZE_MODE_FILL
        else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
    }
    val frame = findViewById<AspectRatioFrameLayout>(androidx.media3.ui.R.id.exo_content_frame)
        ?: return
    val ratio = when (mode) {
        AspectMode.RATIO_16_9 -> 16f / 9f
        AspectMode.RATIO_4_3 -> 4f / 3f
        else -> naturalRatio
    }
    if (ratio > 0f) frame.setAspectRatio(ratio)
}

@UnstableApi
private fun captionStyle(withBackground: Boolean) = CaptionStyleCompat(
    android.graphics.Color.WHITE,
    if (withBackground) android.graphics.Color.argb(160, 0, 0, 0) else android.graphics.Color.TRANSPARENT,
    android.graphics.Color.TRANSPARENT,
    CaptionStyleCompat.EDGE_TYPE_OUTLINE,
    android.graphics.Color.BLACK,
    null
)
