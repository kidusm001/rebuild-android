package com.rebuild.ui

import android.os.Build
import androidx.compose.ui.unit.IntSize
import kotlin.math.ceil
import androidx.compose.animation.core.AnimationState
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.offset
import androidx.compose.animation.core.animateTo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.rebuild.ui.theme.Motion
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * How far a screen's header has collapsed, in px of its own height, fed by the nested scroll of the body beneath it.
 * The header lays out with it, so the body moves with the finger 1:1 while the header gives way:
 *
 * - Toward the end of the body, the header shrinks before the body scrolls.
 * - It opens again only once the body is back at its top, so scanning up and down a page never moves it.
 * - A finger lifted (or a fling ending) part-way settles it with [Motion.settle], from the release speed, to the side it
 *   was heading, or the nearer side on a slow release; a new touch takes over from the settle.
 *
 * Scrolls made in code (scroll to an item, bring into view) don't pass through it; the header stays as it is.
 */
@Stable
internal class HeaderCollapse {
    private var raw by mutableFloatStateOf(0f)
    /** The px the header can give; written as it lays out, read only by the scroll callbacks. */
    var range: Float = 0f
        set(value) {
            field = value
            // A range that shrinks (the subtitle hides for the keyboard, a rotation) never leaves the offset past it.
            if (raw > value) raw = value
        }
    var offset: Float
        get() = raw.coerceIn(0f, range)
        set(value) { raw = value.coerceIn(0f, range) }
    val fraction: Float get() = if (range > 0f) offset / range else 0f

    private var settling: Job? = null

    val connection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (source == NestedScrollSource.UserInput) settling?.cancel()
            if (available.y >= 0f || range <= 0f) return Offset.Zero
            val before = offset
            offset = before - available.y
            return Offset(0f, before - offset)
        }

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            // The body is back at its top: what's left of the pull opens the header.
            if (available.y <= 0f || range <= 0f) return Offset.Zero
            val before = offset
            offset = before - available.y
            return Offset(0f, before - offset)
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            val f = fraction
            if (range <= 0f || f <= 0f || f >= 1f) return Velocity.Zero
            // Toward the end of the body the offset grows, so the header's velocity is the body's, reversed. The
            // release speed picks the side; the spring starts from what's left of it, so a fling that has already
            // come to rest part-way eases on from rest instead of jerking forward again.
            val released = -consumed.y - available.y
            val target = when {
                abs(released) > SETTLE_BY_DIRECTION -> if (released > 0f) range else 0f
                f < 0.5f -> 0f
                else -> range
            }
            coroutineScope {
                settling = launch {
                    AnimationState(offset, -available.y).animateTo(target, Motion.settle(), sequentialAnimation = false) {
                        offset = value
                    }
                }
            }
            settling = null
            return Velocity.Zero
        }
    }

    private companion object {
        /** px/s past which a release settles the way it was going rather than to the nearer side. */
        const val SETTLE_BY_DIRECTION = 600f
    }
}

internal enum class HeaderSlot { NAV, TITLE, ACTIONS, SUBTITLE }

/**
 * A screen's header at its current collapse.
 *
 * - Top-level screens name themselves large; collapsing, the title scales into a compact row that hugs it, and the
 *   subtitle fades and folds away under it.
 * - Pushed screens keep Back and a compact title in a 48 dp row; only the subtitle folds away.
 * - With [actionsBelow] (large text), the actions take a row of their own; collapsing, they move up into the compact
 *   row beside the title when they fit there, and otherwise stay on their own row.
 *
 * Fully faded parts are not placed, so a screen reader never reads what isn't drawn.
 */
