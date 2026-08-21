package com.prizma.iptv.ui.detail

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.prizma.iptv.R
import com.prizma.iptv.core.Fmt
import com.prizma.iptv.core.LocaleHelper
import com.prizma.iptv.core.userMessage
import com.prizma.iptv.data.model.Episode
import com.prizma.iptv.data.model.PlayItem
import com.prizma.iptv.data.model.SavedItem
import com.prizma.iptv.data.model.Section
import com.prizma.iptv.data.model.SeriesInfo
import com.prizma.iptv.data.model.WatchState
import com.prizma.iptv.data.remote.XtreamApi
import com.prizma.iptv.data.repo.App
import com.prizma.iptv.data.repo.Session
import com.prizma.iptv.player.PlayerActivity
import com.prizma.iptv.ui.common.Chip
import com.prizma.iptv.ui.common.MessageBox
import com.prizma.iptv.ui.common.PrimaryButton
import com.prizma.iptv.ui.common.ProgressStrip
import com.prizma.iptv.ui.common.focusHighlight
import com.prizma.iptv.ui.theme.Ink
import com.prizma.iptv.ui.theme.PrizmaTheme
import com.prizma.iptv.ui.theme.accent

class SeriesActivity : ComponentActivity() {

    companion object {
        fun start(ctx: Context, seriesId: String, name: String, cover: String) {
            ctx.startActivity(
                Intent(ctx, SeriesActivity::class.java).apply {
                    putExtra("seriesId", seriesId)
                    putExtra("name", name)
                    putExtra("cover", cover)
                }
            )
        }
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val session = App.ensureSession()
        if (session == null) {
            finish()
            return
        }
        setContent {
            PrizmaTheme {
                SeriesScreen(
                    session = session,
                    seriesId = intent.getStringExtra("seriesId").orEmpty(),
                    name = intent.getStringExtra("name").orEmpty(),
                    cover = intent.getStringExtra("cover").orEmpty(),
                    onBack = { finish() }
                )
            }
        }
    }
}

