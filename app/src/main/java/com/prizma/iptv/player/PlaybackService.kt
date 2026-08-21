package com.prizma.iptv.player

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.prizma.iptv.data.model.PlayItem

/**
 * Oynatıcı örneği süreç genelinde tek. Etkinlik yüzeyi doğrudan bu örneğe
 * bağlandığı için görüntü aynı süreçte çizilir; servis yalnızca oturumu,
 * bildirimi ve kulaklık/Bluetooth tuşlarını yönetir.
 */
object PlayerHolder {

    @Volatile
    var player: ExoPlayer? = null
        private set

    @Volatile
    var session: MediaSession? = null

    fun attach(instance: ExoPlayer) {
        player = instance
    }

    /**
     * Oturumu ve oynatıcıyı bu sırayla bırakır ve tekrar çağrılmaya dayanıklıdır.
     * Hem etkinlik hem servis burayı çağırır; hangisi önce biterse o temizler,
     * böylece "serbest bırakılmış oynatıcı üzerinde oturum" çökmesi olmaz.
     */
    @Synchronized
    fun releaseAll() {
        session?.let { runCatching { it.release() } }
        session = null
        val instance = player
        player = null
        instance?.let { runCatching { it.release() } }
    }
}

/**
 * Oynatıcıya gönderilen sıra. On binlerce kanallı listeler Intent'in 1 MB
 * sınırını aştığı için liste bellek üzerinden aktarılır; Intent yalnızca
 * süreç öldürülüp geri yüklendiğinde kullanılacak tek öğeyi taşır.
 */
object PlaybackRequest {

    @Volatile
    private var pending: Pending? = null

    private data class Pending(
        val items: List<PlayItem>,
        val startIndex: Int,
        val forceRestart: Boolean
    )

    fun offer(items: List<PlayItem>, startIndex: Int, forceRestart: Boolean) {
        pending = Pending(items, startIndex, forceRestart)
    }

    /** Bir kez okunur ve temizlenir. */
    fun take(): Triple<List<PlayItem>, Int, Boolean>? {
        val current = pending ?: return null
        pending = null
        return Triple(current.items, current.startIndex, current.forceRestart)
    }
}

/**
 * Arka planda ses, bildirim kontrolleri ve medya tuşları.
 * Oturum yalnızca oynatıcı hazırken kurulur.
 */
@UnstableApi
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = PlayerHolder.player
        if (player == null) {
            stopSelf()
            return
        }
        session = MediaSession.Builder(this, player)
            .setSessionActivity(playerIntent())
            .build()
        PlayerHolder.session = session
    }

    private fun playerIntent(): PendingIntent {
        val intent = Intent(this, PlayerActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                PendingIntent.FLAG_IMMUTABLE
            } else {
                0
            }
        return PendingIntent.getActivity(this, 0, intent, flags)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Kullanıcı uygulamayı son kullanılanlardan kaydırdıysa oynatmayı sürdürme.
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        session = null
        PlayerHolder.releaseAll()
        super.onDestroy()
    }
}
