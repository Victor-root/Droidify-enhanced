package com.looker.droidify.compose.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.looker.droidify.compose.theme.LocalIsTelevision

/**
 * Android TV only: gives a composable a clear focus indication, since a remote has no pointer. While
 * focused the element scales up, draws above its neighbours and gains an accent outline. A no-op on
 * touch devices (returns the receiver unchanged), so the phone UI is never affected.
 *
 * Apply it just before the element's own `clickable`/`selectable` so it observes that focus, and pass
 * the [shape] the element is clipped to so the outline matches its corners.
 */
@Composable
fun Modifier.tvFocusHighlight(
    shape: Shape,
    focusedScale: Float = 1.1f,
): Modifier {
    if (!LocalIsTelevision.current) return this
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) focusedScale else 1f, label = "tvFocusScale")
    // Animate the outline's alpha rather than adding/removing the border modifier, so the modifier
    // chain stays stable across focus changes.
    val borderAlpha by animateFloatAsState(if (focused) 1f else 0f, label = "tvFocusBorder")
    val borderColor = MaterialTheme.colorScheme.primary
    return this
        .onFocusChanged { focused = it.isFocused }
        .zIndex(if (focused) 1f else 0f)
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .border(3.dp, borderColor.copy(alpha = borderAlpha), shape)
}
