@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

package com.example.myapplication.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.myapplication.ui.theme.AppTheme
import kotlin.math.roundToInt

/*
 * The shared vocabulary of the colour-block redesign.
 *
 * Every collection screen (downloads, playlists, albums, mixes, artists) opens with the same
 * hero: a big cover with a panel in the scheme's accent tone laid over its lower edge,
 * carrying the title in heavy type. The player uses the same idea at full height, and the mini
 * player is that panel collapsed. Lists below sit in one rounded container per group.
 *
 * All colour comes from the Material You scheme; nothing here is a fixed brand hue.
 */

// region Loading

/** Google's Material 3 Expressive loading indicator; the one spinner used across the app. */
@Composable
internal fun AppLoadingIndicator(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    LoadingIndicator(modifier = modifier, color = color)
}

/** The same indicator on its own container, for laying over artwork of any colour. */
@Composable
internal fun AppContainedLoadingIndicator(modifier: Modifier = Modifier) {
    ContainedLoadingIndicator(modifier = modifier)
}

/** Wavy linear progress: determinate when [progress] is known, indeterminate otherwise. */
@Composable
internal fun AppLinearProgress(
    progress: Float?,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = color.copy(alpha = 0.24f)
) {
    if (progress == null) {
        LinearWavyProgressIndicator(modifier = modifier, color = color, trackColor = trackColor)
    } else {
        LinearWavyProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = modifier,
            color = color,
            trackColor = trackColor
        )
    }
}

/** Wavy circular progress for a known fraction; the loading indicator when it isn't known. */
@Composable
internal fun AppCircularProgress(
    progress: Float?,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    if (progress == null) {
        LoadingIndicator(modifier = modifier, color = color)
    } else {
        CircularWavyProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = modifier,
            color = color,
            trackColor = color.copy(alpha = 0.24f)
        )
    }
}

/** A centred loading indicator for a list slot or an empty page. */
@Composable
internal fun LoadingBlock(modifier: Modifier = Modifier, height: Dp = 160.dp) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height),
        contentAlignment = Alignment.Center
    ) {
        AppLoadingIndicator(modifier = Modifier.size(64.dp))
    }
}

// endregion

// region Grouped lists

/** Where a row sits in its group, which decides which corners are rounded. */
internal enum class GroupPosition { Single, First, Middle, Last }

internal fun groupPosition(index: Int, count: Int): GroupPosition = when {
    count <= 1 -> GroupPosition.Single
    index == 0 -> GroupPosition.First
    index == count - 1 -> GroupPosition.Last
    else -> GroupPosition.Middle
}

internal val GroupPosition.hasDividerAbove: Boolean
    get() = this == GroupPosition.Middle || this == GroupPosition.Last

internal fun GroupPosition.shape(outer: Dp = 28.dp): Shape = when (this) {
    GroupPosition.Single -> RoundedCornerShape(outer)
    GroupPosition.First -> RoundedCornerShape(topStart = outer, topEnd = outer)
    GroupPosition.Middle -> RoundedCornerShape(0.dp)
    GroupPosition.Last -> RoundedCornerShape(bottomStart = outer, bottomEnd = outer)
}

/** Hairline between rows of a group, inset past the artwork like a list divider. */
@Composable
internal fun GroupDivider(startInset: Dp = 78.dp) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = startInset, end = 16.dp)
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
    )
}

/**
 * The last row of a group when there is more to load: a quiet text action inside the same
 * container, rather than a separate button floating under the list.
 */
