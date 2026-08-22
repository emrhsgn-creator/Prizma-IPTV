package com.prizma.iptv.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.delay
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import com.prizma.iptv.R
import com.prizma.iptv.core.Fmt
import com.prizma.iptv.data.local.AspectMode
import com.prizma.iptv.data.model.PlayItem
import com.prizma.iptv.data.model.SavedItem
import com.prizma.iptv.ui.common.Pill
import com.prizma.iptv.ui.common.ProgressStrip
import com.prizma.iptv.ui.common.focusHighlight
import com.prizma.iptv.ui.theme.Ink
import com.prizma.iptv.ui.theme.accent

/**
 * Sağdan açılan kanal / bölüm listesi. Canlı yayında o an oynayan program
 * da satırda gösterilir.
 */
@UnstableApi
@Composable
fun ChannelPanel(controller: PlayerController, onDismiss: () -> Unit) {
    val tint = accent()
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }
    val live = controller.current?.isLive == true
    val now = System.currentTimeMillis()

    var query by remember { mutableStateOf("") }

    // Binlerce kanalda gezinmek yerine ada gore suzme. Gercek sira numarasi
    // korunur; suzulmus listeden secim yapinca dogru kanal acilir.
    val rows = remember(query, controller.items) {
        val all = controller.items.withIndex().toList()
        if (query.isBlank()) all
        else all.filter { it.value.title.contains(query.trim(), ignoreCase = true) }
    }

    LaunchedEffect(Unit) {
        listState.scrollToItem((controller.currentIndex - 3).coerceAtLeast(0))
        runCatching { focusRequester.requestFocus() }
    }

    LaunchedEffect(query) {
        if (query.isNotBlank()) runCatching { listState.scrollToItem(0) }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0x66000000))
            .clickable(onClick = onDismiss)
    ) {
        Column(
            Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(360.dp)
                .background(Color(0xF20B0D14))
                .clickable(enabled = false) { }
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Ink.HeaderStart)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(
                        if (live) R.string.player_channels else R.string.player_episodes
                    ),
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    rows.size.toString(),
                    color = Ink.TextMuted,
                    fontSize = 11.sp
                )
            }

            TextField(
                value = query,
                onValueChange = { query = it },
                placeholder = {
                    Text(stringResource(R.string.player_search_channel), fontSize = 12.sp)
                },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Ink.Surface,
                    unfocusedContainerColor = Ink.Surface,
                    focusedTextColor = Ink.TextPrimary,
                    unfocusedTextColor = Ink.TextPrimary,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = tint
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )

            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                items(rows, key = { it.value.id + it.value.url }) { row ->
                    val index = row.index
                    val item = row.value
                    val playingNow = index == controller.currentIndex
                    val program = if (live) {
                        controller.session?.epg
                            ?.nowNext(item.epgChannelId, now)?.first?.title.orEmpty()
                    } else {
                        item.subtitle
                    }

                    Row(
                        Modifier
                            .fillMaxWidth()
                            .then(
                                if (playingNow) Modifier.focusRequester(focusRequester)
                                else Modifier
                            )
                            .focusHighlight(RoundedCornerShape(0.dp))
                            .background(
                                if (playingNow) tint.copy(alpha = 0.18f) else Color.Transparent
                            )
                            .clickable {
                                controller.playIndex(index)
                                onDismiss()
                            }
                            .padding(horizontal = 14.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (item.number > 0) item.number.toString() else (index + 1).toString(),
                            color = Ink.TextMuted,
                            fontSize = 10.sp,
                            modifier = Modifier.width(36.dp)
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                item.title,
                                color = if (playingNow) Color.White else Ink.TextSecondary,
                                fontSize = 12.sp,
                                fontWeight = if (playingNow) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (program.isNotBlank()) {
                                Text(
                                    program,
                                    color = Ink.TextMuted,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        if (playingNow) {
                            Text("▶", color = tint, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

/** Sağdan açılan oynatıcı ayarları. */
@UnstableApi
@Composable
fun PlayerSettingsPanel(
    controller: PlayerController,
    onPickSubtitle: () -> Unit,
    onDismiss: () -> Unit
) {
    val tint = accent()
    val tracks = controller.tracks
    val live = controller.current?.isLive == true

    val favoritesFlow = remember(controller.session) {
        controller.session?.favorites?.items ?: MutableStateFlow(emptyList<SavedItem>())
    }
    val favorites by favoritesFlow.collectAsStateWithLifecycle()

    val panelContext = LocalContext.current
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(150)
        runCatching { firstFocus.requestFocus() }
    }

    val audioTracks = remember(tracks) {
        tracks?.let { collectTracks(it, C.TRACK_TYPE_AUDIO) }.orEmpty()
    }
    val textTracks = remember(tracks) {
        tracks?.let { collectTracks(it, C.TRACK_TYPE_TEXT) }.orEmpty()
    }
    val videoTracks = remember(tracks) {
        tracks?.let { collectTracks(it, C.TRACK_TYPE_VIDEO) }.orEmpty()
    }
    val subtitlesOff = textTracks.none { it.selected }

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
                .width(340.dp)
                .background(Color(0xF2161923))
                .clickable(enabled = false) { }
                .verticalScroll(rememberScrollState())
                .padding(18.dp)
        ) {
            // Kumandada panel acilinca odagin tutunacagi ilk oge.
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.player_settings),
                    color = Ink.TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Pill(
                    stringResource(R.string.close),
                    false,
                    Modifier.focusRequester(firstFocus)
                ) { onDismiss() }
            }
            Spacer(Modifier.height(16.dp))

            // ---- Favori ----
            // Kumandada uzun basmak guvenilir degil; favori islemi buradan
            // dogrudan erisilebilir olmali.
            val favoriteTarget = controller.currentFavoriteItem()
            if (favoriteTarget != null) {
                val isFavorite = favorites.any {
                    it.section == favoriteTarget.section && it.id == favoriteTarget.id
                }
                GroupTitle(stringResource(R.string.favorite))
                Pill(
                    stringResource(
                        if (isFavorite) R.string.player_remove_favorite
                        else R.string.player_add_favorite
                    ),
                    isFavorite
                ) { controller.toggleCurrentFavorite() }
                Spacer(Modifier.height(16.dp))
            }

            // ---- Ses ----
            GroupTitle(stringResource(R.string.player_audio))
            if (audioTracks.isEmpty()) {
                Text(
                    stringResource(R.string.player_audio_none),
                    color = Ink.TextMuted,
                    fontSize = 12.sp
                )
            } else {
                audioTracks.forEach { option ->
                    OptionRow(option.label, option.selected) {
                        controller.applyTrack(C.TRACK_TYPE_AUDIO, option)
                    }
                }
            }

            // ---- Altyazı ----
            Spacer(Modifier.height(16.dp))
            GroupTitle(stringResource(R.string.player_subtitle))
            OptionRow(stringResource(R.string.player_subtitle_off), subtitlesOff) {
                controller.applyTrack(C.TRACK_TYPE_TEXT, null)
            }
            textTracks.forEach { option ->
                OptionRow(option.label, option.selected) {
                    controller.applyTrack(C.TRACK_TYPE_TEXT, option)
                }
            }
            Spacer(Modifier.height(8.dp))
            Pill(stringResource(R.string.player_subtitle_external), false) { onPickSubtitle() }

            Spacer(Modifier.height(12.dp))
            GroupTitle(stringResource(R.string.player_subtitle_size))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    0.04f to R.string.player_size_small,
                    0.06f to R.string.player_size_medium,
                    0.09f to R.string.player_size_large,
                    0.12f to R.string.player_size_huge
                ).forEach { (value, labelRes) ->
                    Pill(stringResource(labelRes), controller.subtitleScale == value) {
                        controller.applySubtitleScale(value)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(
                    stringResource(R.string.player_subtitle_bg),
                    controller.subtitleBackground
                ) { controller.applySubtitleBackground(!controller.subtitleBackground) }
            }

            // ---- Görüntü kalitesi ----
            if (videoTracks.size > 1) {
                Spacer(Modifier.height(16.dp))
                GroupTitle(stringResource(R.string.player_video))
                OptionRow(
                    stringResource(R.string.auto),
                    videoTracks.none { it.selected }
                ) {
                    controller.applyTrack(C.TRACK_TYPE_VIDEO, null)
                    controller.player.trackSelectionParameters =
                        controller.player.trackSelectionParameters.buildUpon()
                            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, false)
                            .build()
                }
                videoTracks.forEach { option ->
                    OptionRow(option.label, option.selected) {
                        controller.applyTrack(C.TRACK_TYPE_VIDEO, option)
                    }
                }
            }

            // ---- Hız ----
            if (!live) {
                Spacer(Modifier.height(16.dp))
                GroupTitle(stringResource(R.string.player_speed))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { value ->
                        Pill(
                            if (value == 1f) stringResource(R.string.player_speed_normal)
                            else value.toString() + "x",
                            controller.speed == value
                        ) { controller.applySpeed(value) }
                    }
                }
            }

            // ---- Görüntü ----
            Spacer(Modifier.height(16.dp))
            GroupTitle(stringResource(R.string.player_aspect))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    AspectMode.FIT to R.string.player_aspect_fit,
                    AspectMode.CROP to R.string.player_aspect_crop,
                    AspectMode.STRETCH to R.string.player_aspect_stretch
                ).forEach { (mode, labelRes) ->
                    Pill(stringResource(labelRes), controller.aspect == mode) {
                        controller.applyAspectMode(mode)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(
                    stringResource(R.string.player_aspect_16_9),
                    controller.aspect == AspectMode.RATIO_16_9
                ) { controller.applyAspectMode(AspectMode.RATIO_16_9) }
                Pill(
                    stringResource(R.string.player_aspect_4_3),
                    controller.aspect == AspectMode.RATIO_4_3
                ) { controller.applyAspectMode(AspectMode.RATIO_4_3) }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                "Zoom " + Fmt.percent(controller.zoom / 2.5f),
                color = Ink.TextMuted,
                fontSize = 11.sp
            )
            Slider(
                value = controller.zoom,
                onValueChange = { controller.zoom = it },
                valueRange = 1f..2.5f,
                colors = SliderDefaults.colors(
                    thumbColor = tint,
                    activeTrackColor = tint,
                    inactiveTrackColor = Color(0x55FFFFFF)
                )
            )

            // ---- Uyku zamanlayıcı ----
            Spacer(Modifier.height(12.dp))
            GroupTitle(stringResource(R.string.player_sleep))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(
                    stringResource(R.string.player_sleep_off),
                    controller.sleepMinutes == 0
                ) { controller.setSleep(0) }
                listOf(15, 30, 60, 90).forEach { minutes ->
                    Pill(
                        stringResource(R.string.player_sleep_minutes, minutes),
                        controller.sleepMinutes == minutes
                    ) { controller.setSleep(minutes) }
                }
            }

            // ---- Teknik bilgi ----
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(stringResource(R.string.player_stats), controller.showStats) {
                    controller.showStats = !controller.showStats
                }
            }

            // ---- Harici oynatici ----
            // Sorunlu akislarda kacis yolu: adresi baska bir oynaticiya devret.
            Spacer(Modifier.height(16.dp))
            Pill(stringResource(R.string.player_external), false) {
                controller.current?.let { openExternally(panelContext, it) }
            }

            Spacer(Modifier.height(24.dp))
            Text(
                stringResource(R.string.player_hint_dpad),
                color = Ink.TextMuted,
                fontSize = 10.sp,
                lineHeight = 14.sp
            )
        }
    }
}

/** Sol üstte teknik bilgi katmanı. */
@UnstableApi
@Composable
fun StatsOverlay(controller: PlayerController, modifier: Modifier = Modifier) {
    val player = controller.player
    var snapshot by remember { mutableStateOf(listOf<Pair<String, String>>()) }

    val labelResolution = stringResource(R.string.player_stats_resolution)
    val labelVideo = stringResource(R.string.player_stats_video_codec)
    val labelAudio = stringResource(R.string.player_stats_audio_codec)
    val labelBitrate = stringResource(R.string.player_stats_bitrate)
    val labelBuffer = stringResource(R.string.player_stats_buffer)
    val labelDropped = stringResource(R.string.player_stats_dropped)
    val labelSource = stringResource(R.string.player_stats_source)
    val labelIndex = stringResource(R.string.player_stats_index)

    LaunchedEffect(Unit) {
        while (true) {
            val video = player.videoFormat
            val audio = player.audioFormat
            val counters = player.videoDecoderCounters
            snapshot = listOf(
                labelResolution to
                    if (video != null && video.height > 0) {
                        val fps = if (video.frameRate > 0f) {
                            " @" + video.frameRate.toInt()
                        } else {
                            ""
                        }
                        video.width.toString() + "x" + video.height + fps
                    } else "-",
                labelVideo to (video?.let { codecName(it) } ?: "-"),
                labelAudio to (audio?.let { codecName(it) } ?: "-"),
                labelBitrate to Fmt.bitrate(
                    listOf(video?.bitrate ?: 0, audio?.bitrate ?: 0)
                        .filter { it > 0 }
                        .sum()
                        .toLong()
                ),
                labelBuffer to Fmt.duration(
                    (player.bufferedPosition - player.currentPosition).coerceAtLeast(0L)
                ),
                labelDropped to (counters?.droppedBufferCount ?: 0).toString(),
                // Teshis: gercekten hangi adresin oynatildigi.
                labelSource to (controller.current?.url?.substringAfterLast('/') ?: "-"),
                labelIndex to ((controller.currentIndex + 1).toString() + " / " +
                    controller.items.size)
            )
            kotlinx.coroutines.delay(1_000)
        }
    }

    Column(
        modifier
            .padding(top = 70.dp, start = 16.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xB3000000))
            .padding(12.dp)
    ) {
        snapshot.forEach { (label, value) ->
            Row(Modifier.padding(vertical = 2.dp)) {
                Text(label, color = Ink.TextMuted, fontSize = 10.sp, modifier = Modifier.width(110.dp))
                Text(value, color = Color.White, fontSize = 10.sp, maxLines = 2)
            }
        }
    }
}

/** Oynatilan adresi cihazdaki baska bir video oynaticiya devreder. */
private fun openExternally(context: android.content.Context, item: PlayItem) {
    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
        setDataAndType(android.net.Uri.parse(item.url), "video/*")
        putExtra("title", item.title)
        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching {
        context.startActivity(
            android.content.Intent.createChooser(intent, item.title)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

@Composable
private fun GroupTitle(text: String) {
    Text(text, color = accent(), fontSize = 12.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun OptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    val tint = accent()
    Row(
        Modifier
            .fillMaxWidth()
            .focusHighlight(RoundedCornerShape(6.dp))
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) tint.copy(alpha = 0.22f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            color = if (selected) Color.White else Ink.TextSecondary,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (selected) Text("✓", color = tint, fontSize = 12.sp)
    }
}
