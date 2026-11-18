package com.rebuild.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import androidx.test.core.app.ApplicationProvider
import com.rebuild.R
import com.rebuild.ui.theme.Archivo
import com.rebuild.ui.theme.Numerals
import com.rebuild.ui.theme.RebuildTheme
import com.rebuild.ui.theme.RebuildTypography
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.abs

/**
 * The bundled Archivo: every style uses it, numbers stay put as they change, and nothing falls back to a system font.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FontTest {
    @get:Rule val compose = createComposeRule()

    private val fonts = listOf(R.font.archivo_regular, R.font.archivo_medium, R.font.archivo_semibold,
        R.font.archivo_bold, R.font.archivo_numerals)

    // Read straight from the files: Robolectric's Paint finds glyphs in system fallback fonts however it's built.
    private val files get() = fonts.map {
        File("src/main/res/font/${ApplicationProvider.getApplicationContext<android.content.Context>()
            .resources.getResourceEntryName(it)}.ttf")
    }

    private fun ByteBuffer.table(tag: String): Int = (0 until getShort(4).toInt()).map { 12 + 16 * it }
        .first { String(ByteArray(4) { i -> get(it + i) }) == tag }.let { getInt(it + 8) }

    /** The characters a TrueType file maps to a real glyph, from its Windows Unicode cmap (format 12, else 4). */
    private fun characters(file: File): Set<Int> = with(ByteBuffer.wrap(file.readBytes())) {
        val cmap = table("cmap")
        val subtables = (0 until u16(cmap + 2)).map { cmap + 4 + 8 * it }
            .associate { (u16(it) to u16(it + 2)) to cmap + getInt(it + 4) }
        subtables[3 to 10]?.let { return format12(this, it) }
        format4(this, subtables.getValue(3 to 1))
    }

    private fun Typography.styles() = with(this) {
        mapOf("displayLarge" to displayLarge, "displayMedium" to displayMedium, "displaySmall" to displaySmall,
            "headlineLarge" to headlineLarge, "headlineMedium" to headlineMedium, "headlineSmall" to headlineSmall,
            "titleLarge" to titleLarge, "titleMedium" to titleMedium, "titleSmall" to titleSmall,
            "bodyLarge" to bodyLarge, "bodyMedium" to bodyMedium, "bodySmall" to bodySmall,
            "labelLarge" to labelLarge, "labelMedium" to labelMedium, "labelSmall" to labelSmall)
    }

    @Test fun everyStyleIsArchivoWithTabularFigures() {
        RebuildTypography.styles().forEach { (name, style) ->
            val expected = if (name.startsWith("display")) Numerals else Archivo
            assertEquals("$name family", expected, style.fontFamily)
            assertEquals("$name figures", "tnum", style.fontFeatureSettings)
        }
    }

    private fun measurer(): Pair<TextMeasurer, Typography> {
        lateinit var measurer: TextMeasurer
        lateinit var typography: Typography
        compose.setContent {
            RebuildTheme {
                measurer = rememberTextMeasurer()
                typography = MaterialTheme.typography
            }
        }
        compose.waitForIdle()
        return measurer to typography
    }

    private fun TextMeasurer.width(text: String, style: TextStyle) = measure(text, style).size.width

    @Test fun aTickingCountdownNeverChangesWidth() {
        val (measurer, typography) = measurer()
        val times = (0..9).flatMap { m -> (0..59).map { s -> "$m:${s.toString().padStart(2, '0')}" } }
        listOf(typography.displayLarge, typography.displaySmall, typography.titleLarge.copy(fontFamily = Numerals))
            .forEach { style ->
                val widths = times.map { measurer.width(it, style) }.toSet()
                assertEquals("countdown widths at ${style.fontSize}", 1, widths.size)
            }
    }

    @Test fun figuresAreEqualWidthInWordsToo() {
        val (measurer, typography) = measurer()
        listOf(typography.bodyMedium, typography.titleMedium, typography.labelLarge).forEach { style ->
            val widths = (0..9).map { measurer.width("$it$it$it$it", style) }.toSet()
            assertEquals("figure widths at ${style.fontSize} ${style.fontWeight}", 1, widths.size)
        }
    }

    @Test fun everyCharacterTheAppWritesIsInEveryFont() {
        // Each non-ASCII character in the app's code and resources, so none falls back to a system font mid-word.
        // Escapes like "\u00A0" count too, and so do the spaces Android 14's time formatting puts before AM/PM.
        val sources = File("src/main").walkTopDown().filter { it.isFile && it.extension in setOf("kt", "xml") }
            .map { it.readText() }.toList()
        val escaped = sources.flatMap { Regex("""\\u([0-9A-Fa-f]{4})""").findAll(it).map { m -> m.groupValues[1] } }
            .map { it.toInt(16).toChar() }
        val written = (sources.flatMap { it.asIterable() } + escaped + listOf('\u202F', '\u200B'))
            .filter { it.code > 0x7E }.toSortedSet()
        assertTrue("found the app's text", written.containsAll(listOf('×', '–', '·', '↳', '●', '○')))
        files.forEach { file ->
            val covered = characters(file)
            assertTrue("reads ${file.name}", 'K'.code in covered && 'ሀ'.code !in covered)
            val missing = written.filter { it.code !in covered }
            assertTrue("${file.name} lacks $missing", missing.isEmpty())
        }
    }

    @Test fun textSitsWhereRobotoDid() {
        // The build gives Archivo Roboto's ascent and descent, so layouts tuned for Roboto keep their baselines.
        files.forEach { file ->
            with(ByteBuffer.wrap(file.readBytes())) {
                val em = u16(table("head") + 18).toFloat()
                val hhea = table("hhea")
                assertTrue("${file.name} ascent", abs(getShort(hhea + 4) / em - 0.928f) < 0.002f)
                assertTrue("${file.name} descent", abs(getShort(hhea + 6) / em + 0.244f) < 0.002f)
            }
        }
    }

    @Test fun numeralsLeaveRoomBetweenANumberAndItsUnit() {
        val (measurer, typography) = measurer()
        val style = typography.displaySmall.copy(fontSize = 100.sp, letterSpacing = 0.sp)
        val space = measurer.width("0 0", style) - measurer.width("00", style)
        val figure = measurer.width("00", style) - measurer.width("0", style)
        assertTrue("space is ${space.toFloat() / figure} of a figure", space >= 0.38f * figure)
    }

    @Test fun cmapReaderTreatsGlyphZeroAsMissing() {
        // Format 4, a delta segment: 'A' + delta lands on glyph 0, 'B' on glyph 1.
        val deltaOnly = format4(listOf(Segment(0x41, 0x42, delta = -0x41)))
        assertEquals(setOf(0x42), deltaOnly)
        // Format 4, a glyph array with delta -3: raw 0 stays missing, 5 becomes glyph 2, and 3 wraps to glyph 0.
        val arrayed = format4(listOf(Segment(0x61, 0x63, delta = -3, glyphs = listOf(0, 5, 3))))
        assertEquals(setOf(0x62), arrayed)
        // Format 12: a group starting at glyph 0 leaves its first character missing.
        val grouped = ByteBuffer.allocate(28).apply {
            putShort(12).putShort(0).putInt(28).putInt(0).putInt(1).putInt(0x30).putInt(0x32).putInt(0)
        }
        assertEquals(setOf(0x31, 0x32), format12(grouped, 0))
    }

    private class Segment(val start: Int, val end: Int, val delta: Int, val glyphs: List<Int>? = null)

    /** A format 4 subtable holding [segments] plus the required final 0xFFFF segment. */
    private fun format4(segments: List<Segment>): Set<Int> {
        val all = segments + Segment(0xFFFF, 0xFFFF, delta = 1)
        val count = all.size
        val arrays = all.flatMap { it.glyphs.orEmpty() }
        val buffer = ByteBuffer.allocate(16 + 8 * count + 2 * arrays.size)
        buffer.putShort(4).putShort(buffer.capacity().toShort()).putShort(0).putShort((2 * count).toShort())
            .putShort(0).putShort(0).putShort(0)
        all.forEach { buffer.putShort(it.end.toShort()) }
        buffer.putShort(0)
        all.forEach { buffer.putShort(it.start.toShort()) }
        all.forEach { buffer.putShort(it.delta.toShort()) }
        var before = 0
        all.forEachIndexed { i, seg ->
            // Bytes from this idRangeOffset entry to the segment's first glyph-array entry.
            val offset = if (seg.glyphs == null) 0 else 2 * (count - i) + 2 * before
            buffer.putShort(offset.toShort())
            before += seg.glyphs?.size ?: 0
        }
        arrays.forEach { buffer.putShort(it.toShort()) }
        return format4(buffer, 0)
    }
}

