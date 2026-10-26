package com.rebuild.domain

const val MAX_WORKOUT_EXERCISES = 100
const val DEFAULT_REST_SECONDS = 90
const val EXTRA_WORKOUT_NAME = "Extra workout"

/**
 * One exercise a routine asks for in future workouts of a phase: an original slot (with any saved change) or the
 * user's own addition. Workouts copy these when they start; later plan edits never reach a started workout.
 */
data class PlannedExercise(
    /** The template slot ID or the addition ID; it becomes the started entry's slot. */
    val slotId: String,
    val exerciseId: String,
    /** Working, timed or distance sets; null for a cardio goal, which is a total instead. */
    val sets: Int?,
    val baselineSets: Int?,
    val upperSets: Int?,
    val minReps: Int?,
    val maxReps: Int?,
    val restSeconds: Int,
    val perLeg: Boolean,
    /** The original prescription, or null for the user's own addition. */
    val slot: PlanSlot?,
    val addition: PlanAddition?,
    val goal: MeasuredGoal = MeasuredGoal()
)

fun AppState.slotRemoved(phase: Phase, slotId: String): Boolean =
    planRemovals.any { it.phase == phase && it.slotId == slotId }

/**
 * What future workouts of [routine] in [phase] require, in plan order: an original routine's kept slots, then the
 * user's additions; a scheduled copy's own exercises. A saved [PlanOrder] rearranges them.
 */
fun AppState.plannedExercises(routine: ScheduledRoutine, phase: Phase): List<PlannedExercise> {
    val slots = when (routine) {
        is WorkoutTemplate -> routine.slots.filterNot { slotRemoved(phase, it.id) }.map { slot ->
            val target = targetFor(slot, phase)
            PlannedExercise(slot.id, target.exerciseId, target.sets, slot.baselineSets, slot.upperSets, slot.minReps,
                slot.maxReps, target.restSeconds, slot.perLeg, slot, null)
        }
        is RoutinePlacement -> emptyList()
    }
    val planned = slots + planAdditions.filter { it.phase == phase && it.templateId == routine.id }.map {
        PlannedExercise(it.id, it.exerciseId, it.sets, null, null, it.minReps, it.maxReps, it.restSeconds,
            it.perLeg, null, it, it.measuredGoal)
    }
    val order = planOrders.find { it.phase == phase && it.routineId == routine.id }?.ids ?: return planned
    val rank = order.withIndex().associate { it.value to it.index }
    return planned.withIndex()
        .sortedWith(compareBy({ rank[it.value.slotId] ?: Int.MAX_VALUE }, { it.index }))
        .map { it.value }
}

/** Original slots left out of [phase]'s future workouts. They stay visible as a reference and can be restored. */
fun AppState.removedSlots(template: WorkoutTemplate, phase: Phase): List<PlanSlot> =
    template.slots.filter { slotRemoved(phase, it.id) }

/**
 * How the plan counts reps of [exerciseId]: true for per leg, false for a plain rep count. It is the convention every
 * plan position holding the exercise agrees on (an original slot, its listed alternatives or saved replacement, an
 * addition or scheduled copy's exercise, or a saved routine's). Null when nothing holds it or positions disagree, so
 * the user says how they count it. [excludingOwnerId] leaves out the routine or copy being edited.
 */
fun AppState.perLegConvention(
    exerciseId: String,
    excludingAdditionId: String? = null,
    excludingOwnerId: String? = null
): Boolean? {
    val slots = templates.flatMap { it.slots }.associateBy { it.id }
    val conventions = (slots.values.filter { it.exerciseId == exerciseId || exerciseId in it.alternatives }
        .map { it.perLeg } +
        overrides.filter { it.exerciseId == exerciseId }.mapNotNull { slots[it.slotId]?.perLeg } +
        planAdditions.filter {
            it.exerciseId == exerciseId && it.id != excludingAdditionId && it.templateId != excludingOwnerId
        }.map { it.perLeg } +
        routines.filter { it.id != excludingOwnerId }.flatMap { it.exercises }
            .filter { it.exerciseId == exerciseId }.map { it.perLeg })
        .distinct()
    return conventions.singleOrNull()
}

