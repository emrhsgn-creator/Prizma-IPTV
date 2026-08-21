package com.prizma.iptv.core

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import coil.util.DebugLogger
import com.prizma.iptv.BuildConfig
import com.prizma.iptv.data.local.Settings
import com.prizma.iptv.data.repo.App
import okhttp3.OkHttpClient
import java.util.Locale

class PrizmaApp : Application(), ImageLoaderFactory {

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleHelper.wrap(base))
    }

    override fun onCreate() {
        super.onCreate()
        App.init(this)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        LocaleHelper.apply(this)
    }

    /**
     * Afiş yükleyici. IPTV panellerinin poster sunucuları genelde yavaş ve
     * önbellek başlıkları güvenilmez olduğu için başlıklar yok sayılıp
     * geniş bir disk önbelleği tutulur.
     */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient { imageClient() }
        .memoryCache {
            MemoryCache.Builder(this)
                .maxSizePercent(0.25)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve("image_cache"))
                .maxSizeBytes(256L * 1024 * 1024)
                .build()
        }
        .memoryCachePolicy(CachePolicy.ENABLED)
        .diskCachePolicy(CachePolicy.ENABLED)
        .respectCacheHeaders(false)
        .crossfade(false)
        .apply { if (BuildConfig.DEBUG) logger(DebugLogger()) }
        .build()

    private fun imageClient(): OkHttpClient = Http.api.newBuilder()
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("User-Agent", Http.DEFAULT_USER_AGENT)
                    .build()
            )
        }
        .build()
}

/** Uygulama dilini ayarlardaki seçime göre zorlar. */
object LocaleHelper {

    fun wrap(base: Context): Context {
        Settings.init(base)
        val tag = Settings.language
        if (tag.isBlank()) return base
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }

    fun apply(ctx: Context) {
        val tag = Settings.language
        if (tag.isNotBlank()) Locale.setDefault(Locale.forLanguageTag(tag))
    }
}
