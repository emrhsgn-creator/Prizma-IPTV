package com.prizma.iptv.core

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration

/**
 * Cihazın kumandayla mı yoksa dokunmatikle mi kullanıldığını belirler.
 * Arayüz bu bilgiye göre odak çerçevesi, boyut ve yerleşim seçer.
 */
object Device {

    fun isTv(ctx: Context): Boolean {
        val uiMode = ctx.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        if (uiMode?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION) return true
        val pm = ctx.packageManager
        if (pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK)) return true
        @Suppress("DEPRECATION")
        if (pm.hasSystemFeature("android.software.leanback_only")) return true
        // Dokunmatik ekranı olmayan cihazlar da kumanda ile kullanılıyordur.
        return !pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
    }

    fun hasPip(ctx: Context): Boolean =
        android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O &&
            ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
}
