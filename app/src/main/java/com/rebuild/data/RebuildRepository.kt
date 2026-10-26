package com.rebuild.data

import android.content.Context
import com.rebuild.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** A write must either durably replace the entire snapshot or leave the previous one unchanged. */
interface SnapshotPersistence {
    fun read(): String?
    fun write(snapshot: String)
}

class RebuildRepository private constructor(
    private val persistence: SnapshotPersistence,
    initial: AppState,
    private val clock: () -> Long
) : AppRepository {
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(initial)
    override val state: StateFlow<AppState> = mutableState.asStateFlow()

    override suspend fun execute(action: Action) = mutex.withLock {
        val previous = mutableState.value
        var updated = StateChanges.apply(previous, action)
        if (action is Action.MarkBackup) {
            updated = updated.copy(unbackedUpSince = if (action.revision == previous.revision) null
                else previous.unbackedUpSince ?: clock())
        }
        if (updated != previous) {
            commit(if (action is Action.MarkBackup) updated else markDirty(updated, previous))
        }
    }

    override suspend fun exportBackup(): BackupDocument = mutex.withLock {
        val captured = mutableState.value
        withContext(Dispatchers.IO) { BackupDocument(captured.revision, SnapshotCodec.encode(captured)) }
    }

    override fun previewBackup(json: String): BackupPreview {
        val source = SnapshotCodec.decode(json)
        val updated = imported(StateMigrations.apply(source))
        val contentUpdated = source.catalogRevision != updated.catalogRevision ||
            source.exercises != updated.exercises || source.workouts != updated.workouts
        return BackupPreview(updated, json,
            source.templates != updated.templates || source.overrides != updated.overrides,
            source.schemaVersion != updated.schemaVersion || contentUpdated,
            contentUpdated,
            source.workouts.any { it.startDate == null })
    }

    override suspend fun restoreBackup(preview: BackupPreview, expectedRevision: Long) = mutex.withLock {
        val previous = mutableState.value
        SnapshotCodec.ensure(previous.revision == expectedRevision,
            "Your data changed after the restore preview. Create a fresh safety backup and preview the restore again.")
        SnapshotCodec.ensure(previous.lastBackupRevision == previous.revision,
            "Save a safety backup of your current data before replacing it.")
        // Never trust a caller-created or modified preview.state, only the validated source.
        val restored = withContext(Dispatchers.IO) { imported(StateMigrations.apply(SnapshotCodec.decode(preview.source))) }
        commit(restored.copy(
            revision = nextRevision(previous),
            restTimer = null,
            lastBackupAt = previous.lastBackupAt,
            lastBackupRevision = previous.lastBackupRevision,
            unbackedUpSince = clock()
        ))
    }

    override suspend fun consumeRestTimer(timerId: String, now: Long): Boolean = mutex.withLock {
        SnapshotCodec.timestamp(now)
        val previous = mutableState.value
        val timer = previous.restTimer ?: return@withLock false
        if (timer.id != timerId || now < timer.deadline) return@withLock false
        val notify = now - timer.deadline <= 5 * 60 * 1000L &&
            previous.activeWorkoutId == timer.workoutId && previous.activeWorkout?.finishedAt == null
        commit(markDirty(previous.copy(restTimer = null), previous))
        notify
    }

    private suspend fun commit(updated: AppState) {
        // Cancellation must not leave disk committed but observers holding an older snapshot.
        withContext(Dispatchers.IO + NonCancellable) {
            val serialized = SnapshotCodec.encode(updated)
            try {
                persistence.write(serialized)
            } catch (error: Exception) {
                throw RepositoryException("Could not save your changes. Your previous data is unchanged. Check available storage and try again.", error)
            }
            mutableState.value = updated
        }
    }

    /**
     * A backup's running stopwatch was timed on another clock, even when it comes from this phone, so its running time
     * is never trusted: it comes back stopped, keeping only its recorded time, for the user to confirm the duration.
     */
    private fun imported(state: AppState): AppState {
        val watch = state.stopwatch?.takeIf { it.running } ?: return state
        return state.copy(stopwatch = watch.copy(runningSince = null, boot = null, session = null, stopped = true,
            needsConfirmation = true, step = watch.nextStep()))
    }

    private fun nextRevision(state: AppState): Long {
        SnapshotCodec.ensure(state.revision < Long.MAX_VALUE,
            "The data revision limit has been reached. Export a backup before making more changes.")
        return state.revision + 1
    }

    private fun markDirty(updated: AppState, previous: AppState): AppState = updated.copy(
        revision = nextRevision(previous),
        unbackedUpSince = previous.unbackedUpSince ?: clock()
    )

    companion object {
        private val openMutex = Mutex()
        @Volatile private var instance: RebuildRepository? = null

        /** Activities and alarm receivers share this application-scoped instance and its write lock. */
        suspend fun open(context: Context): RebuildRepository = openMutex.withLock {
            instance ?: withContext(Dispatchers.IO) {
                val store = SqliteSnapshotPersistence(context.applicationContext)
                try {
                    fromPersistence(store)
                } catch (error: Exception) {
                    store.close()
                    throw error
                }
            }.also { instance = it }
        }

        /** A narrow persistence seam for tests; production always uses SQLite, never an in-memory fallback. */
        suspend fun fromPersistence(
            persistence: SnapshotPersistence,
            clock: () -> Long = System::currentTimeMillis
        ): RebuildRepository = withContext(Dispatchers.IO) {
            val saved = try {
                persistence.read()
            } catch (error: Exception) {
                throw RepositoryException("Rebuild could not open your saved data. It has not been reset. Check storage or restore a verified backup.", error)
            }
            val state = if (saved != null) {
                try {
                    SnapshotCodec.decode(saved)
                } catch (error: RepositoryException) {
                    throw RepositoryException("Saved data is damaged or unsupported and has not been reset. ${error.message}", error)
                }
            } else {
                val initial = Seed.initialState()
                try {
                    persistence.write(SnapshotCodec.encode(initial))
                } catch (error: Exception) {
                    throw RepositoryException("Rebuild could not create its database. Check available storage and try again.", error)
                }
                initial
            }
            val repository = RebuildRepository(persistence, state, clock)
            var loaded = state
            if (loaded.revision > 0 && loaded.lastBackupRevision != loaded.revision &&
                loaded.unbackedUpSince == null) {
                // Older version-one snapshots did not record when plan-only changes became dirty.
                loaded = loaded.copy(unbackedUpSince = loaded.lastBackupAt
                    ?: loaded.workouts.minOfOrNull { it.startedAt } ?: clock())
            }
            val normalized = StateMigrations.apply(loaded)
            // Recording a newer format alone changes nothing the user did, so it doesn't ask for a new backup.
            if (normalized != loaded) loaded = if (normalized == loaded.copy(schemaVersion = normalized.schemaVersion))
                normalized else repository.markDirty(normalized, loaded)
            if (loaded != state) {
                try {
                    repository.commit(loaded)
                } catch (error: RepositoryException) {
                    throw RepositoryException("Could not update your exercise library, future plan, or backup reminder metadata. " +
                        "Your saved data has not been reset. Check available storage and try again.", error)
                }
            }
            repository
        }
    }
}
