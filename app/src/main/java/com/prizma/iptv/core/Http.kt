package com.prizma.iptv.core

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Tüm ağ trafiği tek bir OkHttp havuzunu paylaşır: bağlantılar yeniden kullanılır,
 * gzip otomatik açılır ve ExoPlayer de aynı istemci üzerinden akış çeker.
 */
object Http {

    const val DEFAULT_USER_AGENT = "PrizmaIPTV/2.0 (Android)"

    /** Panel API çağrıları — kısa ve hızlı. */
    val api: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    /** XMLTV / M3U gibi onlarca megabaytlık indirmeler. */
    val bulk: OkHttpClient by lazy {
        api.newBuilder()
            .readTimeout(5, TimeUnit.MINUTES)
            .callTimeout(10, TimeUnit.MINUTES)
            .build()
    }

    /** Video akışı — okuma zaman aşımı kısa tutulur ki kopan yayın hızlı fark edilsin. */
    val media: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }
}
