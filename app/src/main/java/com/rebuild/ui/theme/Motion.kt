package com.rebuild.ui.theme

import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.MotionDurationScale
import kotlinx.coroutines.delay
import kotlin.coroutines.coroutineContext

/**
 * Every duration, curve, distance and rule the app moves by. Screens name a rule from here and never write their own
 * `tween(…)` or `spring(…)` (the Up next takeover in RestEndMotion.kt is the one tuned exception).
 *
 * Animation times follow Android's animator duration scale (Compose reads it through [MotionDurationScale]); at scale 0
 * every rule ends in its first frame. The information thresholds ([BusyAfterMs], [SplashCapMs]) are real time at every
 * scale: they say whether something is slow, not how fast something moves.
 */
object Motion {
    // Durations (ms).
    const val Fast = 100
    const val Short = 150
    const val Medium = 220
    const val Long = 300

    // Real-time thresholds (ms): never scaled.
    /** A save running longer than this shows the busy look. */
    const val BusyAfterMs = 300L
    /** Longest the splash waits for data before the loading screen. */
    const val SplashCapMs = 2000L

    // Easing.
    /** Size and position changes within a screen. */
    val Standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    /** Anything that travels while fading in: most of the travel happens where it can be seen. */
    val Travel: Easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
    /** Opaque sheets coming in: no fade, so the early travel is seen. */
    val Arrive: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    /** Opaque sheets going out: gathering speed, never reversing. */
    val Leave: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    // Springs.
    /** Drag lift and drop, reorder placement, predictive-back cancel: 85 % at 120 ms, 99 % at 235 ms, no overshoot. */
    fun <T> settle(): SpringSpec<T> = spring(dampingRatio = 1f, stiffness = 800f)
    /** The pill-tab press squish. */
    fun <T> press(): SpringSpec<T> = spring(stiffness = Spring.StiffnessMediumLow)

    // Distances.
    /** Lateral travel of the top or peer layer. */
    val Shift: Dp = 32.dp
    /** Lateral travel of the layer beneath. */
    val Parallax: Dp = 8.dp
    /** Rise of content arriving in place. */
    val Nudge: Dp = 8.dp
    /** A held pill's scale and shadow. */
    const val LiftScale = 1.06f
    val LiftShadow: Dp = 8.dp
    /** The predictive-back preview: scale, travel toward the edge, corner radius. */
    const val BackPeekScale = 0.94f
    val BackPeekOffset: Dp = 24.dp
    val BackPeekCorner: Dp = 28.dp
    /** The scrim behind a sheet. */
    const val ScrimAlpha = 0.32f

    // Rules: animation specs. Each inventory entry of the motion spec names exactly one of these.

    /** Fade in: alpha 0→1 over [Short], linear, from t = 0. Readable (alpha >= 0.5) by 75 ms. */
    fun <T> fadeInSpec(): FiniteAnimationSpec<T> = tween(Short, easing = LinearEasing)
    /** Fade out: alpha 1→0 over [Fast], linear. Gone by 100 ms. */
    fun <T> fadeOutSpec(): FiniteAnimationSpec<T> = tween(Fast, easing = LinearEasing)
    /** Colour: [Short], linear. */
    fun <T> colourSpec(): FiniteAnimationSpec<T> = tween(Short, easing = LinearEasing)
    /** The size part of Expand: [Medium], [Standard]. */
    fun <T> expandSizeSpec(): FiniteAnimationSpec<T> = tween(Medium, easing = Standard)
    /** The size part of Collapse: [Short], [Standard]. */
    fun <T> collapseSizeSpec(): FiniteAnimationSpec<T> = tween(Short, easing = Standard)
    /** Move within: offset or position inside a screen, [Medium], [Standard]. */
    fun <T> moveWithinSpec(): FiniteAnimationSpec<T> = tween(Medium, easing = Standard)
    /** Page: a programmatic pager move, [Long], [Standard]. */
    fun <T> pageSpec(): FiniteAnimationSpec<T> = tween(Long, easing = Standard)
    /** Travel of a Lateral enter: [Long] for navigation, [Medium] inside the workout; [Travel]. */
    fun <T> lateralEnterTravelSpec(navigation: Boolean): FiniteAnimationSpec<T> =
        tween(if (navigation) Long else Medium, easing = Travel)
    /** A Lateral exit's alpha and travel together: [Fast], linear. */
    fun <T> lateralExitSpec(): FiniteAnimationSpec<T> = tween(Fast, easing = LinearEasing)
    /** Chrome shift: the shared bottom bars move by the navigation bar's height, [Fast], [Standard]. */
    fun <T> chromeShiftSpec(): FiniteAnimationSpec<T> = tween(Fast, easing = Standard)
    /** Sheet in: travel from its full height, [Long], [Arrive]. 98 % of the travel is done by 200 ms. */
    fun <T> sheetInSpec(): FiniteAnimationSpec<T> = tween(Long, easing = Arrive)
    /** The scrim of Sheet in: 0→[ScrimAlpha] over [Medium], linear. */
    fun <T> scrimInSpec(): FiniteAnimationSpec<T> = tween(Medium, easing = LinearEasing)
    /** Sheet out: travel to its full height, [Medium], [Leave]. */
    fun <T> sheetOutSpec(): FiniteAnimationSpec<T> = tween(Medium, easing = Leave)
    /** The scrim of Sheet out: to 0 over [Fast], linear. */
    fun <T> scrimOutSpec(): FiniteAnimationSpec<T> = tween(Fast, easing = LinearEasing)

