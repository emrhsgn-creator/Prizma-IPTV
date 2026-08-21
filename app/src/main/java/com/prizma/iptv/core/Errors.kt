package com.prizma.iptv.core

import android.content.Context
import androidx.annotation.StringRes
import com.prizma.iptv.R
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * Kullanıcıya gösterilebilir, çevrilmiş hata. Ham istisna mesajları yerine
 * her zaman bu tip fırlatılır ki arayüz tek bir yerden metne çevirebilsin.
 */
class AppError(
    @StringRes val resId: Int,
    val args: List<Any> = emptyList(),
    cause: Throwable? = null
) : Exception(cause)

fun appError(@StringRes resId: Int, vararg args: Any): AppError =
    AppError(resId, args.toList())

/** Herhangi bir istisnayı kullanıcıya gösterilebilir bir metne indirger. */
fun Throwable.userMessage(ctx: Context): String = when (this) {
    is AppError ->
        if (args.isEmpty()) ctx.getString(resId)
        else ctx.getString(resId, *args.toTypedArray())
    is SocketTimeoutException -> ctx.getString(R.string.error_timeout)
    is InterruptedIOException -> ctx.getString(R.string.error_timeout)
    is UnknownHostException -> ctx.getString(R.string.error_network)
    is SSLException -> ctx.getString(R.string.error_network)
    is IOException -> ctx.getString(R.string.error_network)
    else -> message?.takeIf { it.isNotBlank() } ?: ctx.getString(R.string.error_generic)
}
