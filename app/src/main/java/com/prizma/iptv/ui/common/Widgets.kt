package com.prizma.iptv.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.prizma.iptv.core.Fmt
import com.prizma.iptv.ui.theme.Ink
import com.prizma.iptv.ui.theme.accent
import com.prizma.iptv.ui.theme.ui

/**
 * Kumanda odağını görünür kılan sarmalayıcı. TV'de beyaz çerçeve ve hafif
 * büyüme, dokunmatikte yalnızca basılma geri bildirimi verir.
 */
@Composable
fun Modifier.focusFrame(
    shape: Shape = RoundedCornerShape(10.dp),
    scaleUp: Float? = null
): Modifier {
    val profile = ui()
    var focused by remember { mutableStateOf(false) }
    val target = if (focused) (scaleUp ?: profile.focusScale) else 1f
    val scale by animateFloatAsState(targetValue = target, label = "focusScale")
    return this
        .onFocusChanged { focused = it.isFocused }
        .scale(scale)
        .border(
            width = if (focused) 2.dp else 0.dp,
            color = if (focused) Color.White else Color.Transparent,
            shape = shape
        )
}

/** Odak çerçevesi isteyip büyümeyi istemeyen satırlar için. */
@Composable
fun Modifier.focusHighlight(shape: Shape = RoundedCornerShape(8.dp)): Modifier =
    focusFrame(shape, scaleUp = 1f)

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, trailing: String = "") {
    val profile = ui()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 14.dp, top = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            color = Ink.TextPrimary,
            fontSize = profile.titleSize,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        if (trailing.isNotEmpty()) {
            Text(trailing, color = Ink.TextMuted, fontSize = profile.captionSize)
        }
    }
}

@Composable
fun Chip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val profile = ui()
    val tint = accent()
    Text(
        label,
        color = if (selected) Color.White else Ink.TextSecondary,
        fontSize = profile.bodySize,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .focusHighlight(RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) tint.copy(alpha = 0.38f) else Ink.Surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    )
}

@Composable
fun Pill(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val tint = accent()
    Text(
        label,
        color = if (selected) Color.White else Ink.TextSecondary,
        fontSize = 12.sp,
        maxLines = 1,
        modifier = modifier
            .focusHighlight(RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) tint.copy(alpha = 0.42f) else Ink.SurfaceHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    )
}