private fun ByteBuffer.u16(at: Int) = getShort(at).toInt() and 0xFFFF

/** Characters a format 12 subtable at [t] maps to a glyph other than 0 (the missing-character glyph). */
private fun format12(buffer: ByteBuffer, t: Int): Set<Int> = with(buffer) {
    (0 until getInt(t + 12)).flatMap { g ->
        val start = getInt(t + 16 + 12 * g)
        val first = getInt(t + 24 + 12 * g)
        (start..getInt(t + 20 + 12 * g)).filter { c -> first + (c - start) != 0 }
    }.toSet()
}

/** Characters a format 4 subtable at [t] maps to a glyph other than 0, applying idDelta modulo 65536. */
private fun format4(buffer: ByteBuffer, t: Int): Set<Int> = with(buffer) {
    val segments = u16(t + 6) / 2
    val ends = t + 14
    val starts = ends + 2 * segments + 2
    val deltas = starts + 2 * segments
    val ranges = deltas + 2 * segments
    val chars = mutableSetOf<Int>()
    for (i in 0 until segments) for (c in u16(starts + 2 * i)..u16(ends + 2 * i)) {
        if (c == 0xFFFF) continue
        val range = u16(ranges + 2 * i)
        val delta = u16(deltas + 2 * i)
        val glyph = if (range == 0) (c + delta) and 0xFFFF
        else u16(ranges + 2 * i + range + 2 * (c - u16(starts + 2 * i)))
            .let { raw -> if (raw == 0) 0 else (raw + delta) and 0xFFFF }
        if (glyph != 0) chars += c
    }
    chars
}
