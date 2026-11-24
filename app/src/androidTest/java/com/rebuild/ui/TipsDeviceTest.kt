package com.rebuild.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rebuild.data.RebuildRepository
import com.rebuild.data.SnapshotCodec
import com.rebuild.data.SnapshotPersistence
import com.rebuild.data.StateChanges
import com.rebuild.domain.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** In-place teaching tips, per-tip dismissal, More › Show tips, and picking the added splits. */
@RunWith(AndroidJUnit4::class)
class TipsDeviceTest {
    @get:Rule val rule = createComposeRule()

    // FIXED_NOW is Tuesday; full-body-3 trains Mon/Wed/Fri, so Monday has a scheduled workout.
    private val monday = FIXED_NOW - 24L * 60 * 60 * 1000

    private fun readyState(unit: MassUnit = MassUnit.LB, at: Long = monday): AppState = StateChanges.apply(
        Seed.initialState(),
        Action.CompleteSetup(Experience.NEW, Splits.FULL_BODY_3, Gear.GYM, unit, localDateOf(at).toString())
    )

    private fun realRepo(state: AppState, now: Long = monday): RebuildRepository = runBlocking {
        var raw: String? = SnapshotCodec.encode(state)
        val persistence = object : SnapshotPersistence {
            override fun read(): String? = raw
            override fun write(snapshot: String) { raw = snapshot }
        }
        RebuildRepository.fromPersistence(persistence) { now }
    }

    @Test fun firstSetRestAndReceiptTipsShowInPlaceAndRetireCleanly() {
        val platform = FakePlatform(initialTips = TipsState())
        val repo = realRepo(readyState(MassUnit.LB, monday), monday)
        rule.setContent { RebuildApp(repo, platform, clock = { monday }) }

        // Start the first workout: first set entry tip names lb and explains RIR.
        rule.onNodeWithTag("start-scheduled").performClick()
        rule.onNodeWithTag("tip-set-entry").assertIsDisplayed()
        rule.onNodeWithText("reps you could still have done", substring = true).assertIsDisplayed()
        rule.onNodeWithText("weight (lb)", substring = true).assertIsDisplayed()

        // Dismiss the set entry tip on its own.
        rule.onNodeWithTag("dismiss-tip-set-entry").performClick()
        rule.onNodeWithTag("tip-set-entry").assertDoesNotExist()
        assertTrue(Tip.SET_ENTRY in platform.tips.value.dismissed)

        // Log set 1: the rest bar opens with its own dismissible tip; skipping retires it so it never repeats.
        rule.onNodeWithTag("entry-kg").performScrollTo().performTextInput("40")
        rule.onNodeWithTag("entry-reps").performScrollTo().performTextInput("10")
        rule.onNodeWithTag("log-set").performScrollTo().performClick()
        rule.waitUntil(5_000) { repo.state.value.restTimer != null }
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("tip-rest-bar").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("rest-bar").assertIsDisplayed()
        rule.onNodeWithTag("tip-rest-bar").assertIsDisplayed()
        rule.onNodeWithContentDescription("Skip rest").performClick()
        rule.waitUntil(5_000) { repo.state.value.restTimer == null }
        assertTrue(Tip.REST_BAR in platform.tips.value.dismissed)

        // Finish the first workout: the receipt shows its History tip and lets the user dismiss it.
        rule.waitUntil(5_000) { rule.actionGuardOpen() }
        rule.onNodeWithText("Finish").performClick()
        rule.onNodeWithTag("confirm-button").performClick()
        rule.waitUntil(5_000) { repo.state.value.activeWorkoutId == null }
        rule.onNodeWithTag("workout-receipt").assertIsDisplayed()
        rule.onNodeWithTag("tip-first-receipt").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("dismiss-tip-first-receipt").performClick()
        rule.onNodeWithTag("tip-first-receipt").assertDoesNotExist()
    }

    @Test fun afterThirdWorkoutTodaySuggestsBackupAndMoreShowTipsBringsTipsBack() {
        val day = 24L * 60 * 60 * 1000
        var state = readyState(MassUnit.KG)
        repeat(3) { i ->
            val at = FIXED_NOW - (10 - i) * day
            state = StateChanges.apply(state, Action.StartWorkout("full-body-3-a", at, localDateOf(at).toString()))
            val w = state.activeWorkout!!
            state = StateChanges.apply(state, Action.SaveSet(w.id, w.exercises.first().id, "s$i", 40.0, 10, 3.0, false, at + 60_000))
            state = StateChanges.apply(state, Action.FinishWorkout(at + 120_000, countForWeek = false))
        }
        val platform = FakePlatform(initialTips = TipsState())
        val repo = FakeRepository(state)
        rule.setContent { RebuildApp(repo, platform, clock = { FIXED_NOW }) }

        // Even past day 7, the third-workout tip shows and holds back the generic backup notice so they never duplicate.
        rule.onNodeWithTag("tip-backup-after-three").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("notice-backup").assertDoesNotExist()
        rule.onNodeWithTag("tip-action-backup-after-three").performClick()
        rule.onNodeWithText("Backup and restore").assertIsDisplayed()
        rule.onNodeWithContentDescription("Back").performClick()

        // Dismissing removes it.
        rule.onNodeWithTag("dismiss-tip-backup-after-three").performScrollTo().performClick()
        rule.onNodeWithTag("tip-backup-after-three").assertDoesNotExist()

        // More › Tips: "Show tips again" brings dismissed tips back directly, and the switch turns them off and back on.
        rule.onNodeWithText("More").performClick()
        rule.onNodeWithTag("more-reset-tips").performScrollTo().performClick()
        assertTrue(platform.tips.value.dismissed.isEmpty())
        assertTrue(Tip.BACKUP_AFTER_THREE in platform.tips.value.forceShow)

        rule.onNodeWithTag("more-show-tips").performScrollTo().assertIsOn().performClick()
        assertFalse(platform.tips.value.enabled)
        rule.onNodeWithTag("more-show-tips").assertIsOff().performClick()
        assertTrue(platform.tips.value.enabled)

        rule.onNodeWithText("Today").performClick()
        rule.onNodeWithTag("tip-backup-after-three").performScrollTo().assertIsDisplayed()
    }

