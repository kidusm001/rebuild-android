package com.rebuild.ui

import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityCheckResult.AccessibilityCheckResultType
import com.google.android.apps.common.testing.accessibility.framework.integrations.espresso.AccessibilityValidator
import com.rebuild.MainActivity
import com.rebuild.platform.BackupViewModel
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@SdkSuppress(minSdkVersion = 34)
@RunWith(AndroidJUnit4::class)
class BackupAccessibilityDeviceTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun productionPreviewAndSafetyConfirmationPassNativeChecks() {
        rule.enableAccessibilityChecks(
            AccessibilityValidator().setRunChecksFromRootView(true)
                .setThrowExceptionFor(AccessibilityCheckResultType.ERROR)
        )
        val model = ViewModelProvider(rule.activity)[BackupViewModel::class.java]
        rule.waitUntil(30_000) { model.repository.value != null }
        val repository = requireNotNull(model.repository.value)
        val before = repository.state.value.workouts
        val input = File(rule.activity.cacheDir, "a11y-restore-input.json")
        val safety = File(rule.activity.cacheDir, "a11y-safety-output.json")
        try {
            input.writeText(runBlocking { repository.exportBackup().json })
            rule.runOnIdle { model.readRestore(Uri.fromFile(input)) }
            rule.waitUntil { model.ui.value.preview != null && !model.ui.value.busy }
            rule.onNodeWithTag("restore-confirm").assertIsDisplayed().assertTextContains("Save current backup")
            rule.onNodeWithTag("restore-confirm").tryPerformAccessibilityChecks()
            var pickerRequested = false
            rule.runOnIdle { model.beginExport(true) { pickerRequested = true } }
            rule.waitUntil { pickerRequested && !model.ui.value.busy }
            rule.runOnIdle { model.completeExport(Uri.fromFile(safety)) }
            rule.waitUntil { model.ui.value.safetyRevision != null && !model.ui.value.busy }
            rule.onNodeWithTag("restore-confirm").assertTextContains("Replace all data")
            rule.onNodeWithTag("restore-confirm").tryPerformAccessibilityChecks()
            assertTrue("Production safety copy must really be written", safety.length() > 0)
            rule.onNodeWithTag("restore-cancel").performClick()
            assertEquals(before, repository.state.value.workouts)
        } finally {
            input.delete()
            safety.delete()
        }
    }
}
