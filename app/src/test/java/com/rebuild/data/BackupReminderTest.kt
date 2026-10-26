package com.rebuild.data

import com.rebuild.domain.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class BackupReminderTest {
    private val week = 7L * 24 * 60 * 60 * 1000

    @Test fun phaseOnlyChangesBecomeDueAfterAWeekWithoutAnyWorkouts() = runTest {
        var now = 1000L
        val repo = repository(clock = { now })
        assertNull(repo.state.value.unbackedUpSince)
        assertFalse(repo.state.value.backupDue(now + week * 10))
        repo.execute(Action.SelectPhase(Phase.BASELINE))
        assertEquals(1000L, repo.state.value.unbackedUpSince)
        assertTrue(repo.state.value.workouts.isEmpty())
        assertFalse(repo.state.value.backupDue(999))
        assertFalse(repo.state.value.backupDue(now + week - 1))
        assertTrue(repo.state.value.backupDue(now + week))
        now += week
        assertTrue(repo.state.value.backupDue(now))
    }

    @Test fun planOnlyChangesKeepFirstDirtyTimestampAcrossFurtherEditsAndReopen() = runTest {
        var now = 1000L
        val store = MemoryPersistence()
        val repo = repository(store, clock = { now })
        repo.execute(Action.EditPlan(Phase.BASELINE, "push-incline", "incline-db", 3, 180))
        now += 100000
        repo.execute(Action.EditPlan(Phase.BASELINE, "push-incline", "incline-db", 3, 120))
        repo.execute(Action.AddExercise(Exercise("new", "New exercise", custom = true)))
        assertEquals(1000L, repo.state.value.unbackedUpSince)
        assertTrue(repo.state.value.workouts.isEmpty())
        val reopened = repository(store, clock = { now + week })
        assertEquals(repo.state.value, reopened.state.value)
        assertTrue(reopened.state.value.backupDue(1000 + week))
    }

    @Test fun onlyCurrentBackupClearsDirtyTimestampAndNextEditStartsFreshWindow() = runTest {
        var now = 1000L
        val repo = repository(clock = { now })
        repo.execute(Action.SelectPhase(Phase.BASELINE))
        val captured = repo.exportBackup()
        now = 2000
        repo.execute(Action.AddExercise(Exercise("new", "New exercise", custom = true)))
        val changedRevision = repo.state.value.revision
        repo.execute(Action.MarkBackup(captured.revision, now))
        assertEquals(1000L, repo.state.value.unbackedUpSince)
        assertEquals(changedRevision, repo.state.value.revision)
        assertTrue(repo.state.value.backupDue(1000 + week))
        repo.execute(Action.MarkBackup(changedRevision, now))
        assertNull(repo.state.value.unbackedUpSince)
        assertFalse(repo.state.value.backupDue(now + week * 10))
        now = 3000
        repo.execute(Action.EditPlan(Phase.BASELINE, "push-incline", "incline-db", 3, 90))
        assertEquals(3000L, repo.state.value.unbackedUpSince)
        assertFalse(repo.state.value.backupDue(3000 + week - 1))
        assertTrue(repo.state.value.backupDue(3000 + week))
    }

    @Test fun oldLateBackupCompletionCannotClaimNewerDataIsBackedUp() = runTest {
        var now = 1000L
        val repo = repository(clock = { now })
        val old = repo.exportBackup()
        repo.execute(Action.SelectPhase(Phase.BASELINE))
        repo.execute(Action.MarkBackup(repo.state.value.revision, now))
        assertNull(repo.state.value.unbackedUpSince)
        now = 2000
        repo.execute(Action.MarkBackup(old.revision, now))
        assertEquals(old.revision, repo.state.value.lastBackupRevision)
        assertEquals(2000L, repo.state.value.unbackedUpSince)
        assertTrue(repo.state.value.backupDue(2000 + week))
    }

    @Test fun failedMutationAndFailedBackupMarkDoNotChangeReminderMetadata() = runTest {
        var now = 1000L
        val store = MemoryPersistence()
        val repo = repository(store, clock = { now })
        store.failWrites = true
        failure("Could not save") { repo.execute(Action.SelectPhase(Phase.BASELINE)) }
        assertNull(repo.state.value.unbackedUpSince)
        store.failWrites = false
        now = 2000
        repo.execute(Action.SelectPhase(Phase.BASELINE))
        val before = repo.state.value
        store.failWrites = true
        failure("Could not save") { repo.execute(Action.MarkBackup(before.revision, now)) }
        assertEquals(before, repo.state.value)
        assertEquals(2000L, repo.state.value.unbackedUpSince)
    }

    @Test fun historyCorrectionUsesEditClockNotOriginalSetCompletionTime() = runTest {
        var now = 10000L
        val repo = repository(clock = { now })
        val history = repo.start()
        repo.log()
        repo.execute(Action.FinishWorkout(3000))
        repo.execute(Action.MarkBackup(repo.state.value.revision, now))
        now = 20000
        repo.log(workout = history, reps = 11, now = 2000)
        assertEquals(20000L, repo.state.value.unbackedUpSince)
        assertEquals(2000L, repo.state.value.workouts.single().exercises.first().sets.single().completedAt)
    }

    @Test fun restorationStartsLocalDirtyWindowRatherThanImportingOldTimestamp() = runTest {
        val source = repository(clock = { 1000 })
        source.execute(Action.SelectPhase(Phase.BASELINE))
        val document = source.exportBackup()
        val destination = repository(clock = { 50000 })
        destination.execute(Action.MarkBackup(destination.state.value.revision, 40000))
        destination.restoreBackup(destination.previewBackup(document.json), destination.state.value.revision)
        assertEquals(50000L, destination.state.value.unbackedUpSince)
        assertFalse(destination.state.value.backupDue(50000 + week - 1))
        assertTrue(destination.state.value.backupDue(50000 + week))
    }

    @Test fun missingLegacyMetadataDecodesAndInvalidMetadataIsRejected() {
        val seed = Seed.initialState()
        val legacy = SnapshotCodec.encode(seed).replace(",\"unbackedUpSince\":null", "")
        assertEquals(seed, SnapshotCodec.decode(legacy))
        rejected { SnapshotCodec.validate(seed.copy(unbackedUpSince = -1)) }
        rejected { SnapshotCodec.validate(seed.copy(lastBackupAt = 1000, lastBackupRevision = 0, unbackedUpSince = 1000)) }
    }

    @Test fun reopeningLegacyPlanOnlyChangesInitializesReminderOnceWithoutReseeding() = runTest {
        val old = Seed.initialState().copy(revision = 5, phase = Phase.BASELINE,
            overrides = listOf(PlanOverride(Phase.BASELINE, "push-incline", "incline-machine", 3, 123)))
        val store = MemoryPersistence(SnapshotCodec.encode(old))
        val repo = repository(store, clock = { 50000 })
        assertEquals(old.copy(unbackedUpSince = 50000), repo.state.value)
        assertEquals(1, store.writes)
        val reopened = repository(store, clock = { 100000 })
        assertEquals(repo.state.value, reopened.state.value)
        assertEquals(1, store.writes)
        assertTrue(reopened.state.value.backupDue(50000 + week))
    }

    @Test fun failureToInitializeLegacyReminderDoesNotRewriteSavedPlan() = runTest {
        val old = Seed.initialState().copy(revision = 5, phase = Phase.BASELINE)
        val stored = SnapshotCodec.encode(old)
        val store = MemoryPersistence(stored).apply { failWrites = true }
        failure("not been reset") { repository(store, clock = { 50000 }) }
        assertEquals(stored, store.snapshot)
        assertEquals(0, store.writes)
    }
}
