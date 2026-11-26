package com.rebuild.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import com.rebuild.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Every icon asset inflates as a 24 dp vector that draws a visible glyph in whatever tint it is given, the RTL
 * icons mirror and the others don't, and the launcher glyph stays inside Android's adaptive-icon safe zone.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "mdpi")
class IconAssetsTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private val icons: Map<String, Int> = R.drawable::class.java.fields
        .filter { it.name.startsWith("ic_") && !it.name.startsWith("ic_rebuild") }
        .associate { it.name to it.getInt(null) }

    private val mirrored = setOf("ic_arrow_left", "ic_arrow_right", "ic_chevron_right", "ic_undo_2")

    private fun drawable(id: Int): Drawable = requireNotNull(ContextCompat.getDrawable(context, id)).mutate()

    private fun render(d: Drawable, size: Int, tint: Int?): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        d.setBounds(0, 0, size, size)
        tint?.let { d.setTint(it) }
        d.draw(Canvas(bitmap))
        return bitmap
    }

    @Test fun everyIconIsA24dpVectorThatDrawsInItsTint() {
        assertEquals("icon count", 37, icons.size)
        val tint = Color.rgb(0x12, 0x80, 0xFF)
        icons.forEach { (name, id) ->
            val d = drawable(id)
            assertEquals("$name width", 24, d.intrinsicWidth)
            assertEquals("$name height", 24, d.intrinsicHeight)
            val bitmap = render(d, 96, tint)
            var inked = 0
            var offTint = 0
            for (x in 0 until 96) for (y in 0 until 96) {
                val p = bitmap.getPixel(x, y)
                if (Color.alpha(p) > 128) {
                    inked++
                    if (abs(Color.red(p) - 0x12) > 4 || abs(Color.green(p) - 0x80) > 4 || abs(Color.blue(p) - 0xFF) > 4) offTint++
                }
            }
            assertTrue("$name draws a visible glyph ($inked px)", inked > 96 * 96 / 50)
            assertEquals("$name ignores its tint", 0, offTint)
        }
    }

    @Test fun onlyDirectionalIconsMirrorInRtl() {
        icons.forEach { (name, id) ->
            assertEquals("$name autoMirrored", name in mirrored, drawable(id).isAutoMirrored)
        }
    }

    /** Renders a 108 dp icon layer at 1 px per dp and returns its inked pixel count and farthest reach from centre. */
    private fun reach(id: Int): Pair<Int, Double> {
        val bitmap = render(drawable(id), 108, null)
        var inked = 0
        var farthest = 0.0
        for (x in 0 until 108) for (y in 0 until 108) {
            if (Color.alpha(bitmap.getPixel(x, y)) > 0) {
                inked++
                farthest = maxOf(farthest, hypot(x + 0.5 - 54, y + 0.5 - 54))
            }
        }
        return inked to farthest
    }

    @Test fun launcherGlyphStaysInsideTheSafeZone() {
        // The safe zone is the centred 66 dp circle.
        val (inked, farthest) = reach(R.drawable.ic_rebuild_foreground)
        assertTrue("launcher draws a glyph", inked > 300)
        assertTrue("launcher glyph reaches $farthest dp from centre, beyond the 33 dp safe radius", farthest <= 33.0)
    }

    @Test fun themedIconSitsAtTheSizeOfOtherThemedGlyphs() {
        // Themed icons (Android 13+) tint the monochrome layer on a plain disc; a full-size mark crowds the disc, so the
        // R is drawn a little smaller, inside a 28 dp radius, while staying clearly visible.
        val (inked, farthest) = reach(R.drawable.ic_rebuild_monochrome)
        assertTrue("themed icon draws a glyph ($inked px)", inked > 200)
        assertTrue("themed glyph reaches $farthest dp from centre, beyond 28 dp", farthest <= 28.0)
        assertTrue("themed glyph shrank to $farthest dp, too small to read", farthest >= 22.0)
    }
}
