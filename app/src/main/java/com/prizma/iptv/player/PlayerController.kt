package com.prizma.iptv.player

import android.content.Context
import android.net.Uri
import android.view.KeyEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.prizma.iptv.R
import com.prizma.iptv.data.local.AspectMode
import com.prizma.iptv.data.local.Settings
import com.prizma.iptv.data.model.PlayItem
import com.prizma.iptv.data.model.PlayKind
import com.prizma.iptv.data.model.WatchState
import com.prizma.iptv.data.repo.Session
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val MAX_RECONNECT = 8
private const val CHANNEL_INPUT_TIMEOUT = 2_000L
private const val PROGRESS_INTERVAL = 5_000L

/**
 * Oynatıcı ekranının tüm durumu ve davranışı.
 *
 * Compose tarafı yalnızca çizim yapar; kanal geçişi, yeniden bağlanma,
 * ilerleme kaydı ve kumanda tuşları burada toplanır.
 */
@UnstableApi
class PlayerController(
    private val context: Context,
    val player: ExoPlayer,
    val session: Session?,
    private val scope: CoroutineScope,
    private val onExit: () -> Unit
) {

    var items by mutableStateOf<List<PlayItem>>(emptyList())
        private set

    var currentIndex by mutableIntStateOf(0)
        private set

    private var previousIndex = -1

    val current: PlayItem? get() = items.getOrNull(currentIndex)
    val hasQueue: Boolean get() = items.size > 1

    // ---- arayüz durumu ----
    var controlsVisible by mutableStateOf(true)
    var locked by mutableStateOf(false)
    var showChannels by mutableStateOf(false)
    var showSettings by mutableStateOf(false)
    var showStats by mutableStateOf(false)
    var notice by mutableStateOf("")
    var errorText by mutableStateOf("")
    var buffering by mutableStateOf(false)
    var playing by mutableStateOf(true)
    var reconnectAttempt by mutableIntStateOf(0)

    var speed by mutableFloatStateOf(1f)
    var aspect by mutableStateOf(Settings.aspectMode)
    var zoom by mutableFloatStateOf(1f)
    var subtitleScale by mutableFloatStateOf(Settings.subtitleScale)
    var subtitleBackground by mutableStateOf(Settings.subtitleBackground)
    var tracks by mutableStateOf<Tracks?>(null)

    /** Videonun kendi en-boy oranı; zorlanmış oranlardan geri dönerken gerekiyor. */
    var naturalRatio by mutableFloatStateOf(0f)
        private set

    var videoWidth by mutableIntStateOf(0)
        private set

    var videoHeight by mutableIntStateOf(0)
        private set

    var channelInput by mutableStateOf("")
    var sleepMinutes by mutableIntStateOf(0)
    var sleepRemainingMs by mutableLongStateOf(0L)

    var volumeOverlay by mutableStateOf<Float?>(null)
    var brightnessOverlay by mutableStateOf<Float?>(null)
    var seekPreviewMs by mutableStateOf<Long?>(null)
    var speedBoost by mutableStateOf(false)

    private var reconnectJob: Job? = null
    private var channelInputJob: Job? = null
    private var sleepJob: Job? = null
    private var hideJob: Job? = null
    private var noticeJob: Job? = null
    private var triedAlternate = false

    // ------------------------------------------------------------------ kuyruk

    fun setQueue(queue: List<PlayItem>, startIndex: Int, forceRestart: Boolean) {
        if (queue.isEmpty()) {
            onExit()
            return
        }
        items = queue
        currentIndex = startIndex.coerceIn(0, queue.lastIndex)
        previousIndex = -1
        triedAlternate = false

        val start = queue[currentIndex]
        val resumeMs = if (!forceRestart && start.resumable) {
            session?.history?.resumePosition(start.sectionKey(), start.id) ?: 0L
        } else 0L

        player.setMediaItems(queue.map(PlayerEngine::mediaItemOf))
        player.repeatMode =
            if (!Settings.autoNext && !start.isLive) Player.REPEAT_MODE_ONE
            else Player.REPEAT_MODE_OFF
        player.seekTo(currentIndex, if (resumeMs > 0L) resumeMs else C.TIME_UNSET)
        player.playWhenReady = true
        player.prepare()

        if (resumeMs > 0L) showNotice(context.getString(R.string.player_resume_notice))
        rememberLastLive()
    }

    /** Sıradaki / önceki öğe. Liste başına gelince sona sarar. */
    fun jump(delta: Int) {
        if (!hasQueue) return
        val size = items.size
        val target = ((currentIndex + delta) % size + size) % size
        playIndex(target)
    }

    fun playIndex(index: Int) {
        if (index !in items.indices) return
        saveProgress()
        previousIndex = currentIndex
        currentIndex = index
        triedAlternate = false
        reconnectAttempt = 0
        errorText = ""
        player.seekTo(index, C.TIME_UNSET)
        player.playWhenReady = true
        player.prepare()
        showNotice(items[index].title)
        rememberLastLive()
    }

    /** Kumandadaki "önceki kanal" davranışı. */
    fun toggleLastChannel() {
        if (previousIndex in items.indices) playIndex(previousIndex)
    }

    private fun rememberLastLive() {
        val item = current ?: return
        if (item.kind != PlayKind.LIVE) return
        Settings.lastLiveItem = item.toJson()
        session?.history?.record(
            WatchState(
                section = item.sectionKey(),
                id = item.id,
                name = item.title,
                icon = item.icon,
                extension = item.extension
            )
        )
    }

    // ------------------------------------------------------------------ olaylar

    fun listener(): Player.Listener = object : Player.Listener {

        override fun onPlayerError(error: PlaybackException) {
            handleError(error)
        }

        override fun onTracksChanged(newTracks: Tracks) {
            tracks = newTracks
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            videoWidth = videoSize.width
            videoHeight = videoSize.height
            naturalRatio = if (videoSize.height > 0) {
                videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
            } else {
                0f
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            playing = isPlaying
        }

        override fun onPlaybackStateChanged(state: Int) {
            buffering = state == Player.STATE_BUFFERING
            if (state == Player.STATE_READY) {
                reconnectAttempt = 0
                triedAlternate = false
                errorText = ""
            }
            if (state == Player.STATE_ENDED) {
                saveProgress()
                if (!hasQueue) onExit()
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val index = player.currentMediaItemIndex
            if (index != currentIndex) {
                previousIndex = currentIndex
                currentIndex = index
                triedAlternate = false
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                    showNotice(items.getOrNull(index)?.title.orEmpty())
                }
                rememberLastLive()
            }
        }
    }

    private fun handleError(error: PlaybackException) {
        val item = current ?: return

        // 1) Panel .ts vermiyorsa .m3u8 (ya da tersi) denenir.
        if (item.isLive && !triedAlternate) {
            val alternate = PlayerEngine.alternateUrl(item.url)
            if (alternate != null) {
                triedAlternate = true
                val updated = item.copy(url = alternate)
                items = items.toMutableList().also { it[currentIndex] = updated }
                showNotice(context.getString(R.string.player_alt_format))
                player.replaceMediaItem(currentIndex, PlayerEngine.mediaItemOf(updated))
                player.prepare()
                player.play()
                return
            }
        }

        // 2) Ağ kaynaklı kopmalarda artan bekleme ile yeniden bağlan.
        if (Settings.autoReconnect && reconnectAttempt < MAX_RECONNECT) {
            reconnectAttempt++
            val resumeAt = if (item.resumable) player.currentPosition else C.TIME_UNSET
            showNotice(context.getString(R.string.player_reconnecting, reconnectAttempt))
            reconnectJob?.cancel()
            reconnectJob = scope.launch {
                delay((1_200L * reconnectAttempt).coerceAtMost(12_000L))
                if (resumeAt != C.TIME_UNSET && resumeAt > 0L) {
                    player.seekTo(currentIndex, resumeAt)
                } else {
                    player.seekToDefaultPosition(currentIndex)
                }
                player.prepare()
                player.play()
            }
            return
        }

        errorText = context.getString(R.string.player_error, error.errorCodeName)
    }

    fun retryNow() {
        errorText = ""
        reconnectAttempt = 0
        triedAlternate = false
        player.prepare()
        player.play()
    }

    // ------------------------------------------------------------------ ilerleme

    fun startProgressLoop() {
        scope.launch {
            while (true) {
                delay(PROGRESS_INTERVAL)
                saveProgress()
            }
        }
    }

    fun saveProgress() {
        val item = current ?: return
        val history = session?.history ?: return
        if (!item.resumable) return
        val duration = player.duration
        if (duration <= 0L) return
        history.record(
            WatchState(
                section = item.sectionKey(),
                id = item.id,
                name = if (item.subtitle.isBlank()) item.title else item.title + " · " + item.subtitle,
                icon = item.icon,
                extension = item.extension,
                position = player.currentPosition,
                duration = duration,
                parentId = item.parentId
            )
        )
    }

    // ------------------------------------------------------------------ arayüz

    fun showNotice(text: String) {
        if (text.isBlank()) return
        notice = text
        noticeJob?.cancel()
        noticeJob = scope.launch {
            delay(2_600)
            notice = ""
        }
    }

    fun toggleControls() {
        if (locked) return
        controlsVisible = !controlsVisible
        if (controlsVisible) scheduleHide()
    }

    fun revealControls() {
        if (locked) return
        controlsVisible = true
        scheduleHide()
    }

    private fun scheduleHide() {
        hideJob?.cancel()
        hideJob = scope.launch {
            delay(5_000)
            if (!showChannels && !showSettings) controlsVisible = false
        }
    }

    fun applyLock(value: Boolean) {
        locked = value
        controlsVisible = !value
        showNotice(
            context.getString(if (value) R.string.player_locked else R.string.player_unlocked)
        )
    }

    fun applySpeed(value: Float) {
        speed = value
        player.playbackParameters = PlaybackParameters(value)
    }

    fun applySpeedBoost(active: Boolean) {
        if (current?.isLive == true) return
        if (active == speedBoost) return
        speedBoost = active
        player.playbackParameters = PlaybackParameters(if (active) 2f else speed)
    }

    fun cycleAspect() {
        val modes = AspectMode.entries
        aspect = modes[(modes.indexOf(aspect) + 1) % modes.size]
        Settings.aspectMode = aspect
    }

    fun applyAspectMode(mode: AspectMode) {
        aspect = mode
        Settings.aspectMode = mode
    }

    fun applySubtitleScale(value: Float) {
        subtitleScale = value
        Settings.subtitleScale = value
    }

    fun applySubtitleBackground(value: Boolean) {
        subtitleBackground = value
        Settings.subtitleBackground = value
    }

    // ------------------------------------------------------------------ uyku

    fun setSleep(minutes: Int) {
        sleepJob?.cancel()
        sleepMinutes = minutes
        if (minutes <= 0) {
            sleepRemainingMs = 0L
            showNotice(context.getString(R.string.player_sleep_off))
            return
        }
        sleepRemainingMs = minutes * 60_000L
        showNotice(
            context.getString(
                R.string.player_sleep_set,
                context.getString(R.string.player_sleep_minutes, minutes)
            )
        )
        sleepJob = scope.launch {
            while (sleepRemainingMs > 0L) {
                delay(1_000)
                sleepRemainingMs -= 1_000
            }
            player.pause()
            showNotice(context.getString(R.string.player_sleep_fired))
            onExit()
        }
    }

    // ------------------------------------------------------------------ kanal numarası

    fun pushDigit(digit: Int) {
        if (!hasQueue) return
        channelInput = (channelInput + digit).takeLast(4)
        channelInputJob?.cancel()
        channelInputJob = scope.launch {
            delay(CHANNEL_INPUT_TIMEOUT)
            resolveChannelInput()
        }
    }

    private fun resolveChannelInput() {
        val typed = channelInput
        channelInput = ""
        val number = typed.toIntOrNull() ?: return
        val byNumber = items.indexOfFirst { it.number == number }
        val target = if (byNumber >= 0) byNumber else number - 1
        if (target in items.indices) {
            playIndex(target)
        } else {
            showNotice(context.getString(R.string.player_channel_not_found, typed))
        }
    }

    // ------------------------------------------------------------------ parçalar

    fun applyTrack(type: Int, option: TrackOption?) {
        val available = tracks ?: return
        val builder = player.trackSelectionParameters.buildUpon()
        builder.clearOverridesOfType(type)
        if (option == null) {
            builder.setTrackTypeDisabled(type, true)
        } else {
            builder.setTrackTypeDisabled(type, false)
            val group = available.groups.getOrNull(option.groupIndex) ?: return
            builder.addOverride(TrackSelectionOverride(group.mediaTrackGroup, option.trackIndex))
        }
        player.trackSelectionParameters = builder.build()
    }

    /** Cihazdan seçilen altyazı dosyasını mevcut öğeye ekler. */
    fun attachSubtitle(uri: Uri, displayName: String) {
        val item = current ?: return
        val mime = when {
            displayName.endsWith(".vtt", true) -> MimeTypes.TEXT_VTT
            displayName.endsWith(".ass", true) || displayName.endsWith(".ssa", true) ->
                MimeTypes.TEXT_SSA
            displayName.endsWith(".ttml", true) || displayName.endsWith(".xml", true) ->
                MimeTypes.APPLICATION_TTML
            else -> MimeTypes.APPLICATION_SUBRIP
        }
        val subtitle = MediaItem.SubtitleConfiguration.Builder(uri)
            .setMimeType(mime)
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
            .build()

        val position = player.currentPosition
        val rebuilt = PlayerEngine.mediaItemOf(item)
            .buildUpon()
            .setSubtitleConfigurations(listOf(subtitle))
            .build()

        player.replaceMediaItem(currentIndex, rebuilt)
        player.prepare()
        player.seekTo(currentIndex, position)
        player.play()
        showNotice(context.getString(R.string.player_subtitle_loaded))
    }

    // ------------------------------------------------------------------ kumanda

    /** true dönerse tuş burada tüketilmiştir. */
    fun handleKey(keyCode: Int): Boolean {
        if (showSettings || showChannels) return false

        if (locked) {
            // Kilitliyken yalnızca geri tuşu geçer.
            return keyCode != KeyEvent.KEYCODE_BACK
        }

        if (keyCode in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9) {
            pushDigit(keyCode - KeyEvent.KEYCODE_0)
            revealControls()
            return true
        }

        val live = current?.isLive == true

        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                toggleControls()
                true
            }

            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> {
                if (hasQueue) {
                    jump(-1)
                    true
                } else false
            }

            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                if (hasQueue) {
                    jump(1)
                    true
                } else false
            }

            KeyEvent.KEYCODE_DPAD_RIGHT -> when {
                live && hasQueue -> {
                    showChannels = true
                    true
                }
                !live -> {
                    player.seekForward()
                    revealControls()
                    true
                }
                else -> false
            }

            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (!live) {
                    player.seekBack()
                    revealControls()
                    true
                } else false
            }

            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_INFO -> {
                showSettings = true
                true
            }

            KeyEvent.KEYCODE_LAST_CHANNEL -> {
                toggleLastChannel()
                true
            }

            KeyEvent.KEYCODE_CAPTIONS -> {
                cycleSubtitle()
                true
            }

            KeyEvent.KEYCODE_GUIDE -> {
                showChannels = true
                true
            }

            else -> false
        }
    }

    /** Altyazıyı kapalı ve mevcut diller arasında sırayla dolaştırır. */
    fun cycleSubtitle() {
        val available = tracks ?: return
        val options = collectTracks(available, C.TRACK_TYPE_TEXT)
        if (options.isEmpty()) return
        val selected = options.indexOfFirst { it.selected }
        val next = selected + 1
        if (next >= options.size) {
            applyTrack(C.TRACK_TYPE_TEXT, null)
            showNotice(context.getString(R.string.player_subtitle_off))
        } else {
            applyTrack(C.TRACK_TYPE_TEXT, options[next])
            showNotice(options[next].label)
        }
    }

    fun release() {
        saveProgress()
        reconnectJob?.cancel()
        channelInputJob?.cancel()
        sleepJob?.cancel()
        hideJob?.cancel()
        noticeJob?.cancel()
    }
}
