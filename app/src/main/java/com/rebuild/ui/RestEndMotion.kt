package com.rebuild.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

// The sheet leaves fast and lands soft; it exits by gathering speed, never by reversing.
private val Rise = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
private val Exit = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
private const val RISE_MS = 400
private const val HOLD_MS = 560
private const val EXIT_MS = 320
private const val STILL_MS = 700L

private val BarHeight = 96.dp   // where the rest bar's top edge was: the sweep starts from it
private val BarCorner = 24.dp   // the rest bar's top corners, squared off as the sheet reaches the top

/**
 * The rest bar's amber, passing once over the whole screen. The sheet's top edge starts where the rest bar's was
 * and runs to the top of the screen while its corners square off; the screen stays solid amber for one beat; then
 * the sheet's bottom edge follows the top one up and off, so the screen comes back from the bottom, untouched. One
 * direction, two hard edges, no fades, no words.
 *
 * Taps during it are swallowed (the controls beneath can't be seen); it has no semantics of its own, and both edges
 * are read in the draw phase, so nothing beneath recomposes. Luminance goes up once and down once in 1.28 s, far
 * under three flashes a second, and amber is not a saturated red. With [still] (the system's animations are off)
 * it is one unmoving amber frame for 700 ms.
 */
@Composable
internal fun RestEndSweep(
    key: Any,
    still: Boolean,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    /** The rest bar's top edge in this overlay's coordinates, where the sweep starts; null when there was no bar. */
    startTop: Float? = null
) {
    val amber = MaterialTheme.colorScheme.primaryContainer
    val finished by rememberUpdatedState(onFinished)
    // front: the top edge, 0 at the rest bar's height, 1 at the top of the screen.
    // tail: the bottom edge, 0 at the bottom of the screen, 1 at the top (nothing left to draw).
    val front = remember(key) { Animatable(if (still) 1f else 0f) }
    val tail = remember(key) { Animatable(0f) }
    val sheet = remember { Path() }

    LaunchedEffect(key) {
        if (still) {
            delay(STILL_MS)
            tail.snapTo(1f)
        } else {
            front.animateTo(1f, tween(RISE_MS, easing = Rise))
            delay(HOLD_MS.toLong())
            tail.animateTo(1f, tween(EXIT_MS, easing = Exit))
        }
        finished()
    }

    Spacer(
        modifier.fillMaxSize()
            // While the screen is covered its controls can't be seen, so a tap mid-sweep is swallowed rather than
            // landing blind on Log set or Skip.
            .pointerInput(key) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                }
            }
            .drawBehind {
            val f = front.value
            val t = tail.value
            if (t >= 1f) return@drawBehind
            // From the rest bar's real top edge; without one (a preview from settings), from just above the navigation.
            val start = (startTop ?: (size.height - BarHeight.toPx())).coerceIn(0f, size.height)
            val top = start * (1f - f)
            val bottom = size.height * (1f - t)
            if (bottom <= top) return@drawBehind
            val corner = CornerRadius(BarCorner.toPx() * (1f - f))
            sheet.rewind()
            sheet.addRoundRect(
                RoundRect(left = 0f, top = top, right = size.width, bottom = bottom,
                    topLeftCornerRadius = corner, topRightCornerRadius = corner)
            )
            // No word: the amber itself is the message, and the app has no slogans to shout.
            drawPath(sheet, amber)
        }
    )
}
