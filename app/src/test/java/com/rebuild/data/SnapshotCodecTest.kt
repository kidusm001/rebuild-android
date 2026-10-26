package com.rebuild.data

import com.rebuild.domain.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class SnapshotCodecTest {
    private val seed = Seed.initialState()
    private fun raw(state: AppState): String = buildJsonObject {
        put("format", "rebuild-backup")
        put("version", SnapshotCodec.SCHEMA_VERSION)
        put("state", Json { encodeDefaults = true }.encodeToJsonElement(state))
    }.toString()

    private fun escapedRaw(state: AppState): String =
        raw(state).replace("\uD800", "\\uD800").replace("\uDC00", "\\uDC00")

    private suspend fun rejectImportedNotes(surrogate: String) {
        val repo = repository()
        val workout = repo.start()
        val state = repo.state.value
        val invalidWorkouts = listOf(
            workout.copy(notes = "Imported note $surrogate"),
            workout.copy(exercises = workout.exercises.mapIndexed { index, exercise ->
                if (index == 0) exercise.copy(notes = "Imported exercise note $surrogate") else exercise
            })
        )
        invalidWorkouts.forEach {
            rejected { SnapshotCodec.decode(escapedRaw(state.copy(workouts = listOf(it)))) }
        }
    }

    private suspend fun rejectImportedNames(surrogate: String) {
        val repo = repository()
        val workout = repo.start()
        val state = repo.state.value
        val invalidStates = listOf(
            state.copy(exercises = state.exercises.mapIndexed { index, exercise ->
                if (index == 0) exercise.copy(name = "Library name $surrogate") else exercise
            }),
            state.copy(templates = state.templates.mapIndexed { index, template ->
                if (index == 0) template.copy(name = "Template name $surrogate") else template
            }),
            state.copy(workouts = listOf(workout.copy(name = "Workout name $surrogate"))),
            state.copy(workouts = listOf(workout.copy(exercises = workout.exercises.mapIndexed { index, exercise ->
                if (index == 0) exercise.copy(exercise = exercise.exercise.copy(name = "Snapshot name $surrogate")) else exercise
            })))
        )
        invalidStates.forEach { rejected { SnapshotCodec.decode(escapedRaw(it)) } }
    }

    @Test fun importsRejectLoneHighSurrogateInNotes() = runTest {
        rejectImportedNotes("\uD800")
    }

    @Test fun importsRejectLoneLowSurrogateInNotes() = runTest {
        rejectImportedNotes("\uDC00")
    }

    @Test fun importsRejectLoneHighSurrogateInNames() = runTest {
        rejectImportedNames("\uD800")
    }

    @Test fun importsRejectLoneLowSurrogateInNames() = runTest {
        rejectImportedNames("\uDC00")
    }

    @Test fun identifiersAndMuscleLabelsRejectMalformedUtf16() {
        listOf("\uD800", "\uDC00").forEach { surrogate ->
            rejected { SnapshotCodec.id("id-$surrogate") }
            val first = seed.templates.first()
            val invalid = seed.copy(templates = listOf(first.copy(slots = first.slots.mapIndexed { index, slot ->
                if (index == 0) slot.copy(muscle = "Muscle $surrogate") else slot
            })) + seed.templates.drop(1))
            rejected { SnapshotCodec.decode(escapedRaw(invalid)) }
        }
    }

    @Test fun completeEmojiAtNoteAndNameBoundariesRoundTrips() = runTest {
        val repo = repository()
        val workout = repo.start()
        val note = "a".repeat(4094) + "🙂"
        val name = "n".repeat(118) + "🙂"
        assertEquals(4096, note.length)
        assertEquals(120, name.length)
        repo.execute(Action.WorkoutNote(workout.id, note))
        repo.execute(Action.ExerciseNote(workout.id, workout.exercises.first().id, note))
        repo.execute(Action.AddExercise(Exercise("emoji-name", name, custom = true)))
        assertEquals(repo.state.value, SnapshotCodec.decode(SnapshotCodec.encode(repo.state.value)))
    }

    @Test fun versionedDocumentRoundTripsWithoutLoss() {
        val json = SnapshotCodec.encode(seed)
        assertEquals(seed, SnapshotCodec.decode(json))
        assertEquals(json, SnapshotCodec.encode(SnapshotCodec.decode(json)))
    }

    @Test fun malformedEmptyUnsupportedAndAmbiguousDocumentsAreRejected() {
        val good = SnapshotCodec.encode(seed)
        listOf(
            "", "not json", "{}", "[]", good.dropLast(5),
            good.replace("\"version\":7", "\"version\":8"),
            good.replace("\"schemaVersion\":7", "\"schemaVersion\":99"),
            good.replace("\"format\":\"rebuild-backup\"", "\"format\":\"another-app\""),
            good.replace("\"version\":7,", ""),
            good.replace("\"format\":\"rebuild-backup\",", ""),
            good.replace("\"version\":7", "\"version\":7,\"version\":7"),
            good.replace("\"version\":7", "\"version\":7,\"\\u0076ersion\":7"),
            good.replace("\"state\":", "\"unknown\":true,\"state\":")
        ).forEach { invalid -> rejected { SnapshotCodec.decode(invalid) } }
    }

    @Test fun nestedAndOversizePayloadsAreRejectedBeforeParsing() {
        rejected { SnapshotCodec.decode("[".repeat(49) + "]".repeat(49)) }
        rejected { SnapshotCodec.decode("x".repeat(SnapshotCodec.MAX_BYTES + 1)) }
        rejected { SnapshotCodec.decode("é".repeat(SnapshotCodec.MAX_BYTES / 2 + 1)) }
    }

    @Test fun duplicateEntityIdsAndOverrideKeysAreRejected() = runTest {
        val repo = repository()
        val workout = repo.start()
        repo.log()
        val state = repo.state.value
        val exercise = state.activeWorkout!!.exercises.first()
        val badStates = listOf(
            seed.copy(exercises = seed.exercises + seed.exercises.first()),
            seed.copy(templates = seed.templates + seed.templates.first()),
            seed.copy(templates = seed.templates.mapIndexed { index, t ->
                if (index == 0) t.copy(slots = t.slots + t.slots.first()) else t
            }),
            state.copy(workouts = state.workouts + state.workouts.first()),
            state.copy(workouts = listOf(workout.copy(exercises = listOf(exercise, exercise)))),
            state.copy(workouts = listOf(workout.copy(exercises = listOf(exercise.copy(sets = exercise.sets + exercise.sets))))),
            state.copy(overrides = List(2) { PlanOverride(Phase.BASELINE, "push-incline", "incline-db", 2, 180) })
        )
        badStates.forEach { rejected { SnapshotCodec.decode(raw(it)) } }
    }

    @Test fun brokenReferencesAndActiveConstraintsAreRejected() = runTest {
        val repo = repository()
        val workout = repo.start()
        repo.log()
        val state = repo.state.value
        val exercise = state.activeWorkout!!.exercises.first()
        val missingDefinition = exercise.copy(exercise = exercise.exercise.copy(id = "missing"))
        val wrongSlot = exercise.copy(slotId = "pull-row")
        val invalid = listOf(
            state.copy(exercises = state.exercises.filterNot { it.id == "incline-db" }),
            state.copy(activeWorkoutId = "missing"),
            state.copy(activeWorkoutId = null),
            state.copy(phase = Phase.INTRO_ONE),
            state.copy(phase = null),
            state.copy(workouts = listOf(workout.copy(templateId = "missing"))),
            state.copy(workouts = listOf(workout.copy(exercises = listOf(wrongSlot)))),
            state.copy(workouts = listOf(workout.copy(exercises = listOf(missingDefinition)))),
            state.copy(restTimer = state.restTimer!!.copy(workoutId = "missing")),
            state.copy(restTimer = state.restTimer!!.copy(exerciseId = "missing")),
            state.copy(overrides = listOf(PlanOverride(Phase.BASELINE, "missing", "incline-db", 2, 180))),
            state.copy(overrides = listOf(PlanOverride(Phase.BASELINE, "push-incline", "missing", 2, 180))),
            state.copy(overrides = listOf(PlanOverride(Phase.INTRO_ONE, "push-incline", "incline-db", 2, 180))),
            state.copy(workouts = state.workouts + workout.copy(id = "another", exercises = workout.exercises.map { it.copy(id = "second-${it.id}") }))
        )
        invalid.forEach { rejected { SnapshotCodec.decode(raw(it)) } }
        rejected { SnapshotCodec.validate(state.copy(workouts = listOf(workout.copy(finishedAt = 5000)))) }
    }

    @Test fun planRangesAndSchedulesAreValidatedButAboveCapTargetsAreAllowed() {
        val first = seed.templates.first()
        val slot = first.slots.first()
        listOf(slot.copy(baselineSets = 0), slot.copy(upperSets = 1), slot.copy(minReps = 0),
            slot.copy(maxReps = slot.minReps - 1), slot.copy(restSeconds = 0),
            slot.copy(restSeconds = 3601), slot.copy(alternatives = listOf("missing")),
            slot.copy(alternatives = listOf(slot.exerciseId)),
            slot.copy(alternatives = listOf("incline-db", "incline-db"))).forEach { bad ->
            rejected { SnapshotCodec.decode(raw(seed.copy(templates = listOf(first.copy(slots = listOf(bad))) + seed.templates.drop(1)))) }
        }
        rejected { SnapshotCodec.validate(seed.copy(templates = seed.templates.map { it.copy(weekday = 0) })) }
        rejected { SnapshotCodec.validate(seed.copy(templates = seed.templates.map { it.copy(weekday = 2) })) }
        rejected { SnapshotCodec.validate(seed.copy(templates = seed.templates.map { it.copy(introOnly = true, establishedOnly = true) })) }
        SnapshotCodec.validate(seed.copy(overrides = listOf(PlanOverride(Phase.BASELINE, "push-incline", "incline-db", 100, 180))))
    }

    @Test fun invalidTimesNotesNamesIdsAndBackupMetadataAreRejected() {
        listOf(
            seed.copy(revision = -1),
            seed.copy(lastBackupRevision = 1, lastBackupAt = 1000),
            seed.copy(lastBackupRevision = 0),
            seed.copy(lastBackupAt = 1000),
            seed.copy(lastBackupRevision = 0, lastBackupAt = -1),
            seed.copy(exercises = seed.exercises.map { it.copy(id = "") }),
            seed.copy(exercises = seed.exercises.map { it.copy(name = "") }),
            seed.copy(exercises = seed.exercises.map { it.copy(name = "x".repeat(121)) }),
            seed.copy(exercises = seed.exercises.map { it.copy(id = "x".repeat(129)) }),
            seed.copy(exercises = seed.exercises.map { it.copy(id = "bad\nid") }),
            seed.copy(exercises = emptyList()),
            seed.copy(templates = emptyList())
        ).forEach { rejected { SnapshotCodec.decode(raw(it)) } }
    }

    @Test fun loggedValuesAreValidatedOnImportAsWellAsOnActions() = runTest {
        val repo = repository()
        repo.start()
        repo.log()
        val state = repo.state.value
        val workout = state.workouts.single()
        val exercise = workout.exercises.first()
        val set = exercise.sets.single()
        listOf(set.copy(kg = null), set.copy(kg = -1.0), set.copy(reps = 0), set.copy(rir = -1.0),
            set.copy(rir = 10.1), set.copy(completedAt = -1)).forEach { bad ->
            rejected { SnapshotCodec.decode(raw(state.copy(workouts = listOf(workout.copy(
                exercises = listOf(exercise.copy(sets = listOf(bad))) + workout.exercises.drop(1)))))) }
        }
        listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY).forEach { number ->
            rejected { SnapshotCodec.validateSet(exercise.exercise, number, 10, 2.0) }
            rejected { SnapshotCodec.validateSet(exercise.exercise, 20.0, 10, number) }
        }
        rejected { SnapshotCodec.validate(state.copy(workouts = listOf(workout.copy(notes = "x".repeat(4097))))) }
        rejected { SnapshotCodec.validate(state.copy(workouts = listOf(workout.copy(finishedAt = 0)), activeWorkoutId = null, restTimer = null)) }
        rejected { SnapshotCodec.validateExercise(Exercise("bad", "Bad", LoadingType.BODYWEIGHT, perDumbbell = true)) }
    }
}
