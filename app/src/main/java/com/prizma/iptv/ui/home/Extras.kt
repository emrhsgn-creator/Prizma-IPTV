package com.prizma.iptv.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prizma.iptv.R
import com.prizma.iptv.data.local.Settings
import com.prizma.iptv.data.model.Section
import com.prizma.iptv.data.model.StreamItem
import com.prizma.iptv.data.model.WatchState
import com.prizma.iptv.data.repo.Session
import com.prizma.iptv.ui.common.MediaTile
import com.prizma.iptv.ui.common.MessageBox
import com.prizma.iptv.ui.common.Pill
import com.prizma.iptv.ui.common.SectionHeader
import com.prizma.iptv.ui.common.focusHighlight
import com.prizma.iptv.ui.theme.Ink
import com.prizma.iptv.ui.theme.accent
import com.prizma.iptv.ui.theme.ui

private const val MAX_RESULTS_PER_SECTION = 120

/**
 * Üç bölümde birden arama. Katalog zaten bellekte olduğu için sorgu her tuş
 * vuruşunda yerelde çalıştırılır; ağ isteği yapılmaz.
 */
@Composable
fun SearchTab(state: HomeState, data: HomeData) {
    val ctx = LocalContext.current
    val profile = ui()
    val session = state.session
    val query = state.query.trim()

    if (query.length < 2) {
        MessageBox(stringResource(R.string.search_empty))
        return
    }

    val results = remember(query, data.sections) {
        Section.entries.associateWith { section ->
            rank(state.visibleItems(data.section(section)), query)
        }.filterValues { it.isNotEmpty() }
    }

    if (results.isEmpty()) {
        MessageBox(stringResource(R.string.search_no_result, query))
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(profile.posterWidth),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        results.forEach { (section, list) ->
            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionHeader(
                    stringResource(section.titleRes()),
                    trailing = stringResource(R.string.search_results, list.size)
                )
            }
            items(list, key = { section.name + "/" + it.id }) { item ->
                MediaTile(
                    title = item.name,
                    imageUrl = item.icon,
                    wide = section == Section.LIVE,
                    rating = item.rating,
                    onClick = {
                        // Arama sonuclarindan acilan kanal, sonuc listesi
                        // icinde ilerler.
                        Launch.open(ctx, session, section, item, list)
                    },
                    onLongClick = { state.contextItem = section to item }
                )
            }
        }
    }
}

/** Adın başında geçen eşleşmeler öne alınır. */
private fun rank(items: List<StreamItem>, query: String): List<StreamItem> {
    if (query.isEmpty()) return emptyList()
    val starts = ArrayList<StreamItem>()
    val contains = ArrayList<StreamItem>()
    for (item in items) {
        if (item.name.startsWith(query, ignoreCase = true)) {
            starts.add(item)
        } else if (item.name.contains(query, ignoreCase = true)) {
            contains.add(item)
        }
        if (starts.size >= MAX_RESULTS_PER_SECTION) break
    }
    return (starts + contains).take(MAX_RESULTS_PER_SECTION)
}

/** Son izlenenler sekmesi. */
@Composable
fun RecentTab(state: HomeState, data: HomeData) {
    val ctx = LocalContext.current
    val profile = ui()
    val session = state.session

    if (data.history.isEmpty()) {
        MessageBox(stringResource(R.string.history_empty))
        return
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.s_records, data.history.size),
                color = Ink.TextMuted,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f)
            )
            Pill(stringResource(R.string.clear_history), false) {
                session.history.clear()
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(profile.posterWidth),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(data.history, key = { it.section + "/" + it.id }) { watch ->
                MediaTile(
                    title = watch.name,
                    imageUrl = watch.icon,
                    wide = watch.section == Section.LIVE.name,
                    progress = watch.progress,
                    onClick = { Launch.openWatchState(ctx, session, watch) },
                    onLongClick = { session.history.remove(watch.section, watch.id) }
                )
            }
        }
    }
}

/** Yetişkin kategoriler için PIN sorgusu. */
@Composable
fun PinPrompt(
    onSuccess: () -> Unit,
    onDismiss: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    val tint = accent()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Ink.Surface,
        title = {
            Text(
                stringResource(R.string.s_pin_enter),
                color = Ink.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { value ->
                        if (value.length <= 4 && value.all { it.isDigit() }) {
                            pin = value
                            wrong = false
                        }
                    },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                )
                if (wrong) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.s_pin_wrong),
                        color = Ink.Danger,
                        fontSize = 12.sp
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (Settings.checkPin(pin)) onSuccess() else wrong = true
            }) {
                Text(stringResource(R.string.ok), color = tint)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel), color = Ink.TextMuted)
            }
        }
    )
}

/** Kutucuğa uzun basınca açılan işlem listesi. */
@Composable
fun ItemContextSheet(
    state: HomeState,
    session: Session,
    section: Section,
    item: StreamItem,
    onDismiss: () -> Unit
) {
    val favorite = session.favorites.isFavorite(section.name, item.id)
    val inHistory = session.history.find(section.name, item.id) != null
    val tint = accent()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Ink.Surface,
        title = {
            Text(
                item.name,
                color = Ink.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2
            )
        },
        text = {
            Column {
                ActionRow(
                    stringResource(if (favorite) R.string.favorited else R.string.favorite)
                ) {
                    session.favorites.toggle(session.favoriteOf(section, item))
                    onDismiss()
                }

                if (section == Section.LIVE) {
                    ActionRow(stringResource(R.string.guide_title)) {
                        state.epgDialogItem = item
                        onDismiss()
                    }
                }

                if (section == Section.VOD) {
                    ActionRow(stringResource(R.string.detail_mark_watched)) {
                        session.history.markWatched(
                            WatchState(
                                section = section.name,
                                id = item.id,
                                name = item.name,
                                icon = item.icon,
                                extension = item.extension,
                                duration = 1L
                            )
                        )
                        onDismiss()
                    }
                }

                if (inHistory) {
                    ActionRow(stringResource(R.string.clear_history)) {
                        session.history.remove(section.name, item.id)
                        onDismiss()
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close), color = tint)
            }
        }
    )
}

@Composable
private fun ActionRow(label: String, onClick: () -> Unit) {
    Text(
        label,
        color = Ink.TextSecondary,
        fontSize = 13.sp,
        modifier = Modifier
            .fillMaxWidth()
            .focusHighlight(RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp)
    )
}
