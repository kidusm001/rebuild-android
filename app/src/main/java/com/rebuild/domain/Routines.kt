package com.rebuild.domain

import kotlinx.serialization.Serializable

const val MAX_ROUTINES = 100
/** Scheduled copies, including removed ones that workouts still refer to. */
const val MAX_PLACEMENTS = 1000
const val MAX_ROUTINE_NAME = 120
/** Plan additions across all routines, phases and scheduled copies. */
const val MAX_PLAN_ADDITIONS = 40000

fun AppState.scheduleRemoved(phase: Phase, templateId: String): Boolean =
    scheduleRemovals.any { it.phase == phase && it.templateId == templateId }

/** Scheduled copies currently on [phase]'s week. */
fun AppState.placementsIn(phase: Phase): List<RoutinePlacement> =
    placements.filter { it.phase == phase && !it.removedFromPlan }

/**
 * [phase]'s weekly plan in weekday order: the original routines still on its week, then the user's scheduled copies.
 * A day may hold several; each occurrence is its own checklist row, whatever its name.
 */
fun AppState.scheduledRoutines(phase: Phase = requireNotNull(this.phase)): List<ScheduledRoutine> =
    (availableTemplates(phase).filterNot { scheduleRemoved(phase, it.id) } + placementsIn(phase)).sortedBy { it.weekday }

/** The occurrence [id] if it is on [phase]'s week now. Removed or other phases' occurrences are never returned. */
fun AppState.scheduledRoutine(phase: Phase, id: String?): ScheduledRoutine? = scheduledRoutines(phase).find { it.id == id }

/** Original routines of [phase] taken off its week, which can be put back. */
fun AppState.removedTemplates(phase: Phase): List<WorkoutTemplate> =
    availableTemplates(phase).filter { scheduleRemoved(phase, it.id) }

/** The original routine or scheduled copy (current or removed) that a workout's [Workout.templateId] names. */
fun AppState.occurrenceOf(workout: Workout): ScheduledRoutine? = workout.templateId?.let { id ->
    templates.find { it.id == id } ?: placements.find { it.id == id }
}

/** A scheduled copy's own exercises as routine targets, in its order. */
fun AppState.placementExercises(placement: RoutinePlacement): List<RoutineExercise> =
    planAdditions.filter { it.templateId == placement.id && it.phase == placement.phase }.map {
        RoutineExercise(it.exerciseId, it.sets, it.minReps, it.maxReps, it.restSeconds, it.perLeg, it.effortSeconds,
            it.effortDistance, it.totalSeconds, it.totalDistance)
    }

/** Copies of library routine [routineId] on [phase]'s week now, in weekday order. */
fun AppState.linkedPlacements(routineId: String, phase: Phase): List<RoutinePlacement> =
    placementsIn(phase).filter { it.routineId == routineId }.sortedBy { it.weekday }

/** Every phase's current copies of [routineId]. */
fun AppState.allLinkedPlacements(routineId: String): List<RoutinePlacement> =
    placements.filter { it.routineId == routineId && !it.removedFromPlan }

/** True when the copy's linked library routine now has a different name or exercises than the copy. */
fun AppState.libraryDiffers(placement: RoutinePlacement): Boolean {
    val routine = routines.find { it.id == placement.routineId } ?: return false
    return routine.name != placement.name || routine.exercises != placementExercises(placement)
}

/** One row of a routine being edited. Targets stay blank until the user enters them; nothing is guessed. */
@Serializable
data class DraftExercise(
    /** Stable within the draft, so duplicate exercises from a workout stay separate rows. */
    val key: String,
    val exerciseId: String,
    val sets: Int?,
    val minReps: Int?,
    val maxReps: Int?,
    val restSeconds: Int?,
    val perLeg: Boolean,
    val effortSeconds: Int? = null,
    val effortDistance: Distance? = null,
    val totalSeconds: Int? = null,
    val totalDistance: Distance? = null
)

@Serializable
data class RoutineDraft(val name: String, val exercises: List<DraftExercise>)

