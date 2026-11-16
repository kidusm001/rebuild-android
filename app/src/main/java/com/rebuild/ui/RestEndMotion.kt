package com.rebuild.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rebuild.platform.RestContext
import com.rebuild.ui.theme.Numerals
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Material's emphasized accelerate: leaves gathering speed, never reversing. */
private val Leave = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
/** Material's emphasized decelerate: arrives fast and settles soft. */
private val Arrive = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)


/**
 * Edge light: the rest's end glows in from every edge of the screen, hottest at the rim and feathering to nothing
 * inward, along rounded corners that follow the display, three smooth beats a second apart ([flashLevel]). The
 * middle of the screen, where the workout is, stays readable; the edges carry the signal, which is what reads from
 * across a room. White-hot rim into amber on the dark theme, deep amber on the light one. Draw-only: it takes no
 * touches. With [still] it is one soft, unmoving glow for 700 ms.
 */
@Composable
internal fun RestEndGlow(key: Any, still: Boolean, onFinished: () -> Unit, modifier: Modifier = Modifier) {
    val amber = MaterialTheme.colorScheme.primaryContainer
    val dark = MaterialTheme.colorScheme.surface.run { red + green + blue } < 1.5f
    val rim = if (dark) Color.White else amber
    val body = if (dark) amber else Color(0xFFFF9500)
    val finished by rememberUpdatedState(onFinished)
    val clock = remember(key) { Animatable(0f) }
    LaunchedEffect(key) {
        if (still) {
            clock.snapTo(-1f)
            delay(700)
        } else {
            clock.animateTo(FLASH_TOTAL_MS, tween(FLASH_TOTAL_MS.toInt(), easing = LinearEasing))
        }
        finished()
    }
    Spacer(
        modifier
            .fillMaxSize()
            .testTag("rest-end-glow")
            // The beat only changes the layer's opacity: the glow is drawn once, so each frame costs a composite,
            // not seven new gradients.
            .graphicsLayer {
                val t = clock.value
                alpha = if (t < 0f) 0.6f else flashLevel(t)
            }
            .drawWithCache {
                val d = size.minDimension * 0.30f
                val w = size.width
                val h = size.height
                // One falloff for every piece, so edges and corners meet seamlessly: hot at the glass, warm, gone.
                val stops = arrayOf(
                    0f to rim,
                    0.18f to lerp(rim, body, 0.6f).copy(alpha = 0.95f),
                    0.5f to body.copy(alpha = 0.5f),
                    1f to body.copy(alpha = 0f)
                )
                val inward = stops.map { (f, c) -> (1f - f) to c }.reversed().toTypedArray()
                val top = Brush.verticalGradient(*stops, startY = 0f, endY = d)
                val bottom = Brush.verticalGradient(*stops, startY = h, endY = h - d)
                val left = Brush.horizontalGradient(*stops, startX = 0f, endX = d)
                val right = Brush.horizontalGradient(*stops, startX = w, endX = w - d)
                // Corners: the same falloff radially from inside each corner, so the glow rounds it instead of
                // doubling up in it.
                val corners = listOf(Offset(0f, 0f), Offset(w - d, 0f), Offset(0f, h - d), Offset(w - d, h - d)).map { at ->
                    at to Brush.radialGradient(*inward,
                        center = Offset(if (at.x == 0f) d else at.x, if (at.y == 0f) d else at.y), radius = d)
                }
                onDrawBehind {
                    // A faint veil lifts the whole screen with the beat; the rim does the talking.
                    drawRect(body.copy(alpha = 0.08f))
                    drawRect(top, Offset(d, 0f), Size(w - 2 * d, d))
                    drawRect(bottom, Offset(d, h - d), Size(w - 2 * d, d))
                    drawRect(left, Offset(0f, d), Size(d, h - 2 * d))
                    drawRect(right, Offset(w - d, d), Size(d, h - 2 * d))
                    corners.forEach { (at, brush) -> drawRect(brush, at, Size(d, d)) }
                }
            }
    )
}

