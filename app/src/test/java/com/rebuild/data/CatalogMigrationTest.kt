package com.rebuild.data

import com.rebuild.domain.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class CatalogMigrationTest {
    @Test fun v1DecodesAsKgThenMigratesOnceWithOneDirtyRevisionAndOneWrite() = runTest {
        val old = legacyState().copy(revision = 12, lastBackupRevision = 12, lastBackupAt = 1000)
        val source = legacyJson(old)
        assertEquals(old, SnapshotCodec.decode(source))
        assertEquals(old, SnapshotCodec.decode(source.replace("\"schemaVersion\":1,", "")))
        val store = MemoryPersistence(source)
        val repo = repository(store, clock = { 5000 })
        val migrated = repo.state.value
        assertEquals(Seed.exercises, migrated.exercises)
        assertEquals(SnapshotCodec.SCHEMA_VERSION, migrated.schemaVersion)
        assertEquals("Every catalog revision is applied in the one migration", ExerciseCatalog.REVISION,
            migrated.catalogRevision)
        assertEquals(13L, migrated.revision)
        assertEquals(5000L, migrated.unbackedUpSince)
        assertEquals(old.lastBackupAt, migrated.lastBackupAt)
        assertEquals(old.lastBackupRevision, migrated.lastBackupRevision)
        assertEquals(old.templates, migrated.templates)
        assertEquals(old.overrides, migrated.overrides)
        assertEquals(1, store.writes)
        assertEquals(migrated, SnapshotCodec.decode(requireNotNull(store.snapshot)))
        assertEquals(migrated, repository(store, clock = { 9000 }).state.value)
        assertEquals(1, store.writes)
    }

    @Test fun editedCanonicalAndCustomAndCollidingNewIdsAreNeverOverwritten() = runTest {
        val renamed = Exercise("incline-db", "My edited incline", perDumbbell = true)
        val changedLoading = Exercise("pulldown", "Lat pulldown", LoadingType.ASSISTANCE)
        val changedConvention = Exercise("pushdown", "Cable triceps pushdown", perDumbbell = true)
        val customCanonical = Exercise("curl-cable", "Cable curl", custom = true)
        val collision = Exercise("row-db", "My old custom row", custom = true)
        val custom = Exercise("my-cable", "Cable stack exercise", custom = true)
        val edits = listOf(renamed, changedLoading, changedConvention, customCanonical)
        val old = legacyState().copy(phase = Phase.BASELINE, revision = 20, unbackedUpSince = 200,
            exercises = legacyState().exercises.map { e -> edits.find { it.id == e.id } ?: e } + collision + custom,
            overrides = listOf(PlanOverride(Phase.BASELINE, "pull-vertical", custom.id, 3, 150)))
        val migrated = repository(MemoryPersistence(legacyJson(old))).state.value
        (edits + collision + custom).forEach { expected ->
            assertEquals(expected, migrated.exercises.single { it.id == expected.id })
            assertFalse(migrated.exercises.single { it.id == expected.id }.supportsPlates)
        }
        assertEquals("Revision one fills the library to 61; revision two adds its timed, distance and cardio exercises",
            61 + ExerciseCatalog.revisionTwo.size, migrated.exercises.size)
        assertEquals(old.overrides, migrated.overrides)
        assertEquals(old.templates, migrated.templates)
        assertEquals(old.phase, migrated.phase)
        assertEquals(200L, migrated.unbackedUpSince)
    }

    private fun withHistoryAndActive(): AppState {
        var state = legacyState().copy(phase = Phase.BASELINE)
        state = StateChanges.apply(state, Action.StartWorkout("pull", 1000))
        var workout = requireNotNull(state.activeWorkout)
        state = StateChanges.apply(state, Action.SaveSet(workout.id, workout.exercises.first().id,
            "old-warmup", 11.25, 8, null, true, 1100))
        state = StateChanges.apply(state, Action.SaveSet(workout.id, workout.exercises.first().id,
            "old-working", 23.75, 12, 2.5, false, 1200))
        state = StateChanges.apply(state, Action.FinishWorkout(2000))
        state = StateChanges.apply(state, Action.StartWorkout("pull", 3000))
        workout = requireNotNull(state.activeWorkout)
        state = StateChanges.apply(state, Action.SaveSet(workout.id, workout.exercises.first().id,
            "active-working", 26.125, 9, 1.5, false, 3100))
        state = StateChanges.apply(state, Action.WorkoutNote(workout.id, "Keep seat at 4"))
        return StateChanges.apply(state, Action.ExerciseNote(workout.id, workout.exercises.first().id, "Old notes"))
            .copy(revision = 7, unbackedUpSince = 100)
    }

    @Test fun historyIsExactActiveGetsOnlySafeMetadataAndNumbersAndTimerStayExact() = runTest {
        val old = withHistoryAndActive()
        val repo = repository(MemoryPersistence(legacyJson(old)))
        val state = repo.state.value
        assertEquals(old.workouts.first(), state.workouts.first())
        assertEquals(old.activeWorkoutId, state.activeWorkoutId)
        assertEquals(old.restTimer, state.restTimer)
        val before = requireNotNull(old.activeWorkout)
        val after = requireNotNull(state.activeWorkout)
        assertEquals(before, after.copy(exercises = after.exercises.zip(before.exercises) { a, b ->
            a.copy(exercise = b.exercise)
        }))
        assertTrue(after.exercises.first().exercise.supportsPlates)
        assertEquals(LoadUnit.KG, after.exercises.first().entryLoadUnit)
        assertTrue(after.exercises.flatMap { it.sets }.all { it.loadUnit == LoadUnit.KG && it.plates == null })
        assertEquals(old.workouts.first().exercises.first(),
            state.previousComparable(after.exercises.first().exercise, after.id))
        assertNull(state.previousComparable(after.exercises.first().exercise, after.id, LoadUnit.PLATES))
        repo.execute(Action.SetLoadUnit(after.id, after.exercises.first().id, LoadUnit.PLATES))
        assertEquals(old.restTimer, repo.state.value.restTimer)
        assertEquals(before.exercises.first().sets, repo.state.value.activeWorkout!!.exercises.first().sets)
    }

    @Test fun renamedOrChangedActiveSnapshotsAreNotGrantedCapabilitiesByLibraryId() = runTest {
        val old = withHistoryAndActive()
        val workout = requireNotNull(old.activeWorkout)
        val first = workout.exercises.first()
        listOf(
            first.exercise.copy(name = "My older cable"),
            first.exercise.copy(loading = LoadingType.ASSISTANCE),
            first.exercise.copy(perDumbbell = true),
            first.exercise.copy(custom = true)
        ).forEach { definition ->
            val altered = workout.copy(exercises = listOf(first.copy(exercise = definition)) + workout.exercises.drop(1))
            val source = old.copy(workouts = listOf(old.workouts.first(), altered))
            val repo = repository(MemoryPersistence(legacyJson(source)))
            assertEquals(definition, repo.state.value.activeWorkout!!.exercises.first().exercise)
            assertFalse(repo.state.value.activeWorkout!!.exercises.first().exercise.supportsPlates)
            val before = repo.state.value
            failure("Plate") { repo.execute(Action.SetLoadUnit(workout.id, first.id, LoadUnit.PLATES)) }
            assertEquals(before, repo.state.value)
        }
    }

    @Test fun planAndCatalogMigrateTogetherWithoutDoubleRevisionOrChangingActivePullup() = runTest {
        val seed = legacyState()
        var old = seed.copy(planRevision = 0, phase = Phase.BASELINE, revision = 9,
            lastBackupAt = 500, lastBackupRevision = 9,
            templates = seed.templates.map { t -> if (t.id != "pull") t else t.copy(slots = t.slots.map {
                if (it.id != "pull-vertical") it else it.copy(exerciseId = "pullup", alternatives = listOf("pullup-assisted", "pulldown"))
            }) })
        old = StateChanges.apply(old, Action.StartWorkout("pull", 1000))
        val active = requireNotNull(old.activeWorkout)
        old = StateChanges.apply(old, Action.SaveSet(active.id, active.exercises.first().id, "bw", null, 4, null, false, 1100))
        val store = MemoryPersistence(legacyJson(old))
        val repo = repository(store, clock = { 5000 })
        assertEquals(10L, repo.state.value.revision)
        assertEquals(1, store.writes)
        assertEquals(1, repo.state.value.planRevision)
        assertEquals("pulldown", repo.state.value.templates.single { it.id == "pull" }.slots.first().exerciseId)
        assertEquals("pullup", repo.state.value.activeWorkout!!.exercises.first().exercise.id)
        assertEquals(old.activeWorkout!!.exercises.first().sets, repo.state.value.activeWorkout!!.exercises.first().sets)
        assertEquals(old.restTimer, repo.state.value.restTimer)
        val preview = repo.previewBackup(legacyJson(old))
        assertTrue(preview.futurePlanUpdated)
        assertTrue(preview.dataUpdated)
    }

    @Test fun failedMigrationNeverResetsOrPartiallyWritesAndRetrySucceeds() = runTest {
        val source = legacyJson(withHistoryAndActive())
        val store = MemoryPersistence(source).apply { failWrites = true }
        failure("not been reset") { repository(store) }
        assertEquals(source, store.snapshot)
        assertEquals(0, store.writes)
        store.failWrites = false
        val migrated = repository(store).state.value
        assertEquals(8L, migrated.revision)
        assertEquals(1, store.writes)
        assertEquals(migrated, repository(store).state.value)
        assertEquals(1, store.writes)
    }

    @Test fun revisionAndLibraryCapacityLimitsLeaveLegacyDiskUntouched() = runTest {
        val old = legacyState()
        val full = old.copy(exercises = old.exercises + List(1000 - old.exercises.size) {
            Exercise("custom-$it", "Custom $it", custom = true)
        })
        listOf(old.copy(revision = Long.MAX_VALUE, unbackedUpSince = 100), full).forEach { state ->
            val source = legacyJson(state)
            val store = MemoryPersistence(source)
            failure { repository(store) }
            assertEquals(source, store.snapshot)
            assertEquals(0, store.writes)
        }
    }

    @Test fun combinedReminderAndCatalogMigrationUsesOneCommitAndKeepsEarliestDirtyTime() = runTest {
        val old = legacyState().copy(revision = 8, lastBackupRevision = 6, lastBackupAt = 1000,
            phase = Phase.BASELINE,
            overrides = listOf(PlanOverride(Phase.BASELINE, "pull-vertical", "row-cable", 2, 120)))
        val store = MemoryPersistence(legacyJson(old))
        val state = repository(store, clock = { 9000 }).state.value
        assertEquals(9L, state.revision)
        assertEquals(1000L, state.unbackedUpSince)
        assertEquals(old.overrides, state.overrides)
        assertEquals(1, store.writes)
        assertEquals(state, repository(store).state.value)
        assertEquals(1, store.writes)
    }

    @Test fun futureCatalogAndMalformedLegacySourceNeverNormalizeAwayInvalidData() = runTest {
        val source = legacyJson()
        val invalidSources = listOf(
            source.replace("\"loading\":\"EXTERNAL\"", "\"loading\":\"WEIGHTED\""),
            source.replace("\"version\":1", "\"version\":1,\"version\":1"),
            source.replace("\"revision\":0", "\"revision\":0,\"\\u0072evision\":0"),
            source.replace("\"exerciseId\":\"pulldown\"", "\"exerciseId\":\"absent\""),
            source.replace("\"schemaVersion\":1", "\"schemaVersion\":1,\"unknown\":true"),
            legacyJson(legacyState().copy(exercises = legacyState().exercises + legacyState().exercises.first())),
            SnapshotCodec.encode(Seed.initialState()).replace("\"catalogRevision\":${ExerciseCatalog.REVISION}",
                "\"catalogRevision\":${ExerciseCatalog.REVISION + 1}")
        )
        invalidSources.forEach { json ->
            val store = MemoryPersistence(json)
            failure("not been reset") { repository(store) }
            assertEquals(json, store.snapshot)
            assertEquals(0, store.writes)
        }
    }

    @Test fun migrationAtPenultimateRevisionSucceedsButNeverWrapsAndRestoreUsesLocalRevision() = runTest {
        val old = legacyState().copy(revision = Long.MAX_VALUE - 1, unbackedUpSince = 100)
        val store = MemoryPersistence(legacyJson(old))
        val migrated = repository(store).state.value
        assertEquals(Long.MAX_VALUE, migrated.revision)
        assertEquals(1, store.writes)
        assertEquals(migrated, repository(store).state.value)
        assertEquals(1, store.writes)
        val local = repository(clock = { 5000 })
        val preview = local.previewBackup(legacyJson(old.copy(revision = Long.MAX_VALUE)))
        val revision = local.state.value.revision
        local.execute(Action.MarkBackup(revision, 4000))
        local.restoreBackup(preview, revision)
        assertEquals(revision + 1, local.state.value.revision)
    }

    @Test fun completedCatalogRevisionPreventsResurrectionAndPreservesLaterMetadataEdits() = runTest {
        val seed = Seed.initialState()
        val edited = seed.exercises.single { it.id == "pulldown" }.copy(equipment = Equipment.UNKNOWN,
            primaryMuscle = MuscleGroup.UNKNOWN, aliases = emptyList())
        val state = seed.copy(exercises = seed.exercises.filterNot { it.id == "chinup" }.map {
            if (it.id == edited.id) edited else it
        })
        val store = MemoryPersistence(SnapshotCodec.encode(state))
        assertEquals(state, repository(store).state.value)
        assertEquals(0, store.writes)
        assertEquals(state, repository(store).previewBackup(requireNotNull(store.snapshot)).state)
    }

    @Test fun oldBackupPreviewIsReadOnlyRestoreIsAtomicAndNewExportDoesNotRemigrate() = runTest {
        val old = withHistoryAndActive()
        val store = MemoryPersistence()
        val repo = repository(store, clock = { 9000 })
        val original = repo.state.value
        val writes = store.writes
        val preview = repo.previewBackup(legacyJson(old))
        assertTrue(preview.dataUpdated)
        assertTrue(preview.contentUpdated)
        assertFalse(preview.futurePlanUpdated)
        assertEquals(original, repo.state.value)
        assertEquals(writes, store.writes)
        val safety = repo.exportBackup()
        repo.execute(Action.MarkBackup(safety.revision, 8000))
        val before = repo.state.value
        val disk = store.snapshot
        store.failWrites = true
        failure("unchanged") { repo.restoreBackup(preview, safety.revision) }
        assertEquals(before, repo.state.value)
        assertEquals(disk, store.snapshot)
        store.failWrites = false
        repo.restoreBackup(preview.copy(state = original), safety.revision)
        val restored = repo.state.value
        assertEquals(preview.state.copy(revision = safety.revision + 1, restTimer = null,
            lastBackupAt = 8000, lastBackupRevision = safety.revision, unbackedUpSince = 9000), restored)
        val exported = repo.exportBackup()
        assertTrue(exported.json.contains("\"version\":${SnapshotCodec.SCHEMA_VERSION}"))
        assertTrue(exported.json.contains("\"schemaVersion\":${SnapshotCodec.SCHEMA_VERSION}"))
        val nextPreview = repo.previewBackup(exported.json)
        assertEquals(restored, nextPreview.state)
        assertFalse(nextPreview.dataUpdated)
        assertFalse(nextPreview.contentUpdated)
        assertFalse(nextPreview.futurePlanUpdated)
        val savedWrites = store.writes
        assertEquals(restored, repository(store).state.value)
        assertEquals(savedWrites, store.writes)
    }
}
