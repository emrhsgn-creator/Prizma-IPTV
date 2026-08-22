package com.prizma.iptv.ui.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.prizma.iptv.BuildConfig
import com.prizma.iptv.R
import com.prizma.iptv.core.Fmt
import com.prizma.iptv.data.local.Backup
import com.prizma.iptv.data.local.DecoderMode
import com.prizma.iptv.data.local.GridDensity
import com.prizma.iptv.data.local.Paths
import com.prizma.iptv.data.local.ProfileStore
import com.prizma.iptv.data.local.Settings
import com.prizma.iptv.data.model.Profile
import com.prizma.iptv.data.repo.Session
import com.prizma.iptv.ui.common.Card
import com.prizma.iptv.ui.common.DangerRow
import com.prizma.iptv.ui.common.InfoRow
import com.prizma.iptv.ui.common.Pill
import com.prizma.iptv.ui.common.SettingSwitch
import com.prizma.iptv.ui.common.focusHighlight
import com.prizma.iptv.ui.home.HomeState
import com.prizma.iptv.ui.theme.AccentChoices
import com.prizma.iptv.ui.theme.Ink
import com.prizma.iptv.ui.theme.accent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(
    state: HomeState,
    session: Session,
    onAddProfile: () -> Unit,
    onSwitchProfile: (Profile) -> Unit,
    onRemoveProfile: (Profile) -> Unit,
    onSignOut: () -> Unit
) {
    val ctx = LocalContext.current
    val tint = accent()

    val revision by Settings.revision.collectAsStateWithLifecycle()
    val profiles by ProfileStore.profiles.collectAsStateWithLifecycle()
    val account by session.account.collectAsStateWithLifecycle()
    val server by session.server.collectAsStateWithLifecycle()
    val favorites by session.favorites.items.collectAsStateWithLifecycle()
    val history by session.history.items.collectAsStateWithLifecycle()
    val epgRefreshing by session.epg.refreshing.collectAsStateWithLifecycle()
    val epgProgress by session.epg.progress.collectAsStateWithLifecycle()

    var pinDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Favori sırası kullanıcının elle kurduğu tek veri; cihaz değişiminde
    // kaybolmaması için dosyaya alınabiliyor.
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val payload = Backup.export(
                        session.favorites.items.value,
                        session.history.items.value
                    )
                    ctx.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(payload.toByteArray(Charsets.UTF_8))
                    } ?: error("stream")
                }.isSuccess
            }
            Toast.makeText(
                ctx,
                ctx.getString(if (ok) R.string.s_backup_ok else R.string.s_restore_failed),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val restored = withContext(Dispatchers.IO) {
                runCatching {
                    val raw = ctx.contentResolver.openInputStream(uri)
                        ?.bufferedReader(Charsets.UTF_8)
                        ?.use { it.readText() }
                        .orEmpty()
                    Backup.parse(raw)
                }.getOrNull()
            }
            if (restored == null) {
                Toast.makeText(
                    ctx, ctx.getString(R.string.s_restore_failed), Toast.LENGTH_SHORT
                ).show()
            } else {
                session.favorites.replaceAll(restored.favorites)
                session.history.replaceAll(restored.history)
                Toast.makeText(
                    ctx, ctx.getString(R.string.s_restore_ok), Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    val cacheSize = remember(revision, favorites, history) { Paths.totalSize() }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 40.dp)
    ) {

        // ---------------------------------------------------------- Hesap
        Group(stringResource(R.string.settings_account))
        Card {
            InfoRow(stringResource(R.string.s_username), account.username.ifBlank { "-" })
            InfoRow(stringResource(R.string.s_status), account.status.ifBlank { "-" })
            InfoRow(
                stringResource(R.string.s_expiry),
                if (account.expiryMs > 0L) Fmt.date(account.expiryMs)
                else stringResource(R.string.s_expiry_never)
            )
            InfoRow(
                stringResource(R.string.s_connections),
                account.activeConnections + " / " + account.maxConnections
            )
            InfoRow(
                stringResource(R.string.s_server),
                server.url.ifBlank { session.profile.serverLabel() }
            )
            if (account.isTrial) {
                InfoRow(stringResource(R.string.s_trial), stringResource(R.string.on))
            }
        }

        // ---------------------------------------------------------- Profiller
        Group(stringResource(R.string.settings_profiles))
        Card {
            profiles.forEach { profile ->
                val active = profile.id == session.profile.id
                Row(
                    Modifier
                        .fillMaxWidth()
                        .focusHighlight(RoundedCornerShape(8.dp))
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (active) tint.copy(alpha = 0.16f) else Color.Transparent)
                        .clickable(enabled = !active) { onSwitchProfile(profile) }
                        .padding(horizontal = 10.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            profile.displayName(),
                            color = if (active) Ink.TextPrimary else Ink.TextSecondary,
                            fontSize = 13.sp,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            profile.serverLabel(),
                            color = Ink.TextMuted,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (active) {
                        Text(stringResource(R.string.s_active), color = tint, fontSize = 10.sp)
                    } else {
                        Box(
                            Modifier
                                .focusHighlight(CircleShape)
                                .clip(CircleShape)
                                .clickable { onRemoveProfile(profile) }
                                .padding(6.dp)
                        ) {
                            Icon(
                                Icons.Default.Delete, null,
                                tint = Ink.TextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.s_add_profile),
                color = tint,
                fontSize = 12.sp,
                modifier = Modifier
                    .focusHighlight(RoundedCornerShape(8.dp))
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onAddProfile)
                    .padding(horizontal = 10.dp, vertical = 10.dp)
            )
            DangerRow(stringResource(R.string.logout), onClick = onSignOut)
        }

        // ---------------------------------------------------------- Oynatma
        Group(stringResource(R.string.settings_playback))
        Card {
            Text(stringResource(R.string.s_buffer), color = Ink.TextSecondary, fontSize = 13.sp)
            Text(
                stringResource(R.string.s_buffer_desc),
                color = Ink.TextMuted,
                fontSize = 10.sp,
                lineHeight = 14.sp
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    10 to R.string.s_buffer_low,
                    30 to R.string.s_buffer_normal,
                    60 to R.string.s_buffer_high,
                    120 to R.string.s_buffer_max
                ).forEach { (seconds, labelRes) ->
                    Pill(
                        stringResource(R.string.s_buffer_value, stringResource(labelRes), seconds),
                        Settings.bufferSeconds == seconds
                    ) { Settings.bufferSeconds = seconds }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.s_decoder), color = Ink.TextSecondary, fontSize = 13.sp)
            Text(
                stringResource(R.string.s_decoder_desc),
                color = Ink.TextMuted,
                fontSize = 10.sp,
                lineHeight = 14.sp
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(
                    stringResource(R.string.s_decoder_hw),
                    Settings.decoderMode == DecoderMode.HARDWARE
                ) { Settings.decoderMode = DecoderMode.HARDWARE }
                Pill(
                    stringResource(R.string.s_decoder_sw),
                    Settings.decoderMode == DecoderMode.SOFTWARE
                ) { Settings.decoderMode = DecoderMode.SOFTWARE }
            }

            Spacer(Modifier.height(12.dp))
            SettingSwitch(
                stringResource(R.string.s_autonext),
                stringResource(R.string.s_autonext_desc),
                Settings.autoNext
            ) { Settings.autoNext = it }

            SettingSwitch(
                stringResource(R.string.s_resume),
                stringResource(R.string.s_resume_desc),
                Settings.resumeEnabled
            ) { Settings.resumeEnabled = it }

            SettingSwitch(
                stringResource(R.string.s_reconnect),
                stringResource(R.string.s_reconnect_desc),
                Settings.autoReconnect
            ) { Settings.autoReconnect = it }

            SettingSwitch(
                stringResource(R.string.s_background_audio),
                stringResource(R.string.s_background_audio_desc),
                Settings.backgroundAudio
            ) { Settings.backgroundAudio = it }

            SettingSwitch(
                stringResource(R.string.s_autopip),
                stringResource(R.string.s_autopip_desc),
                Settings.autoPip
            ) { Settings.autoPip = it }

            SettingSwitch(
                stringResource(R.string.s_autoplay_last),
                stringResource(R.string.s_autoplay_last_desc),
                Settings.autoplayLastChannel
            ) { Settings.autoplayLastChannel = it }

            SettingSwitch(
                stringResource(R.string.s_tunneling),
                stringResource(R.string.s_tunneling_desc),
                Settings.tunneling
            ) { Settings.tunneling = it }
        }

        // ---------------------------------------------------------- Görünüm
        Group(stringResource(R.string.settings_appearance))
        Card {
            Text(stringResource(R.string.s_theme_accent), color = Ink.TextSecondary, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AccentChoices.forEach { value ->
                    val selected = Settings.accentColor == value
                    Box(
                        Modifier
                            .focusHighlight(CircleShape)
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color(value))
                            .clickable { Settings.accentColor = value },
                        contentAlignment = Alignment.Center
                    ) {
                        if (selected) Text("✓", color = Color.White, fontSize = 14.sp)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.s_grid_density), color = Ink.TextSecondary, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    GridDensity.COMPACT to R.string.s_grid_compact,
                    GridDensity.NORMAL to R.string.s_grid_normal,
                    GridDensity.LARGE to R.string.s_grid_large
                ).forEach { (density, labelRes) ->
                    Pill(stringResource(labelRes), Settings.gridDensity == density) {
                        Settings.gridDensity = density
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.s_language), color = Ink.TextSecondary, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "" to R.string.s_language_system,
                    "tr" to R.string.s_language_tr,
                    "en" to R.string.s_language_en
                ).forEach { (tag, labelRes) ->
                    Pill(stringResource(labelRes), Settings.language == tag) {
                        Settings.language = tag
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            SettingSwitch(
                stringResource(R.string.s_force_tv_ui),
                stringResource(R.string.s_force_tv_ui_desc),
                Settings.forceTvUi
            ) { Settings.forceTvUi = it }
        }

        // ---------------------------------------------------------- EPG
        Group(stringResource(R.string.settings_epg))
        Card {
            InfoRow(
                stringResource(R.string.s_epg_last),
                if (session.epg.lastUpdated() > 0L) Fmt.relative(ctx, session.epg.lastUpdated())
                else stringResource(R.string.s_epg_never)
            )
            InfoRow(
                stringResource(R.string.guide_program),
                stringResource(R.string.s_records, session.epg.programCount)
            )
            Text(
                stringResource(R.string.s_epg_source_desc),
                color = Ink.TextMuted,
                fontSize = 10.sp,
                lineHeight = 14.sp
            )

            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.s_epg_days), color = Ink.TextSecondary, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1, 2, 3, 5, 7).forEach { days ->
                    Pill(
                        stringResource(R.string.s_epg_days_value, days),
                        Settings.epgDays == days
                    ) { Settings.epgDays = days }
                }
            }

            Spacer(Modifier.height(12.dp))
            SettingSwitch(
                stringResource(R.string.s_epg_auto),
                "",
                Settings.epgAutoRefresh
            ) { Settings.epgAutoRefresh = it }

            Spacer(Modifier.height(6.dp))
            Pill(
                if (epgRefreshing) {
                    stringResource(R.string.guide_refreshing) + " " + epgProgress
                } else {
                    stringResource(R.string.guide_refresh)
                },
                false
            ) { if (!epgRefreshing) state.refreshEpg() }
        }

        // ---------------------------------------------------------- Ebeveyn kontrolü
        Group(stringResource(R.string.settings_parental))
        Card {
            SettingSwitch(
                stringResource(R.string.s_parental_hide_adult),
                stringResource(R.string.s_parental_hide_adult_desc),
                Settings.hideAdult
            ) { Settings.hideAdult = it }

            SettingSwitch(
                stringResource(R.string.s_parental_lock_adult),
                "",
                Settings.lockAdult
            ) { Settings.lockAdult = it }

            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(
                    stringResource(
                        if (Settings.hasPin) R.string.s_parental_change
                        else R.string.s_parental_set
                    ),
                    false
                ) { pinDialog = true }
                if (Settings.hasPin) {
                    Pill(stringResource(R.string.s_parental_remove), false) {
                        Settings.clearPin()
                    }
                }
            }
        }

        // ---------------------------------------------------------- Veri
        Group(stringResource(R.string.settings_data))
        Card {
            InfoRow(
                stringResource(R.string.s_favorites_count),
                stringResource(R.string.s_records, favorites.size)
            )
            InfoRow(
                stringResource(R.string.s_history_count),
                stringResource(R.string.s_records, history.size)
            )
            InfoRow(stringResource(R.string.s_cache_size), Fmt.bytes(cacheSize))

            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.s_catalog_ttl), color = Ink.TextSecondary, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1, 6, 12, 24, 72).forEach { hours ->
                    Pill(
                        stringResource(R.string.s_catalog_ttl_value, hours),
                        Settings.catalogTtlHours == hours
                    ) { Settings.catalogTtlHours = hours }
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(stringResource(R.string.s_backup), false) {
                    exportLauncher.launch(Backup.suggestedFileName())
                }
                Pill(stringResource(R.string.s_restore), false) {
                    importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                }
            }

            Spacer(Modifier.height(12.dp))
            DangerRow(stringResource(R.string.s_refresh_catalog)) { state.refreshAll() }
            DangerRow(stringResource(R.string.clear_history)) { session.history.clear() }
            DangerRow(stringResource(R.string.s_clear_cache)) {
                // Favori ve geçmiş korunur; yalnızca indirilebilir veri silinir.
                Paths.clearCache(session.profile.id)
                state.refreshAll()
            }
        }

        // ---------------------------------------------------------- Hakkında
        Group(stringResource(R.string.settings_about))
        Card {
            InfoRow(
                stringResource(R.string.s_version),
                BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")"
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.s_about_note),
                color = Ink.TextMuted,
                fontSize = 10.sp
            )
        }
    }

    if (pinDialog) {
        PinSetupDialog(onDismiss = { pinDialog = false })
    }
}

