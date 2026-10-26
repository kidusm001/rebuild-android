package com.rebuild.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rebuild.domain.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlanMigrationDeviceTest {
    @Test fun sqliteUpgradeAndOldRestoreChangeOnlyFuturePlanWithoutConvertingSets() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "plan-migration-test.db"
        context.deleteDatabase(name)
        val store = SqliteSnapshotPersistence(context, name)
        try {
            val seed = Seed.initialState()
            var old = seed.copy(planRevision = 0, phase = Phase.BASELINE, templates = seed.templates.map { t ->
                if (t.id != "pull") t else t.copy(slots = t.slots.map { s ->
                    if (s.id != "pull-vertical") s else s.copy(exerciseId = "pullup",
                        alternatives = listOf("pullup-assisted", "pulldown"))
                })
            })
            old = StateChanges.apply(old, Action.StartWorkout("pull", 1000))
            val workout = requireNotNull(old.activeWorkout)
            old = StateChanges.apply(old, Action.SaveSet(workout.id, workout.exercises.first().id,
                "old-bodyweight-set", null, 3, 2.0, false, 1100))
            old = StateChanges.apply(old, Action.SaveSet(workout.id, workout.exercises.first().id,
                "old-warmup", null, 2, null, true, 1200))
            val json = SnapshotCodec.encode(old).replace("\"planRevision\":0,", "")
            store.write(json)
            val repo = RebuildRepository.fromPersistence(store, clock = { 2000 })
            val migrated = repo.state.value
            assertEquals(old.workouts, migrated.workouts)
            assertEquals(old.restTimer, migrated.restTimer)
            assertEquals("pulldown", migrated.templates.single { it.id == "pull" }.slots.first().exerciseId)
            assertEquals(1L, migrated.revision)
            assertEquals(migrated, RebuildRepository.fromPersistence(store).state.value)
            val safety = repo.exportBackup()
            repo.execute(Action.MarkBackup(safety.revision, 3000))
            val preview = repo.previewBackup(json)
            assertTrue(preview.futurePlanUpdated)
            repo.restoreBackup(preview, safety.revision)
            assertEquals(old.workouts, repo.state.value.workouts)
            assertNull(repo.state.value.restTimer)
            assertEquals(repo.state.value, SnapshotCodec.decode(store.read()))
            assertEquals(repo.state.value, RebuildRepository.fromPersistence(store).state.value)
            repo.execute(Action.FinishWorkout(4000))
            repo.execute(Action.StartWorkout("pull", 5000))
            val next = requireNotNull(repo.state.value.activeWorkout).exercises.first()
            assertEquals(LoadingType.EXTERNAL, next.exercise.loading)
            assertTrue(next.sets.isEmpty())
            assertNull(repo.state.value.previousComparable(next.exercise, requireNotNull(repo.state.value.activeWorkoutId)))
        } finally {
            store.close()
            context.deleteDatabase(name)
        }
    }
}
