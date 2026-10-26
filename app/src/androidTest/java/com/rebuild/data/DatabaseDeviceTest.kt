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
class DatabaseDeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "rebuild-device-test.db"
    private var store: SqliteSnapshotPersistence? = null

    @Before fun setup() { context.deleteDatabase(name) }
    @After fun cleanup() {
        store?.close()
        context.deleteDatabase(name)
    }

    @Test fun activeWorkoutAndLoggedSetSurviveSqliteReopen() = runBlocking {
        val first = SqliteSnapshotPersistence(context, name).also { store = it }
        val repo = RebuildRepository.fromPersistence(first)
        repo.execute(Action.SelectPhase(Phase.BASELINE))
        repo.execute(Action.StartWorkout("push", 1000))
        val active = repo.state.value.activeWorkout!!
        repo.execute(Action.SaveSet(active.id, active.exercises.first().id, "stable-set", 20.0, 10, 2.0, false, 2000))
        val saved = repo.state.value
        first.close()
        val second = SqliteSnapshotPersistence(context, name).also { store = it }
        val reopened = RebuildRepository.fromPersistence(second)
        assertEquals(saved, reopened.state.value)
        assertNotNull(reopened.state.value.restTimer)
    }

    @Test fun safetyRestoreIsAtomicAndCancelsRestTimer() = runBlocking {
        val helper = SqliteSnapshotPersistence(context, name).also { store = it }
        val repo = RebuildRepository.fromPersistence(helper)
        val empty = repo.previewBackup(repo.exportBackup().json)
        repo.execute(Action.SelectPhase(Phase.BASELINE))
        repo.execute(Action.StartWorkout("push", 1000))
        val workout = repo.state.value.activeWorkout!!
        repo.execute(Action.SaveSet(workout.id, workout.exercises.first().id, "set", 20.0, 10, null, false, 2000))
        val timer = repo.state.value.restTimer!!
        val safety = repo.exportBackup()
        repo.execute(Action.MarkBackup(safety.revision, 3000))
        repo.restoreBackup(empty, safety.revision)
        assertTrue(repo.state.value.workouts.isEmpty())
        assertNull(repo.state.value.restTimer)
        assertFalse(repo.consumeRestTimer(timer.id, timer.deadline))
        assertEquals(repo.state.value, SnapshotCodec.decode(helper.read()))
    }

    @Test fun activitiesAndReceiversGetSameRepositoryInstance() = runBlocking {
        val applicationRepo = RebuildRepository.open(context.applicationContext)
        val componentRepo = RebuildRepository.open(context)
        assertSame(applicationRepo, componentRepo)
    }

    @Test fun damagedDatabaseFileIsNeverDeletedOrReinitialized() = runBlocking {
        val first = SqliteSnapshotPersistence(context, name).also { store = it }
        first.read()
        first.close()
        val file = context.getDatabasePath(name)
        val damaged = file.readBytes().apply {
            "not a database!!".toByteArray().copyInto(this, endIndex = 16)
        }
        file.writeBytes(damaged)
        val second = SqliteSnapshotPersistence(context, name).also { store = it }
        try {
            RebuildRepository.fromPersistence(second)
            fail("A damaged database must not open as a fresh account")
        } catch (error: RepositoryException) {
            assertTrue(error.message!!.contains("not been reset"))
        }
        assertTrue(file.exists())
        assertArrayEquals(damaged, file.readBytes())
    }
}
