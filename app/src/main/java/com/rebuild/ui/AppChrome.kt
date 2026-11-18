package com.rebuild.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Inert: no semantics and no input. Built once, outside composition, so switching a layer between this and [Modifier]
 * never shifts the slot table (an inline `clearAndSetSemantics { }` in a branch would rebuild the whole layer).
 */
internal val INERT: Modifier = Modifier
    .clearAndSetSemantics { }
    .pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
        }
    }

/**
 * Whether the layer being composed is the target, the one on screen taking input. Hidden kept tabs and a layer leaving
 * read false, so what only the target shows (the busy look, a press ripple) is neither tracked nor kept there.
 */
internal val LocalLayerActive = compositionLocalOf { true }

/** Whether [route] shows the navigation bar or rail: everywhere except the workout, a past workout and editors. */
internal fun showsNav(route: String): Boolean =
    route != Routes.WORKOUT && !route.startsWith("workout-detail/") && !Routes.isEditor(route) && route != SETUP_ROUTE

/** The layer key of the setup flow, which has no navigation and no shared bars. */
internal const val SETUP_ROUTE = "setup"

/**
 * The window's chrome, measured once per frame by [AppFrame] and read by every layer while it lays out, so each layer
 * pads itself with its own final insets (status bar, rail, shared bars, navigation bar) and none of them changes
 * while a transition runs.
 *
 * - The navigation bar or rail is one overlay instance whose alpha follows the layers ([navAlpha]).
 * - The shared bars (stopwatch, rest, alarm) sit above it, offset by its height times [navShown] (Chrome shift).
 * - Inactive kept tabs read the bars' settled height, so they re-measure once per bar change, never per frame.
 */
@Stable
internal class Chrome(navShownAtStart: Boolean = true) {
    var density: Density = Density(1f)
    var navigationBars: WindowInsets = WindowInsets(0, 0, 0, 0)
    var ime: WindowInsets = WindowInsets(0, 0, 0, 0)
    /** Insets of an ordinary screen (Scaffold's default content insets). */
    var content: WindowInsets = WindowInsets(0, 0, 0, 0)
    /** The workout pads the gesture area itself, so its dock colour reaches the screen edge. */
    var workoutContent: WindowInsets = WindowInsets(0, 0, 0, 0)

    var wide by mutableStateOf(false)
    var navHeight by mutableIntStateOf(0)
    var railWidth by mutableIntStateOf(0)
    /** A stopwatch exists, so every screen but the workout shows its bar. */
    var watchShown by mutableStateOf(false)
    var watchHeight by mutableIntStateOf(0)
    /** The rest (or alarm) bar's height as it is drawn now; 0 without one. */
    var restHeight by mutableIntStateOf(0)
    /** The rest bar's height once any change of it has finished; what hidden tabs lay out with. */
    var restSettled by mutableIntStateOf(0)
    /** The rest (or alarm) bar's full height, and whether it is shown once its Expand or Collapse ends. */
    var restFull by mutableIntStateOf(0)
    var restTargetShown by mutableStateOf(false)
    /** How much of the rest bar is revealed (0–1, animated): read by the bar and the live layers in the same frame. */
    var restReveal: () -> Float = { 1f }
    /** The same for the stopwatch bar. */
    var watchFull by mutableIntStateOf(0)
    var watchTargetShown by mutableStateOf(false)
    var watchSettled by mutableIntStateOf(0)
    var watchReveal: () -> Float = { 1f }

    /** The bars' drawn heights this frame, computed exactly as the bars compute them. */
    private fun restDrawn(): Int = (restFull * restReveal()).roundToInt()
    private fun watchDrawn(): Int = (watchFull * watchReveal()).roundToInt()

    /** 1 while the bottom navigation bar is part of the target layer's chrome, 0 when not (Chrome shift). */
    val navShown = Animatable(if (navShownAtStart) 1f else 0f)
    /** The navigation overlay's alpha, following the layer that shows it. */
    var navAlpha: () -> Float by mutableStateOf({ 1f })
    /** The navigation overlay takes input only when it is opaque and its route is the target. */
    var navInteractive by mutableStateOf(true)

