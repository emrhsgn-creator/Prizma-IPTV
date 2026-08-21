package com.prizma.iptv.core

import android.content.Context
import com.prizma.iptv.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** Tarih, süre ve boyut biçimlendirmeleri tek yerde toplanır. */
object Fmt {

    private val zone: ZoneId get() = ZoneId.systemDefault()

    private val hhmm: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private val dmy: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    private fun dayMonth(): DateTimeFormatter =
        DateTimeFormatter.ofPattern("d MMMM EEEE", Locale.getDefault())

    /** 21:45 */
    fun clock(epochMs: Long): String =
        if (epochMs <= 0L) "--:--"
        else hhmm.format(Instant.ofEpochMilli(epochMs).atZone(zone))

    /** 21.08.2026 */
    fun date(epochMs: Long): String =
        if (epochMs <= 0L) "-"
        else dmy.format(Instant.ofEpochMilli(epochMs).atZone(zone))

    /** 21 Ağustos Cuma */
    fun longDay(epochMs: Long): String =
        if (epochMs <= 0L) "-"
        else dayMonth().format(Instant.ofEpochMilli(epochMs).atZone(zone))

    /** Bugün / Yarın / Dün ya da uzun gün adı. */
    fun dayLabel(ctx: Context, epochMs: Long): String {
        if (epochMs <= 0L) return "-"
        val day = Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate()
        val today = LocalDate.now(zone)
        return when (day) {
            today -> ctx.getString(R.string.guide_today)
            today.plusDays(1) -> ctx.getString(R.string.guide_tomorrow)
            today.minusDays(1) -> ctx.getString(R.string.guide_yesterday)
            else -> longDay(epochMs)
        }
    }

    /** 1:23:45 ya da 23:45 */
    fun duration(ms: Long): String {
        if (ms <= 0L) return "0:00"
        val total = ms / 1000
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        else String.format(Locale.US, "%d:%02d", m, s)
    }

    /** "45 dk" / "2 sa 15 dk" */
    fun minutes(ctx: Context, ms: Long): String {
        val totalMin = (ms / 60000L).toInt().coerceAtLeast(0)
        return if (totalMin < 60) ctx.getString(R.string.time_minutes, totalMin)
        else ctx.getString(R.string.time_hours_minutes, totalMin / 60, totalMin % 60)
    }

    /** "az önce" / "3 saat önce" / "2 gün önce" */
    fun relative(ctx: Context, epochMs: Long): String {
        if (epochMs <= 0L) return "-"
        val diff = System.currentTimeMillis() - epochMs
        val hours = diff / 3_600_000L
        return when {
            hours < 1 -> ctx.getString(R.string.time_just_now)
            hours < 24 -> ctx.getString(R.string.time_hours_ago, hours.toInt())
            else -> ctx.getString(R.string.time_days_ago, (hours / 24).toInt())
        }
    }

    fun bitrate(bitsPerSecond: Long): String = when {
        bitsPerSecond <= 0L -> "-"
        bitsPerSecond >= 1_000_000L ->
            String.format(Locale.US, "%.1f Mbps", bitsPerSecond / 1_000_000.0)
        else -> String.format(Locale.US, "%d kbps", bitsPerSecond / 1000)
    }

    fun bytes(b: Long): String = when {
        b <= 0L -> "0 B"
        b >= 1L shl 30 -> String.format(Locale.US, "%.1f GB", b / (1L shl 30).toDouble())
        b >= 1L shl 20 -> String.format(Locale.US, "%.1f MB", b / (1L shl 20).toDouble())
        b >= 1L shl 10 -> String.format(Locale.US, "%.0f KB", b / (1L shl 10).toDouble())
        else -> "$b B"
    }

    /** 10 üzerinden puanı "7.4" biçiminde verir, puan yoksa boş döner. */
    fun rating(value: Double): String =
        if (value <= 0.0) "" else String.format(Locale.US, "%.1f", value)

    /** Saniye cinsinden süreyi "42 dk" gibi gösterir. */
    fun secondsToMinutes(ctx: Context, seconds: Int): String =
        if (seconds <= 0) "" else minutes(ctx, seconds * 1000L)

    fun percent(v: Float): String = "${(v * 100f).roundToInt()}%"
}
