package com.prizma.iptv.player

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.RenderersFactory
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import com.prizma.iptv.core.Http
import com.prizma.iptv.data.local.DecoderMode
import com.prizma.iptv.data.local.Settings
import com.prizma.iptv.data.model.PlayItem
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.NextRenderersFactory

/**
 * ExoPlayer kurulumu.
 *
 * IPTV yayınlarında iki nokta kritik:
 *  1. Ses kodekleri. Birçok kanal AC3 / E-AC3 / DTS ses taşıyor ve pek çok
 *     Android cihazda bunlar için donanım çözücü yok. FFmpeg tabanlı yazılım
 *     çözücüler devrede olduğu için "görüntü var, ses yok" sorunu ortadan kalkar.
 *  2. Tampon. MPEG-TS akışlarında zaman damgası araması ve erişim birimi
 *     tespiti açık olmazsa bazı paneller ilk saniyelerde takılıyor.
 */
@UnstableApi
object PlayerEngine {

    fun create(context: Context, userAgent: String): ExoPlayer {
        val app = context.applicationContext

        val trackSelector = DefaultTrackSelector(app).apply {
            parameters = buildUponParameters()
                .setTunnelingEnabled(Settings.tunneling)
                .build()
        }

        return ExoPlayer.Builder(app, renderersFactory(app))
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl())
            .setMediaSourceFactory(mediaSourceFactory(app, userAgent))
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(30_000)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setHandleAudioBecomingNoisy(true)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                /* handleAudioFocus = */ true
            )
            .build()
    }

    private fun renderersFactory(context: Context): RenderersFactory =
        NextRenderersFactory(context)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(
                if (Settings.decoderMode == DecoderMode.SOFTWARE) {
                    // Yazılım çözücü önce denenir.
                    DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
                } else {
                    // Donanım önce, yetmezse yazılıma düşülür.
                    DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
                }
            )

    private fun loadControl(): DefaultLoadControl {
        val seconds = Settings.bufferSeconds.coerceIn(5, 240)
        val minBuffer = seconds * 1000
        val maxBuffer = (seconds * 2000).coerceAtLeast(minBuffer)
        return DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                minBuffer,
                maxBuffer,
                /* bufferForPlaybackMs = */ 1_500,
                /* bufferForPlaybackAfterRebufferMs = */ 3_000
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .setBackBuffer(30_000, true)
            .build()
    }

    private fun mediaSourceFactory(context: Context, userAgent: String): DefaultMediaSourceFactory {
        val http = OkHttpDataSource.Factory(Http.media)
            .setUserAgent(userAgent.ifBlank { Http.DEFAULT_USER_AGENT })
            .setDefaultRequestProperties(
                mapOf("Accept" to "*/*", "Connection" to "keep-alive")
            )

        // Yerel altyazı dosyaları için dosya/içerik şemaları da desteklenir.
        val dataSource = DefaultDataSource.Factory(context, http)

        val extractors = DefaultExtractorsFactory()
            .setTsExtractorFlags(DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS)
            .setTsExtractorTimestampSearchBytes(1500 * 188)

        return DefaultMediaSourceFactory(dataSource, extractors)
    }

    /** Bildirimde ve kilit ekranında görünecek üst veriyi hazırlar. */
    fun mediaItemOf(item: PlayItem): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(item.title)
            .setArtist(item.subtitle.ifBlank { null })
            .setArtworkUri(item.icon.takeIf { it.isNotBlank() }?.let(android.net.Uri::parse))
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .build()

        return MediaItem.Builder()
            .setUri(item.url)
            .setMediaId(item.sectionKey() + "/" + item.id)
            .setMediaMetadata(metadata)
            .build()
    }

    /**
     * Canlı yayınlarda panel bazen .ts, bazen .m3u8 servis ediyor.
     * Biri açılmazsa diğeri denenir.
     */
    fun alternateUrl(url: String): String? = when {
        url.endsWith(".ts", true) -> url.dropLast(3) + ".m3u8"
        url.endsWith(".m3u8", true) -> url.dropLast(5) + ".ts"
        else -> null
    }
}
