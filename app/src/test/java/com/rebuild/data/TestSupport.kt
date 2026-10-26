package com.rebuild.data

import com.rebuild.domain.*
import kotlinx.serialization.json.*
import org.junit.Assert.*

internal class MemoryPersistence(var snapshot: String? = null) : SnapshotPersistence {
    var failWrites = false
    var writes = 0
    var beforeWrite: (() -> Unit)? = null
    override fun read() = snapshot
    override fun write(snapshot: String) {
        beforeWrite?.invoke()
        if (failWrites) throw java.io.IOException("Storage full")
        this.snapshot = snapshot
        writes++
    }
}

internal suspend fun repository(
    store: MemoryPersistence = MemoryPersistence(),
    clock: () -> Long = System::currentTimeMillis
): RebuildRepository = RebuildRepository.fromPersistence(store, clock)

internal suspend fun RebuildRepository.start(phase: Phase = Phase.BASELINE, template: String = "push"): Workout {
    execute(Action.SelectPhase(phase))
    execute(Action.StartWorkout(template, 1000))
    return state.value.activeWorkout!!
}

internal suspend fun RebuildRepository.log(
    workout: Workout = state.value.activeWorkout!!,
    exercise: WorkoutExercise = workout.exercises.first(),
    id: String = "set-1",
    kg: Double? = if (exercise.exercise.loading == LoadingType.BODYWEIGHT) null else 20.0,
    reps: Int = 10,
    rir: Double? = 2.0,
    warmup: Boolean = false,
    now: Long = 2000,
    loadUnit: LoadUnit = LoadUnit.KG,
    plates: Int? = null
) = execute(Action.SaveSet(workout.id, exercise.id, id, kg, reps, rir, warmup, now, loadUnit, plates))

internal fun legacyState(): AppState = Seed.initialState().copy(
    schemaVersion = 1, catalogRevision = 0,
    exercises = ExerciseCatalog.legacyExercises.map {
        Exercise(it.id, it.name, it.loading, it.perDumbbell, it.custom)
    }
)

internal val versionTwoKeys = setOf("catalogRevision", "equipment", "primaryMuscle", "aliases",
    "preferredLoadUnit", "entryLoadUnit", "loadUnit", "plates")
internal val versionThreeKeys = setOf("groupId", "setGroups", "startDate", "countedForWeek", "shortPause")
internal val versionFourKeys = setOf("planAdditions", "planRemovals", "optional", "skipped")
internal val versionFiveKeys = setOf("routines", "placements", "scheduleRemovals")
internal val versionSixKeys = setOf("measure", "efforts", "durationSeconds", "distance", "thousandths", "unit",
    "effortSeconds", "effortDistance", "totalSeconds", "totalDistance", "stopwatch", "elapsedMs", "runningSince",
    "boot", "session", "stopped", "needsConfirmation", "step")
internal val versionSevenKeys = setOf("lb", "bodyweights", "measurements", "measurementSites", "liftingUnit")

/**
 * [this] as Rebuild 0.1.5 to 0.1.9 could have stored it: exercise catalog revision one at most, so none of the timed,
 * distance and cardio exercises revision two adds. Older documents are always written from this.
 */
internal fun AppState.beforeCatalogTwo(): AppState {
    val added = ExerciseCatalog.revisionTwo.map { it.id }.toSet()
    return copy(catalogRevision = minOf(catalogRevision, 1), exercises = exercises.filterNot { it.id in added })
}

private fun strip(value: JsonElement, keys: Set<String>): JsonElement = when (value) {
    is JsonObject -> JsonObject(value.filterKeys { it !in keys }.mapValues { strip(it.value, keys) })
    is JsonArray -> JsonArray(value.map { strip(it, keys) })
    else -> value
}

/** A real v1 wire document: no newly introduced fields, even when they have default values. */
internal fun legacyJson(state: AppState = legacyState()): String {
    val json = Json { encodeDefaults = true }
    return buildJsonObject {
        put("format", "rebuild-backup")
        put("version", 1)
        put("state", strip(json.encodeToJsonElement(state.beforeCatalogTwo().copy(schemaVersion = 1, catalogRevision = 0)),
            versionTwoKeys + versionThreeKeys + versionFourKeys + versionFiveKeys + versionSixKeys + versionSevenKeys))
    }.toString()
}

/** A real v2 (0.1.5/0.1.6) wire document: no version-three or later fields, even when they have default values. */
internal fun v2Json(state: AppState): String {
    val json = Json { encodeDefaults = true }
    return buildJsonObject {
        put("format", "rebuild-backup")
        put("version", 2)
        put("state", strip(json.encodeToJsonElement(state.beforeCatalogTwo().copy(schemaVersion = 2)),
            versionThreeKeys + versionFourKeys + versionFiveKeys + versionSixKeys + versionSevenKeys))
    }.toString()
}

/** A real v3 (0.1.7) wire document: no version-four fields, even when they have default values. */
internal fun v3Json(state: AppState): String {
    val json = Json { encodeDefaults = true }
    return buildJsonObject {
        put("format", "rebuild-backup")
        put("version", 3)
        put("state", strip(json.encodeToJsonElement(state.beforeCatalogTwo().copy(schemaVersion = 3)),
            versionFourKeys + versionFiveKeys + versionSixKeys + versionSevenKeys))
    }.toString()
}

/** A real v4 (0.1.8) wire document: no version-five fields, even when they have default values. */
internal fun v4Json(state: AppState): String {
    val json = Json { encodeDefaults = true }
    return buildJsonObject {
        put("format", "rebuild-backup")
        put("version", 4)
        put("state", strip(json.encodeToJsonElement(state.beforeCatalogTwo().copy(schemaVersion = 4)),
            versionFiveKeys + versionSixKeys + versionSevenKeys))
    }.toString()
}

/** A real v5 (0.1.9) wire document: no version-six fields, even when they have default values. */
internal fun v5Json(state: AppState): String {
    val json = Json { encodeDefaults = true }
    return buildJsonObject {
        put("format", "rebuild-backup")
        put("version", 5)
        put("state", strip(json.encodeToJsonElement(state.beforeCatalogTwo().copy(schemaVersion = 5)), versionSixKeys + versionSevenKeys))
    }.toString()
}

/** A real v6 (0.1.10/0.1.11) wire document: no pounds, weigh-ins or measurements, even with default values. */
internal fun v6Json(state: AppState): String {
    val json = Json { encodeDefaults = true }
    return buildJsonObject {
        put("format", "rebuild-backup")
        put("version", 6)
        put("state", strip(json.encodeToJsonElement(state.copy(schemaVersion = 6)), versionSevenKeys))
    }.toString()
}

/** [source] relabelled from version [from] to [to] without removing anything, as a downgrade attempt would. */
internal fun relabelled(source: String, from: Int, to: Int): String {
    val labelled = source.replace("\"version\":$from", "\"version\":$to")
        .replace("\"schemaVersion\":$from", "\"schemaVersion\":$to")
    assertNotEquals("Nothing was relabelled", source, labelled)
    return labelled
}

internal suspend fun failure(messagePart: String = "", block: suspend () -> Unit) {
    try {
        block()
        fail("Expected RepositoryException")
    } catch (error: RepositoryException) {
        assertTrue("Expected '$messagePart' in '${error.message}'", error.message!!.contains(messagePart, ignoreCase = true))
    }
}

internal fun rejected(block: () -> Unit) {
    try {
        block()
        fail("Expected invalid snapshot to be rejected")
    } catch (error: RepositoryException) {
        assertFalse(error.message.isNullOrBlank())
    }
}
