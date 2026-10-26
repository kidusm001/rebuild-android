package com.rebuild.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rebuild.domain.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LargeSnapshotDeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val databaseName = "large-snapshot-regression-test.db"
    private var store: SqliteSnapshotPersistence? = null

    @Before fun setup() {
        context.deleteDatabase(databaseName)
    }

    @After fun cleanup() {
        store?.close()
        context.deleteDatabase(databaseName)
    }

    private fun openStore() = SqliteSnapshotPersistence(context, databaseName).also { store = it }

    @Test fun validSnapshotLargerThanCursorWindowSurvivesPersistCloseAndReopen() = runBlocking {
        val expected = largeState(unicode = false)
        val source = SnapshotCodec.encode(expected)
        assertLargeButSupported(source)
        val first = openStore()
        first.write(source)
        first.close()

        val reopened = RebuildRepository.fromPersistence(openStore())
        assertEquals(expected, reopened.state.value)
        assertEquals(source, reopened.exportBackup().json)
    }

    @Test fun largeUnicodeRestoreSurvivesCloseAndReopenWithoutLosingActiveSnapshot() = runBlocking {
        val imported = largeState(unicode = true)
        val source = SnapshotCodec.encode(imported)
        assertLargeButSupported(source)
        val first = openStore()
        val repo = RebuildRepository.fromPersistence(first, clock = { 900000 })
        val preview = repo.previewBackup(source)
        val safety = repo.exportBackup()
        repo.execute(Action.MarkBackup(safety.revision, 800000))
        repo.restoreBackup(preview, safety.revision)
        val restored = repo.state.value
        assertEquals(imported.workouts, restored.workouts)
        assertEquals(imported.activeWorkoutId, restored.activeWorkoutId)
        assertNull(restored.restTimer)
        first.close()

        val reopened = RebuildRepository.fromPersistence(openStore())
        assertEquals(restored, reopened.state.value)
        assertEquals("é🙂漢".repeat(800), reopened.state.value.activeWorkout!!.notes)
        assertEquals(restored, reopened.previewBackup(reopened.exportBackup().json).state)
    }

    private fun assertLargeButSupported(source: String) {
        val bytes = source.toByteArray(Charsets.UTF_8).size
        assertTrue("Fixture must exceed the Android CursorWindow limit: $bytes bytes", bytes > 2 * 1024 * 1024)
        assertTrue("Fixture must remain within the supported backup limit: $bytes bytes", bytes < SnapshotCodec.MAX_BYTES)
    }

    private fun largeState(unicode: Boolean): AppState {
        val seed = Seed.initialState()
        val template = seed.templates.single { it.id == "push" }
        val notes = if (unicode) "é🙂漢".repeat(800) else "a".repeat(4096)
        val workouts = List(120) { index ->
            val startedAt = 1000L + index * 1000L
            Workout(
                id = "large-workout-$index",
                templateId = template.id,
                name = template.name,
                phase = Phase.BASELINE,
                startedAt = startedAt,
                startDate = localDateOf(startedAt).toString(),
                finishedAt = if (index == 119) null else startedAt + 500,
                notes = notes,
                exercises = template.slots.map { slot ->
                    WorkoutExercise(
                        id = "large-exercise-$index-${slot.id}",
                        slotId = slot.id,
                        exercise = seed.exercises.single { it.id == slot.exerciseId },
                        targetSets = slot.baselineSets,
                        baselineSets = slot.baselineSets,
                        upperSets = slot.upperSets,
                        minReps = slot.minReps,
                        maxReps = slot.maxReps,
                        targetRir = Phase.BASELINE.targetRir,
                        restSeconds = slot.restSeconds,
                        perLeg = slot.perLeg,
                        notes = notes,
                        sets = listOf(LoggedSet("large-set-$index-${slot.id}", 20.0, 10, 2.0, false, startedAt + 100))
                    )
                }
            )
        }
        val active = workouts.last()
        return seed.copy(
            revision = 12,
            phase = Phase.BASELINE,
            workouts = workouts,
            activeWorkoutId = active.id,
            restTimer = RestTimer("large-timer", active.id, active.exercises.first().id, 500000),
            unbackedUpSince = 1000
        )
    }
}