/** How reps of [exerciseId] were counted the last time working sets were logged for it, or null if never. */
fun AppState.lastLoggedPerLeg(exerciseId: String): Boolean? =
    workouts.sortedByDescending { it.startedAt }.asSequence()
        .flatMap { it.exercises.asSequence() }
        .firstOrNull { entry -> entry.exercise.id == exerciseId && entry.sets.any { !it.warmup } }
        ?.perLeg

/** How reps of an exercise added outside the plan are counted: as the plan counts them, else as last logged. */
fun AppState.settledPerLeg(exerciseId: String): Boolean? = perLegConvention(exerciseId) ?: lastLoggedPerLeg(exerciseId)

private val ONE_LEG_CANDIDATES = setOf(MuscleGroup.QUADS, MuscleGroup.HAMSTRINGS, MuscleGroup.GLUTES,
    MuscleGroup.CALVES, MuscleGroup.UNKNOWN)

/**
 * True when adding [exercise] must ask how its reps are counted: nothing settles it, and it may be trained one leg at
 * a time (a leg or unspecified exercise) or the plan's positions disagree. Other exercises use a plain rep count, and
 * timed, distance and cardio exercises have no reps to count.
 */
fun AppState.asksPerLeg(exercise: Exercise): Boolean {
    if (exercise.measured || settledPerLeg(exercise.id) != null) return false
    val heldByPlan = templates.any { t -> t.slots.any { it.exerciseId == exercise.id || exercise.id in it.alternatives } } ||
        overrides.any { it.exerciseId == exercise.id } || planAdditions.any { it.exerciseId == exercise.id } ||
        routines.any { r -> r.exercises.any { it.exerciseId == exercise.id } }
    return heldByPlan || exercise.primaryMuscle in ONE_LEG_CANDIDATES
}

enum class RestSource(val label: String) {
    LAST_USED("last used"),
    PLAN("from your plan"),
    ROUTINE("from your routines"),
    DEFAULT("default")
}

data class RestSuggestion(val seconds: Int, val source: RestSource)

/**
 * Rest for an exercise being added in [phase]: the rest it was last logged with (an entry with a working set or an
 * effort), else its rest in that phase's plan (original routines and scheduled copies), else its slot's original
 * prescription, else its rest in a saved routine, else a visible 90-second default.
 */
fun AppState.restSuggestion(exerciseId: String, phase: Phase?, excludingEntryId: String? = null): RestSuggestion {
    workouts.sortedByDescending { it.startedAt }.asSequence()
        .flatMap { it.exercises.asSequence() }
        .firstOrNull { entry ->
            entry.id != excludingEntryId && entry.exercise.id == exerciseId && entry.hasLoggedWork()
        }?.let { return RestSuggestion(it.restSeconds, RestSource.LAST_USED) }
    if (phase != null) (availableTemplates(phase) + placementsIn(phase)).asSequence()
        .flatMap { plannedExercises(it, phase).asSequence() }
        .firstOrNull { it.exerciseId == exerciseId }
        ?.let { return RestSuggestion(it.restSeconds, RestSource.PLAN) }
    templates.asSequence().flatMap { it.slots.asSequence() }
        .firstOrNull { it.exerciseId == exerciseId || exerciseId in it.alternatives }
        ?.let { return RestSuggestion(it.restSeconds, RestSource.PLAN) }
    routines.asSequence().flatMap { it.exercises.asSequence() }
        .firstOrNull { it.exerciseId == exerciseId }
        ?.let { return RestSuggestion(it.restSeconds, RestSource.ROUTINE) }
    return RestSuggestion(DEFAULT_REST_SECONDS, RestSource.DEFAULT)
}

/**
 * The plan addition or template slot behind a required entry, if it is still in the future plan of an occurrence that
 * is on its phase's week now. A removed occurrence has no future plan to change.
 */
fun AppState.planSourceOf(workout: Workout, entry: WorkoutExercise): PlannedExercise? {
    if (entry.optional || entry.slotId == null) return null
    val routine = scheduledRoutine(workout.phase, workout.templateId) ?: return null
    return plannedExercises(routine, workout.phase).find { it.slotId == entry.slotId }
}

/** True when [exerciseId] is already among future [routine] workouts of [phase]. */
fun AppState.inPlan(routine: ScheduledRoutine, phase: Phase, exerciseId: String): Boolean =
    plannedExercises(routine, phase).any { it.exerciseId == exerciseId }
