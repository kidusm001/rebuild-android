package com.rebuild.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RestAlertTest {
    @Test fun defaultIsTheShortBuzzWithNothingExtraOnScreen() {
        assertEquals(RestAlert(RestSound.BUZZ, RestVisual.NONE), RestAlert())
    }

    @Test fun everySoundGoesWithEveryVisualExceptSilentWithNothing() {
        RestSound.entries.forEach { sound ->
            RestVisual.entries.forEach { visual ->
                val expected = !(sound == RestSound.SILENT && visual == RestVisual.NONE)
                assertEquals("$sound + $visual", expected, RestAlert(sound, RestVisual.FLASH).allows(visual))
            }
        }
        assertNotNull(RestAlert(RestSound.SILENT, RestVisual.FLASH).blockedReason(RestVisual.NONE))
        assertNull(RestAlert(RestSound.SILENT, RestVisual.FLASH).blockedReason(RestVisual.FLASH))
    }

    @Test fun choosingSilentMovesNothingOnScreenToFlash() {
        assertEquals(RestAlert(RestSound.SILENT, RestVisual.FLASH), RestAlert().withSound(RestSound.SILENT))
    }

    @Test fun aForbiddenVisualIsIgnored() {
        val silent = RestAlert(RestSound.SILENT, RestVisual.FLASH)
        assertEquals(silent, silent.withVisual(RestVisual.NONE))
    }

    @Test fun theFlashSwellsSmoothlyThreeTimesASecondApart() {
        val samples = (0..2800).map { flashLevel(it.toFloat()) }
        // Smooth: brightness never jumps between neighbouring milliseconds.
        samples.zipWithNext().forEach { (a, b) -> assertTrue("jump of ${b - a}", kotlin.math.abs(b - a) < 0.012f) }
        // Three full peaks, one second apart, dark in between and after.
        listOf(140, 1140, 2140).forEach { assertEquals(1f, samples[it], 0.001f) }
        listOf(0, 700, 999, 1700, 2700).forEach { assertEquals(0f, samples[it], 0.001f) }
        assertEquals(3, samples.zipWithNext().zipWithNext().count { (p, q) -> p.second > p.first && q.second < q.first })
        assertEquals(0f, flashLevel(-1f))
    }

    @Test fun onlyTheAlarmNeedsAStopButton() {
        assertTrue(RestAlert(RestSound.ALARM).needsStop)
        RestSound.entries.filter { it != RestSound.ALARM }.forEach { assertFalse(RestAlert(it, RestVisual.FLASH).needsStop) }
    }
}