@Composable
internal fun CollapsingHeader(
    state: HeaderCollapse,
    topLevel: Boolean,
    actionsBelow: Boolean,
    /** The title's compact size over its full size (titleLarge over headlineLarge, in px at this font scale). */
    compactScale: Float,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Layout(content, modifier.clipToBounds()) { measurables, constraints ->
        fun slot(s: HeaderSlot) = measurables.firstOrNull { it.layoutId == s }
        val width = constraints.maxWidth
        val padH = 8.dp.roundToPx()
        val padV = 4.dp.roundToPx()
        val inner = width - 2 * padH

        val nav = slot(HeaderSlot.NAV)?.measure(Constraints(maxWidth = inner))
        val actions = slot(HeaderSlot.ACTIONS)?.measure(Constraints(maxWidth = inner))
        val navW = nav?.width ?: 0
        val actionsW = actions?.width ?: 0
        val navH = nav?.height ?: 0
        val actionsH = actions?.height ?: 0
        val titleStart = if (topLevel) 12.dp.roundToPx() else 8.dp.roundToPx()
        val titleEnd = 8.dp.roundToPx()
        val titleW = max(0, inner - navW - (if (actionsBelow) 0 else actionsW) - titleStart - titleEnd)
        val title = requireNotNull(slot(HeaderSlot.TITLE)) { "A header needs its title" }
            .measure(Constraints(maxWidth = titleW))
        val subtitle = slot(HeaderSlot.SUBTITLE)?.measure(Constraints(maxWidth = inner))

        // Full: what the header always was. A top-level title sits 16 dp below the top and 4 dp above the subtitle.
        val titleTop = if (topLevel) 16.dp.roundToPx() else 0
        val titleBottom = if (topLevel) 4.dp.roundToPx() else 0
        val titleBox = titleTop + title.height + titleBottom
        val rowH = maxOf(titleBox, if (actionsBelow) 0 else actionsH, navH)
        val belowH = if (actionsBelow) actionsH else 0
        val subtitleH = subtitle?.height ?: 0
        val fullH = padV + rowH + belowH + subtitleH + padV

        // Compact: one row. A top-level row without controls hugs its scaled title; with controls it is at least 48 dp.
        val shrink = if (topLevel) compactScale else 1f
        val compactTitleW = (title.width * shrink).roundToInt()
        val compactTitleH = (title.height * shrink).roundToInt()
        // Large-text actions join the compact row when they fit beside the compact title.
        val actionsJoin = actionsBelow && actions != null &&
            navW + titleStart + compactTitleW + titleEnd + actionsW <= inner
        val controlsH = maxOf(navH, if (!actionsBelow || actionsJoin) actionsH else 0)
        val cRowH = if (controlsH > 0) maxOf(48.dp.roundToPx(), controlsH, compactTitleH)
            else compactTitleH + 8.dp.roundToPx()
        val compactH = padV + cRowH + (if (actionsBelow && !actionsJoin) actionsH else 0) + padV
        val range = max(0, fullH - compactH).toFloat()
        if (state.range != range) state.range = range
        val f = state.fraction
        val height = (fullH - state.offset).roundToInt().coerceAtLeast(0)

        layout(width, height) {
            fun lerp(a: Float, b: Float) = a + (b - a) * f
            val x0 = padH
            val cy = padV + cRowH / 2f
            val rowTop = padV.toFloat()
            val fullTitleY = rowTop + (rowH - titleBox) / 2f + titleTop
            val belowY = lerp(rowTop + rowH, (padV + cRowH).toFloat())
            // The subtitle folds under the shrinking row, painted beneath it and gone by a third of the way.
            val subtitleAlpha = (1f - 3f * f).coerceIn(0f, 1f)
            if (subtitle != null && subtitleAlpha > 0f) {
                val y = belowY + (if (actionsBelow && !actionsJoin) actionsH.toFloat() else lerp(belowH.toFloat(), 0f))
                subtitle.placeRelativeWithLayer(x0, (y - subtitleH * f).roundToInt()) { alpha = subtitleAlpha }
            }
            nav?.placeRelative(x0, lerp(rowTop + (rowH - navH) / 2f, cy - navH / 2f).roundToInt())
            val scale = lerp(1f, shrink)
            // Scales toward its start edge, beside Back, which is on the right in a right-to-left layout.
            val startEdge = if (layoutDirection == LayoutDirection.Rtl) 1f else 0f
            title.placeRelativeWithLayer(x0 + navW + titleStart, lerp(fullTitleY, cy - compactTitleH / 2f).roundToInt()) {
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(startEdge, 0f)
            }
            if (actions != null) {
                val endX = (x0 + inner - actionsW).toFloat()
                if (actionsBelow && !actionsJoin) {
                    actions.placeRelative(endX.roundToInt(), belowY.roundToInt())
                } else {
                    val fullY = if (actionsBelow) rowTop + rowH else rowTop + (rowH - actionsH) / 2f
                    actions.placeRelative(endX.roundToInt(), lerp(fullY, cy - actionsH / 2f).roundToInt())
                }
            }
        }
    }
}

/**
 * The header's glass: the page colour at [HEADER_GLASS_ALPHA] over the header's whole box, then, once the page has
 * scrolled under it, a feather below its edge ([HEADER_GLASS_FEATHER], longer at large text) that fades from the glass
 * to clear. Rows passing under the edge dissolve rather than being cut by a line, and the title always keeps a calm
 * band beneath it. Grey text never sits on the glass (the subtitle is gone before anything scrolls under it).
 */
internal const val HEADER_GLASS_ALPHA = 0.91f
internal val HEADER_GLASS_FEATHER = 24.dp
/** How far the page beneath the glass is blurred (Android 12+, where RenderEffect exists; plain glass before). */
internal val HEADER_GLASS_BLUR = 2.dp
private val glassBlurs = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
internal val HEADER_GLASS_FEATHER_LARGE = 40.dp

