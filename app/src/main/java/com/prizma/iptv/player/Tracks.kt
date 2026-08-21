package com.prizma.iptv.player

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import java.util.Locale

/** Ayar panelinde listelenen tek bir ses / altyazı / görüntü seçeneği. */
data class TrackOption(
    val label: String,
    val groupIndex: Int,
    val trackIndex: Int,
    val selected: Boolean
)

/**
 * Parça gruplarını insan okunur seçeneklere çevirir.
 * Dil kodları cihazın diline göre adlandırılır (tur -> Türkçe).
 */
@UnstableApi
fun collectTracks(tracks: Tracks, type: Int): List<TrackOption> {
    val out = ArrayList<TrackOption>()
    tracks.groups.forEachIndexed { groupIndex, group ->
        if (group.type != type) return@forEachIndexed
        for (trackIndex in 0 until group.length) {
            if (!group.isTrackSupported(trackIndex)) continue
            val format = group.getTrackFormat(trackIndex)
            out.add(
                TrackOption(
                    label = describe(format, type, trackIndex),
                    groupIndex = groupIndex,
                    trackIndex = trackIndex,
                    selected = group.isTrackSelected(trackIndex)
                )
            )
        }
    }
    return out
}

private fun describe(format: Format, type: Int, index: Int): String {
    val base = when {
        !format.label.isNullOrBlank() -> format.label!!
        !format.language.isNullOrBlank() && format.language != "und" ->
            Locale(format.language!!).getDisplayLanguage(Locale.getDefault())
                .replaceFirstChar { it.uppercase() }
        else -> null
    }

    return when (type) {
        C.TRACK_TYPE_AUDIO -> {
            val name = base ?: ("#" + (index + 1))
            val extras = buildList {
                if (format.channelCount > 0) add(channelLabel(format.channelCount))
                codecName(format)?.let { add(it) }
            }
            if (extras.isEmpty()) name else name + " · " + extras.joinToString(" · ")
        }

        C.TRACK_TYPE_VIDEO -> {
            val resolution = if (format.height > 0) format.height.toString() + "p" else "?"
            val bitrate = if (format.bitrate > 0) {
                " · " + (format.bitrate / 1000) + " kbps"
            } else ""
            resolution + bitrate
        }

        else -> base ?: ("#" + (index + 1))
    }
}

private fun channelLabel(channels: Int): String = when (channels) {
    1 -> "Mono"
    2 -> "Stereo"
    6 -> "5.1"
    8 -> "7.1"
    else -> channels.toString() + "ch"
}

/** "audio/eac3" -> "EAC3" */
fun codecName(format: Format): String? {
    val mime = format.sampleMimeType ?: return null
    return mime.substringAfter('/').uppercase().takeIf { it.isNotBlank() }
}
