package com.rebuild.data

import com.rebuild.domain.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class PlanMigrationTest {
    /** A 0.1.5/0.1.6 install, from before both the plan change and exercise catalog revision two. */
    private fun legacy(): AppState {
        val seed = Seed.initialState().beforeCatalogTwo()
        return seed.copy(phase = Phase.BASELINE, revision = 12, lastBackupRevision = 12, lastBackupAt = 900,
            templates = seed.templates.map { template ->
                if (template.id != "pull") template else template.copy(slots = template.slots.map {
                    if (it.id != "pull-vertical") it else it.copy(
                        exerciseId = "pullup", alternatives = listOf("pullup-assisted", "pulldown"))
                })
            })
    }

    private fun oldJson(state: AppState = legacy()) =
        v2Json(state).replace("\"planRevision\":1,", "").replace("\"planRevision\":0,", "")

    private fun AppState.vertical() = templates.single { it.id == "pull" }.slots.single { it.id == "pull-vertical" }

    @Test fun oldInstallUpdatesOnlyFuturePrescriptionAndMarksDirtyOnce() = runTest {
        val old = legacy()
        val store = MemoryPersistence(oldJson(old))
        val repo = repository(store, clock = { 5000 })
        val updated = repo.state.value
        assertEquals(old.vertical().copy(exerciseId = "pulldown", alternatives = emptyList()), updated.vertical())
        assertEquals(old.templates.filter { it.id != "pull" }, updated.templates.filter { it.id != "pull" })
        assertEquals("No library exercise changes; catalog revision two's are only added",
            old.exercises + ExerciseCatalog.revisionTwo, updated.exercises)
        assertEquals(13L, updated.revision)
        assertEquals(5000L, updated.unbackedUpSince)
        assertEquals(old.lastBackupRevision, updated.lastBackupRevision)
        assertEquals(1, store.writes)
        assertEquals(updated, repository(store).state.value)
        assertEquals(1, store.writes)
    }

    @Test fun editedTargetsAndBothLegacyOverrideIdentitiesArePreservedExceptExercise() = runTest {
        val old = legacy().let { state -> state.copy(
            templates = state.templates.map { t -> if (t.id != "pull") t else t.copy(slots = t.slots.map {
                if (it.id != "pull-vertical") it else it.copy(baselineSets = 4, upperSets = 6,
                    minReps = 7, maxReps = 11, restSeconds = 210)
            }) },
            overrides = listOf(
                PlanOverride(Phase.BASELINE, "pull-vertical", "pullup", 5, 240),
                PlanOverride(Phase.FIVE_TWO, "pull-vertical", "pullup-assisted", 2, 150)
            ), lastBackupRevision = 11, unbackedUpSince = 700
        ) }
        val updated = repository(MemoryPersistence(oldJson(old))).state.value
        assertEquals(old.vertical().copy(exerciseId = "pulldown", alternatives = emptyList()), updated.vertical())
        assertEquals(old.overrides.map { it.copy(exerciseId = "pulldown") }, updated.overrides)
        assertEquals(700L, updated.unbackedUpSince)
    }

    @Test fun customDefaultAndSubstitutionAndUnrelatedOverrideAreUntouched() = runTest {
        val custom = Exercise("my-row", "My comfortable row", custom = true)
        val old = legacy().let { state -> state.copy(
            exercises = state.exercises + custom,
            templates = state.templates.map { t -> if (t.id != "pull") t else t.copy(slots = t.slots.map {
                if (it.id != "pull-vertical") it else it.copy(exerciseId = custom.id,
                    alternatives = listOf("pulldown", "pullup", "pullup-assisted"))
            }) },
            overrides = listOf(PlanOverride(Phase.BASELINE, "pull-vertical", custom.id, 4, 222),
                PlanOverride(Phase.BASELINE, "upper-pulldown", "pullup", 2, 120))
        ) }
        val updated = repository(MemoryPersistence(oldJson(old))).state.value
        assertEquals(custom.id, updated.vertical().exerciseId)
        assertEquals(listOf("pulldown"), updated.vertical().alternatives)
        assertEquals(old.overrides, updated.overrides)
        assertEquals(old.exercises + ExerciseCatalog.revisionTwo, updated.exercises)
    }

    private fun withSnapshots(): AppState {
        var state = StateChanges.apply(legacy(), Action.StartWorkout("pull", 1000))
        var active = requireNotNull(state.activeWorkout)
        state = StateChanges.apply(state, Action.SaveSet(active.id, active.exercises.first().id,
            "bodyweight", null, 3, 2.0, false, 1100))
        state = StateChanges.apply(state, Action.FinishWorkout(1200))
        state = StateChanges.apply(state, Action.StartWorkout("pull", 2000))
        active = requireNotNull(state.activeWorkout)
        state = StateChanges.apply(state, Action.EditToday(active.exercises.first().id, "pullup-assisted", 3, 180))
        state = StateChanges.apply(state, Action.SaveSet(active.id, active.exercises.first().id,
            "assisted-warmup", 25.0, 5, null, true, 2100))
        return StateChanges.apply(state, Action.SaveSet(active.id, active.exercises.first().id,
            "assisted-working", 20.0, 4, 2.0, false, 2200))
    }

    @Test fun activeWarmupsLoadingHistoryAndTimerRemainExactAndNeverPrefillPulldown() = runTest {
        val old = withSnapshots()
        val repo = repository(MemoryPersistence(oldJson(old)))
        assertEquals(old.workouts, repo.state.value.workouts)
        assertEquals(old.activeWorkoutId, repo.state.value.activeWorkoutId)
        assertEquals(old.restTimer, repo.state.value.restTimer)
        repo.execute(Action.FinishWorkout(3000))
        repo.execute(Action.StartWorkout("pull", 4000))
        val next = requireNotNull(repo.state.value.activeWorkout).exercises.first()
        assertEquals("pulldown", next.exercise.id)
        assertEquals(LoadingType.EXTERNAL, next.exercise.loading)
        assertTrue(next.sets.isEmpty())
        assertNull(repo.state.value.previousComparable(next.exercise, requireNotNull(repo.state.value.activeWorkoutId)))
    }

    @Test fun oldRestorePreviewsNewPlanAndRoundTripsWithoutAnotherMigration() = runTest {
        val old = withSnapshots()
        val store = MemoryPersistence()
        val repo = repository(store, clock = { 5000 })
        val before = repo.state.value
        val preview = repo.previewBackup(oldJson(old))
        assertEquals("pulldown", preview.state.vertical().exerciseId)
        assertEquals(old.workouts, preview.state.workouts)
        assertEquals(before, repo.state.value)
        val safety = repo.exportBackup()
        repo.execute(Action.MarkBackup(safety.revision, 4000))
        repo.restoreBackup(preview, safety.revision)
        val restored = repo.state.value
        assertEquals("pulldown", restored.vertical().exerciseId)
        assertEquals(old.workouts, restored.workouts)
        assertNull(restored.restTimer)
        assertEquals(safety.revision + 1, restored.revision)
        assertEquals(5000L, restored.unbackedUpSince)
        assertEquals(restored, repo.previewBackup(repo.exportBackup().json).state)
        val writes = store.writes
        assertEquals(restored, repository(store).state.value)
        assertEquals(writes, store.writes)
    }

    @Test fun migrationWriteFailureLeavesOldDatabaseIntact() = runTest {
        val json = oldJson()
        val store = MemoryPersistence(json).apply { failWrites = true }
        failure("plan") { repository(store) }
        assertEquals(json, store.snapshot)
        assertEquals(0, store.writes)
        store.failWrites = false
        assertEquals("pulldown", repository(store).state.value.vertical().exerciseId)
    }

    @Test fun failedOldRestoreKeepsCurrentStateAndSafetyRevision() = runTest {
        val store = MemoryPersistence()
        val repo = repository(store)
        val preview = repo.previewBackup(oldJson())
        val safety = repo.exportBackup()
        repo.execute(Action.MarkBackup(safety.revision, 4000))
        val before = repo.state.value
        val disk = store.snapshot
        store.failWrites = true
        failure("previous data is unchanged") { repo.restoreBackup(preview, safety.revision) }
        assertEquals(before, repo.state.value)
        assertEquals(disk, store.snapshot)
    }

    @Test fun deliberatePostUpdateManualVariantIsNotRewrittenOnNextOpen() = runTest {
        val store = MemoryPersistence(oldJson())
        val repo = repository(store)
        assertEquals("pulldown", repo.state.value.vertical().exerciseId)
        repo.execute(Action.EditPlan(Phase.BASELINE, "pull-vertical", "pullup-assisted", 3, 180))
        val captured = repo.state.value
        assertEquals(captured, repository(store).state.value)
        assertEquals(captured, repo.previewBackup(repo.exportBackup().json).state)
    }

    @Test fun changedPulldownLoadingIsRejectedWithoutRewritingItsCatalogOrSource() = runTest {
        val old = legacy().let { it.copy(exercises = it.exercises.map { exercise ->
            if (exercise.id == "pulldown") exercise.copy(loading = LoadingType.ASSISTANCE) else exercise
        }) }
        val json = oldJson(old)
        val store = MemoryPersistence(json)
        failure("external-load") { repository(store) }
        assertEquals(json, store.snapshot)
        assertEquals(0, store.writes)
        val repo = repository()
        failure("external-load") { repo.previewBackup(json) }
        assertTrue(repo.state.value.workouts.isEmpty())
    }

    @Test fun unknownFuturePlanRevisionIsRejectedInsteadOfSilentlyDowngraded() {
        rejected { SnapshotCodec.decode(oldJson().replace("\"schemaVersion\":2", "\"schemaVersion\":2,\"planRevision\":2")) }
    }
}
