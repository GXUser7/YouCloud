package com.example.myapplication.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.myapplication.data.social.Showcase
import com.example.myapplication.data.social.ShowcaseItem
import com.example.myapplication.data.social.ShowcaseSlot
import com.example.myapplication.i18n.tr

/** What a place of the showcase holds, said over it. */
internal val ShowcaseSlot.label: String
    get() = when (this) {
        ShowcaseSlot.ARTIST -> tr("Исполнитель")
        ShowcaseSlot.TRACK -> tr("Трек")
        ShowcaseSlot.ALBUM -> tr("Альбом")
    }

/** The place as a title: what picking it is called, in search. */
internal val ShowcaseSlot.pickTitle: String
    get() = when (this) {
        ShowcaseSlot.ARTIST -> tr("Любимый исполнитель")
        ShowcaseSlot.TRACK -> tr("Любимый трек")
        ShowcaseSlot.ALBUM -> tr("Любимый альбом")
    }

private val ShowcaseSlot.icon: ImageVector
    get() = when (this) {
        ShowcaseSlot.ARTIST -> Icons.Rounded.Mic
        ShowcaseSlot.TRACK -> Icons.Rounded.MusicNote
        ShowcaseSlot.ALBUM -> Icons.Rounded.Album
    }

/**
 * The showcase, under "Любимое": a row each for the favourite artist, track and album, a tap
 * playing the track or opening the others. One's own ([onPick] given) shows the places not yet
 * picked too, a tap picking one, and a held one is to be changed or taken away ([onHold]).
 * Someone else's leaves them out, and with nothing picked shows nothing.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ShowcaseSection(
    showcase: Showcase,
    onOpen: (ShowcaseItem) -> Unit,
    onPick: ((ShowcaseSlot) -> Unit)? = null,
    onHold: ((ShowcaseSlot) -> Unit)? = null
) {
    val haptic = LocalHapticFeedback.current
    val slots = ShowcaseSlot.entries.filter { onPick != null || showcase[it] != null }
    if (slots.isEmpty()) return
    Column {
        SectionRule(tr("Любимое"))
        slots.forEachIndexed { index, slot ->
            val item = showcase[slot]
            val shape = groupedShape(index, slots.size)
            GroupedCard(
                shape = shape,
                modifier = Modifier
                    .padding(bottom = 3.dp)
                    .clip(shape)
                    .combinedClickable(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (item != null) onOpen(item) else onPick?.invoke(slot)
                        },
                        onLongClick = if (item != null && onHold != null) {
                            {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onHold(slot)
                            }
                        } else {
                            null
                        }
                    )
            ) {
                ShowcaseCover(slot, item?.cover, picked = item != null, size = 56.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(slot.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (item != null) {
                        Text(item.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        item.subtitle?.takeIf { it.isNotBlank() }?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    } else {
                        Text(tr("Выбрать"), style = MaterialTheme.typography.titleMedium, color = PanelColors.accent)
                    }
                }
                when {
                    item == null -> Unit
                    // The track plays where it is: the accent's round button says so.
                    slot == ShowcaseSlot.TRACK && item.link != null -> Box(
                        modifier = Modifier.size(40.dp).clip(CircleShape).background(PanelColors.accent),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = PanelColors.onAccent)
                    }
                    item.link != null -> Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** What plays here now, on one's own showcase: a tap opens the player. */
@Composable
internal fun OwnNowPlayingRow(title: String, artist: String?, cover: String?, playing: Boolean, onClick: () -> Unit) {
    GroupedCard(shape = RoundedCornerShape(28.dp), onClick = onClick) {
        Box(
            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)).background(PanelColors.accent.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = PanelColors.accent)
            if (cover != null) AsyncImage(model = cover, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                if (playing) tr("Сейчас слушаю") else tr("На паузе"),
                style = MaterialTheme.typography.labelMedium,
                color = PanelColors.accent
            )
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            artist?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (playing) Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = PanelColors.accent)
    }
}

/**
 * A place's picture: the artist cut to the avatars' cookie, as people are; a track or an album a
 * rounded square. Not picked yet: a "+" on the accent's tint.
 */
@Composable
private fun ShowcaseCover(slot: ShowcaseSlot, url: String?, picked: Boolean, size: Dp) {
    val shape = if (slot == ShowcaseSlot.ARTIST) AvatarShape else RoundedCornerShape(14.dp)
    Box(
        modifier = Modifier.size(size).clip(shape).background(PanelColors.accent.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(if (picked) slot.icon else Icons.Rounded.Add, contentDescription = null, tint = PanelColors.accent)
        if (url != null) AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
    }
}
