package com.prizma.iptv.ui.guide

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prizma.iptv.R
import com.prizma.iptv.core.Fmt
import com.prizma.iptv.data.model.EpgProgram
import com.prizma.iptv.data.model.Section
import com.prizma.iptv.data.model.StreamItem
import com.prizma.iptv.data.repo.Session
import com.prizma.iptv.ui.common.Chip
import com.prizma.iptv.ui.common.MessageBox
import com.prizma.iptv.ui.common.Pill
import com.prizma.iptv.ui.common.focusHighlight
import com.prizma.iptv.ui.home.HomeData
import com.prizma.iptv.ui.home.HomeState
import com.prizma.iptv.ui.home.Launch
import com.prizma.iptv.ui.theme.Ink
import com.prizma.iptv.ui.theme.accent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val DAY_MS = 86_400_000L
private const val HOUR_MS = 3_600_000L

/** Zaman ekseninde bir dakikanın kaç dp yer kapladığı. */
private val MINUTE_WIDTH = 4.dp
private val CHANNEL_COLUMN = 160.dp
private val ROW_HEIGHT = 58.dp

/**
 * Klasik TV rehberi: solda kanal sütunu, sağda zaman ekseninde program blokları.
 * Tüm satırlar aynı yatay kaydırma durumunu paylaştığı için eksen hizalı kalır.
 */
@Composable
fun GuideScreen(state: HomeState, data: HomeData) {
    val ctx = LocalContext.current
    val session = state.session
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    val channels = state.visibleItems(data.section(Section.LIVE))
    val timeline = rememberScrollState()

    var dayOffset by remember { mutableIntStateOf(0) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Canlı çubuğun ilerlemesi için dakikada bir tazelenir.
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            now = System.currentTimeMillis()
        }
    }

    val windowStart = remember(dayOffset, now) { startOfDay(now) + dayOffset * DAY_MS }
    val windowEnd = windowStart + DAY_MS

    val minuteWidthPx = with(density) { MINUTE_WIDTH.toPx() }

    fun scrollToNow() {
        val target = ((now - windowStart) / 60_000L).coerceAtLeast(0L)
        scope.launch {
            timeline.animateScrollTo((target * minuteWidthPx).toInt())
        }
    }

    LaunchedEffect(dayOffset) {
        if (dayOffset == 0) {
            val target = (((now - windowStart) / 60_000L) - 30L).coerceAtLeast(0L)
            timeline.scrollTo((target * minuteWidthPx).toInt())
        } else {
            timeline.scrollTo(0)
        }
    }

    if (channels.isEmpty()) {
        MessageBox(stringResource(R.string.guide_loading))
        return
    }

    if (session.epg.isEmpty) {
        MessageBox(
            message = stringResource(R.string.guide_epg_missing),
            actionLabel = stringResource(R.string.guide_refresh),
            onAction = { state.refreshEpg() }
        )
        return
    }

    Column(Modifier.fillMaxSize()) {

        // Gün seçimi
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LazyRow(
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items((-1..5).toList()) { offset ->
                    Chip(
                        Fmt.dayLabel(ctx, startOfDay(now) + offset * DAY_MS),
                        offset == dayOffset
                    ) { dayOffset = offset }
                }
            }
            Pill(stringResource(R.string.guide_jump_now), false) { scrollToNow() }
        }

        // Zaman cetveli
        Row(Modifier.fillMaxWidth().background(Ink.Surface)) {
            Box(Modifier.width(CHANNEL_COLUMN).height(26.dp))
            Row(
                Modifier
                    .weight(1f)
                    .horizontalScroll(timeline)
            ) {
                for (halfHour in 0 until 48) {
                    Text(
                        Fmt.clock(windowStart + halfHour * (HOUR_MS / 2)),
                        color = Ink.TextMuted,
                        fontSize = 10.sp,
                        modifier = Modifier
                            .width(MINUTE_WIDTH * 30)
                            .height(26.dp)
                            .padding(start = 4.dp, top = 6.dp)
                    )
                }
            }
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 20.dp)
        ) {
            items(channels, key = { it.id }) { channel ->
                GuideRow(
                    session = session,
                    channel = channel,
                    windowStart = windowStart,
                    windowEnd = windowEnd,
                    now = now,
                    timeline = timeline,
                    epgRevision = data.epgRevision,
                    onProgram = { program ->
                        openProgram(ctx, state, channel, program, now, channels)
                    },
                    onChannel = {
                        Launch.openLive(ctx, session, channels, channel)
                    }
                )
            }
        }
    }
}

