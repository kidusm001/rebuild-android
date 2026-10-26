package com.rebuild.data

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rebuild.MainActivity
import com.rebuild.RebuildApplication
import com.rebuild.domain.Phase
import com.rebuild.domain.Seed
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Writes the production database in a disposable emulator, never an owner's installation. */
@RunWith(AndroidJUnit4::class)
class ColdLoadDeviceTest {
    @get:Rule val rule = createEmptyComposeRule()

    @Test fun slowColdLoadShowsOnlyLoadingUntilSavedStateIsReady() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val saved = Seed.initialState().copy(phase = Phase.BASELINE)
        val json = SnapshotCodec.encode(saved)
        val bytes = SnapshotCodec.MAX_BYTES - 1024
        val padded = json + " ".repeat(bytes - json.toByteArray(Charsets.UTF_8).size)
        SqliteSnapshotPersistence(context).use { it.write(padded) }

        ActivityScenario.launch(MainActivity::class.java).use {
            rule.onNodeWithText("Opening your saved workouts").assertIsDisplayed()
            rule.onNodeWithText("Choose where to start").assertDoesNotExist()
            rule.onNodeWithTag("start-scheduled").assertDoesNotExist()
            rule.onNodeWithTag("log-set").assertDoesNotExist()
            rule.waitUntil(timeoutMillis = 30_000) {
                rule.onAllNodes(hasText("Today")).fetchSemanticsNodes().isNotEmpty()
            }
            rule.onNodeWithText("Opening your saved workouts").assertDoesNotExist()
            val loaded = runBlocking { (context as RebuildApplication).repository().state.value }
            assertEquals(saved, loaded)
            SqliteSnapshotPersistence(context).use { store ->
                store.readableDatabase.rawQuery(
                    "SELECT length(CAST(payload AS BLOB)) FROM snapshot WHERE id = 1", null
                ).use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals("Loading must not reseed or rewrite the snapshot", bytes, cursor.getInt(0))
                }
            }
        }
    }
}
