package com.rebuild.platform

import android.content.Context
import com.rebuild.ui.Tip
import com.rebuild.ui.TipsState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class TipsPrefsTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test fun freshPrefsDefaultToEnabledWithNoDismissalsAndPersistAcrossInstances() {
        val first = TipsPrefs(context)
        assertEquals(TipsState(enabled = true, dismissed = emptySet(), forceShow = emptySet()), first.tips.value)

        first.dismiss(Tip.SET_ENTRY)
        first.dismiss(Tip.REST_BAR)
        val reopened = TipsPrefs(context)
        assertTrue(reopened.tips.value.enabled)
        assertEquals(setOf(Tip.SET_ENTRY, Tip.REST_BAR), reopened.tips.value.dismissed)

        reopened.setEnabled(false)
        assertFalse(TipsPrefs(context).tips.value.enabled)

        reopened.setEnabled(true)
        val reenabled = TipsPrefs(context).tips.value
        assertTrue(reenabled.enabled)
        assertTrue(reenabled.dismissed.isEmpty())
        assertEquals(Tip.entries.toSet(), reenabled.forceShow)

        reopened.dismiss(Tip.SET_ENTRY)
        val afterOne = TipsPrefs(context).tips.value
        assertEquals(setOf(Tip.SET_ENTRY), afterOne.dismissed)
        assertFalse(Tip.SET_ENTRY in afterOne.forceShow)
        assertTrue(Tip.REST_BAR in afterOne.forceShow)
    }

    @Test fun unknownTipNamesInStoredPrefsAreIgnoredSafely() {
        context.getSharedPreferences("tips", Context.MODE_PRIVATE).edit()
            .putBoolean("enabled", true)
            .putStringSet("dismissed", setOf("FUTURE_TIP_V99", Tip.FIRST_RECEIPT.name))
            .putStringSet("force_show", setOf("ANOTHER_UNKNOWN", Tip.BACKUP_AFTER_THREE.name))
            .apply()

        val loaded = TipsPrefs(context).tips.value
        assertTrue(loaded.enabled)
        assertEquals(setOf(Tip.FIRST_RECEIPT), loaded.dismissed)
        assertEquals(setOf(Tip.BACKUP_AFTER_THREE), loaded.forceShow)
    }
}
