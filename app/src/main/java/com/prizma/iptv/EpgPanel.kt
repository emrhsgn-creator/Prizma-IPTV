package com.prizma.iptv

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Creds {
    var host = ""
    var user = ""
    var pass = ""
}

private val clock = SimpleDateFormat("HH:mm", Locale.getDefault())

fun epgClock(ts: Long): String =
    if (ts <= 0L) "--:--" else clock.format(Date(ts * 1000))

fun epgProgress(now: Long, start: Long, stop: Long): Float {
    if (start <= 0L || stop <= start) return 0f
    if (now < start || now > stop) return 0f
    return ((now - start).toFloat() / (stop - start).toFloat()).coerceIn(0f, 1f)
}

@Composable
fun EpgDialog(streamId: String, channelName: String, onDismiss: () -> Unit) {
    var list by remember { mutableStateOf<List<EpgItem>?>(null) }
    var error by remember { mutableStateOf("") }

    LaunchedEffect(streamId) {
        try {
            list = XtreamApi.shortEpg(Creds.host, Creds.user, Creds.pass, streamId)
        } catch (e: Exception) {
            error = "Yayın akışı alınamadı"
        }
    }

    val now = System.currentTimeMillis() / 1000

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF16171F),
        title = {
            Text(channelName, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Kapat", color = PrizmaAccent) }
        },
        text = {
            val data = list
            when {
                error.isNotEmpty() -> Text(error, color = Color(0xFFFF6B6B), fontSize = 13.sp)

                data == null -> Box(Modifier.fillMaxWidth().height(80.dp), Alignment.Center) {
                    CircularProgressIndicator(color = PrizmaAccent)
                }

                data.isEmpty() -> Text(
                    "Bu kanal için yayın akışı yok.",
                    color = Color(0xFF8A90A0),
                    fontSize = 13.sp
                )

                else -> LazyColumn(
                    modifier = Modifier.heightIn(max = 380.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(data) { e ->
                        val live = now in e.start..e.stop
                        Row {
                            Column(Modifier.width(52.dp)) {
                                Text(
                                    epgClock(e.start),
                                    color = if (live) PrizmaAccent else Color(0xFF8A90A0),
                                    fontSize = 12.sp,
                                    fontWeight = if (live) FontWeight.Bold else FontWeight.Normal
                                )
                                Text(
                                    epgClock(e.stop),
                                    color = Color(0xFF5D6472),
                                    fontSize = 10.sp
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    e.title.ifBlank { "Program" },
                                    color = if (live) Color.White else Color(0xFFC3C8D4),
                                    fontSize = 13.sp,
                                    fontWeight = if (live) FontWeight.Bold else FontWeight.Normal
                                )
                                if (live) {
                                    Spacer(Modifier.height(4.dp))
                                    Box(
                                        Modifier
                                            .fillMaxWidth()
                                            .height(3.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(Color(0xFF2A2E3A))
                                    ) {
                                        Box(
                                            Modifier
                                                .fillMaxWidth(epgProgress(now, e.start, e.stop))
                                                .height(3.dp)
                                                .background(PrizmaAccent)
                                        )
                                    }
                                }
                                if (e.description.isNotBlank()) {
                                    Spacer(Modifier.height(3.dp))
                                    Text(
                                        e.description,
                                        color = Color(0xFF8A90A0),
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
