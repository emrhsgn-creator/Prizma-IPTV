package com.prizma.iptv.ui.login

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.prizma.iptv.R
import com.prizma.iptv.data.local.ProfileStore
import com.prizma.iptv.data.model.Profile
import com.prizma.iptv.data.model.SourceType
import com.prizma.iptv.ui.common.Chip
import com.prizma.iptv.ui.common.PrimaryButton
import com.prizma.iptv.ui.common.SecondaryButton
import com.prizma.iptv.ui.common.focusHighlight
import com.prizma.iptv.ui.theme.Ink
import com.prizma.iptv.ui.theme.accent
import com.prizma.iptv.ui.theme.ui

@Composable
fun LoginScreen(
    state: Stage.SignIn,
    profiles: List<Profile>,
    canCancel: Boolean,
    onDraftChange: (ProfileDraft) -> Unit,
    onSubmit: (ProfileDraft) -> Unit,
    onPickProfile: (Profile) -> Unit,
    onDeleteProfile: (Profile) -> Unit,
    onCancel: () -> Unit
) {
    val profile = ui()
    val tint = accent()
    val draft = state.draft
    var advanced by remember { mutableStateOf(false) }

    Surface(color = Ink.Bg, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Text(
                stringResource(R.string.login_title),
                color = tint,
                fontSize = if (profile.isTv) 38.sp else 30.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.login_subtitle),
                color = Ink.TextMuted,
                fontSize = 13.sp
            )
            Spacer(Modifier.height(24.dp))

            val fieldWidth = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp)

            // Bağlantı türü
            Row(
                fieldWidth,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Chip(
                    stringResource(R.string.login_type_xtream),
                    draft.type == SourceType.XTREAM
                ) { onDraftChange(draft.copy(type = SourceType.XTREAM)) }
                Chip(
                    stringResource(R.string.login_type_m3u),
                    draft.type == SourceType.M3U
                ) { onDraftChange(draft.copy(type = SourceType.M3U)) }
            }

            Spacer(Modifier.height(16.dp))

            val isXtream = draft.type == SourceType.XTREAM

            LoginField(
                value = draft.host,
                onValueChange = { onDraftChange(draft.copy(host = it)) },
                label = stringResource(
                    if (isXtream) R.string.login_host else R.string.login_m3u_url
                ),
                placeholder = stringResource(
                    if (isXtream) R.string.login_host_hint else R.string.login_m3u_hint
                ),
                keyboardType = KeyboardType.Uri,
                modifier = fieldWidth
            )

            if (isXtream) {
                Spacer(Modifier.height(12.dp))
                LoginField(
                    value = draft.user,
                    onValueChange = { onDraftChange(draft.copy(user = it)) },
                    label = stringResource(R.string.login_user),
                    modifier = fieldWidth
                )
                Spacer(Modifier.height(12.dp))
                LoginField(
                    value = draft.pass,
                    onValueChange = { onDraftChange(draft.copy(pass = it)) },
                    label = stringResource(R.string.login_pass),
                    password = true,
                    modifier = fieldWidth
                )
            }

            Spacer(Modifier.height(10.dp))

            Box(fieldWidth) {
                Text(
                    (if (advanced) "▾ " else "▸ ") + stringResource(R.string.login_advanced),
                    color = Ink.TextMuted,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .focusHighlight(RoundedCornerShape(6.dp))
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { advanced = !advanced }
                        .padding(horizontal = 6.dp, vertical = 8.dp)
                )
            }

            if (advanced) {
                Spacer(Modifier.height(8.dp))
                LoginField(
                    value = draft.label,
                    onValueChange = { onDraftChange(draft.copy(label = it)) },
                    label = stringResource(R.string.login_label),
                    modifier = fieldWidth
                )
                Spacer(Modifier.height(12.dp))
                LoginField(
                    value = draft.epgUrl,
                    onValueChange = { onDraftChange(draft.copy(epgUrl = it)) },
                    label = stringResource(R.string.login_epg_url),
                    keyboardType = KeyboardType.Uri,
                    modifier = fieldWidth
                )
                Spacer(Modifier.height(12.dp))
                LoginField(
                    value = draft.userAgent,
                    onValueChange = { onDraftChange(draft.copy(userAgent = it)) },
                    label = stringResource(R.string.login_user_agent),
                    modifier = fieldWidth
                )
            }

            Spacer(Modifier.height(20.dp))

            Row(
                fieldWidth,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PrimaryButton(
                    label = stringResource(
                        if (state.busy) R.string.login_busy else R.string.login_button
                    ),
                    enabled = !state.busy && draft.host.isNotBlank(),
                    modifier = Modifier.weight(1f),
                    onClick = { onSubmit(draft) }
                )
                if (canCancel) {
                    SecondaryButton(stringResource(R.string.cancel), onClick = onCancel)
                }
            }

            if (state.error != null) {
                Spacer(Modifier.height(14.dp))
                Text(
                    state.error,
                    color = Ink.Danger,
                    fontSize = 13.sp,
                    modifier = fieldWidth
                )
            }

            if (profiles.isNotEmpty()) {
                Spacer(Modifier.height(28.dp))
                Column(fieldWidth) {
                    Text(
                        stringResource(R.string.login_saved_profiles),
                        color = tint,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    profiles.forEach { saved ->
                        SavedProfileRow(
                            profile = saved,
                            onClick = { onPickProfile(saved) },
                            onDelete = { onDeleteProfile(saved) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SavedProfileRow(
    profile: Profile,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .focusHighlight(RoundedCornerShape(10.dp))
            .clip(RoundedCornerShape(10.dp))
            .background(Ink.Surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                profile.displayName(),
                color = Ink.TextPrimary,
                fontSize = 13.sp,
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
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier
                .focusHighlight(RoundedCornerShape(6.dp))
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onDelete)
                .padding(8.dp)
        ) {
            Icon(
                Icons.Default.Delete,
                contentDescription = stringResource(R.string.delete),
                tint = Ink.TextMuted,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun LoginField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    password: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    val tint = accent()
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 12.sp) },
        placeholder = if (placeholder.isEmpty()) null else {
            { Text(placeholder, fontSize = 12.sp, color = Ink.TextMuted) }
        },
        singleLine = true,
        visualTransformation =
            if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = tint,
            unfocusedBorderColor = Ink.Outline,
            focusedTextColor = Ink.TextPrimary,
            unfocusedTextColor = Ink.TextPrimary,
            focusedLabelColor = tint,
            unfocusedLabelColor = Ink.TextMuted,
            cursorColor = tint,
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent
        ),
        modifier = modifier
    )
}

/** Giriş ekranında gösterilecek kayıtlı profiller. */
@Composable
fun rememberSavedProfiles(): List<Profile> {
    val profiles by ProfileStore.profiles.collectAsStateWithLifecycle()
    return profiles
}