    // Rules: transitions for AnimatedVisibility / AnimatedContent.

    fun fadeIn(): EnterTransition = fadeIn(fadeInSpec())
    fun fadeOut(): ExitTransition = fadeOut(fadeOutSpec())

    /**
     * Crossfade: Fade out of A and Fade in of B in the same place, both from t = 0; never both above 0.5. Only for
     * content on a shared, persistent background and whole screen layers over the canvas, never for two opaque
     * surfaces in one place (that is [FadeInOver]).
     */
    fun crossfade(): ContentTransform = fadeIn() togetherWith fadeOut()

    /** Expand: size by [expandSizeSpec] from [from], content Fade in. */
    fun expand(from: Alignment.Vertical = Alignment.Top): EnterTransition =
        expandVertically(expandSizeSpec(), expandFrom = from) + fadeIn()

    /** Collapse: size by [collapseSizeSpec] towards [towards], content Fade out. */
    fun collapse(towards: Alignment.Vertical = Alignment.Top): ExitTransition =
        shrinkVertically(collapseSizeSpec(), shrinkTowards = towards) + fadeOut()

    /** Expand (opaque bar): the size part only, for an opaque bar whose travelling edge is the motion. */
    fun expandBar(from: Alignment.Vertical = Alignment.Top): EnterTransition =
        expandVertically(expandSizeSpec(), expandFrom = from)

    /** Collapse (opaque bar): the size part only. */
    fun collapseBar(towards: Alignment.Vertical = Alignment.Top): ExitTransition =
        shrinkVertically(collapseSizeSpec(), shrinkTowards = towards)

    /** The travel of a Rise: [Medium], [Travel]. */
    fun <T> riseSpec(): FiniteAnimationSpec<T> = tween(Medium, easing = Travel)

    /** Rise: Fade in plus [Nudge] of upward travel over [Medium], [Travel]. */
    fun rise(nudgePx: Int): EnterTransition =
        fadeIn() + slideInVertically(tween(Medium, easing = Travel)) { nudgePx }

    /** The travel of a Roll: [Short], [Travel], no alpha. */
    fun <T> rollSpec(): FiniteAnimationSpec<T> = tween(Short, easing = Travel)

    /**
     * Roll: a count changes like an odometer, clipped to its line box. [up] (the count grew): the new value arrives
     * from below as the old one leaves upward; down is the reverse.
     */
    fun roll(up: Boolean): ContentTransform {
        val dir = if (up) 1 else -1
        return ContentTransform(
            slideInVertically(rollSpec()) { dir * it },
            slideOutVertically(rollSpec()) { -dir * it },
            sizeTransform = SizeTransform(clip = true)
        )
    }

    /** The size transform for a surface that grows by Expand's size spec and shrinks by Collapse's. */
    fun expandOrCollapse(clip: Boolean = false): SizeTransform = SizeTransform(clip) { initial, target ->
        if (target.height >= initial.height) expandSizeSpec() else collapseSizeSpec()
    }
}

/**
 * The Crossfade rule as a container, for content on a shared, persistent background (labels, digits, chart drawings):
 * the old content fades out by Fade out while the new fades in by Fade in, in the same place, no size animation.
 * [contentKey] decides what counts as new content. The alpha is applied to each draw (no offscreen copy), which is
 * right for text and drawings on the background and keeps a crossfade from costing an extra layer per frame.
 */
@Composable
fun <S> MotionCrossfade(
    targetState: S,
    modifier: Modifier = Modifier,
    contentKey: (S) -> Any? = { it },
    label: String = "crossfade",
    content: @Composable (S) -> Unit
) = AnimatedContent(
    targetState = targetState,
    modifier = modifier,
    contentKey = contentKey,
    transitionSpec = {
        ContentTransform(EnterTransition.None, ExitTransition.KeepUntilTransitionsFinished, sizeTransform = null)
    },
    label = label
) { shown ->
    val alpha by transition.animateFloat(transitionSpec = {
        if (targetState == EnterExitState.Visible) Motion.fadeInSpec() else Motion.fadeOutSpec()
    }, label = "crossfade-alpha") { if (it == EnterExitState.Visible) 1f else 0f }
    Box(Modifier.graphicsLayer {
        this.alpha = alpha
        compositingStrategy = CompositingStrategy.ModulateAlpha
    }) { content(shown) }
}

