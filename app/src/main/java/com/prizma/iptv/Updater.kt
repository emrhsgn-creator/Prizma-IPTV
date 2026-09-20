package com.prizma.iptv

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * GitHub Releases uzerinden uygulama ici guncelleme.
 *
 * Actions artifact'lari indirmek GitHub girisi ister, bu yuzden uygulama
 * onlara ulasamaz. Release varliklari ise herkese acik; surumler oraya
 * yayinlaniyor ve buradan indiriliyor.
 *
 * Surum karsilastirmasi APK'nin adindaki versionCode ile yapiliyor
 * (prizma-0.6.0-35.apk). Surum ADI ile karsilastirma yanilticidir:
 * "0.5.10" metin olarak "0.5.9"dan kucuk cikar.
 */
private const val RELEASES_URL =
    "https://api.github.com/repos/emrhsgn-creator/Prizma-IPTV/releases/latest"

/** Yayindaki surum. */
data class Release(
    val versionName: String,
    val versionCode: Int,
    val apkUrl: String,
    val notes: String
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpToDate(val versionName: String) : UpdateState
    data class Available(val release: Release) : UpdateState
    data class Downloading(val percent: Int) : UpdateState
    data class Ready(val release: Release, val file: File) : UpdateState
    data class NeedsPermission(val release: Release, val file: File) : UpdateState
    data class Failed(val message: String) : UpdateState
}

object Updater {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    fun installedVersionCode(ctx: Context): Int = runCatching {
        val info = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode.toInt()
        } else {
            @Suppress("DEPRECATION")
            info.versionCode
        }
    }.getOrDefault(0)

    fun installedVersionName(ctx: Context): String = runCatching {
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName.orEmpty()
    }.getOrDefault("?")

    /** Son yayinlanan surumu sorar. */
    suspend fun check(): Release = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url(RELEASES_URL)
            .header("User-Agent", "PrizmaIPTV/1.0")
            .header("Accept", "application/vnd.github+json")
            .build()
        client.newCall(req).execute().use { res ->
            if (res.code == 404) throw Exception("Henüz yayınlanmış bir sürüm yok.")
            if (res.code == 403) throw Exception("GitHub istek sınırı doldu, biraz sonra dene.")
            if (!res.isSuccessful) throw Exception("Sunucu hatası: HTTP ${res.code}")
            val root = try {
                JSONObject(res.body?.string().orEmpty())
            } catch (e: Exception) {
                throw Exception("Sürüm bilgisi okunamadı.")
            }
            val name = root.optString("tag_name").removePrefix("v").ifEmpty { "?" }
            val notes = root.optString("body", "").trim()
            val assets = root.optJSONArray("assets")
                ?: throw Exception("Sürümde dosya yok.")

            // Ad kalibi: prizma-<ad>-<kod>.apk
            val codeFromName = Regex("-(\\d+)\\.apk$")
            for (i in 0 until assets.length()) {
                val a = assets.optJSONObject(i) ?: continue
                val fileName = a.optString("name")
                if (!fileName.endsWith(".apk", true)) continue
                val code = codeFromName.find(fileName)?.groupValues?.get(1)?.toIntOrNull()
                    ?: continue
                val url = a.optString("browser_download_url")
                if (url.isNotEmpty()) return@use Release(name, code, url, notes)
            }
            throw Exception("Sürümde uygun APK bulunamadı.")
        }
    }

    /** APK'yi onbellege indirir. Yuzdeyi arayan tarafa bildirir. */
    suspend fun download(
        ctx: Context,
        release: Release,
        onProgress: (Int) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val dir = File(ctx.cacheDir, "updates")
        dir.mkdirs()
        // Eski indirmeler yer kaplamasin; cihazda disk dar.
        dir.listFiles()?.forEach { runCatching { it.delete() } }

        val out = File(dir, "prizma-${release.versionCode}.apk")
        val req = Request.Builder()
            .url(release.apkUrl)
            .header("User-Agent", "PrizmaIPTV/1.0")
            .build()
        client.newCall(req).execute().use { res ->
            if (!res.isSuccessful) throw Exception("İndirilemedi: HTTP ${res.code}")
            val body = res.body ?: throw Exception("Sunucu boş yanıt verdi.")
            val total = body.contentLength()
            body.byteStream().use { input ->
                out.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    var lastPct = -1
                    while (true) {
                        val read = input.read(buf)
                        if (read < 0) break
                        output.write(buf, 0, read)
                        done += read
                        if (total > 0) {
                            val pct = ((done * 100) / total).toInt()
                            if (pct != lastPct) {
                                lastPct = pct
                                onProgress(pct)
                            }
                        }
                    }
                }
            }
        }
        if (out.length() <= 0L) throw Exception("İndirilen dosya boş.")
        out
    }

    /**
     * Bilinmeyen kaynaktan kuruluma izin verilmis mi.
     *
     * Android 8'den itibaren bu izin uygulama basina veriliyor ve
     * kullanicinin bir kez ayarlardan acmasi gerekiyor.
     */
    fun canInstall(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            ctx.packageManager.canRequestPackageInstalls()

    /**
     * Izin ekranini acar.
     *
     * Android TV'de ACTION_MANAGE_UNKNOWN_APP_SOURCES her zaman
     * cozumlenmiyor; cozumlenmezse genel guvenlik ayarlarina dusuyoruz.
     */
    fun openInstallPermission(ctx: Context): Boolean {
        val candidates = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                add(
                    Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${ctx.packageName}")
                    )
                )
                add(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES))
            }
            add(Intent(Settings.ACTION_SECURITY_SETTINGS))
            add(Intent(Settings.ACTION_SETTINGS))
        }
        for (i in candidates) {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (i.resolveActivity(ctx.packageManager) != null) {
                val ok = runCatching { ctx.startActivity(i) }.isSuccess
                if (ok) return true
            }
        }
        return false
    }

    /** Android'in kurulum ekranini acar. */
    fun install(ctx: Context, file: File) {
        val uri = FileProvider.getUriForFile(
            ctx, "${ctx.packageName}.fileprovider", file
        )
        val i = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(i)
    }
}