@Composable
private fun Group(title: String) {
    Text(
        title,
        color = accent(),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 22.dp, top = 22.dp, bottom = 8.dp)
    )
}

@Composable
private fun PinSetupDialog(onDismiss: () -> Unit) {
    var first by remember { mutableStateOf("") }
    var second by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<Int?>(null) }
    val tint = accent()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Ink.Surface,
        title = {
            Text(
                stringResource(R.string.s_parental_set),
                color = Ink.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                PinField(first, stringResource(R.string.s_pin_new)) {
                    first = it
                    error = null
                }
                Spacer(Modifier.height(10.dp))
                PinField(second, stringResource(R.string.s_pin_repeat)) {
                    second = it
                    error = null
                }
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(it), color = Ink.Danger, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when {
                    first.length != 4 -> error = R.string.s_pin_short
                    first != second -> error = R.string.s_pin_mismatch
                    else -> {
                        Settings.setPin(first)
                        onDismiss()
                    }
                }
            }) {
                Text(stringResource(R.string.save), color = tint)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel), color = Ink.TextMuted)
            }
        }
    )
}

@Composable
private fun PinField(value: String, label: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { input ->
            if (input.length <= 4 && input.all { it.isDigit() }) onChange(input)
        },
        label = { Text(label, fontSize = 12.sp) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier.fillMaxWidth()
    )
}
