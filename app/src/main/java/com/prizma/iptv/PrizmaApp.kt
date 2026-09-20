package com.prizma.iptv

import android.app.Activity
import android.app.Application
import android.os.Bundle
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy

class PrizmaApplication : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()

        // Favori ve gecmis listelerini arka planda ayristir. Boylece ilk
        // kompozisyon (HomeScreen) ve oynaticinin acilisi bu maliyeti ana
        // is parcaciginda odemez.
        Store.warm(this)

        // Bekleyen favori/gecmis yazimlarini, herhangi bir ekran arka
        // plana gecerken diske bosalt.
        //
        // Store yazimlari kendi is parcaciginda yapiyor; bu, Android'in
        // apply() icin sagladigi "surec olurken bosalt" garantisini
        // devre disi birakiyor. Tek bir yasam dongusu dinleyicisi butun
        // aktiviteleri kapsiyor: favori ekranlarda (Home, Movie, Series)
        // degisebiliyor, izleme konumu oynaticida yaziliyor.
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityPaused(activity: Activity) = Store.flush()

            override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, out: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(200L * 1024 * 1024)
                    .build()
            }
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .respectCacheHeaders(false)
            .crossfade(false)
            .build()
    }
}