@Composable
internal fun LoadMoreRow(
    isLoading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    position: GroupPosition = GroupPosition.Last
) {
    Surface(
        onClick = onClick,
        enabled = !isLoading,
        modifier = modifier.fillMaxWidth(),
        shape = position.shape(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.primary
    ) {
        Column {
            if (position.hasDividerAbove) GroupDivider(startInset = 16.dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isLoading) {
                    AppLoadingIndicator(modifier = Modifier.size(40.dp))
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("Показать ещё", style = MaterialTheme.typography.titleSmall)
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

// endregion

// region Headings

@Composable
internal fun SectionTitle(
    text: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) {
                Text(actionLabel, style = MaterialTheme.typography.labelLarge)
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/** Small spaced capitals above a block: "ЛУЧШИЙ РЕЗУЛЬТАТ", "АЛЬБОМ · 2017". */
@Composable
internal fun Kicker(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium.copy(
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.2.sp
        ),
        color = color,
        modifier = modifier,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

// endregion

// region Colour-block hero

/**
 * Colours of the colour-block panels, from [AppTheme.panel]. They are large surfaces, so in a
 * dark theme they sit on the same deep tone the launcher's widgets use rather than on
 * `primaryContainer`, which lit up half the screen. The accent itself is kept for the one
 * control on a panel that should pop.
 */
internal object PanelColors {
    val container: Color
        @Composable @ReadOnlyComposable get() = AppTheme.panel.container
    val content: Color
        @Composable @ReadOnlyComposable get() = AppTheme.panel.content
    val accent: Color
        @Composable @ReadOnlyComposable get() = AppTheme.panel.accent
    val onAccent: Color
        @Composable @ReadOnlyComposable get() = AppTheme.panel.onAccent
}

/**
 * The opening block of every collection screen: a large cover, with a colour-block panel laid
 * over its bottom edge that carries the kicker, the title in heavy type, a subtitle and the
 * actions.
 */
@Composable
internal fun CollectionHero(
    title: String,
    modifier: Modifier = Modifier,
    kicker: String? = null,
    subtitle: String? = null,
    artworkAspectRatio: Float = 1f,
    onArtworkClick: (() -> Unit)? = null,
    artwork: @Composable BoxScope.() -> Unit,
    actions: (@Composable RowScope.() -> Unit)? = null
) {
    val overlap = 44.dp
    Layout(
        modifier = modifier.fillMaxWidth(),
        content = {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(44.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .then(
                        if (onArtworkClick != null) Modifier.clickable(onClick = onArtworkClick)
                        else Modifier
                    ),
                content = artwork
            )
            Surface(
                shape = RoundedCornerShape(36.dp),
                color = PanelColors.container,
                contentColor = PanelColors.content
            ) {
                Column(modifier = Modifier.padding(start = 22.dp, top = 20.dp, end = 22.dp, bottom = 22.dp)) {
                    if (kicker != null) {
                        OnPanelChip(text = kicker)
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineMedium,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyLarge,
                            color = PanelColors.content.copy(alpha = 0.8f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (actions != null) {
                        Spacer(modifier = Modifier.height(18.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            content = actions
                        )
                    }
                }
            }
        }
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val artHeight = (width / artworkAspectRatio).roundToInt()
        val overlapPx = overlap.roundToPx()
        val art = measurables[0].measure(Constraints.fixed(width, artHeight))
        val panel = measurables[1].measure(Constraints(minWidth = width, maxWidth = width))
        val height = artHeight - overlapPx + panel.height
        layout(width, height) {
            art.place(0, 0)
            panel.place(0, artHeight - overlapPx)
        }
    }
}

/** Small tinted label on a colour-block panel. */
@Composable
internal fun OnPanelChip(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = PanelColors.content.copy(alpha = 0.12f),
        contentColor = PanelColors.content
    ) {
        Kicker(
            text = text,
            color = PanelColors.content,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

/** The main action on a panel, in the scheme's accent so it is the one thing that pops. */
@Composable
internal fun PanelPrimaryButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(56.dp),
        shape = RoundedCornerShape(20.dp),
        color = PanelColors.accent,
        contentColor = PanelColors.onAccent
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
            Text(text, style = MaterialTheme.typography.titleMedium)
        }
    }
}

/**
 * An icon action on a colour-block panel. Toggles (like, shuffle, repeat) switch on loudly:
 * the accent fill, a squarer shape and a small spring, the way the player's controls always
 * did, so their state reads at a glance.
 */
@Composable
internal fun PanelIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    size: Dp = 56.dp,
    iconSize: Dp = 24.dp
) {
    val container by animateColorAsState(
        targetValue = if (selected) PanelColors.accent else PanelColors.content.copy(alpha = 0.12f),
        animationSpec = tween(300),
        label = "panelButtonContainer"
    )
    val content by animateColorAsState(
        targetValue = if (selected) PanelColors.onAccent else PanelColors.content,
        animationSpec = tween(300),
        label = "panelButtonContent"
    )
    val corner by animateDpAsState(
        targetValue = if (selected) size * 0.3f else size / 2,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "panelButtonCorner"
    )
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.06f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "panelButtonScale"
    )
    Surface(
        onClick = onClick,
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        shape = RoundedCornerShape(corner),
        color = container,
        contentColor = content
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(iconSize))
        }
    }
}

/** Placeholder cover for collections that have no artwork of their own. */
@Composable
internal fun IconCover(icon: ImageVector, modifier: Modifier = Modifier, iconSize: Dp = 96.dp) {
    // Panel tone with the accent glyph: how the launcher draws its themed icons.
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PanelColors.container),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = PanelColors.accent,
            modifier = Modifier.size(iconSize)
        )
    }
}

// endregion

// region Cards

/**
 * "Лучший результат": the one hit worth singling out, as a compact colour-block card.
 */
@Composable
internal fun TopResultCard(
    kicker: String,
    title: String,
    subtitle: String,
    artworkUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        color = PanelColors.container,
        contentColor = PanelColors.content
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CoverImage(
                url = artworkUrl,
                size = 120.dp,
                shape = RoundedCornerShape(24.dp)
            )
            Column(modifier = Modifier.weight(1f).height(120.dp)) {
                Kicker(text = kicker, color = PanelColors.content.copy(alpha = 0.78f))
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = PanelColors.content.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .align(Alignment.End)
                        .size(44.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(PanelColors.accent),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = PanelColors.onAccent,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }
    }
}

/** Compact two-up card for playlists: small cover, two lines of title, a count. */
@Composable
internal fun CompactCollectionCard(
    title: String,
    caption: String,
    artworkUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(80.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CoverImage(url = artworkUrl, size = 60.dp, shape = RoundedCornerShape(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = caption,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

/** One album in an [AlbumCarousel]. */
internal data class CarouselAlbum(
    val key: Any,
    val title: String,
    val subtitle: String,
    val caption: String,
    val artworkUrl: String?,
    val onClick: () -> Unit
)

/**
 * Material 3's multi-browse carousel: one large cover and smaller ones shrinking toward the
 * edge. The caption under it follows whichever album is in front.
 */
@Composable
internal fun AlbumCarousel(
    albums: List<CarouselAlbum>,
    modifier: Modifier = Modifier,
    height: Dp = 200.dp
) {
    if (albums.isEmpty()) return
    val state = rememberCarouselState { albums.size }
    Column(modifier = modifier.fillMaxWidth()) {
        HorizontalMultiBrowseCarousel(
            state = state,
            preferredItemWidth = 220.dp,
            itemSpacing = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
        ) { index ->
            val album = albums[index]
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .maskClip(RoundedCornerShape(28.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable(onClick = album.onClick)
            ) {
                if (album.artworkUrl != null) {
                    AsyncImage(
                        model = artworkUrlForSize(album.artworkUrl, 240.dp),
                        contentDescription = album.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    IconCover(icon = Icons.Default.Album, iconSize = 48.dp)
                }
            }
        }
        val current = albums.getOrNull(state.currentItem) ?: albums.first()
        AnimatedContent(
            targetState = current,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            contentKey = { it.key },
            label = "albumCaption"
        ) { album ->
            Column(modifier = Modifier.padding(top = 10.dp, start = 4.dp, end = 4.dp)) {
                Text(
                    text = album.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (album.subtitle.isNotBlank()) {
                        Text(
                            text = album.subtitle + " · ",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                    Text(
                        text = album.caption,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/** Rounded cover image at a size-appropriate resolution, with a tonal placeholder. */
@Composable
internal fun CoverImage(
    url: String?,
    size: Dp,
    shape: Shape,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (url != null) {
            AsyncImage(
                model = artworkUrlForSize(url, size),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Icon(
                Icons.Default.Album,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(size * 0.42f)
            )
        }
    }
}

// endregion

// region Artwork URLs

private val SizedPathPattern = Regex("\\d{2,4}x\\d{2,4}")

/**
 * SoundCloud serves several artwork sizes. Asking for t500x500 everywhere meant a 50dp queue
 * thumbnail downloaded and decoded a 500x500 JPEG, which is pure cost on every row that scrolls
 * into view. Thresholds are generous — these are dp against a ~3x screen.
 */
internal fun artworkUrlForSize(url: String?, size: Dp): String? {
    if (url == null) return null
    if (url.contains("avatars.yandex.net") || url.contains("music-content")) {
        // Yandex encodes the dimensions in the path itself, so the SoundCloud "large" swap
        // never matched and every Yandex cover stayed at the 200x200 the mapper asked for.
        val yandexSize = when {
            size <= 64.dp -> "200x200"
            size <= 120.dp -> "400x400"
            size <= 260.dp -> "800x800"
            else -> "1000x1000"
        }
        return SizedPathPattern.replace(url, yandexSize)
    }
    val variant = when {
        size <= 64.dp -> "t200x200"
        size <= 120.dp -> "t300x300"
        else -> "t500x500"
    }
    return url.replace("large", variant)
}

// endregion