    private fun barsPx(route: String, live: Boolean): Int =
        (if (watchShown && route != Routes.WORKOUT && route != SETUP_ROUTE) (if (live) watchDrawn() else watchSettled) else 0) +
            (if (route == SETUP_ROUTE) 0 else if (live) restDrawn() else restSettled)

    fun bottomPx(route: String, live: Boolean): Int {
        val ime = ime.getBottom(density)
        val nav = navigationBars.getBottom(density)
        val bars = barsPx(route, live)
        return when {
            showsNav(route) && !wide -> navHeight + bars + max(0, ime - nav)
            bars > 0 -> bars + max(ime, nav)
            else -> insetsOf(route).getBottom(density)
        }
    }

    fun insetsOf(route: String): WindowInsets = if (route == Routes.WORKOUT) workoutContent else content
}

/**
 * A layer's own padding: what Scaffold's inner padding was when this route was on screen, computed from the chrome
 * measured this frame. [live] layers (the target and the one leaving) read the bars' drawn height; hidden kept tabs
 * read the settled height.
 */
private class LayerPadding(private val chrome: Chrome, private val route: String, private val live: Boolean) : PaddingValues {
    private fun px(value: Int): Dp = with(chrome.density) { value.toDp() }
    private val rail get() = chrome.wide && showsNav(route)

    override fun calculateTopPadding(): Dp = px(chrome.insetsOf(route).getTop(chrome.density))
    override fun calculateBottomPadding(): Dp = px(chrome.bottomPx(route, live))
    override fun calculateLeftPadding(layoutDirection: LayoutDirection): Dp =
        if (rail && layoutDirection == LayoutDirection.Ltr) px(chrome.railWidth)
        else px(chrome.insetsOf(route).getLeft(chrome.density, layoutDirection))
    override fun calculateRightPadding(layoutDirection: LayoutDirection): Dp =
        if (rail && layoutDirection == LayoutDirection.Rtl) px(chrome.railWidth)
        else px(chrome.insetsOf(route).getRight(chrome.density, layoutDirection))

    override fun equals(other: Any?) = other is LayerPadding && other.chrome === chrome && other.route == route &&
        other.live == live
    override fun hashCode() = (route.hashCode() * 31 + live.hashCode()) * 31 + System.identityHashCode(chrome)
}

/** Pads a layer for [route] by its own final insets and hands what it consumed to its content, as Scaffold did. */
internal fun Modifier.layerInsets(chrome: Chrome, route: String, live: Boolean): Modifier {
    val padding = LayerPadding(chrome, route, live)
    return padding(padding).consumeWindowInsets(padding).imePadding()
}

private enum class Part { LAYERS, NAV, RAIL, WATCH, REST, FOOT, SNACK }

/**
 * The window: layers full size underneath, then the navigation overlay, the shared bars and the snackbar. The chrome
 * is measured first and written to [chrome], so the layers measured after it pad themselves by this frame's values.
 */
