package com.looker.droidify.compose.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.looker.droidify.compose.theme.LocalIsTelevision

/**
 * Android TV only: the focused element scales up. Layout-neutral (draw-only), so it never shifts its
 * neighbours; safe in non-scrolling rows like the top bar and for compact items like app tiles. A
 * no-op on touch. Not suited to full-width rows, whose scaled width would overflow the screen — use
 * [tvFocusFill] there instead.
 */
@Composable
fun Modifier.tvFocusScale(focusedScale: Float = 1.15f): Modifier {
    if (!LocalIsTelevision.current) return this
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) focusedScale else 1f, label = "tvFocusScale")
    return this
        .onFocusChanged { focused = it.isFocused }
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
}

/**
 * D-pad bridge: when the focused descendant is at the bottom of this container and the user presses
 * "down", hand focus to [target] (typically the screen content below a top bar or tab row). Material3's
 * top bars and tab rows don't release focus downward on their own, leaving a remote user stuck in the
 * header; this is the explicit escape hatch. A no-op on touch (touch never sends D-pad keys), so it
 * needs no television guard.
 */
fun Modifier.tvDpadDownTo(target: FocusRequester): Modifier =
    onPreviewKeyEvent { event ->
        if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
            runCatching { target.requestFocus() }.isSuccess
        } else {
            false
        }
    }

/**
 * Android TV only: scales the focused element up *and* draws an accent ring around it, for a focus cue
 * that's obvious even on a filled button seen from across the room (a ring alone can vanish against a
 * same-coloured fill). Draw-only (graphicsLayer + border), so it never shifts neighbours; suited to
 * compact controls (buttons, chips, the favourite toggle). A no-op on touch.
 */
@Composable
fun Modifier.tvFocusScaleOutline(
    shape: Shape,
    color: Color = MaterialTheme.colorScheme.primary,
    focusedScale: Float = 1.08f,
    width: Dp = 3.dp,
): Modifier {
    if (!LocalIsTelevision.current) return this
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) focusedScale else 1f, label = "tvFocusScaleOutlineScale")
    val alpha by animateFloatAsState(if (focused) 1f else 0f, label = "tvFocusScaleOutlineRing")
    return this
        .onFocusChanged { focused = it.isFocused }
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .border(width, color.copy(alpha = alpha), shape)
}

/**
 * Android TV only: fills the background of a focused composable with a soft accent tint, aligned to
 * the element's own bounds and [shape]. Layout-neutral (only a background draw, no size change), so it
 * is safe inside scrolling lists and is the right choice for full-width rows (section headers, list
 * rows) where a scale would overflow the screen edges. A no-op on touch.
 *
 * The [clip] is applied first, above the caller's `clickable`, so the focused element's default
 * Material focus indication (a grey state layer drawn on the clickable's own rectangular bounds) is
 * forced into the same rounded [shape]; otherwise that grey layer stays a hard square regardless of
 * the fill shape.
 */
@Composable
fun Modifier.tvFocusFill(
    shape: Shape,
    color: Color = MaterialTheme.colorScheme.primary,
): Modifier {
    if (!LocalIsTelevision.current) return this
    var focused by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(if (focused) 0.16f else 0f, label = "tvFocusFill")
    return this
        .clip(shape)
        .onFocusChanged { focused = it.isFocused }
        .background(color.copy(alpha = alpha), shape)
}

/**
 * Android TV only: draws an accent outline around a focused composable. Layout-neutral, so it is safe
 * in scrolling lists. A no-op on touch. Still used on the detail screen; the home screen now uses
 * [tvFocusScale] / [tvFocusFill] for a cleaner look.
 */
@Composable
fun Modifier.tvFocusOutline(
    shape: Shape,
    color: Color = MaterialTheme.colorScheme.primary,
    width: Dp = 3.dp,
): Modifier {
    if (!LocalIsTelevision.current) return this
    var focused by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(if (focused) 1f else 0f, label = "tvFocusOutline")
    return this
        .onFocusChanged { focused = it.isFocused }
        .border(width, color.copy(alpha = alpha), shape)
}
