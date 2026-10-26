package com.rebuild.data

import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rebuild.domain.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UnicodeSnapshotDeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val databaseName = "unicode-snapshot-regression-test.db"
    private var store: SqliteSnapshotPersistence? = null

    @Before fun setup() {
        context.deleteDatabase(databaseName)
    }

    @After fun cleanup() {
        store?.close()
        context.deleteDatabase(databaseName)
    }

    private fun openStore() = SqliteSnapshotPersistence(context, databaseName).also { store = it }

    private suspend fun startWorkout(repo: RebuildRepository): Workout {
        repo.execute(Action.SelectPhase(Phase.BASELINE))
        repo.execute(Action.StartWorkout("push", 1000))
        return repo.state.value.activeWorkout!!
    }

    private fun escapedRaw(state: AppState): String = buildJsonObject {
        put("format", "rebuild-backup")
        put("version", state.schemaVersion)
        put("state", Json.encodeToJsonElement(state))
    }.toString().replace("\uD800", "\\uD800").replace("\uDC00", "\\uDC00")

    @Test fun splitEmojiNoteIsRejectedBeforeMutationOrSurvivesReopenExactly() = runBlocking {
        val first = openStore()
        val repo = RebuildRepository.fromPersistence(first)
        val workout = startWorkout(repo)
        repo.execute(Action.WorkoutNote(workout.id, "Existing saved note"))
        val before = repo.state.value
        val diskBefore = first.read()
        val split = ("a".repeat(4095) + "🙂").take(4096)
        assertEquals(4096, split.length)
        assertTrue(Character.isHighSurrogate(split.last()))

        var rejected = false
        try {
            repo.execute(Action.WorkoutNote(workout.id, split))
        } catch (error: RepositoryException) {
            rejected = true
            assertFalse(error.message.isNullOrBlank())
            assertEquals(before, repo.state.value)
            assertEquals(diskBefore, first.read())
        }
        first.close()

        val reopened = RebuildRepository.fromPersistence(openStore())
        if (rejected) {
            assertEquals(before, reopened.state.value)
        } else {
            assertEquals("Accepted text must survive SQLite unchanged", split, reopened.state.value.activeWorkout!!.notes)
        }
    }

    @Test fun malformedImportedSnapshotWriteIsRejectedWithoutChangingExistingData() = runBlocking {
        val first = openStore()
        val repo = RebuildRepository.fromPersistence(first)
        val workout = startWorkout(repo)
        repo.execute(Action.WorkoutNote(workout.id, "Keep this note"))
        val before = repo.state.value
        val diskBefore = first.read()

        listOf("\uD800", "\uDC00").forEach { surrogate ->
            val invalidStates = listOf(
                before.copy(workouts = listOf(before.activeWorkout!!.copy(notes = "Bad note $surrogate"))),
                before.copy(exercises = before.exercises.mapIndexed { index, exercise ->
                    if (index == 0) exercise.copy(name = "Bad name $surrogate") else exercise
                })
            )
            invalidStates.forEach { invalid ->
                try {
                    first.write(escapedRaw(invalid))
                    fail("Malformed UTF-16 must be rejected before the saved snapshot is replaced")
                } catch (error: RepositoryException) {
                    assertFalse(error.message.isNullOrBlank())
                }
                assertEquals(diskBefore, first.read())
                assertEquals(before, repo.state.value)
            }
        }
        first.close()
        assertEquals(before, RebuildRepository.fromPersistence(openStore()).state.value)
    }

    @Test fun completeEmojiAtTextBoundariesSurvivesPersistAndReopen() = runBlocking {
        val first = openStore()
        val repo = RebuildRepository.fromPersistence(first)
        val workout = startWorkout(repo)
        val note = "a".repeat(4094) + "🙂"
        val name = "n".repeat(118) + "🙂"
        repo.execute(Action.WorkoutNote(workout.id, note))
        repo.execute(Action.ExerciseNote(workout.id, workout.exercises.first().id, note))
        repo.execute(Action.AddExercise(Exercise("emoji-boundary-name", name, custom = true)))
        val expected = repo.state.value
        first.close()

        val reopened = RebuildRepository.fromPersistence(openStore())
        assertEquals(expected, reopened.state.value)
        assertEquals(note, reopened.state.value.activeWorkout!!.notes)
        assertEquals(name, reopened.state.value.exercises.single { it.id == "emoji-boundary-name" }.name)
    }

    @Test fun nearTenMiBWhitespacePaddedSnapshotReopensWithMeasuredTiming() = runBlocking {
        val expected = Seed.initialState()
        val base = SnapshotCodec.encode(expected)
        val fixtureBytes = SnapshotCodec.MAX_BYTES - 1024
        val source = base + " ".repeat(fixtureBytes - base.toByteArray(Charsets.UTF_8).size)
        assertEquals(fixtureBytes, source.toByteArray(Charsets.UTF_8).size)
        val first = openStore()
        first.write(source)
        first.close()

        val readStarted = SystemClock.elapsedRealtime()
        val loaded = openStore().read()
        val reopenReadMs = SystemClock.elapsedRealtime() - readStarted
        val decodeStarted = SystemClock.elapsedRealtime()
        val decoded = SnapshotCodec.decode(loaded)
        val decodeMs = SystemClock.elapsedRealtime() - decodeStarted
        val measurement = "fixture=valid-whitespace-padded-seed utf8Bytes=$fixtureBytes " +
            "reopenReadMs=$reopenReadMs decodeMs=$decodeMs"
        Log.i("RebuildDataTiming", measurement)
        println("RebuildDataTiming $measurement")

        assertEquals(source, loaded)
        assertEquals(expected, decoded)
    }
}
