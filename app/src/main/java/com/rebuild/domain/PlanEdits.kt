package com.rebuild.domain

import java.util.Locale

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
        // A slot the plan's gear leaves out (no dumbbell or bodyweight match) isn't asked for, unless a saved
        // change has already given it an exercise.
        is WorkoutTemplate -> routine.slots.filterNot { slotRemoved(phase, it.id) }
            .filter { slot -> slot.exerciseFor(gear) != null ||
                overrides.any { it.phase == phase && it.slotId == slot.id && it.appliesTo(gear) } }
            .map { slot ->
                val target = targetFor(slot, phase)
                val reps = slot.repsFor(gear)
                PlannedExercise(slot.id, target.exerciseId, target.sets, slot.baselineSets, slot.upperSets, reps.first,
                    reps.last, target.restSeconds, slot.perLeg, slot, null)
            }
        is RoutinePlacement -> emptyList()
    }
    // An exercise you added that a slot now asks for itself (say, push-ups once the gear is bodyweight) is set aside
    // rather than listed twice; it stays saved and comes back with the gear it was added for.
    val fromSlots = slots.map { it.exerciseId }.toSet()
    val planned = slots + planAdditions.filter {
        it.phase == phase && it.templateId == routine.id && it.exerciseId !in fromSlots
    }.map {
        PlannedExercise(it.id, it.exerciseId, it.sets, null, null, it.minReps, it.maxReps, it.restSeconds,
            it.perLeg, null, it, it.measuredGoal)
    }
    val order = planOrders.find { it.phase == phase && it.routineId == routine.id }?.ids ?: return planned
    val rank = order.withIndex().associate { it.value to it.index }
    return planned.withIndex()
        .sortedWith(compareBy({ rank[it.value.slotId] ?: Int.MAX_VALUE }, { it.index }))
        .map { it.value }
}

/** Whether [exerciseId] is this slot's exercise, a listed alternative, or its dumbbell or bodyweight option. */
private fun PlanSlot.holds(exerciseId: String): Boolean = exerciseId == this.exerciseId || exerciseId in alternatives ||
    exerciseId == gear?.dumbbell || exerciseId == gear?.bodyweight

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
    val slots = activeTemplates.flatMap { it.slots }.associateBy { it.id }
    val conventions = (slots.values.filter { it.holds(exerciseId) }
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
    val heldByPlan = activeTemplates.any { t -> t.slots.any { it.holds(exercise.id) } } ||
        overrides.any { it.exerciseId == exercise.id } || planAdditions.any { it.exerciseId == exercise.id } ||
        routines.any { r -> r.exercises.any { it.exerciseId == exercise.id } }
    return heldByPlan || exercise.primaryMuscle in ONE_LEG_CANDIDATES
}

/**
 * Whether [this] piece of equipment is available with [gear]. A dumbbell plan also uses bodyweight exercises; a
 * bodyweight plan uses bodyweight only; a gym plan allows every piece of equipment (including older custom exercises
 * with [Equipment.UNKNOWN]).
 */
fun Equipment.fits(gear: Gear): Boolean = when (gear) {
    Gear.GYM -> true
    Gear.DUMBBELLS -> this == Equipment.DUMBBELL || this == Equipment.BODYWEIGHT
    Gear.BODYWEIGHT -> this == Equipment.BODYWEIGHT
}

enum class SwapGroupKind {
    PLAN_ALTERNATIVES,
    SAME_MOVEMENT,
    SAME_MUSCLE
}

/** One candidate exercise offered when swapping, with a plain-text reason and whether the lifter has logged it before. */
data class SwapSuggestion(
    val exercise: Exercise,
    val reason: String,
    val loggedBefore: Boolean
)

/** A labelled group of swap suggestions in display order. Empty groups are omitted. */
data class SwapGroup(
    val kind: SwapGroupKind,
    val title: String,
    val suggestions: List<SwapSuggestion>
)

/**
 * Grouped swap suggestions for [exerciseId], ordered from closest prescription match to broader same-muscle options:
 * 1. Plan alternatives: [slot]'s default exercise and [PlanSlot.alternativesFor] [gear].
 * 2. Same movement: built-in exercises sharing the code-side [Movement] tag.
 * 3. More for `<muscle>`: other library and custom exercises with the same primary muscle.
 *
 * Every suggestion shares [exerciseId]'s [Measure], fits [gear] (when non-null), and is neither [exerciseId] nor in
 * [excluding]. Within `Plan alternatives`, the slot's default exercise stays first; all other items within each group
 * rank exercises logged before first, then alphabetical by name. The order is fixed and never applied automatically.
 */