@Stable
internal class HeaderInset {
    var height by mutableIntStateOf(0)
    /**
     * The scaffold's body column, where "first" is measured; never a pager page or other nested parent. Until it is
     * placed a body can't tell, so it lays out without the reach for that one frame (nothing differs at rest).
     */
    var column: LayoutCoordinates? = null
    /** The page as last drawn, recorded so the glass can draw it blurred (see [recordsPageFor]). */
    var page: GraphicsLayer? = null
    /** Whether the body under the header has scrolled from its top; state, so the glass redraws when a body takes over. */
    var scrolled: () -> Boolean by mutableStateOf({ false })
}

internal val LocalHeaderInset = staticCompositionLocalOf<HeaderInset?> { null }

/** Whether this body sits at the top of the scaffold's body column, so it may reach beneath the header. */
@Stable
internal class UnderHeader(private val inset: HeaderInset?) {
    var first by mutableStateOf(false)
    val px: Int get() = if (inset != null && first) inset.height else 0
    /** The body's own "has scrolled" (state-backed), handed to the header's glass while this body is first. */
    var scrolled: () -> Boolean = { false }

    fun placed(coordinates: LayoutCoordinates) {
        val column = inset?.column?.takeIf { it.isAttached } ?: return
        first = column.localPositionOf(coordinates, Offset.Zero).y < 1f
        if (first && inset.scrolled !== scrolled) inset.scrolled = scrolled
    }
}

@Composable
internal fun rememberUnderHeader(): UnderHeader {
    val inset = LocalHeaderInset.current
    return remember(inset) { UnderHeader(inset) }
}

/** The body itself: grows up by the header's height and is placed that far up, under the header (drawn above it). */
internal fun Modifier.reachUnderHeader(under: UnderHeader): Modifier = this
    .onPlaced(under::placed)
    .layout { measurable, constraints ->
        val up = under.px
        val placeable = measurable.measure(
            if (constraints.hasBoundedHeight) constraints.copy(minHeight = constraints.minHeight + up,
                maxHeight = constraints.maxHeight + up) else constraints
        )
        layout(placeable.width, (placeable.height - up).coerceAtLeast(0)) { placeable.place(0, -up) }
    }

/** The body's content: starts below the header at rest, by padding as tall as the header now is. */
internal fun Modifier.padUnderHeader(under: UnderHeader): Modifier = layout { measurable, constraints ->
    val top = under.px
    val placeable = measurable.measure(constraints.offset(vertical = -top))
    layout(placeable.width, placeable.height + top) { placeable.place(0, top) }
}

/** A lazy list's padding plus the header's height when the list sits under it; read as the list measures. */
internal class UnderHeaderPadding(
    private val base: PaddingValues,
    private val under: UnderHeader,
    private val density: Density
) : PaddingValues by base {
    override fun calculateTopPadding(): Dp = base.calculateTopPadding() + with(density) { under.px.toDp() }
}

/**
 * Bringing something into view (a focused field, TalkBack's next row) counts the strip under the header as hidden,
 * so nothing is "shown" behind the glass.
 */
@OptIn(ExperimentalFoundationApi::class)
internal class UnderHeaderBringIntoView(private val under: UnderHeader) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val hidden = under.px.toFloat()
        val leading = offset - hidden
        val visible = containerSize - hidden
        val trailing = leading + size
        return when {
            leading >= 0f && trailing <= visible -> 0f
            leading < 0f && trailing > visible -> 0f
            abs(leading) < abs(trailing - visible) -> leading
            else -> trailing - visible
        }
    }
}

/**
 * The body's own scrolling uses [UnderHeaderBringIntoView]; everything inside it (fields, chip rows, dialogs opened
 * from it) gets back the spec from outside through [inside], so the strip under the header means nothing to them.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ProvideUnderHeaderBringIntoView(
    under: UnderHeader,
    content: @Composable (inside: @Composable (@Composable () -> Unit) -> Unit) -> Unit
) {
    val outer = LocalBringIntoViewSpec.current
    val spec = remember(under) { UnderHeaderBringIntoView(under) }
    val inside: @Composable (@Composable () -> Unit) -> Unit = remember(outer) {
        { child -> CompositionLocalProvider(LocalBringIntoViewSpec provides outer, content = child) }
    }
    CompositionLocalProvider(LocalBringIntoViewSpec provides spec) { content(inside) }
}

/** A lazy list scope whose every item gets the outer bring-into-view spec back (see [ProvideUnderHeaderBringIntoView]). */
internal class InsideScope(
    private val scope: androidx.compose.foundation.lazy.LazyListScope,
    private val inside: @Composable (@Composable () -> Unit) -> Unit
) : androidx.compose.foundation.lazy.LazyListScope {
    override fun item(key: Any?, contentType: Any?, content: @Composable androidx.compose.foundation.lazy.LazyItemScope.() -> Unit) =
        inside.let { wrap -> scope.item(key, contentType) { val item = this; wrap { item.content() } } }

    override fun items(
        count: Int,
        key: ((index: Int) -> Any)?,
        contentType: (index: Int) -> Any?,
        itemContent: @Composable androidx.compose.foundation.lazy.LazyItemScope.(index: Int) -> Unit
    ) = inside.let { wrap -> scope.items(count, key, contentType) { i -> val item = this; wrap { item.itemContent(i) } } }

    override fun stickyHeader(
        key: Any?,
        contentType: Any?,
        content: @Composable androidx.compose.foundation.lazy.LazyItemScope.(Int) -> Unit
    ) = inside.let { wrap -> scope.stickyHeader(key, contentType) { i -> val item = this; wrap { item.content(i) } } }
}