@Composable
private fun GuideRow(
    session: Session,
    channel: StreamItem,
    windowStart: Long,
    windowEnd: Long,
    now: Long,
    timeline: ScrollState,
    epgRevision: Int,
    onProgram: (EpgProgram) -> Unit,
    onChannel: () -> Unit
) {
    val tint = accent()
    val programs = remember(channel.id, windowStart, epgRevision) {
        session.epg.inWindow(session.epg.resolveChannelId(channel), windowStart, windowEnd)
    }

    Row(Modifier.fillMaxWidth().height(ROW_HEIGHT)) {

        Row(
            Modifier
                .width(CHANNEL_COLUMN)
                .fillMaxHeight()
                .focusHighlight(RoundedCornerShape(0.dp))
                .background(Ink.Surface)
                .clickable(onClick = onChannel)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (channel.number > 0) {
                Text(
                    channel.number.toString(),
                    color = Ink.TextMuted,
                    fontSize = 10.sp,
                    modifier = Modifier.width(30.dp)
                )
            }
            Text(
                channel.name,
                color = Ink.TextPrimary,
                fontSize = 11.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        Row(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .horizontalScroll(timeline)
        ) {
            if (programs.isEmpty()) {
                Box(
                    Modifier
                        .width(MINUTE_WIDTH * 1440)
                        .fillMaxHeight()
                        .padding(1.dp)
                        .background(Ink.SurfaceHigh),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        stringResource(R.string.guide_no_data),
                        color = Ink.TextMuted,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
            } else {
                var cursor = windowStart
                programs.forEach { program ->
                    val start = program.start.coerceAtLeast(windowStart)
                    val stop = program.stop.coerceAtMost(windowEnd)
                    if (stop <= start) return@forEach

                    if (start > cursor) {
                        Spacer(Modifier.width(MINUTE_WIDTH * minutesBetween(cursor, start)))
                    }
                    ProgramBlock(
                        program = program,
                        widthMinutes = minutesBetween(start, stop),
                        live = program.isLiveAt(now),
                        past = program.stop <= now,
                        hasArchive = channel.hasArchive,
                        accentColor = tint,
                        onClick = { onProgram(program) }
                    )
                    cursor = stop
                }
                if (cursor < windowEnd) {
                    Spacer(Modifier.width(MINUTE_WIDTH * minutesBetween(cursor, windowEnd)))
                }
            }
        }
    }
}

@Composable
private fun ProgramBlock(
    program: EpgProgram,
    widthMinutes: Int,
    live: Boolean,
    past: Boolean,
    hasArchive: Boolean,
    accentColor: Color,
    onClick: () -> Unit
) {
    val background = when {
        live -> accentColor.copy(alpha = 0.32f)
        past && hasArchive -> Ink.SurfaceHigh
        past -> Ink.Surface
        else -> Ink.SurfaceHigh
    }
    Column(
        Modifier
            .width(MINUTE_WIDTH * widthMinutes.coerceAtLeast(6))
            .fillMaxHeight()
            .padding(1.dp)
            .focusHighlight(RoundedCornerShape(4.dp))
            .clip(RoundedCornerShape(4.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 6.dp)
    ) {
        Text(
            program.title.ifBlank { stringResource(R.string.guide_program) },
            color = if (past && !live) Ink.TextMuted else Ink.TextPrimary,
            fontSize = 11.sp,
            fontWeight = if (live) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                Fmt.clock(program.start) + " - " + Fmt.clock(program.stop),
                color = Ink.TextMuted,
                fontSize = 9.sp
            )
            if (past && hasArchive) {
                Spacer(Modifier.width(4.dp))
                Text("⟲", color = accentColor, fontSize = 9.sp)
            }
        }
    }
}

private fun minutesBetween(from: Long, to: Long): Int =
    (((to - from) / 60_000L).toInt()).coerceAtLeast(0)

private fun startOfDay(ts: Long): Long {
    val zone = java.time.ZoneId.systemDefault()
    return java.time.Instant.ofEpochMilli(ts)
        .atZone(zone)
        .toLocalDate()
        .atStartOfDay(zone)
        .toInstant()
        .toEpochMilli()
}

/**
 * Programa tıklanınca: şu an yayındaysa kanalı aç, geçmişte kaldıysa ve
 * kanalda arşiv varsa catch-up olarak oynat.
 */
private fun openProgram(
    ctx: android.content.Context,
    state: HomeState,
    channel: StreamItem,
    program: EpgProgram,
    now: Long,
    channels: List<StreamItem>
) {
    val session = state.session
    when {
        program.isLiveAt(now) -> Launch.openLive(ctx, session, channels, channel)

        program.stop <= now -> {
            val minutes = ((program.stop - program.start) / 60_000L).toInt().coerceAtLeast(1)
            val item = session.catchupItem(channel, program.start, minutes, program.title)
            if (item != null) {
                Launch.openSingle(ctx, item)
            } else {
                state.epgDialogItem = channel
            }
        }

        else -> state.epgDialogItem = channel
    }
}
