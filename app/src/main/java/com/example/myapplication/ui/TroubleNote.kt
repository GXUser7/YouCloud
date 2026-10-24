package com.example.myapplication.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.myapplication.i18n.tr
import kotlinx.coroutines.launch

/**
 * What stands where something didn't come — a shelf of home, a search, an artist's or a
 * collection's page: on matte glass, a mark of what went wrong, what it was, and the button that
 * tries again. The same everywhere, where a slab of red at the top of a page said it before, in a
 * colour nothing else in the app has, and most of them with nothing to do about it.
 */
@Composable
internal fun TroubleNote(
    text: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    icon: ImageVector = troubleIcon(text),
    action: String? = null,
    actionIcon: ImageVector = Icons.Rounded.Refresh,
    onAction: (() -> Unit)? = null,
    // A second way out, beside the first and quieter: setting it right by hand, say.
    other: String? = null,
    onOther: (() -> Unit)? = null
) {
    val shape = RoundedCornerShape(28.dp)
    val fill = MaterialTheme.colorScheme.surfaceContainer
    val accent = MaterialTheme.colorScheme.primary
    Column(
        modifier = modifier
            .fillMaxWidth()
            .glassOr(shape, fill)
            .background(glassFill(fill), shape)
            .padding(start = 18.dp, top = 18.dp, end = 18.dp, bottom = if (onAction != null) 14.dp else 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                modifier = Modifier.size(44.dp).background(accent.copy(alpha = 0.16f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(24.dp))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (title != null) {
                    Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                }
                Text(
                    text = text,
                    style = if (title != null) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
                    color = if (title != null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                )
            }
        }
        if (onAction != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (other != null && onOther != null) {
                    TroubleAction(text = other, icon = null, onClick = onOther, modifier = Modifier.weight(1f), quiet = true)
                }
                TroubleAction(text = action ?: tr("Повторить"), icon = actionIcon, onClick = onAction, modifier = Modifier.weight(1f))
            }
        }
    }
}

/**
 * The note's button, the width of it: in the accent, squarer and a little smaller while held, as
 * the player's buttons give under a finger; its arrow goes once round on a tap, the try under way.
 */
@Composable
private fun TroubleAction(
    text: String,
    icon: ImageVector?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    // The second button: the accent faint behind it, not filling it.
    quiet: Boolean = false
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val corner by animateDpAsState(
        targetValue = if (pressed) 14.dp else 26.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "troubleActionCorner"
    )
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "troubleActionScale"
    )
    val turn = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    Surface(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            scope.launch { turn.animateTo(turn.value + 360f, tween(700, easing = FastOutSlowInEasing)) }
            onClick()
        },
        modifier = modifier
            .height(52.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        shape = RoundedCornerShape(corner),
        color = if (quiet) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else MaterialTheme.colorScheme.primary,
        contentColor = if (quiet) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimary,
        interactionSource = interaction
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp).graphicsLayer { rotationZ = turn.value })
            }
            Text(text, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** No network: a cloud struck through; anything else, the plain mark of something wrong. */
internal fun troubleIcon(text: String): ImageVector =
    if (text == tr("Нет соединения с сетью.")) Icons.Rounded.CloudOff else Icons.Rounded.ErrorOutline