@Composable
internal fun AppFrame(
    chrome: Chrome,
    targetRoute: String,
    barsBase: Color,
    modifier: Modifier = Modifier,
    navBar: (@Composable () -> Unit)?,
    rail: (@Composable () -> Unit)?,
    watchBar: (@Composable () -> Unit)?,
    restBar: (@Composable () -> Unit)?,
    snackbar: @Composable () -> Unit,
    layers: @Composable () -> Unit
) {
    val hasBars = restBar != null || (watchBar != null && targetRoute != Routes.WORKOUT)
    Layout(
        modifier = modifier,
        content = {
            Box(Modifier.layoutId(Part.LAYERS)) { layers() }
            if (navBar != null) Box(Modifier.layoutId(Part.NAV).then(if (chrome.navInteractive) Modifier else INERT)) { navBar() }
            if (rail != null) Box(Modifier.layoutId(Part.RAIL).then(if (chrome.navInteractive) Modifier else INERT)) { rail() }
            if (hasBars) Box(Modifier.layoutId(Part.FOOT).background(barsBase))
            if (watchBar != null) Box(
                Modifier.layoutId(Part.WATCH).background(barsBase)
                    .then(if (targetRoute == Routes.WORKOUT) INERT else Modifier)
            ) { watchBar() }
            if (restBar != null) Box(Modifier.layoutId(Part.REST).background(barsBase)) { restBar() }
            Box(Modifier.layoutId(Part.SNACK)) { snackbar() }
        }
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        fun part(p: Part) = measurables.firstOrNull { it.layoutId == p }

        val nav = part(Part.NAV)?.measure(loose.copy(minWidth = width))
        val rail = part(Part.RAIL)?.measure(Constraints(maxWidth = width, minHeight = height, maxHeight = height))
        if (nav != null && chrome.navHeight != nav.height) chrome.navHeight = nav.height
        if (rail != null && chrome.railWidth != rail.width) chrome.railWidth = rail.width

        val railOffset = if (chrome.wide && showsNav(targetRoute)) chrome.railWidth else 0
        val barsWidth = width - railOffset
        val barConstraints = Constraints(minWidth = barsWidth, maxWidth = barsWidth, maxHeight = height)
        val watch = part(Part.WATCH)?.measure(barConstraints)
        val rest = part(Part.REST)?.measure(barConstraints)
        val watchHeight = watch?.height ?: 0
        if (chrome.watchHeight != watchHeight) chrome.watchHeight = watchHeight
        val restHeight = rest?.height ?: 0
        if (chrome.restHeight != restHeight) chrome.restHeight = restHeight
        // Hidden tabs lay out with where the bars will end, so an Expand or Collapse re-measures them once, not per frame.
        val restSettled = if (rest != null && chrome.restTargetShown) chrome.restFull else 0
        if (chrome.restSettled != restSettled) chrome.restSettled = restSettled
        val watchSettled = if (watch != null && chrome.watchTargetShown) chrome.watchFull else 0
        if (chrome.watchSettled != watchSettled) chrome.watchSettled = watchSettled

        val ime = chrome.ime.getBottom(this)
        val sysNav = chrome.navigationBars.getBottom(this)
        val navLift = max(0, ime - sysNav)
        val shown = chrome.navShown.value
        // The bars' bottom edge: on the navigation bar when it's part of the target's chrome, else on the gesture area
        // or keyboard; Chrome shift moves between the two.
        val bareBottom = max(ime, sysNav)
        val barsBottom = if (chrome.wide) bareBottom
            else (bareBottom + (chrome.navHeight + navLift - bareBottom) * shown).roundToInt()
        val foot = if (hasBars) part(Part.FOOT)?.measure(Constraints.fixed(barsWidth, max(0, barsBottom))) else null

        val layers = part(Part.LAYERS)!!.measure(Constraints.fixed(width, height))
        val snack = part(Part.SNACK)?.measure(loose)
        val contentBottom = chrome.bottomPx(targetRoute, live = true)

        layout(width, height) {
            layers.place(0, 0)
            nav?.placeWithLayer(0, height - nav.height - navLift) { alpha = chrome.navAlpha() }
            rail?.placeWithLayer(0, 0) { alpha = chrome.navAlpha() }
            var y = height - barsBottom
            foot?.placeWithLayer(railOffset, y) { alpha = if (chrome.wide) 1f else 1f - chrome.navShown.value }
            if (rest != null) {
                y -= rest.height
                rest.place(railOffset, y)
            }
            if (watch != null && targetRoute != Routes.WORKOUT) {
                y -= watch.height
                watch.place(railOffset, y)
            }
            snack?.place((width - snack.width) / 2, height - contentBottom - snack.height)
        }
    }
}

/** A layer's motion: its alpha and lateral offset (px), animated by [AppLayers]. */
internal class LayerMotion(alpha: Float, dx: Float = 0f) {
    val alpha = Animatable(alpha)
    val dx = Animatable(dx)
}
