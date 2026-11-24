package com.rebuild.ui

import androidx.compose.runtime.getValue
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.background
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rebuild.platform.RestContext
import com.rebuild.ui.theme.Numerals
import kotlinx.coroutines.coroutineScope
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalDensity
import com.rebuild.ui.theme.Motion
import com.rebuild.ui.theme.motionDelay
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val Arrive = Motion.Arrive


/**
 * Edge light: the rest's end shows as light around the screen's edge, three beats a second apart that settle (peaks
 * 1.0, 0.8, 0.65; see [flashLevel]). It is a halo following the display's own rounded corners: a crisp signal-amber
 * core at the glass, a bloom, and a wide ember spill, each blurred once into a bitmap, so there are no seams and
 * nothing is redrawn per frame; only the layer's opacity animates. On the dark theme the layer is added to the screen
 * (Plus blending) rather than laid over it, so it can only brighten: never a brown film on near-black. The middle of
 * the screen, where the workout is, stays readable. Draw-only: it takes no touches. With [still] it is one soft,
 * unmoving glow for 700 ms.
 */
@Composable
internal fun RestEndGlow(key: Any, still: Boolean, onFinished: () -> Unit, modifier: Modifier = Modifier) {
    val dark = MaterialTheme.colorScheme.surface.run { red + green + blue } < 1.5f
    val view = LocalView.current
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
            .graphicsLayer {
                val t = clock.value
                alpha = if (t < 0f) 0.6f else flashLevel(t) * BEAT_PEAKS[(t / 1000f).toInt().coerceIn(0, 2)]
                if (dark) {
                    blendMode = BlendMode.Plus
                    compositingStrategy = CompositingStrategy.Offscreen
                }
            }
            .drawWithCache {
                val glow = edgeGlowBitmap(size.width.toInt(), size.height.toInt(), displayCornerPx(view, size.minDimension),
                    density, light = !dark)
                onDrawBehind { drawImage(glow, dstSize = IntSize(size.width.toInt(), size.height.toInt())) }
            }
    )
}

/** Each beat a little softer than the last, so the light settles instead of ticking like a metronome. */
private val BEAT_PEAKS = floatArrayOf(1f, 0.8f, 0.65f)

/** The display's corner radius where Android reports it (API 31+), else a typical modern phone's. */
private fun displayCornerPx(view: android.view.View, shortSide: Float): Float {
    if (android.os.Build.VERSION.SDK_INT >= 31) {
        view.rootWindowInsets?.getRoundedCorner(android.view.RoundedCorner.POSITION_TOP_LEFT)?.let {
            return it.radius.toFloat()
        }
    }
    return shortSide * 0.07f
}

/**
 * The halo, drawn once at half resolution (blur hides the difference and it costs a quarter of the memory): three
 * strokes along the screen's rounded edge, each blurred, from a crisp core out to a wide spill. Strokes sit on the
 * edge, so their outer half falls off-screen and the light reads as coming from the glass.
 */