    @Test fun changeSplitOffersTheFourAddedSplitsForWeightedGearAndHidesThemForBodyweight() {
        val repo = FakeRepository(readyState(MassUnit.KG))
        rule.setContent { RebuildApp(repo, FakePlatform(), clock = { FIXED_NOW }) }

        rule.onNodeWithText("Plan").performClick()
        rule.onNodeWithTag("plan-change-split").performClick()

        // All four newly added splits are selectable with a gym.
        val weightedOnly = listOf(Splits.PHUL, Splits.TORSO_LIMBS, Splits.BRO_SPLIT, Splits.ARNOLD_SPLIT)
        for (id in weightedOnly) {
            rule.onNodeWithTag("split-pick-$id").performScrollTo().assertIsDisplayed()
        }

        // Switching to bodyweight hides all four weighted-only splits while keeping the 6 bodyweight-ready splits.
        rule.onNodeWithTag("split-gear-bodyweight").performScrollTo().performClick()
        for (id in weightedOnly) {
            rule.onNodeWithTag("split-pick-$id").assertDoesNotExist()
        }
        rule.onNodeWithTag("split-pick-${Splits.UPPER_LOWER}").performScrollTo().performClick()
        rule.onNodeWithTag("split-name").performScrollTo().assertTextEquals("Upper and lower")
    }

    @Test fun restTipRetiresWhenANewSetReplacesTheFirstRestTimer() {
        val platform = FakePlatform(initialTips = TipsState())
        // Experience.REGULAR starts in Steady (multiple sets per exercise) so set 2 can be logged right away.
        val steadyState = StateChanges.apply(
            Seed.initialState(),
            Action.CompleteSetup(Experience.REGULAR, Splits.FULL_BODY_3, Gear.GYM, MassUnit.KG, localDateOf(monday).toString())
        )
        val repo = realRepo(steadyState, monday)
        rule.setContent { RebuildApp(repo, platform, clock = { monday }) }

        rule.onNodeWithTag("start-scheduled").performClick()
        // Log set 1: starts rest 1 and shows the rest-bar tip (without skipping or dismissing it).
        rule.onNodeWithTag("entry-kg").performScrollTo().performTextInput("40")
        rule.onNodeWithTag("entry-reps").performScrollTo().performTextInput("10")
        rule.onNodeWithTag("log-set").performScrollTo().performClick()
        rule.waitUntil(5_000) { repo.state.value.restTimer != null }
        val firstTimerId = repo.state.value.restTimer!!.id
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("tip-rest-bar").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("tip-rest-bar").assertIsDisplayed()

        // Log set 2 while rest 1 is still running (set 2 prefills set 1's weight and reps): replacing timer 1 retires
        // Tip.REST_BAR cleanly via DisposableEffect.
        rule.waitUntil(5_000) { rule.actionGuardOpen() }
        rule.onNodeWithTag("log-set").performScrollTo().performClick()
        rule.waitUntil(5_000) {
            val next = repo.state.value.restTimer?.id
            next != null && next != firstTimerId
        }
        rule.waitUntil(5_000) { Tip.REST_BAR in platform.tips.value.dismissed }
        assertTrue(Tip.REST_BAR in platform.tips.value.dismissed)
        rule.onNodeWithTag("tip-rest-bar").assertDoesNotExist()
    }

    @Test fun completingABackupRetiresAReenabledBackupTipForGood() {
        val day = 24L * 60 * 60 * 1000
        var state = readyState(MassUnit.KG)
        repeat(3) { i ->
            val at = FIXED_NOW - (10 - i) * day
            state = StateChanges.apply(state, Action.StartWorkout("full-body-3-a", at, localDateOf(at).toString()))
            val w = state.activeWorkout!!
            state = StateChanges.apply(state, Action.SaveSet(w.id, w.exercises.first().id, "s$i", 40.0, 10, 3.0, false, at + 60_000))
            state = StateChanges.apply(state, Action.FinishWorkout(at + 120_000, countForWeek = false))
        }
        val backedUp = state.copy(lastBackupAt = FIXED_NOW - day, lastBackupRevision = state.revision)
        val unsaved = backedUp.copy(revision = backedUp.revision + 1)
        val platform = FakePlatform(initialTips = TipsState().reset())
        val repo = realRepo(unsaved, FIXED_NOW)
        rule.setContent { RebuildApp(repo, platform, clock = { FIXED_NOW }) }

        // Re-enabled tip shows because there are unbacked-up changes.
        rule.onNodeWithTag("tip-backup-after-three").performScrollTo().assertIsDisplayed()

        // Exporting a backup records MarkBackup and retires BACKUP_AFTER_THREE from forceShow.
        runBlocking { repo.execute(Action.MarkBackup(repo.state.value.revision, FIXED_NOW)) }
        rule.waitUntil(5_000) { Tip.BACKUP_AFTER_THREE in platform.tips.value.dismissed }
        assertFalse(Tip.BACKUP_AFTER_THREE in platform.tips.value.forceShow)

        // Making another change later does not bring the backup tip back.
        runBlocking { repo.execute(Action.SetLiftingUnit(MassUnit.LB)) }
        rule.waitForIdle()
        rule.onNodeWithTag("tip-backup-after-three").assertDoesNotExist()
    }
}