/**
 * Waits [ms] scaled by the animator duration scale, for the takeover's tuned timings only (the rules have no delays).
 * Outside an animation context the scale is 1.
 */
suspend fun motionDelay(ms: Long) {
    val scale = coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f
    if (scale > 0f) delay((ms * scale).toLong())
}

/** True when Android's animator duration scale is 0 (Remove animations): for the few places that change behaviour. */
@Composable
fun animationsOff(): Boolean {
    if (LocalInspectionMode.current) return false
    val context = LocalContext.current
    return remember(context) {
        runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) }
            .getOrDefault(1f) == 0f
    }
}

/**
 * Fade in over: replaces an opaque surface in place. The outgoing surface stays at alpha 1 underneath; the incoming one,
 * opaque over its whole rect (its container colour drawn behind its content), fades in on top by Fade in; the outgoing
 * is removed the frame the incoming reaches 1. Any part of the outgoing outside the incoming's rect fades out by Fade
 * out. Identical pixels therefore stay at 100 % at every frame, and pixels that differ truly crossfade.
 *
 * [contentKey] decides what counts as a new surface; the size follows by Expand (growing) or Collapse (shrinking), so
 * the content below moves instead of jumping. [contentAlignment] is where both copies sit while the size changes (a
 * bottom-pinned dock keeps its foot still with [Alignment.BottomStart]); [clip] reveals and hides by the moving edge;
 * [backing] is the container colour drawn behind each copy, for content that does not paint its own.
 */
@Composable
fun <S> FadeInOver(
    targetState: S,
    modifier: Modifier = Modifier,
    contentKey: (S) -> Any? = { it },
    contentAlignment: Alignment = Alignment.TopStart,
    clip: Boolean = false,
    backing: Color = Color.Unspecified,
    label: String = "fade-in-over",
    content: @Composable AnimatedVisibilityScope.(S) -> Unit
) {
    val sizes = remember { mutableStateMapOf<Any?, IntSize>() }
    val targetKey = contentKey(targetState)
    // Read through a state: the copy on screen follows what its caller passes now, not what it passed when it came in.
    val latest by rememberUpdatedState(content)
    AnimatedContent(
        targetState = targetState,
        modifier = modifier,
        contentKey = contentKey,
        contentAlignment = contentAlignment,
        transitionSpec = {
            ContentTransform(Motion.fadeIn(), ExitTransition.KeepUntilTransitionsFinished,
                sizeTransform = Motion.expandOrCollapse(clip = clip))
        },
        label = label
    ) { shown ->
        val key = contentKey(shown)
        // The outgoing copy's overhang (outside the incoming rect) fades by Fade out; inside it stays at 1.
        val overhang by transition.animateFloat(transitionSpec = { Motion.fadeOutSpec() }, label = "overhang") {
            if (it == EnterExitState.PostExit) 0f else 1f
        }
        Box(
            Modifier
                .onSizeChanged { sizes[key] = it }
                .fadeOver(incoming = { sizes[targetKey].takeIf { key != targetKey } }, overhang = { overhang },
                    alignment = contentAlignment)
                .then(if (backing.isSpecified) Modifier.background(backing) else Modifier)
        ) { latest(this@AnimatedContent, shown) }
    }
}

/**
 * The outgoing side of [FadeInOver], for copies laid out by hand: drawn at alpha 1 inside the incoming copy's rect
 * ([incoming] is its size, null when this copy is not leaving), and at [overhang] outside it. The two copies are aligned
 * by [alignment] in the same space.
 */
fun Modifier.fadeOver(incoming: () -> IntSize?, overhang: () -> Float, alignment: Alignment = Alignment.TopStart): Modifier =
    drawWithContent {
        val size = incoming()
        if (size == null) {
            drawContent()
            return@drawWithContent
        }
        val at = alignment.align(size, IntSize(this.size.width.toInt(), this.size.height.toInt()), layoutDirection)
        val l = at.x.toFloat()
        val t = at.y.toFloat()
        val r = l + size.width
        val b = t + size.height
        clipRect(l, t, r, b) { this@drawWithContent.drawContent() }
        val fade = overhang()
        if (fade > 0f) {
            val paint = Paint().apply { alpha = fade }
            drawIntoCanvas { canvas ->
                canvas.saveLayer(Rect(0f, 0f, this.size.width, this.size.height), paint)
                clipRect(l, t, r, b, clipOp = ClipOp.Difference) { this@drawWithContent.drawContent() }
                canvas.restore()
            }
        }
    }