private fun edgeGlowBitmap(width: Int, height: Int, cornerPx: Float, density: Float, light: Boolean): ImageBitmap {
    val scale = 0.5f
    val w = (width * scale).toInt().coerceAtLeast(1)
    val h = (height * scale).toInt().coerceAtLeast(1)
    val bitmap = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    val edge = android.graphics.RectF(0f, 0f, w.toFloat(), h.toFloat())
    val r = cornerPx * scale
    fun dp(v: Float) = v * density * scale
    // (stroke width, blur radius, colour, alpha): core, bloom, spill. On the light theme the spill is kept narrow and
    // faint, or it turns the cream canvas into an orange haze.
    val layers = listOf(
        Triple(dp(2.5f), 0f, 0xFFFFB224.toInt()) to 1f,
        Triple(dp(14f), dp(22f), 0xFFFF9A1F.toInt()) to 0.85f,
        Triple(dp(if (light) 32f else 40f), dp(48f), 0xFFFF7A00.toInt()) to if (light) 0.15f else 0.28f
    )
    layers.asReversed().forEach { (spec, alpha) ->
        val (stroke, blur, color) = spec
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = stroke * 2
            this.color = color
            this.alpha = (alpha * 255).toInt()
            if (blur > 0f) maskFilter = android.graphics.BlurMaskFilter(blur, android.graphics.BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawRoundRect(edge, r, r, paint)
    }
    return bitmap.asImageBitmap()
}

/**
 * Up next: the rest bar becomes the whole screen and says what to do now, with the number as the hero. The sheet
 * springs from where the bar was to the top of the screen; "Rest over" and the exercise fade in; the set numeral
 * rolls like an odometer, the finished set's number leaving upward as the next one arrives, so the motion itself says
 * the count went up; one firm tick when the sheet lands. A thin ink line under the status bar drains through the hold,
 * telling the user it leaves on its own; then the words go and the sheet drops away. A tap sends it early, and
 * nothing beneath can be pressed blind while it covers the screen. With [still] it is the finished frame for 1.5 s.
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
    val amber = MaterialTheme.colorScheme.primaryContainer // the sheet is a fill: softened on dark
    val ink = MaterialTheme.colorScheme.onPrimaryContainer
    val finished by rememberUpdatedState(onFinished)
    val haptic = LocalHapticFeedback.current
    val start = if (still) 1f else 0f
    val cover = remember(key) { Animatable(start) }
    val words = remember(key) { Animatable(start) }
    val roll = remember(key) { Animatable(start) }
    val hold = remember(key) { Animatable(0f) }
    val wordsOut = remember(key) { Animatable(0f) }
    val leave = remember(key) { Animatable(0f) }
    val early = remember(key) { kotlinx.coroutines.CompletableDeferred<Unit>() }
    val nextSet = about?.nextSet?.takeIf { !shortPause }
    LaunchedEffect(key) {
        if (!still) coroutineScope {
            // A firm spring: it arrives with intent and settles without drifting; one tick as it lands.
            // The tick lands with the sheet: in the animation frame it first reaches 98 % (where it looks landed),
            // not when the spring has crept to its threshold, which is visibly later.
            launch {
                var ticked = false
                cover.animateTo(1f, spring(dampingRatio = 0.92f, stiffness = 420f)) {
                    if (!ticked && value >= 0.98f) {
                        ticked = true
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                }
            }
            // Once the sheet is most of the way up: the small lines simply fade in, the numeral rolls.
            launch { motionDelay(180); words.animateTo(1f, tween(150, easing = LinearEasing)) }
            launch { motionDelay(170); roll.animateTo(1f, tween(420, easing = Arrive)) }
        }
        // Hold long enough to read from arm's length, the ink line draining as it goes, unless a tap asks it to go.
        if (still) kotlinx.coroutines.withTimeoutOrNull(1_500) { early.await() }
        else kotlinx.coroutines.withTimeoutOrNull(HOLD_MS.toLong()) {
            coroutineScope {
                val drain = launch { hold.animateTo(1f, tween(HOLD_MS, easing = LinearEasing)) }
                early.await()
                drain.cancel()
            }
        }
        if (!still) {
            // The words leave first, so they don't ride the sheet down like a sticker; then the sheet drops.
            wordsOut.animateTo(1f, tween(120, easing = LinearEasing))
            leave.animateTo(1f, Motion.sheetOutSpec())
        }
        finished()
    }
    val density = LocalDensity.current
    /** The sheet's top edge: from the rest bar's top (or near the foot) up to the screen's top as it covers. */
    fun sheetTop(height: Float): Float {
        val from = (startTop ?: (height - with(density) { 120.dp.toPx() })).coerceIn(0f, height)
        return from * (1f - cover.value.coerceIn(0f, 1f))
    }
    val numeral = MaterialTheme.typography.displayLarge.copy(fontFamily = Numerals, fontWeight = FontWeight.Bold,
        fontSize = 240.sp, lineHeight = 200.sp, letterSpacing = (-6).sp)
    val name = MaterialTheme.typography.headlineMedium.copy(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Medium)
    Box(
        modifier
            .fillMaxSize()
            .testTag("rest-end-takeover")
            // TalkBack and Switch Access can send it away early too, not only a finger.
            .semantics {
                onClick(label = "Dismiss") { early.complete(Unit); true }
                dismiss(label = "Dismiss") { early.complete(Unit); true }
            }
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
            // Nothing of the takeover is drawn outside its sheet: the old numeral, at full ink from the start, would
            // otherwise sit over the uncovered workout while the sheet rises.
            .drawWithContent { clipRect(top = sheetTop(size.height)) { this@drawWithContent.drawContent() } }
            .drawBehind {
                val c = cover.value.coerceIn(0f, 1f)
                val top = sheetTop(size.height)
                val radius = 24.dp.toPx() * (1f - c)
                drawRoundRect(amber, topLeft = Offset(0f, top), size = Size(size.width, size.height - top + radius),
                    cornerRadius = CornerRadius(radius))
            }
    ) {
        // The hold, as a line under the status bar that drains away: it leaves on its own.
        Box(
            Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .fillMaxWidth()
                .height(3.dp)
                .graphicsLayer {
                    alpha = words.value * (1f - wordsOut.value)
                    scaleX = 1f - hold.value
                    transformOrigin = TransformOrigin(0f, 0.5f)
                }
                .background(ink.copy(alpha = 0.3f))
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 28.dp, end = 28.dp, bottom = 40.dp)
                .graphicsLayer {
                    alpha = 1f - wordsOut.value
                    translationY = -8.dp.toPx() * wordsOut.value
                }
        ) {
            val fade = Modifier.graphicsLayer { alpha = words.value }
            // One small label above the hero: "Up next" says it all when the exercise is done.
            val movingOn = nextSet == null && !shortPause && about?.nextExercise != null
            if (!movingOn) Text(if (shortPause) "Pause over" else "Rest over", color = ink.copy(alpha = 0.7f),
                style = MaterialTheme.typography.titleMedium, modifier = fade)
            val exercise = about?.exercise?.takeIf { it.isNotBlank() }
            when {
                nextSet != null -> {
                    if (exercise != null) Text(exercise, color = ink, style = name, maxLines = 2,
                        overflow = TextOverflow.Ellipsis, modifier = fade.padding(top = 4.dp))
                    Row(Modifier.testTag("rest-end-headline")) {
                        // The odometer: the finished set's number leaves upward as the next arrives from below. Both
                        // sit on one baseline with "of 3", which is where the eye reads them together.
                        Box(Modifier.alignByBaseline().clipToBounds()) {
                            if (nextSet > 1) Text("${nextSet - 1}", color = ink, style = numeral,
                                modifier = Modifier.graphicsLayer {
                                    translationY = -size.height * roll.value
                                    // Gone by the time it is half out, so it never shows as a clipped stub.
                                    alpha = (1f - roll.value * 2f).coerceIn(0f, 1f)
                                })
                            Text("$nextSet", color = ink, style = numeral, modifier = Modifier.graphicsLayer {
                                translationY = size.height * (1f - roll.value)
                            })
                        }
                        about.ofSets?.let { of ->
                            Text("of $of", color = ink.copy(alpha = 0.55f), modifier = fade.alignByBaseline().padding(start = 8.dp),
                                style = MaterialTheme.typography.displaySmall.copy(fontFamily = Numerals,
                                    fontWeight = FontWeight.Bold, fontSize = 40.sp))
                        }
                    }
                }
                else -> {
                    // No set number to roll: the next exercise, or the plain fact, set large.
                    val (small, big) = when {
                        shortPause -> exercise to "Go"
                        about?.nextExercise != null -> "Up next" to about.nextExercise
                        else -> exercise to "All sets done"
                    }
                    if (small != null) Text(small, color = ink, style = name, modifier = fade.padding(top = 4.dp))
                    Text(big, color = ink, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        style = numeral.copy(fontSize = if (big.length > 3) 64.sp else 240.sp,
                            lineHeight = if (big.length > 3) 66.sp else 200.sp,
                            letterSpacing = if (big.length > 3) 0.sp else (-6).sp),
                        modifier = Modifier.graphicsLayer {
                            alpha = roll.value
                            translationY = (1f - roll.value) * 32.dp.toPx()
                        }.testTag("rest-end-headline"))
                }
            }
        }
    }
}

private const val HOLD_MS = 1_800