@Composable
fun RatingBadge(rating: Double, modifier: Modifier = Modifier) {
    val text = Fmt.rating(rating)
    if (text.isEmpty()) return
    Row(
        modifier = modifier
            .padding(5.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(Ink.Scrim)
            .padding(horizontal = 5.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Star, null, tint = Ink.Gold, modifier = Modifier.size(10.dp))
        Spacer(Modifier.width(3.dp))
        Text(text, color = Color.White, fontSize = 10.sp)
    }
}

@Composable
fun ProgressStrip(progress: Float, modifier: Modifier = Modifier) {
    if (progress <= 0f) return
    val tint = accent()
    Box(
        modifier
            .fillMaxWidth()
            .height(3.dp)
            .background(Color(0x55FFFFFF))
    ) {
        Box(
            Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(3.dp)
                .background(tint)
        )
    }
}

/** Afiş ya da kanal logosu; görsel yoksa adın baş harfi gösterilir. */
@Composable
fun Artwork(
    url: String,
    fallbackText: String,
    wide: Boolean,
    modifier: Modifier = Modifier
) {
    val ctx = LocalContext.current
    Box(modifier.background(Ink.SurfaceHigh), contentAlignment = Alignment.Center) {
        if (url.isNotBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(ctx)
                    .data(url)
                    .size(if (wide) 360 else 260)
                    .crossfade(false)
                    .build(),
                contentDescription = null,
                contentScale = if (wide) ContentScale.Fit else ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(if (wide) 8.dp else 0.dp)
            )
        } else {
            Text(
                fallbackText.take(2).uppercase(),
                color = Ink.TextMuted,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Katalogdaki tek kutucuk. Canlı kanallar 16:9 ve logo sığdırılarak,
 * film/dizi 2:3 afiş olarak çizilir.
 */
@Composable
fun MediaTile(
    title: String,
    imageUrl: String,
    wide: Boolean,
    modifier: Modifier = Modifier,
    rating: Double = 0.0,
    progress: Float = 0f,
    badge: String = "",
    subtitle: String = "",
    favorite: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    val profile = ui()
    Column(modifier) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(if (wide) 16f / 9f else 2f / 3f)
                .focusFrame(RoundedCornerShape(10.dp))
                .clip(RoundedCornerShape(10.dp))
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                )
        ) {
            Artwork(imageUrl, title, wide, Modifier.fillMaxSize())

            if (rating > 0.0) RatingBadge(rating, Modifier.align(Alignment.TopEnd))

            if (badge.isNotEmpty()) {
                Text(
                    badge,
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(5.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Ink.Live)
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                )
            }

            if (favorite) {
                Text(
                    "★",
                    color = Ink.Gold,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Ink.Scrim)
                        .padding(horizontal = 4.dp)
                )
            }

            if (progress > 0f) {
                ProgressStrip(progress, Modifier.align(Alignment.BottomStart))
            }
        }

        Spacer(Modifier.height(6.dp))
        Text(
            title,
            color = Ink.TextPrimary,
            fontSize = profile.tileTitleSize,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = profile.tileTitleSize * 1.25f
        )
        if (subtitle.isNotEmpty()) {
            Text(
                subtitle,
                color = Ink.TextMuted,
                fontSize = profile.captionSize,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun LoadingBox(modifier: Modifier = Modifier, label: String = "") {
    val tint = accent()
    Box(modifier.fillMaxSize(), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = tint)
            if (label.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(label, color = Ink.TextMuted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun MessageBox(
    message: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    actionLabel: String = "",
    onAction: (() -> Unit)? = null
) {
    Box(modifier.fillMaxSize(), Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            Text(
                message,
                color = if (isError) Ink.Danger else Ink.TextMuted,
                fontSize = 13.sp
            )
            if (actionLabel.isNotEmpty() && onAction != null) {
                Spacer(Modifier.height(14.dp))
                PrimaryButton(actionLabel, onClick = onAction)
            }
        }
    }
}

@Composable
fun PrimaryButton(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leading: String = "",
    onClick: () -> Unit
) {
    val tint = accent()
    Row(
        modifier
            .focusHighlight(RoundedCornerShape(10.dp))
            .clip(RoundedCornerShape(10.dp))
            .background(if (enabled) tint else Ink.SurfaceHigh)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leading.isNotEmpty()) {
            Text(leading, color = Color.White, fontSize = 14.sp)
            Spacer(Modifier.width(8.dp))
        }
        Text(
            label,
            color = if (enabled) Color.White else Ink.TextMuted,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun SecondaryButton(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Text(
        label,
        color = Ink.TextSecondary,
        fontSize = 13.sp,
        modifier = modifier
            .focusHighlight(RoundedCornerShape(10.dp))
            .clip(RoundedCornerShape(10.dp))
            .background(Ink.SurfaceHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp)
    )
}

@Composable
fun SettingSwitch(
    title: String,
    description: String,
    checked: Boolean,
    modifier: Modifier = Modifier,
    onCheckedChange: (Boolean) -> Unit
) {
    val tint = accent()
    Row(
        modifier
            .fillMaxWidth()
            .focusHighlight(RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 6.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Ink.TextSecondary, fontSize = 13.sp)
            if (description.isNotEmpty()) {
                Text(
                    description,
                    color = Ink.TextMuted,
                    fontSize = 10.sp,
                    lineHeight = 14.sp
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedTrackColor = tint)
        )
    }
}

@Composable
fun InfoRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Ink.TextMuted, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Text(
            value,
            color = Ink.TextPrimary,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun DangerRow(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Text(
        label,
        color = Ink.Danger,
        fontSize = 12.sp,
        modifier = modifier
            .fillMaxWidth()
            .focusHighlight(RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp)
    )
}

@Composable
fun Card(modifier: Modifier = Modifier, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Ink.Surface)
            .padding(14.dp),
        content = content
    )
}

/** Üstteki degrade başlık şeridi. */
@Composable
fun HeaderBar(
    modifier: Modifier = Modifier,
    height: Dp = 0.dp,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (height > 0.dp) Modifier.height(height) else Modifier)
            .background(Brush.horizontalGradient(listOf(Ink.HeaderStart, Ink.HeaderEnd)))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

/** Yuvarlak, ikon benzeri metin düğmesi (oynatıcı üst çubuğu). */
@Composable
fun GlyphButton(
    glyph: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    fontSize: androidx.compose.ui.unit.TextUnit = 16.sp,
    background: Color = Color(0x88000000),
    onClick: () -> Unit
) {
    Box(
        modifier
            .focusFrame(RoundedCornerShape(size / 2), scaleUp = 1.12f)
            .clip(RoundedCornerShape(size / 2))
            .background(background)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .size(size),
        contentAlignment = Alignment.Center
    ) {
        Text(glyph, color = Color.White, fontSize = fontSize)
    }
}
