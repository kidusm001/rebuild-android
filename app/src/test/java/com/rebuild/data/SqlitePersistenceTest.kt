package com.rebuild.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.rebuild.domain.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SqlitePersistenceTest {
    private lateinit var context: Context
    private val name = "persistence-test.db"
    private var helper: SqliteSnapshotPersistence? = null

    @Before fun setup() {
        context = RuntimeEnvironment.getApplication()
        context.deleteDatabase(name)
    }

    @After fun cleanup() {
        helper?.close()
        context.deleteDatabase(name)
    }

    private fun store(): SqliteSnapshotPersistence =
        SqliteSnapshotPersistence(context, name).also { helper = it }

    @Test fun sqliteReopenPreservesAllUserEditsAndActiveTimer() = runBlocking {
        val firstStore = store()
        val repo = RebuildRepository.fromPersistence(firstStore)
        repo.execute(Action.AddExercise(Exercise("custom", "Custom exercise", custom = true)))
        repo.execute(Action.EditPlan(Phase.BASELINE, "push-incline", "custom", 3, 100))
        val workout = repo.start()
        repo.log()
        repo.execute(Action.WorkoutNote(workout.id, "Persistent"))
        val expected = repo.state.value
        firstStore.close()
        val reopened = RebuildRepository.fromPersistence(store())
        assertEquals(expected, reopened.state.value)
    }

    @Test fun invalidSnapshotIsNotWrittenAndDatabaseRemainsReadable() = runBlocking {
        val store = store()
        val repo = RebuildRepository.fromPersistence(store)
        repo.start()
        val disk = store.read()
        rejected { store.write("broken JSON") }
        assertEquals(disk, store.read())
        assertEquals(repo.state.value, SnapshotCodec.decode(store.read()))
    }

    @Test fun malformedStoredPayloadIsNotReseeded() = runBlocking {
        val store = store()
        store.writableDatabase.execSQL("UPDATE snapshot SET payload = 'damaged' WHERE id = 1")
        failure("has not been reset") { RebuildRepository.fromPersistence(store) }
        assertEquals("damaged", store.read())
    }

    @Test fun missingSnapshotIsNotSilentlyRecreated() = runBlocking {
        val store = store()
        store.writableDatabase.execSQL("DELETE FROM snapshot")
        failure("not been reset") { RebuildRepository.fromPersistence(store) }
        store.readableDatabase.rawQuery("SELECT COUNT(*) FROM snapshot", null).use {
            assertTrue(it.moveToFirst())
            assertEquals(0, it.getInt(0))
        }
    }

    @Test fun databaseConstraintRollsBackEntireReplacement() = runBlocking {
        val store = store()
        val repo = RebuildRepository.fromPersistence(store)
        repo.start()
        val before = repo.state.value
        val disk = store.read()
        store.writableDatabase.execSQL("CREATE TRIGGER fail_save BEFORE UPDATE ON snapshot BEGIN SELECT RAISE(ABORT, 'storage rejected'); END")
        failure("previous data is unchanged") { repo.log() }
        assertEquals(before, repo.state.value)
        assertEquals(disk, store.read())
        store.writableDatabase.execSQL("DROP TRIGGER fail_save")
        repo.log()
        assertNotEquals(before, repo.state.value)
    }

    @Test fun newerDatabaseVersionIsNotDestructivelyDowngraded() = runBlocking {
        val first = store()
        val contents = first.read()
        first.writableDatabase.version = 2
        first.close()
        failure("has not been reset") { RebuildRepository.fromPersistence(store()) }
        SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            assertEquals(2, db.version)
            db.rawQuery("SELECT payload FROM snapshot", null).use {
                assertTrue(it.moveToFirst())
                assertEquals(contents, it.getString(0))
            }
        }
    }

    @Test fun unicodeAcrossChunksPreservesUtf16SurrogatePairsAndSnapshotContent() = runBlocking {
        val first = store()
        val repo = RebuildRepository.fromPersistence(first)
        val note = "🙂漢é".repeat(1000)
        repeat(4) {
            val workout = repo.start()
            repo.execute(Action.WorkoutNote(workout.id, note))
            workout.exercises.forEach {
                repo.execute(Action.ExerciseNote(workout.id, it.id, note))
            }
            repo.execute(Action.FinishWorkout(workout.startedAt + 1000))
        }
        val expected = repo.state.value
        val encoded = requireNotNull(first.read())
        assertTrue(encoded.codePointCount(0, encoded.length) > 64 * 1024)
        first.close()
        val reopened = RebuildRepository.fromPersistence(store())
        assertEquals(expected, reopened.state.value)
        assertEquals(note, reopened.state.value.workouts.last().notes)
    }

    @Test fun payloadAtFullTenMiBLimitRemainsReadable() = runBlocking {
        val store = store()
        val base = SnapshotCodec.encode(Seed.initialState())
        val source = base + " ".repeat(SnapshotCodec.MAX_BYTES - base.toByteArray(Charsets.UTF_8).size)
        store.write(source)
        assertEquals(source, store.read())
    }

    @Test fun byteLimitRejectsMultibytePayloadEvenWhenCharacterCountIsWithinLimit() = runBlocking {
        val store = store()
        val oversized = "é".repeat(SnapshotCodec.MAX_BYTES / 2 + 1)
        store.writableDatabase.execSQL("UPDATE snapshot SET payload = ? WHERE id = 1", arrayOf(oversized))
        failure("10 MiB") { store.read() }
        store.readableDatabase.rawQuery("SELECT length(CAST(payload AS BLOB)) FROM snapshot WHERE id = 1", null).use {
            assertTrue(it.moveToFirst())
            assertEquals(SnapshotCodec.MAX_BYTES + 2, it.getInt(0))
        }
    }

    @Test fun embeddedNulCannotSilentlyTruncateStoredTextToValidJson() = runBlocking {
        val store = store()
        val damaged = SnapshotCodec.encode(Seed.initialState()) + "\u0000trailing corruption"
        store.writableDatabase.execSQL("UPDATE snapshot SET payload = ? WHERE id = 1", arrayOf(damaged))
        failure("invalid text") { store.read() }
        store.readableDatabase.rawQuery("SELECT instr(payload, char(0)) FROM snapshot WHERE id = 1", null).use {
            assertTrue(it.moveToFirst())
            assertTrue(it.getLong(0) > 0)
        }
    }
}