@Composable
private fun SeriesScreen(
    session: Session,
    seriesId: String,
    name: String,
    cover: String,
    onBack: () -> Unit
) {
    val ctx = LocalContext.current
    val tint = accent()

    var info by remember(seriesId) { mutableStateOf<SeriesInfo?>(null) }
    var error by remember(seriesId) { mutableStateOf<Throwable?>(null) }
    var seasonNumber by remember(seriesId) { mutableIntStateOf(-1) }

    val favorites by session.favorites.items.collectAsStateWithLifecycle()
    val history by session.history.items.collectAsStateWithLifecycle()

    val isFavorite = favorites.any { it.section == Section.SERIES.name && it.id == seriesId }

    LaunchedEffect(seriesId) {
        runCatching { XtreamApi.seriesInfo(session.profile, seriesId) }
            .onSuccess {
                info = it
                seasonNumber = it.seasons.firstOrNull()?.number ?: -1
            }
            .onFailure { error = it }
    }

    val data = info

    /** Bölüm listesini oynatma sırası olarak oynatıcıya verir. */
    fun playFrom(episodes: List<Episode>, index: Int, fromStart: Boolean = false) {
        if (episodes.isEmpty()) return
        val items = episodes.map { session.episodeItem(it, name, seriesId, cover) }
        PlayerActivity.start(ctx, items, index.coerceIn(0, items.lastIndex), forceRestart = fromStart)
    }

    Surface(color = Ink.Bg, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {

            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Ink.HeaderStart)
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .focusHighlight(CircleShape)
                        .clip(CircleShape)
                        .background(Color(0x33FFFFFF))
                        .clickable(onClick = onBack)
                        .size(34.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("‹", color = Color.White, fontSize = 20.sp)
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    name,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    Modifier
                        .focusHighlight(CircleShape)
                        .clip(CircleShape)
                        .clickable {
                            session.favorites.toggle(
                                SavedItem(
                                    section = Section.SERIES.name,
                                    id = seriesId,
                                    name = name,
                                    icon = data?.cover?.ifBlank { cover } ?: cover,
                                    rating = data?.rating ?: 0.0
                                )
                            )
                        }
                        .padding(8.dp)
                ) {
                    Icon(
                        Icons.Default.Star, null,
                        tint = if (isFavorite) Ink.Gold else Color(0xAAFFFFFF),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            when {
                error != null -> MessageBox(
                    message = error!!.userMessage(ctx),
                    isError = true,
                    actionLabel = stringResource(R.string.retry),
                    onAction = { error = null }
                )

                data == null -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator(color = tint)
                }

                else -> {
                    val season = data.seasons.firstOrNull { it.number == seasonNumber }
                        ?: data.seasons.firstOrNull()
                    val episodes = season?.episodes.orEmpty()

                    val watchByEpisode = remember(history, episodes) {
                        history.filter { it.section == PlayItem.EPISODE_SECTION }
                            .associateBy { it.id }
                    }
                    val nextIndex = remember(episodes, watchByEpisode) {
                        episodes.indexOfFirst { episode ->
                            val watch = watchByEpisode[episode.id]
                            watch == null || !watch.finished
                        }.coerceAtLeast(0)
                    }

                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 28.dp)
                    ) {
                        item {
                            SeriesHeader(data, cover)
                        }

                        item {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                PrimaryButton(
                                    label = stringResource(
                                        if (nextIndex > 0) R.string.detail_next_episode
                                        else R.string.detail_play_all
                                    ),
                                    leading = "▶",
                                    onClick = { playFrom(episodes, nextIndex) }
                                )
                            }
                        }

                        if (data.seasons.size > 1) item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(data.seasons, key = { it.number }) { bundle ->
                                    Chip(
                                        stringResource(R.string.detail_season, bundle.number),
                                        bundle.number == season?.number
                                    ) { seasonNumber = bundle.number }
                                }
                            }
                        }

                        items(episodes, key = { it.id }) { episode ->
                            val watch = watchByEpisode[episode.id]
                            EpisodeRow(
                                episode = episode,
                                progress = watch?.progress ?: 0f,
                                watched = watch?.finished == true,
                                fallbackImage = data.cover.ifBlank { cover },
                                onClick = {
                                    playFrom(episodes, episodes.indexOf(episode))
                                },
                                onToggleWatched = {
                                    if (watch?.finished == true) {
                                        session.history.markUnwatched(
                                            PlayItem.EPISODE_SECTION, episode.id
                                        )
                                    } else {
                                        session.history.markWatched(
                                            WatchState(
                                                section = PlayItem.EPISODE_SECTION,
                                                id = episode.id,
                                                name = name,
                                                icon = episode.icon.ifBlank { cover },
                                                extension = episode.extension,
                                                duration = 1L,
                                                parentId = seriesId
                                            )
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SeriesHeader(data: SeriesInfo, fallbackCover: String) {
    val tint = accent()
    Row(Modifier.padding(14.dp)) {
        Box(
            Modifier
                .width(110.dp)
                .height(165.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Ink.SurfaceHigh)
        ) {
            val image = data.cover.ifBlank { fallbackCover }
            if (image.isNotEmpty()) {
                AsyncImage(
                    model = image,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            val meta = listOf(
                data.genre,
                data.releaseDate.take(10),
                Fmt.rating(data.rating)
            ).filter { it.isNotBlank() }.joinToString(" · ")
            if (meta.isNotEmpty()) {
                Text(meta, color = tint, fontSize = 11.sp)
                Spacer(Modifier.height(6.dp))
            }
            if (data.plot.isNotBlank()) {
                Text(
                    data.plot,
                    color = Ink.TextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    maxLines = 8,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (data.cast.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.detail_cast) + ": " + data.cast,
                    color = Ink.TextMuted,
                    fontSize = 10.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun EpisodeRow(
    episode: Episode,
    progress: Float,
    watched: Boolean,
    fallbackImage: String,
    onClick: () -> Unit,
    onToggleWatched: () -> Unit
) {
    val tint = accent()
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 3.dp)
            .focusHighlight(RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .width(118.dp)
                .height(66.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Ink.SurfaceHigh)
        ) {
            val image = episode.icon.ifBlank { fallbackImage }
            if (image.isNotEmpty()) {
                AsyncImage(
                    model = image,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Text(
                episode.episodeNum.toString(),
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(4.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Ink.Scrim)
                    .padding(horizontal = 6.dp, vertical = 1.dp)
            )
            if (progress > 0f) {
                Box(Modifier.align(Alignment.BottomStart)) {
                    ProgressStrip(progress)
                }
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Text(
                episode.title,
                color = Ink.TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            val duration = if (episode.durationSecs > 0) {
                Fmt.duration(episode.durationSecs * 1000L)
            } else ""
            if (duration.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(duration, color = Ink.TextMuted, fontSize = 10.sp)
            }
            if (episode.plot.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    episode.plot,
                    color = Ink.TextMuted,
                    fontSize = 10.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 13.sp
                )
            }
        }

        Spacer(Modifier.width(8.dp))

        Text(
            if (watched) "✓" else "○",
            color = if (watched) tint else Ink.TextMuted,
            fontSize = 14.sp,
            modifier = Modifier
                .focusHighlight(CircleShape)
                .clip(CircleShape)
                .clickable(onClick = onToggleWatched)
                .padding(8.dp)
        )
    }
}
