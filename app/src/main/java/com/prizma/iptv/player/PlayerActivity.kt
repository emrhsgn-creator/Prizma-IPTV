package com.prizma.iptv.player

import android.Manifest
import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.prizma.iptv.core.LocaleHelper
import com.prizma.iptv.data.local.Settings
import com.prizma.iptv.data.model.PlayItem
import com.prizma.iptv.data.repo.App
import com.prizma.iptv.ui.theme.PrizmaTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

@UnstableApi
class PlayerActivity : ComponentActivity() {

    companion object {
        private const val EXTRA_FALLBACK = "fallback_item"

        /**
         * Oynatıcıyı açar. Liste bellek üzerinden aktarılır; Intent yalnızca
         * süreç öldürülürse kullanılacak tek öğeyi taşır.
         */
        fun start(
            ctx: Context,
            items: List<PlayItem>,
            startIndex: Int,
            forceRestart: Boolean = false
        ) {
            if (items.isEmpty()) return
            val index = startIndex.coerceIn(0, items.lastIndex)
            PlaybackRequest.offer(items, index, forceRestart)
            ctx.startActivity(
                Intent(ctx, PlayerActivity::class.java).apply {
                    putExtra(EXTRA_FALLBACK, items[index].toJson())
                }
            )
        }
    }

    private lateinit var player: ExoPlayer
    private lateinit var controller: PlayerController
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var listener: Player.Listener? = null
    private var serviceStarted = false

    private var inPip by mutableStateOf(false)
    private var landscapeLocked by mutableStateOf(true)

    private val subtitlePicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            controller.attachSubtitle(uri, uri.lastPathSegment.orEmpty())
        }
    }

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* reddedilse de oynatma sürer, yalnızca bildirim görünmez */ }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        goFullscreen()
        applyOrientation()

        val session = App.ensureSession()
        player = PlayerEngine.create(this, session?.profile?.userAgent.orEmpty())
        PlayerHolder.attach(player)

        controller = PlayerController(
            context = this,
            player = player,
            session = session,
            scope = scope,
            onExit = { finish() }
        )
        listener = controller.listener().also { player.addListener(it) }

        loadQueue(intent)
        controller.startProgressLoop()
        maybeStartService()

        setContent {
            PrizmaTheme {
                PlayerScreen(
                    controller = controller,
                    inPip = inPip,
                    landscapeLocked = landscapeLocked,
                    onToggleOrientation = { toggleOrientation() },
                    onEnterPip = { enterPip() },
                    onPickSubtitle = { subtitlePicker.launch(arrayOf("*/*")) },
                    onExit = { finish() }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        loadQueue(intent)
    }

    private fun loadQueue(source: Intent?) {
        val pending = PlaybackRequest.take()
        if (pending != null) {
            controller.setQueue(pending.first, pending.second, pending.third)
            return
        }
        // Süreç yeniden kurulduysa yalnızca son öğe elimizde kalır.
        val fallback = PlayItem.fromJson(source?.getStringExtra(EXTRA_FALLBACK))
        if (fallback != null) {
            controller.setQueue(listOf(fallback), 0, false)
        } else {
            finish()
        }
    }

    private fun maybeStartService() {
        if (!Settings.backgroundAudio) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        runCatching {
            startService(Intent(this, PlaybackService::class.java))
            serviceStarted = true
        }
    }

    private fun stopService() {
        if (!serviceStarted) return
        runCatching { stopService(Intent(this, PlaybackService::class.java)) }
        serviceStarted = false
    }

    // ------------------------------------------------------------------ ekran

    private fun goFullscreen() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controllerCompat = WindowInsetsControllerCompat(window, window.decorView)
        controllerCompat.hide(WindowInsetsCompat.Type.systemBars())
        controllerCompat.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    private fun applyOrientation() {
        requestedOrientation = if (landscapeLocked) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        }
    }

    private fun toggleOrientation() {
        landscapeLocked = !landscapeLocked
        applyOrientation()
    }

    // ------------------------------------------------------------------ PiP

    private fun pipParams(): PictureInPictureParams? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
        val size = player.videoSize
        val ratio = if (size.width > 0 && size.height > 0) {
            Rational(size.width, size.height)
        } else {
            Rational(16, 9)
        }
        // Sistem 2.39:1 üstü / 1:2.39 altı oranları reddediyor.
        val safe = when {
            ratio.toFloat() > 2.39f -> Rational(239, 100)
            ratio.toFloat() < 0.42f -> Rational(100, 239)
            else -> ratio
        }
        val builder = PictureInPictureParams.Builder().setAspectRatio(safe)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(Settings.autoPip && player.isPlaying)
            builder.setSeamlessResizeEnabled(true)
        }
        return builder.build()
    }

    private fun enterPip() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (!packageManager.hasSystemFeature(
                android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE
            )
        ) return
        val params = pipParams() ?: return
        runCatching { enterPictureInPictureMode(params) }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Android 12 ve üstünde otomatik geçiş sistem tarafından yapılır.
        if (Build.VERSION.SDK_INT in Build.VERSION_CODES.O until Build.VERSION_CODES.S &&
            Settings.autoPip && player.isPlaying && !controller.locked
        ) {
            enterPip()
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip = isInPictureInPictureMode
        if (isInPictureInPictureMode) {
            controller.controlsVisible = false
            controller.showChannels = false
            controller.showSettings = false
        }
    }

    // ------------------------------------------------------------------ yaşam döngüsü

    override fun onStart() {
        super.onStart()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching { pipParams()?.let { setPictureInPictureParams(it) } }
        }
    }

    override fun onStop() {
        super.onStop()
        controller.saveProgress()
        // Arka planda ses istenmiyorsa ya da PiP'te değilsek duraklat.
        if (!Settings.backgroundAudio && !inPip) {
            player.pause()
        }
    }

    override fun onDestroy() {
        controller.release()
        listener?.let { player.removeListener(it) }
        // Servis medya oturumuyla oynatıcıya tutunuyor. Durdurma isteği
        // gönderilir; temizliği hangi taraf önce tamamlarsa o yapar.
        stopService()
        PlayerHolder.releaseAll()
        scope.cancel()
        super.onDestroy()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && !inPip) {
            if (controller.handleKey(event.keyCode)) return true
        }
        return super.dispatchKeyEvent(event)
    }
}
