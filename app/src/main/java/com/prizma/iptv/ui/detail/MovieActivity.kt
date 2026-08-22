package com.prizma.iptv.ui.detail

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Brush
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
import com.prizma.iptv.data.model.Section
import com.prizma.iptv.data.model.VodInfo
import com.prizma.iptv.data.model.WatchState
import com.prizma.iptv.data.remote.XtreamApi
import com.prizma.iptv.data.repo.App
import com.prizma.iptv.data.repo.Session
import com.prizma.iptv.player.PlayerActivity
import com.prizma.iptv.ui.common.PrimaryButton
import com.prizma.iptv.ui.common.SecondaryButton
import com.prizma.iptv.ui.common.focusHighlight
import com.prizma.iptv.ui.theme.Ink
import com.prizma.iptv.ui.theme.PrizmaTheme
import com.prizma.iptv.ui.theme.accent

class MovieActivity : ComponentActivity() {

    companion object {
        fun start(ctx: Context, vodId: String, name: String, icon: String, extension: String) {
            ctx.startActivity(
                Intent(ctx, MovieActivity::class.java).apply {
                    putExtra("vodId", vodId)
                    putExtra("name", name)
                    putExtra("icon", icon)
                    putExtra("ext", extension)
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
                MovieScreen(
                    session = session,
                    vodId = intent.getStringExtra("vodId").orEmpty(),
                    name = intent.getStringExtra("name").orEmpty(),
                    icon = intent.getStringExtra("icon").orEmpty(),
                    extension = intent.getStringExtra("ext").orEmpty(),
                    onBack = { finish() }
                )
            }
        }
    }
}

@Composable
private fun MovieScreen(
    session: Session,
    vodId: String,
    name: String,
    icon: String,
    extension: String,
    onBack: () -> Unit
) {
    val ctx = LocalContext.current
    val tint = accent()

    // Kumandada ekran acilir acilmaz odagin oynat dugmesine oturmasi gerekiyor.
    val playFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(200)
        runCatching { playFocus.requestFocus() }
    }

    var info by remember(vodId) { mutableStateOf<VodInfo?>(null) }
    var loading by remember(vodId) { mutableStateOf(true) }

    val favorites by session.favorites.items.collectAsStateWithLifecycle()
    val history by session.history.items.collectAsStateWithLifecycle()

    val isFavorite = favorites.any { it.section == Section.VOD.name && it.id == vodId }
    val watch = history.firstOrNull { it.section == Section.VOD.name && it.id == vodId }
    val resumeMs = session.history.resumePosition(Section.VOD.name, vodId)

    LaunchedEffect(vodId) {
        info = runCatching { XtreamApi.vodInfo(session.profile, vodId) }.getOrNull()
        loading = false
    }

    val data = info
    val realExtension = (data?.extension ?: "").ifBlank { extension }.ifBlank { "mp4" }
    val rating = data?.rating ?: 0.0

    fun play(fromStart: Boolean) {
        val item = session.movieItem(vodId, name, data?.cover?.ifBlank { icon } ?: icon, realExtension)
        PlayerActivity.start(ctx, listOf(item), 0, forceRestart = fromStart)
    }