fun AppState.swapSuggestions(
    exerciseId: String,
    slot: PlanSlot? = null,
    gear: Gear? = this.gear,
    excluding: Set<String> = emptySet()
): List<SwapGroup> {
    val current = exercises.find { it.id == exerciseId } ?: return emptyList()
    val measure = current.measure
    val byId = exercises.associateBy { it.id }
    val logged = workouts.asSequence()
        .flatMap { it.exercises.asSequence() }
        .filter { it.hasLoggedWork() }
        .map { it.exercise.id }
        .toSet()
    val order = compareByDescending<Exercise> { it.id in logged }
        .thenBy { it.name.lowercase(Locale.ROOT) }
        .thenBy { it.name }
        .thenBy { it.id }
    val seen = mutableSetOf(exerciseId).apply { addAll(excluding) }
    fun fits(exercise: Exercise): Boolean =
        exercise.measure == measure && (gear == null || exercise.equipment.fits(gear))

    val groups = mutableListOf<SwapGroup>()

    // 1. Plan alternatives (for a plan slot).
    val slotDefaultId = slot?.let { if (gear != null) it.exerciseFor(gear) else it.exerciseId }
    val slotAltIds = slot?.let { if (gear != null) it.alternativesFor(gear) else it.alternatives }.orEmpty()
    if (slot != null) {
        val planOrder = compareByDescending<Exercise> { it.id == slotDefaultId }.then(order)
        val planCandidates = (listOfNotNull(slotDefaultId) + slotAltIds)
            .distinct()
            .filter { it !in seen }
            .mapNotNull(byId::get)
            .filter(::fits)
            .sortedWith(planOrder)
        if (planCandidates.isNotEmpty()) {
            planCandidates.forEach { seen.add(it.id) }
            groups += SwapGroup(
                kind = SwapGroupKind.PLAN_ALTERNATIVES,
                title = "Plan alternatives",
                suggestions = planCandidates.map { ex ->
                    SwapSuggestion(
                        exercise = ex,
                        reason = if (ex.id == slotDefaultId) "Plan default" else "Plan alternative",
                        loggedBefore = ex.id in logged
                    )
                }
            )
        }
    }

    // Anchor movement and muscle on the slot's default exercise when present (matching existing plan-slot behaviour),
    // falling back to the current exercise when there is no slot or the slot has no exercise for this gear.
    val anchor = slotDefaultId?.let(byId::get) ?: current
    val movement = ExerciseCatalog.movementOf(anchor.id) ?: ExerciseCatalog.movementOf(current.id)
    val muscle = anchor.primaryMuscle.takeIf { it != MuscleGroup.UNKNOWN } ?: current.primaryMuscle

    // 2. Same movement.
    if (movement != null) {
        val movementMatches = exercises.filter { ex ->
            ex.id !in seen && fits(ex) && ExerciseCatalog.movementOf(ex.id) == movement
        }.sortedWith(order)
        if (movementMatches.isNotEmpty()) {
            movementMatches.forEach { seen.add(it.id) }
            groups += SwapGroup(
                kind = SwapGroupKind.SAME_MOVEMENT,
                title = "Same movement",
                suggestions = movementMatches.map { ex ->
                    SwapSuggestion(ex, reason = movement.label, loggedBefore = ex.id in logged)
                }
            )
        }
    }

    // 3. More for <muscle> (library + custom exercises with the same primary muscle).
    if (muscle != MuscleGroup.UNKNOWN) {
        val muscleMatches = exercises.filter { ex ->
            ex.id !in seen && fits(ex) && ex.primaryMuscle == muscle
        }.sortedWith(order)
        if (muscleMatches.isNotEmpty()) {
            muscleMatches.forEach { seen.add(it.id) }
            groups += SwapGroup(
                kind = SwapGroupKind.SAME_MUSCLE,
                title = "More for ${muscle.title}",
                suggestions = muscleMatches.map { ex ->
                    SwapSuggestion(ex, reason = "Same muscle", loggedBefore = ex.id in logged)
                }
            )
        }
    }

    return groups
}

/**
 * The user's own exercises offered beside a planned one when swapping it: only those working the same primary muscle,
 * so a curl isn't offered a pec deck. The rest, and any without a muscle, are one tap away in the full library.
 */
fun AppState.customSwapsFor(exerciseId: String?): List<String> {
    val muscle = exercises.find { it.id == exerciseId }?.primaryMuscle ?: return emptyList()
    if (muscle == MuscleGroup.UNKNOWN) return emptyList()
    return exercises.filter { it.custom && it.id != exerciseId && it.primaryMuscle == muscle }.map { it.id }
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
    activeTemplates.asSequence().flatMap { it.slots.asSequence() }
        .firstOrNull { it.holds(exerciseId) }
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
