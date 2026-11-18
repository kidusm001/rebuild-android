package com.rebuild.ui

import androidx.compose.animation.core.TweenSpec
import com.rebuild.ui.theme.Motion
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The motion rules' promises, checked on their curves (spec "Verification plan" 1). */
class MotionCurvesTest {
    private fun tween(spec: Any) = spec as TweenSpec<*>
    private fun TweenSpec<*>.at(ms: Float) = easing.transform((ms / durationMillis).coerceIn(0f, 1f))
    private val fadeIn = tween(Motion.fadeInSpec<Float>())
    private val fadeOut = tween(Motion.fadeOutSpec<Float>())
    private fun alphaIn(ms: Float) = fadeIn.at(ms)
    private fun alphaOut(ms: Float) = 1f - fadeOut.at(ms)
    private val times = (0..400).map { it.toFloat() }

    @Test fun fadeInIsReadableBy75msAndFadeOutGoneBy100ms() {
        assertTrue(alphaIn(75f) >= 0.5f)
        assertEquals(0f, alphaOut(100f), 1e-4f)
    }

    @Test fun travelThatFadesInIsStillHalfLeftWhenItBecomesReadable() {
        // Alpha reaches 0.5 at 75 ms: Lateral enter (navigation and in-workout) and Rise leave >= 50 % of travel.
        val half = times.first { alphaIn(it) >= 0.5f }
        for (navigation in listOf(true, false)) {
            val travel = tween(Motion.lateralEnterTravelSpec<Float>(navigation))
            assertTrue("navigation=$navigation", 1f - travel.at(half) >= 0.5f)
        }
        assertTrue(1f - tween(Motion.lateralEnterTravelSpec<Float>(true)).at(half) >= 0.75f)
    }

    @Test fun lateralExitDoesHalfItsTravelWhileStillAboveHalfAlpha() {
        val exit = tween(Motion.lateralExitSpec<Float>())
        val half = times.first { 1f - exit.at(it) <= 0.5f }
        assertTrue(exit.at(half) >= 0.5f - 1e-3f)
    }

    @Test fun crossfadeNeverShowsBothAboveHalfAndNeverDipsBelowTwoThirds() {
        for (t in times) {
            val a = alphaOut(t)
            val b = alphaIn(t)
            assertTrue("t=$t", !(a > 0.5f && b > 0.5f))
            assertTrue("t=$t sum=${a + b}", a + b >= 0.66f)
        }
    }

    @Test fun chromeShiftIsDoneBeforeTheIncomingLayerIsReadable() {
        val shift = tween(Motion.chromeShiftSpec<Float>())
        val half = times.first { alphaIn(it) >= 0.5f }
        assertTrue(shift.at(half) >= 0.95f)
        // On a pop it starts only once the outgoing layer is at or below 0.5.
        val outHalf = times.first { alphaOut(it) <= 0.5f }
        assertEquals(50f, outHalf, 1f)
    }

    @Test fun sheetInIsAt98PercentBy200ms() {
        assertTrue(tween(Motion.sheetInSpec<Float>()).at(200f) >= 0.98f)
    }

    @Test fun noRuleIsLongerThanLong() {
        val specs = listOf(
            Motion.fadeInSpec<Float>(), Motion.fadeOutSpec<Float>(), Motion.colourSpec<Float>(),
            Motion.expandSizeSpec<Float>(), Motion.collapseSizeSpec<Float>(), Motion.moveWithinSpec<Float>(),
            Motion.pageSpec<Float>(), Motion.lateralEnterTravelSpec<Float>(true), Motion.lateralEnterTravelSpec<Float>(false),
            Motion.lateralExitSpec<Float>(), Motion.chromeShiftSpec<Float>(), Motion.sheetInSpec<Float>(),
            Motion.scrimInSpec<Float>(), Motion.sheetOutSpec<Float>(), Motion.scrimOutSpec<Float>()
        )
        specs.forEach { assertTrue(tween(it).durationMillis <= Motion.Long) }
        assertTrue("exits are faster than entrances", fadeOut.durationMillis < fadeIn.durationMillis)
    }

    @Test fun fadeInOverKeepsIdenticalPixelsWhereAPlainCrossfadeDims() {
        // An opaque pixel p shown by both surfaces: Fade in over holds the outgoing at 1 underneath.
        var crossfadeMin = 1f
        for (t in times) {
            val b = alphaIn(t)
            val over = b * 1f + (1f - b) * 1f
            assertTrue(over >= 0.95f)
            val a = alphaOut(t)
            crossfadeMin = minOf(crossfadeMin, b + (1f - b) * a)
        }
        assertTrue("a plain crossfade over identical pixels dips (${crossfadeMin})", crossfadeMin < 0.7f)
    }

    @Test fun screensNameRulesInsteadOfWritingTheirOwnTimings() {
        val ui = File("src/main/java/com/rebuild/ui")
        assertTrue(ui.isDirectory)
        val offenders = ui.walkTopDown().filter { it.isFile && it.extension == "kt" }
            .filter { f -> Regex("""\b(tween|spring)\(""").containsMatchIn(f.readText()) }
            .map { it.name }.toSet() - ALLOWED - NOT_YET_MIGRATED
        assertEquals(emptySet<String>(), offenders)
    }

    private companion object {
        val ALLOWED = setOf("Motion.kt", "RestEndMotion.kt")
        /** Screens whose motion later phases of the spec rework (P0-8, P1-*, P2-*); this list only shrinks. */
        val NOT_YET_MIGRATED = setOf(
            "Charts.kt", "ExerciseProgress.kt", "HomeScreen.kt", "WorkoutScreen.kt", "Setup.kt", "SetLedger.kt",
            "Components.kt", "RebuildApp.kt"
        )
    }
}
