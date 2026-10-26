package com.rebuild.data

import com.rebuild.domain.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class BackupTest {
    @Test fun exportIsStableAndCapturedRevisionIsNotAffectedByLaterEdits() = runTest {
        val repo = repository()
        val workout = repo.start()
        repo.log()
        val first = repo.exportBackup()
        assertEquals(first, repo.exportBackup())
        assertEquals(first.revision, repo.previewBackup(first.json).state.revision)
        repo.execute(Action.WorkoutNote(workout.id, "Newer"))
        assertNotEquals(first.revision, repo.state.value.revision)
        assertEquals("", repo.previewBackup(first.json).state.activeWorkout!!.notes)
    }

    @Test fun roundTripPreservesCustomLibraryTemplatesPlanPhaseHistoryAndActiveSnapshots() = runTest {
        val seed = Seed.initialState()
        val customized = seed.copy(templates = seed.templates.map {
            if (it.id == "push") it.copy(name = "My push day") else it
        })
        val source = repository(MemoryPersistence(SnapshotCodec.encode(customized)))
        source.execute(Action.AddExercise(Exercise("custom-lift", "My cable press", custom = true)))
        source.execute(Action.EditPlan(Phase.BASELINE, "push-incline", "custom-lift", 3, 123))
        source.execute(Action.EditPlan(Phase.INTRO_ONE, "intro-chest", "chest-machine", 1, 45))
        val completed = source.start()
        source.log()
        source.execute(Action.WorkoutNote(completed.id, "History notes"))
        source.execute(Action.ExerciseNote(completed.id, completed.exercises.first().id, "Equipment notes"))
        source.execute(Action.FinishWorkout(5000))
        source.execute(Action.StartWorkout("upper", 6000))
        source.log(id = "active-set", now = 7000)
        val original = source.state.value
        val document = source.exportBackup()
        val destinationStore = MemoryPersistence()
        val destination = repository(destinationStore, clock = { 11000 })
        val safety = destination.exportBackup()
        destination.execute(Action.MarkBackup(safety.revision, 10000))
        destination.restoreBackup(destination.previewBackup(document.json), safety.revision)
        val restored = destination.state.value
        assertEquals(original.copy(revision = safety.revision + 1, restTimer = null,
            lastBackupAt = 10000, lastBackupRevision = safety.revision, unbackedUpSince = 11000), restored)
        assertNotNull(restored.activeWorkout)
        assertEquals("My push day", restored.workouts.first().name)
        assertEquals("History notes", restored.workouts.first().notes)
        assertEquals("custom-lift", restored.workouts.first().exercises.first().exercise.id)
        assertEquals(restored, repository(destinationStore).state.value)
        assertNull(restored.restTimer)
    }

    @Test fun missingOrStaleSafetyBackupCannotReplaceAnyData() = runTest {
        val repo = repository()
        val restore = repo.previewBackup(repo.exportBackup().json)
        val original = repo.state.value
        failure("safety backup") { repo.restoreBackup(restore, original.revision) }
        assertEquals(original, repo.state.value)
        val safety = repo.exportBackup()
        repo.execute(Action.MarkBackup(safety.revision, 1000))
        repo.execute(Action.SelectPhase(Phase.BASELINE))
        val changed = repo.state.value
        failure("data changed") { repo.restoreBackup(restore, safety.revision) }
        failure("safety backup") { repo.restoreBackup(restore, changed.revision) }
        assertEquals(changed, repo.state.value)
    }

    @Test fun markingStaleExportDoesNotMarkNewerRevisionBackedUpOrBumpRevision() = runTest {
        val repo = repository()
        val captured = repo.exportBackup()
        repo.start()
        val revision = repo.state.value.revision
        repo.execute(Action.MarkBackup(captured.revision, 3000))
        assertEquals(revision, repo.state.value.revision)
        assertEquals(captured.revision, repo.state.value.lastBackupRevision)
        failure("safety backup") { repo.restoreBackup(repo.previewBackup(captured.json), revision) }
        failure("invalid") { repo.execute(Action.MarkBackup(revision + 1, 4000)) }
    }

    @Test fun failedBackupMarkDoesNotEnableRestore() = runTest {
        val store = MemoryPersistence()
        val repo = repository(store)
        val document = repo.exportBackup()
        val before = repo.state.value
        store.failWrites = true
        failure("Could not save") { repo.execute(Action.MarkBackup(document.revision, 3000)) }
        assertEquals(before, repo.state.value)
        store.failWrites = false
        failure("safety backup") { repo.restoreBackup(repo.previewBackup(document.json), document.revision) }
    }

    @Test fun previewIsReadOnlyAndAbandoningItDoesNotChangeAnything() = runTest {
        val store = MemoryPersistence()
        val repo = repository(store)
        repo.start()
        repo.log()
        val before = repo.state.value
        val disk = store.snapshot
        repo.previewBackup(repo.exportBackup().json)
        assertEquals(before, repo.state.value)
        assertEquals(disk, store.snapshot)
    }

    @Test fun restoreRevalidatesSourceAndNeverTrustsForgedPreviewState() = runTest {
        val repo = repository()
        repo.start()
        val source = repo.exportBackup()
        repo.execute(Action.MarkBackup(source.revision, 3000))
        val before = repo.state.value
        failure("JSON") { repo.restoreBackup(BackupPreview(Seed.initialState(), "broken"), before.revision) }
        assertEquals(before, repo.state.value)
        repo.restoreBackup(BackupPreview(Seed.initialState(), source.json), before.revision)
        assertEquals(before.activeWorkout, repo.state.value.activeWorkout)
        assertEquals(before.revision + 1, repo.state.value.revision)
    }

    @Test fun failedRestorePreservesDatabaseStateAndCurrentTimer() = runTest {
        val store = MemoryPersistence()
        val repo = repository(store)
        val empty = repo.previewBackup(repo.exportBackup().json)
        repo.start()
        repo.log()
        val safety = repo.exportBackup()
        repo.execute(Action.MarkBackup(safety.revision, 3000))
        val before = repo.state.value
        val disk = store.snapshot
        store.failWrites = true
        failure("previous data is unchanged") { repo.restoreBackup(empty, before.revision) }
        assertEquals(before, repo.state.value)
        assertEquals(disk, store.snapshot)
        assertNotNull(repo.state.value.restTimer)
        store.failWrites = false
        repo.restoreBackup(empty, before.revision)
        assertTrue(repo.state.value.workouts.isEmpty())
        assertNull(repo.state.value.restTimer)
    }

    @Test fun restoredAlarmIdNeverNotifiesAndNewRevisionRequiresNewSafetyBackup() = runTest {
        val repo = repository()
        repo.start()
        repo.log()
        val timer = repo.state.value.restTimer!!
        val backup = repo.exportBackup()
        repo.execute(Action.MarkBackup(backup.revision, 3000))
        val preview = repo.previewBackup(backup.json)
        repo.restoreBackup(preview, backup.revision)
        assertFalse(repo.consumeRestTimer(timer.id, timer.deadline))
        failure("safety backup") { repo.restoreBackup(preview, repo.state.value.revision) }
    }

    @Test fun revisionOverflowFailsWithoutWriting() = runTest {
        val store = MemoryPersistence(SnapshotCodec.encode(Seed.initialState().copy(
            revision = Long.MAX_VALUE, unbackedUpSince = 1000)))
        val repo = repository(store)
        val before = repo.state.value
        failure("revision limit") { repo.execute(Action.SelectPhase(Phase.BASELINE)) }
        assertEquals(before, repo.state.value)
        assertEquals(0, store.writes)
    }

    @Test fun concurrentRestoreAndExportNeverPublishAnUncommittedOrStaleReplacement() = runTest {
        val store = MemoryPersistence()
        val repo = repository(store)
        val empty = repo.previewBackup(repo.exportBackup().json)
        repo.start()
        repo.log()
        val safety = repo.exportBackup()
        repo.execute(Action.MarkBackup(safety.revision, 3000))
        val before = repo.state.value
        val oldDisk = store.snapshot
        val writing = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        store.beforeWrite = {
            assertEquals(before, repo.state.value)
            assertEquals(oldDisk, store.snapshot)
            writing.complete(Unit)
            check(release.await(10, TimeUnit.SECONDS))
        }
        val restoring = async(Dispatchers.Default) { repo.restoreBackup(empty, before.revision) }
        writing.await()
        val exporting = async(Dispatchers.Default) { repo.exportBackup() }
        val competing = async(Dispatchers.Default) {
            failure("data changed") { repo.restoreBackup(empty, before.revision) }
        }
        assertEquals(before, repo.state.value)
        release.countDown()
        restoring.await()
        competing.await()
        val exported = exporting.await()
        val restored = repo.state.value
        assertEquals(before.revision + 1, restored.revision)
        assertTrue(restored.workouts.isEmpty())
        assertNull(restored.restTimer)
        assertEquals(restored, SnapshotCodec.decode(store.snapshot!!))
        assertEquals(restored, SnapshotCodec.decode(exported.json))
    }
}