    Surface(color = Ink.Bg, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(240.dp)
            ) {
                val backdrop = listOf(data?.backdrop, data?.cover, icon)
                    .firstOrNull { !it.isNullOrBlank() }
                    .orEmpty()
                if (backdrop.isNotEmpty()) {
                    AsyncImage(
                        model = backdrop,
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
                                listOf(Color(0x99000000), Color(0xE60B0D14), Ink.Bg)
                            )
                        )
                )
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp)
                        .focusHighlight(CircleShape)
                        .clip(CircleShape)
                        .background(Color(0x66000000))
                        .clickable(onClick = onBack)
                        .size(38.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("‹", color = Color.White, fontSize = 22.sp)
                }
            }

            Column(Modifier.padding(horizontal = 18.dp)) {

                Text(
                    name,
                    color = Ink.TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (rating > 0.0) {
                        Icon(
                            Icons.Default.Star, null,
                            tint = Ink.Gold,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(Fmt.rating(rating), color = Ink.TextPrimary, fontSize = 12.sp)
                        Spacer(Modifier.width(10.dp))
                    }
                    val meta = listOfNotNull(
                        data?.releaseDate?.take(10)?.takeIf { it.isNotBlank() },
                        data?.duration?.takeIf { it.isNotBlank() },
                        data?.country?.takeIf { it.isNotBlank() }
                    ).joinToString(" · ")
                    if (meta.isNotEmpty()) {
                        Text(meta, color = Ink.TextMuted, fontSize = 12.sp)
                    }
                }

                val genre = data?.genre.orEmpty()
                if (genre.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(genre, color = tint, fontSize = 11.sp)
                }

                Spacer(Modifier.height(18.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PrimaryButton(
                        label = stringResource(
                            if (resumeMs > 0L) R.string.detail_resume else R.string.detail_play
                        ),
                        leading = "▶",
                        modifier = Modifier.weight(1f).focusRequester(playFocus),
                        onClick = { play(fromStart = false) }
                    )
                    if (resumeMs > 0L) {
                        SecondaryButton(stringResource(R.string.detail_restart)) {
                            play(fromStart = true)
                        }
                    }
                    Box(
                        Modifier
                            .focusHighlight(RoundedCornerShape(10.dp))
                            .clip(RoundedCornerShape(10.dp))
                            .background(Ink.Surface)
                            .clickable {
                                session.favorites.toggle(
                                    com.prizma.iptv.data.model.SavedItem(
                                        section = Section.VOD.name,
                                        id = vodId,
                                        name = name,
                                        icon = data?.cover?.ifBlank { icon } ?: icon,
                                        extension = realExtension,
                                        rating = rating
                                    )
                                )
                            }
                            .padding(horizontal = 18.dp, vertical = 13.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Star, null,
                            tint = if (isFavorite) Ink.Gold else Ink.TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                if (resumeMs > 0L) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.detail_resume_at, Fmt.duration(resumeMs)),
                        color = Ink.TextMuted,
                        fontSize = 11.sp
                    )
                }

                if (watch != null && watch.finished) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.detail_watched),
                        color = tint,
                        fontSize = 11.sp,
                        modifier = Modifier
                            .focusHighlight(RoundedCornerShape(6.dp))
                            .clickable {
                                session.history.markUnwatched(Section.VOD.name, vodId)
                            }
                            .padding(vertical = 4.dp)
                    )
                } else if (watch == null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.detail_mark_watched),
                        color = Ink.TextMuted,
                        fontSize = 11.sp,
                        modifier = Modifier
                            .focusHighlight(RoundedCornerShape(6.dp))
                            .clickable {
                                session.history.markWatched(
                                    WatchState(
                                        section = Section.VOD.name,
                                        id = vodId,
                                        name = name,
                                        icon = icon,
                                        extension = realExtension,
                                        duration = 1L
                                    )
                                )
                            }
                            .padding(vertical = 4.dp)
                    )
                }

                val trailer = data?.youtube.orEmpty()
                if (trailer.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    SecondaryButton(stringResource(R.string.detail_trailer)) {
                        openTrailer(ctx, trailer)
                    }
                }

                Spacer(Modifier.height(20.dp))

                when {
                    loading -> Box(Modifier.fillMaxWidth(), Alignment.Center) {
                        CircularProgressIndicator(color = tint)
                    }

                    data == null -> Text(
                        stringResource(R.string.detail_no_info),
                        color = Ink.TextMuted,
                        fontSize = 12.sp
                    )

                    else -> {
                        if (data.plot.isNotBlank()) {
                            InfoBlock(stringResource(R.string.detail_plot), data.plot)
                        }
                        if (data.director.isNotBlank()) {
                            InfoBlock(stringResource(R.string.detail_director), data.director)
                        }
                        if (data.cast.isNotBlank()) {
                            InfoBlock(stringResource(R.string.detail_cast), data.cast)
                        }
                    }
                }

                Spacer(Modifier.height(36.dp))
            }
        }
    }
}

@Composable
private fun InfoBlock(label: String, value: String) {
    val tint = accent()
    Column(Modifier.padding(bottom = 14.dp)) {
        Text(label, color = tint, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(value, color = Ink.TextSecondary, fontSize = 12.sp, lineHeight = 18.sp)
    }
}

/** Fragman bağlantısını cihazın YouTube uygulamasına ya da tarayıcıya devreder. */
private fun openTrailer(ctx: Context, youtube: String) {
    val id = youtube.substringAfterLast('/').substringAfter("v=").substringBefore('&')
    if (id.isBlank()) return
    val uri = Uri.parse("https://www.youtube.com/watch?v=$id")
    runCatching {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, uri))
    }
}