enum class DraftProblem {
    /** Sets, the rep range, the timed or distance minimum, the cardio total or rest is missing. */
    NEEDS_TARGETS,
    /** The same exercise is listed more than once; the user keeps one. */
    DUPLICATE,
    /** The plan already counts this exercise's reps the other way. */
    PER_LEG_CONFLICT
}

/** How the row's exercise is measured, as its library entry says; an unknown one is treated as rep-based. */
fun AppState.measureOf(exerciseId: String): Measure = exercises.find { it.id == exerciseId }?.measure ?: Measure.REPS

/** Every part of the goal its kind of exercise needs is entered, and the rest. */
fun DraftExercise.complete(measure: Measure): Boolean = restSeconds != null && when (measure) {
    Measure.REPS -> sets != null && minReps != null && maxReps != null
    Measure.TIMED -> sets != null && effortSeconds != null
    Measure.DISTANCE -> sets != null && effortDistance != null
    Measure.CARDIO -> totalSeconds != null || totalDistance != null
}

/**
 * What stops [draft] from being saved, per row key. [ownerId] is the routine or copy being edited, whose own rows
 * don't settle how reps are counted.
 */
fun AppState.draftProblems(draft: RoutineDraft, ownerId: String?): Map<String, Set<DraftProblem>> {
    val counts = draft.exercises.groupingBy { it.exerciseId }.eachCount()
    return draft.exercises.associate { row ->
        row.key to buildSet {
            if (!row.complete(measureOf(row.exerciseId))) add(DraftProblem.NEEDS_TARGETS)
            if ((counts[row.exerciseId] ?: 0) > 1) add(DraftProblem.DUPLICATE)
            perLegConvention(row.exerciseId, excludingOwnerId = ownerId)?.let { if (it != row.perLeg) add(DraftProblem.PER_LEG_CONFLICT) }
        }
    }.filterValues { it.isNotEmpty() }
}

/**
 * The confirmed exercises, or null while any row is incomplete. Each keeps only the goal parts its kind of exercise
 * uses: reps, a timed or distance minimum per set, or cardio totals.
 */
fun AppState.confirmedExercises(draft: RoutineDraft): List<RoutineExercise>? = draft.exercises.map { row ->
    val measure = measureOf(row.exerciseId)
    if (!row.complete(measure)) return null
    val rest = row.restSeconds!!
    when (measure) {
        Measure.REPS -> RoutineExercise(row.exerciseId, row.sets!!, row.minReps!!, row.maxReps!!, rest, row.perLeg)
        Measure.TIMED -> RoutineExercise(row.exerciseId, row.sets!!, null, null, rest, false,
            effortSeconds = row.effortSeconds!!)
        Measure.DISTANCE -> RoutineExercise(row.exerciseId, row.sets!!, null, null, rest, false,
            effortDistance = row.effortDistance!!)
        Measure.CARDIO -> RoutineExercise(row.exerciseId, null, null, null, rest, false,
            totalSeconds = row.totalSeconds, totalDistance = row.totalDistance)
    }
}

fun CustomRoutine.draft(): RoutineDraft = RoutineDraft(name, exercises.map { it.draftRow(it.exerciseId) })

fun AppState.placementDraft(placement: RoutinePlacement): RoutineDraft =
    RoutineDraft(placement.name, placementExercises(placement).map { it.draftRow(it.exerciseId) })

private fun RoutineExercise.draftRow(key: String) =
    DraftExercise(key, exerciseId, sets, minReps, maxReps, restSeconds, perLeg, effortSeconds, effortDistance,
        totalSeconds, totalDistance)

/**
 * A routine draft from a finished workout, for review. Every exercise is listed in the workout's order, duplicates
 * included, with only the targets or goals the entry itself had. Performed loads, reps, stages, durations and
 * distances never become targets.
 */
fun routineDraftFrom(workout: Workout): RoutineDraft = RoutineDraft(
    name = if (workout.extra && workout.name == EXTRA_WORKOUT_NAME) "" else workout.name,
    exercises = workout.exercises.map { entry ->
        DraftExercise(entry.id, entry.exercise.id, entry.targetSets, entry.minReps, entry.maxReps, entry.restSeconds,
            entry.perLeg, entry.effortSeconds, entry.effortDistance, entry.totalSeconds, entry.totalDistance)
    }
)
