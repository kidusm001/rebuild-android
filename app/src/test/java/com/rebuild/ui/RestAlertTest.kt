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
                assertEquals("$sound + $visual", expected, RestAlert(sound, RestVisual.PULSE).allows(visual))
            }
        }
        assertNotNull(RestAlert(RestSound.SILENT, RestVisual.FLASH).blockedReason(RestVisual.NONE))
        assertNull(RestAlert(RestSound.SILENT, RestVisual.FLASH).blockedReason(RestVisual.FLASH))
    }

    @Test fun choosingSilentMovesNothingOnScreenToPulseButKeepsAnotherVisual() {
        assertEquals(RestAlert(RestSound.SILENT, RestVisual.PULSE), RestAlert().withSound(RestSound.SILENT))
        assertEquals(RestAlert(RestSound.SILENT, RestVisual.FLASH),
            RestAlert(visual = RestVisual.FLASH).withSound(RestSound.SILENT))
    }

    @Test fun aForbiddenVisualIsIgnored() {
        val silent = RestAlert(RestSound.SILENT, RestVisual.FLASH)
        assertEquals(silent, silent.withVisual(RestVisual.NONE))
        assertEquals(RestAlert(RestSound.SILENT, RestVisual.PULSE), silent.withVisual(RestVisual.PULSE))
    }

    @Test fun onlyTheAlarmNeedsAStopButton() {
        assertTrue(RestAlert(RestSound.ALARM).needsStop)
        RestSound.entries.filter { it != RestSound.ALARM }.forEach { assertFalse(RestAlert(it, RestVisual.FLASH).needsStop) }
    }
}
