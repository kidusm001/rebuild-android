package com.rebuild.ui

import com.rebuild.data.StateChanges
import com.rebuild.domain.Action
import com.rebuild.domain.AppState
import com.rebuild.domain.Exercise
import com.rebuild.domain.Experience
import com.rebuild.domain.Gear
import com.rebuild.domain.LoadUnit
import com.rebuild.domain.LoadingType
import com.rebuild.domain.MassUnit
import com.rebuild.domain.Seed
import com.rebuild.domain.Splits
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TipsTest {
    private val day = 24L * 60 * 60 * 1000
    private val start = 1_794_800_000_000L

    private fun freshState(): AppState = StateChanges.apply(
        Seed.initialState(),
        Action.CompleteSetup(Experience.NEW, Splits.FULL_BODY_3, Gear.GYM, MassUnit.KG, "2026-11-16")
    )

    private fun startFirstWorkout(state: AppState, at: Long = start): AppState =
        StateChanges.apply(state, Action.StartWorkout("full-body-3-a", at, "2026-11-16"))

    private fun logOneSet(state: AppState, id: String, at: Long = start + 60_000): AppState {
        val workout = state.activeWorkout!!
        val we = workout.exercises.first()
        return StateChanges.apply(state, Action.SaveSet(workout.id, we.id, id, 40.0, 10, 3.0, false, at))
    }

    private fun finishWorkouts(count: Int, baseTime: Long = start): AppState {
        var state = freshState()
        repeat(count) { i ->
            val t = baseTime + i * day
            state = StateChanges.apply(state, Action.StartWorkout("full-body-3-a", t, "2026-11-${16 + i}"))
            state = logOneSet(state, "set-$i", t + 60_000)
            state = StateChanges.apply(state, Action.FinishWorkout(t + 120_000, countForWeek = false))
        }
        return state
    }

    @Test fun setEntryTipShowsBeforeFirstSetRetiresAfterOneSetAndReturnsOnceWhenReenabled() {
        val started = startFirstWorkout(freshState())
        val tips = TipsState()
        assertTrue(shouldShowSetEntryTip(started, tips))
        assertFalse(shouldShowSetEntryTip(started, tips.dismiss(Tip.SET_ENTRY)))
        assertFalse(shouldShowSetEntryTip(started, tips.setEnabled(false)))

        val afterSet = logOneSet(started, "s1")
        assertFalse("retires once a set has been logged", shouldShowSetEntryTip(afterSet, tips))
        val reenabled = tips.setEnabled(false).setEnabled(true)
        assertTrue("turning Show tips back on brings it back even with logged sets", shouldShowSetEntryTip(afterSet, reenabled))
        assertFalse("dismissing clears its forceShow entry", shouldShowSetEntryTip(afterSet, reenabled.dismiss(Tip.SET_ENTRY)))
        assertTrue("other tips stay force-shown until dismissed", Tip.REST_BAR in reenabled.dismiss(Tip.SET_ENTRY).forceShow)
    }

    @Test fun restBarTipShowsOnFirstWorkoutsRestNeverOnShortPauseAndRetiresAfterFinishing() {
        val active = logOneSet(startFirstWorkout(freshState()), "s1")
        val tips = TipsState()
        assertTrue(shouldShowRestBarTip(active, tips, shortPause = false))
        assertFalse("short rest-pause never shows the rest bar tip", shouldShowRestBarTip(active, tips, shortPause = true))
        assertFalse(shouldShowRestBarTip(active, tips.dismiss(Tip.REST_BAR), shortPause = false))

        val secondWorkout = startFirstWorkout(finishWorkouts(1), start + day)
        assertFalse("retires once a workout is finished", shouldShowRestBarTip(secondWorkout, tips, shortPause = false))
        val reenabled = tips.reset()
        assertTrue(shouldShowRestBarTip(secondWorkout, reenabled, shortPause = false))
        assertFalse(shouldShowRestBarTip(secondWorkout, reenabled.dismiss(Tip.REST_BAR), shortPause = false))
    }

    @Test fun firstReceiptTipShowsOnlyOnFirstFinishedWorkoutUntilReenabled() {
        val tips = TipsState()
        val first = finishWorkouts(1)
        assertTrue(shouldShowFirstReceiptTip(first, tips))
        assertFalse(shouldShowFirstReceiptTip(first, tips.dismiss(Tip.FIRST_RECEIPT)))

        val second = finishWorkouts(2)
        assertFalse(shouldShowFirstReceiptTip(second, tips))
        val reenabled = tips.reset()
        assertTrue(shouldShowFirstReceiptTip(second, reenabled))
        assertFalse(shouldShowFirstReceiptTip(second, reenabled.dismiss(Tip.FIRST_RECEIPT)))
    }

    @Test fun backupTipShowsAfterThirdWorkoutNeverBackedUpAndNeverNagsACurrentBackup() {
        val tips = TipsState()
        assertFalse(shouldShowBackupTip(finishWorkouts(2), tips))

        val three = finishWorkouts(3)
        assertTrue("shows after 3 workouts with no backup", shouldShowBackupTip(three, tips))
        assertFalse(shouldShowBackupTip(three, tips.dismiss(Tip.BACKUP_AFTER_THREE)))

        val backedUp = three.copy(lastBackupAt = start + 3 * day, lastBackupRevision = three.revision)
        assertFalse("gone once backed up", shouldShowBackupTip(backedUp, tips))
        assertFalse("turning tips back on never nags when the backup is current",
            shouldShowBackupTip(backedUp, tips.reset()))

        val unsavedAfterBackup = backedUp.copy(revision = backedUp.revision + 1)
        assertTrue("re-enabling tips can remind when there are unbacked-up changes",
            shouldShowBackupTip(unsavedAfterBackup, tips.reset()))
    }

    @Test fun setEntryCopyMatchesTheExercisesLoadPerDumbbellAndUnit() {
        val weighted = Exercise("bench", "Bench press")
        assertTrue("kg" in setEntryTipText(weighted, LoadUnit.KG))
        assertTrue("lb" in setEntryTipText(weighted, LoadUnit.LB))
        assertTrue("plates" in setEntryTipText(weighted, LoadUnit.PLATES))
        assertTrue("reps you could still have done" in setEntryTipText(weighted, LoadUnit.KG))

        val dumbbell = Exercise("bench-db", "Flat dumbbell bench press", perDumbbell = true)
        assertTrue("one dumbbell (kg)" in setEntryTipText(dumbbell, LoadUnit.KG))
        assertTrue("one dumbbell (lb)" in setEntryTipText(dumbbell, LoadUnit.LB))

        val bw = Exercise("pushup", "Push-up", LoadingType.BODYWEIGHT)
        assertFalse("kg" in setEntryTipText(bw, LoadUnit.KG))
        assertTrue("reps you could still have done" in setEntryTipText(bw, LoadUnit.KG))

        val assisted = Exercise("pullup-assisted", "Assisted pull-up", LoadingType.ASSISTANCE)
        assertTrue("assistance (lb)" in setEntryTipText(assisted, LoadUnit.LB))
    }
}