/**
 * Up next: the rest bar becomes the whole screen and says what to do now. Its top edge springs from where the bar
 * was to the top of the screen while its corners square off; "Rest over", the next set in huge numerals ("Set 2")
 * and what it belongs to ("of 3 · Bench press") rise into place one after another; it holds for a breath, then the
 * whole sheet drops away and the workout is back exactly as it was. A tap anywhere sends it away early; nothing
 * beneath can be pressed blind while it covers the screen. With [still] it is the finished frame for 1.5 s.
 */
@Composable
internal fun RestEndTakeover(
    key: Any,
    still: Boolean,
    about: RestContext?,
    shortPause: Boolean,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    /** The rest bar's top edge, where the sheet grows from; null when there was no bar (a preview). */
    startTop: Float? = null
) {
    val amber = MaterialTheme.colorScheme.primaryContainer
    val ink = MaterialTheme.colorScheme.onPrimaryContainer
    val finished by rememberUpdatedState(onFinished)
    val cover = remember(key) { Animatable(if (still) 1f else 0f) }
    val words = remember(key) { Animatable(if (still) 1f else 0f) }
    val leave = remember(key) { Animatable(0f) }
    val early = remember(key) { kotlinx.coroutines.CompletableDeferred<Unit>() }
    LaunchedEffect(key) {
        if (!still) coroutineScope {
            launch { cover.animateTo(1f, spring(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow)) }
            launch { delay(110); words.animateTo(1f, tween(620, easing = Arrive)) }
        }
        // Hold long enough to read "Set 2" from arm's length, unless a tap asks it to go.
        kotlinx.coroutines.withTimeoutOrNull(if (still) 1_500 else 1_650) { early.await() }
        if (!still) leave.animateTo(1f, tween(340, easing = Leave))
        finished()
    }
    val headline = about?.headline ?: if (shortPause) "Go again" else "Rest over"
    val detail = about?.detail
    val long = headline.length > 9
    Box(
        modifier
            .fillMaxSize()
            .testTag("rest-end-takeover")
            .pointerInput(key) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        event.changes.forEach { it.consume() }
                        if (event.changes.any { !it.pressed }) early.complete(Unit)
                    }
                }
            }
            .graphicsLayer { translationY = size.height * leave.value }
            .drawBehind {
                val from = (startTop ?: (size.height - 120.dp.toPx())).coerceIn(0f, size.height)
                val c = cover.value
                val top = from * (1f - c)
                val radius = 24.dp.toPx() * (1f - c).coerceIn(0f, 1f)
                drawRoundRect(amber, topLeft = Offset(0f, top), size = Size(size.width, size.height - top + radius),
                    cornerRadius = CornerRadius(radius))
            }
    ) {
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .safeDrawingPadding()
                .padding(start = 28.dp, end = 28.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Each line rises a little and fades in after the one above it.
            fun Modifier.rise(from: Float, to: Float) = graphicsLayer {
                val p = ((words.value - from) / (to - from)).coerceIn(0f, 1f)
                alpha = p
                translationY = (1f - p) * 28.dp.toPx()
            }
            Text(if (shortPause) "PAUSE OVER" else "REST OVER", color = ink.copy(alpha = 0.72f),
                style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 2.sp, fontWeight = FontWeight.Bold),
                modifier = Modifier.rise(0f, 0.55f))
            Text(headline, color = ink, maxLines = 2, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.displayLarge.copy(fontFamily = Numerals, fontWeight = FontWeight.Bold,
                    fontSize = if (long) 56.sp else 112.sp, lineHeight = if (long) 58.sp else 108.sp,
                    letterSpacing = if (long) 0.sp else (-2).sp),
                modifier = Modifier.rise(0.12f, 0.75f).testTag("rest-end-headline"))
            if (detail != null) Text(detail, color = ink.copy(alpha = 0.8f), maxLines = 2,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Medium),
                modifier = Modifier.rise(0.28f, 1f))
        }
    }
}