/**
 * Records the page as it draws into [HeaderInset.page], so the glass can draw a blurred copy of it. The page draws
 * itself from that same recording, so this costs no second pass over its content.
 */
internal fun Modifier.recordsPageFor(inset: HeaderInset): Modifier = if (!glassBlurs) this else drawWithContent {
    val layer = inset.page ?: return@drawWithContent drawContent()
    // The body reaches up under the header, above this column's own top. A layer clips to its size on the GPU, so it
    // is recorded from the header's top: rows passing under the glass stay in it, for the page and for the blur.
    val up = inset.height.toFloat()
    layer.record(size = IntSize(ceil(size.width).toInt(), ceil(size.height + up).toInt())) {
        translate(top = up) { this@drawWithContent.drawContent() }
    }
    translate(top = -up) { drawLayer(layer) }
}

/**
 * The glass: the header's box, and once the page has scrolled under it, a feather below its edge ([HEADER_GLASS_FEATHER],
 * longer at large text). Inside, an opaque page with the page's own recording drawn blurred over it, then the page
 * colour at [HEADER_GLASS_ALPHA]; what shows through is the page softened, never sharp text behind the title. The
 * whole frosted pane fades out over the feather, so blurred meets sharp gradually, never at a line. Without
 * RenderEffect (before Android 12) the same pane is plain glass.
 */
@Composable
internal fun HeaderGlass(page: Color, inset: HeaderInset, large: Boolean, modifier: Modifier = Modifier) {
    val featherDp = if (large) HEADER_GLASS_FEATHER_LARGE else HEADER_GLASS_FEATHER
    androidx.compose.foundation.layout.Box(
        modifier
            // As tall as the header plus the feather, while laying out as the header: the feather hangs below it.
            .layout { measurable, constraints ->
                val feather = featherDp.roundToPx()
                val placeable = measurable.measure(constraints.copy(minHeight = constraints.minHeight + feather,
                    maxHeight = if (constraints.hasBoundedHeight) constraints.maxHeight + feather else constraints.maxHeight))
                layout(placeable.width, (placeable.height - feather).coerceAtLeast(0)) { placeable.place(0, 0) }
            }
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                val edge = size.height - featherDp.toPx()
                if (inset.scrolled()) {
                    // The header's box keeps the pane; over the feather it fades out.
                    drawRect(
                        Brush.verticalGradient(0f to Color.Black, (edge / size.height) to Color.Black, 1f to Color.Transparent),
                        blendMode = BlendMode.DstIn
                    )
                } else {
                    // Nothing beneath yet: no feather.
                    drawRect(Color.Black, topLeft = Offset(0f, edge), size = size.copy(height = size.height - edge),
                        blendMode = BlendMode.Clear)
                }
            }
    ) {
        androidx.compose.foundation.layout.Box(
            Modifier
                .matchParentSize()
                .then(if (glassBlurs) Modifier.graphicsLayer {
                    val r = HEADER_GLASS_BLUR.toPx()
                    renderEffect = BlurEffect(r, r, TileMode.Clamp)
                    clip = true
                } else Modifier)
                .drawBehind {
                    val recorded = inset.page
                    if (glassBlurs && recorded != null && inset.scrolled()) {
                        drawRect(page)
                        // The recording starts at the header's top (see recordsPageFor), where this pane starts too.
                        drawLayer(recorded)
                    }
                    drawRect(page.copy(alpha = HEADER_GLASS_ALPHA))
                }
        )
    }
}

/**
 * The header as one layer over the page: taps on its empty space stay with it, never reaching a row scrolled beneath,
 * and as a semantics node of its own it covers those rows for TalkBack and other accessibility services too.
 */
internal val CoversThePageBeneath: Modifier = Modifier
    .semantics { isTraversalGroup = true }
    .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } } }
