package com.prizma.iptv.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.prizma.iptv.core.Device
import com.prizma.iptv.data.local.GridDensity
import com.prizma.iptv.data.local.Settings

/** Uygulamanın sabit renk paleti. Vurgu rengi ayarlardan gelir. */
object Ink {
    val Bg = Color(0xFF0B0D14)
    val Surface = Color(0xFF161923)
    val SurfaceHigh = Color(0xFF1E2230)
    val Outline = Color(0xFF2A2F3D)
    val HeaderStart = Color(0xFF1B2350)
    val HeaderEnd = Color(0xFF4B2F8F)
    val TextPrimary = Color(0xFFECEEF3)
    val TextSecondary = Color(0xFFB6BCCB)
    val TextMuted = Color(0xFF7C8496)
    val Danger = Color(0xFFFF6B6B)
    val Gold = Color(0xFFF5C518)
    val Live = Color(0xFFFF4D4F)
    val Scrim = Color(0xCC000000)
}

/** Ayarlarda renk kutucuğu olarak sunulan vurgu renkleri. */
val AccentChoices = listOf(
    0xFF4F8DF7L,
    0xFF00C2A8L,
    0xFFE0457BL,
    0xFF7C5CFFL,
    0xFFF5A524L,
    0xFF37C267L
)

/**
 * Ekranın kumandayla mı dokunmatikle mi kullanıldığı ve buna bağlı ölçüler.
 * Tek bir yerde tanımlanır ki telefon ve TV düzeni her ekranda tutarlı olsun.
 */
data class UiProfile(
    val isTv: Boolean,
    val density: GridDensity
) {
    val posterWidth: Dp get() = density.posterWidthDp.dp * if (isTv) 1.15f else 1f
    val channelWidth: Dp get() = density.channelWidthDp.dp * if (isTv) 1.15f else 1f

    /** Kumandada odak çerçevesi gerekir; dokunmatikte gereksiz gürültü yapar. */
    val focusOutline: Boolean get() = isTv
    val focusScale: Float get() = if (isTv) 1.07f else 1.03f

    val titleSize get() = if (isTv) 17.sp else 15.sp
    val bodySize get() = if (isTv) 14.sp else 12.sp
    val captionSize get() = if (isTv) 12.sp else 10.sp
    val tileTitleSize get() = if (isTv) 13.sp else 11.sp
    val screenPadding: Dp get() = if (isTv) 24.dp else 12.dp
}

val LocalUiProfile = staticCompositionLocalOf { UiProfile(false, GridDensity.NORMAL) }
val LocalAccent = staticCompositionLocalOf { Color(0xFF4F8DF7) }

@Composable
fun accent(): Color = LocalAccent.current

@Composable
fun ui(): UiProfile = LocalUiProfile.current

@Composable
fun PrizmaTheme(
    forceTv: Boolean? = null,
    content: @Composable () -> Unit
) {
    val ctx = LocalContext.current
    val revision by Settings.revision.collectAsStateWithLifecycle()

    // revision anahtar olarak kullanılır: herhangi bir ayar değişince
    // aşağıdaki değerler yeniden okunur ve tüm ağaç kendini tazeler.
    val snapshot = remember(revision) {
        Triple(Settings.forceTvUi, Settings.accentColor, Settings.gridDensity)
    }

    val isTv = forceTv ?: (snapshot.first || Device.isTv(ctx))
    val accentColor = Color(snapshot.second)
    val profile = UiProfile(isTv = isTv, density = snapshot.third)

    val scheme = darkColorScheme(
        primary = accentColor,
        onPrimary = Color.White,
        secondary = accentColor,
        background = Ink.Bg,
        onBackground = Ink.TextPrimary,
        surface = Ink.Surface,
        onSurface = Ink.TextPrimary,
        surfaceVariant = Ink.SurfaceHigh,
        onSurfaceVariant = Ink.TextSecondary,
        outline = Ink.Outline,
        error = Ink.Danger
    )

    CompositionLocalProvider(
        LocalUiProfile provides profile,
        LocalAccent provides accentColor
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = MaterialTheme.typography.copy(
                bodyMedium = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = profile.bodySize
                ),
                titleMedium = MaterialTheme.typography.titleMedium.copy(
                    fontSize = profile.titleSize,
                    fontWeight = FontWeight.Bold
                )
            ),
            content = content
        )
    }
}
