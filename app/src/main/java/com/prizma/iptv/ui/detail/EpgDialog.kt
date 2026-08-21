package com.prizma.iptv.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prizma.iptv.R
import com.prizma.iptv.core.Fmt
import com.prizma.iptv.data.model.EpgProgram
import com.prizma.iptv.data.model.StreamItem
import com.prizma.iptv.data.repo.Session
import com.prizma.iptv.ui.common.ProgressStrip
import com.prizma.iptv.ui.common.focusHighlight
import com.prizma.iptv.ui.theme.Ink
import com.prizma.iptv.ui.theme.accent

/**
 * Tek bir kanalın yayın akışı. Geçmiş programlarda kanalın arşivi varsa
 * dokunulduğunda catch-up olarak açılır.
 */
@Composable
fun EpgDialog(
    session: Session,
    item: StreamItem,
    onDismiss: () -> Unit,
    onPlayCatchup: ((EpgProgram) -> Unit)? = null
) {
    val ctx = LocalContext.current
    val tint = accent()
    var programs by remember(item.id) {
        mutableStateOf(session.epg.programsFor(item))
    }
    var loading by remember(item.id) { mutableStateOf(programs.isEmpty()) }
    val listState = rememberLazyListState()
    val now = System.currentTimeMillis()

    LaunchedEffect(item.id) {
        if (programs.isEmpty()) {
            programs = runCatching { session.epg.fetchSingle(item) }.getOrDefault(emptyList())
        }
        loading = false
    }

    // Açılınca şu anki programa kaydır.
    LaunchedEffect(programs) {
        val index = programs.indexOfFirst { it.stop > now }
        if (index > 0) runCatching { listState.scrollToItem(index) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Ink.Surface,
        title = {
            Column {
                Text(
                    item.name,
                    color = Ink.TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2
                )
                if (item.hasArchive) {
                    Text(
                        stringResource(R.string.guide_catchup),
                        color = tint,
                        fontSize = 10.sp
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close), color = tint)
            }
        },
        text = {
            when {
                loading -> Box(
                    Modifier.fillMaxWidth().height(90.dp),
                    Alignment.Center
                ) { CircularProgressIndicator(color = tint) }

                programs.isEmpty() -> Text(
                    stringResource(R.string.guide_no_data),
                    color = Ink.TextMuted,
                    fontSize = 13.sp
                )

                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.heightIn(max = 420.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(programs, key = { it.start.toString() + it.channelId }) { program ->
                        val live = program.isLiveAt(now)
                        val past = program.stop <= now
                        val playable = past && item.hasArchive && onPlayCatchup != null

                        Row(
                            Modifier
                                .fillMaxWidth()
                                .then(
                                    if (playable) {
                                        Modifier
                                            .focusHighlight(RoundedCornerShape(6.dp))
                                            .clip(RoundedCornerShape(6.dp))
                                            .clickable { onPlayCatchup?.invoke(program) }
                                    } else {
                                        Modifier
                                    }
                                )
                                .padding(4.dp)
                        ) {
                            Column(Modifier.width(54.dp)) {
                                Text(
                                    Fmt.clock(program.start),
                                    color = if (live) tint else Ink.TextMuted,
                                    fontSize = 12.sp,
                                    fontWeight = if (live) FontWeight.Bold else FontWeight.Normal
                                )
                                Text(
                                    Fmt.clock(program.stop),
                                    color = Ink.TextMuted,
                                    fontSize = 10.sp
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        program.title.ifBlank {
                                            stringResource(R.string.guide_program)
                                        },
                                        color = when {
                                            live -> Ink.TextPrimary
                                            past -> Ink.TextMuted
                                            else -> Ink.TextSecondary
                                        },
                                        fontSize = 13.sp,
                                        fontWeight = if (live) FontWeight.Bold else FontWeight.Normal,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (playable) {
                                        Text("⟲", color = tint, fontSize = 12.sp)
                                    }
                                }
                                if (live) {
                                    Spacer(Modifier.height(4.dp))
                                    ProgressStrip(program.progressAt(now))
                                }
                                if (program.description.isNotBlank()) {
                                    Spacer(Modifier.height(3.dp))
                                    Text(
                                        program.description,
                                        color = Ink.TextMuted,
                                        fontSize = 10.sp,
                                        lineHeight = 13.sp,
                                        maxLines = 3
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    )
}
