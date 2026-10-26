package com.rebuild.domain

/**
 * One planned working set as it was performed: a normal working set, or a drop or rest-pause group of stages.
 * Warm-ups are never entries. A finished group counts as exactly one working set however many stages it has;
 * an open group is in progress and counts as none yet.
 */
sealed interface WorkEntry {
    val unit: LoadUnit
    val counted: Boolean

    data class Single(val set: LoggedSet) : WorkEntry {
        override val unit: LoadUnit get() = set.loadUnit
        override val counted: Boolean get() = true
    }

    data class Group(val group: SetGroup, val stages: List<LoggedSet>) : WorkEntry {
        /** The unit the group was entered in; stages keep their own units if a correction changed one. */
        override val unit: LoadUnit get() = stages.first().loadUnit
        val open: Boolean get() = group.finishedAt == null
        override val counted: Boolean get() = !open
    }
}

/**
 * The set itself: a normal working set, or the first stage of a set someone then dropped from (or rest-paused). It is
 * the part that was already a set before anything was added, so it is what charts, records and comparisons read.
 */
val WorkEntry.mainSet: LoggedSet
    get() = when (this) {
        is WorkEntry.Single -> set
        is WorkEntry.Group -> stages.first()
    }

/** What followed the main set: its drops, or its rest-pause stages, in order. Empty for a normal set. */
val WorkEntry.followUps: List<LoggedSet>
    get() = when (this) {
        is WorkEntry.Single -> emptyList()
        is WorkEntry.Group -> stages.drop(1)
    }

/** Each counted working set's main set, in logged order: never a warm-up, a drop or a set still in progress. */
fun WorkoutExercise.mainSets(): List<LoggedSet> = workEntries().filter { it.counted }.map { it.mainSet }

const val MAX_GROUP_STAGES = 20

/** Working entries in logged order. Stages are folded into their group at the position of its first stage. */
fun WorkoutExercise.workEntries(): List<WorkEntry> {
    val groups = setGroups.associateBy { it.id }
    val result = mutableListOf<WorkEntry>()
    val positions = mutableMapOf<String, Int>()
    for (set in sets) {
        if (set.warmup) continue
        val group = set.groupId?.let(groups::get)
        if (group == null) {
            // A stage without its group is rejected by validation; shown as a plain set rather than hidden.
            result += WorkEntry.Single(set)
            continue
        }
        val at = positions[group.id]
        if (at == null) {
            positions[group.id] = result.size
            result += WorkEntry.Group(group, listOf(set))
        } else {
            val existing = result[at] as WorkEntry.Group
            result[at] = existing.copy(stages = existing.stages + set)
        }
    }
    return result
}

/** Completed working sets toward the target: normal working sets plus finished groups. */
fun WorkoutExercise.workingCount(): Int = workEntries().count { it.counted }

fun WorkoutExercise.openGroup(): SetGroup? = setGroups.firstOrNull { it.finishedAt == null }

fun WorkoutExercise.stages(groupId: String): List<LoggedSet> = sets.filter { it.groupId == groupId }

/**
 * The working entry a drop can be added to right now: the latest entry, when it is a normal working set or a finished
 * drop set, with nothing open on the exercise. Null otherwise.
 */
fun WorkoutExercise.dropTarget(): WorkEntry? {
    if (openGroup() != null) return null
    return when (val last = workEntries().lastOrNull()) {
        is WorkEntry.Single -> last
        is WorkEntry.Group -> last.takeIf { it.group.method == SetMethod.DROP && it.stages.size < MAX_GROUP_STAGES }
        null -> null
    }
}

fun Workout.openGroupExercise(): WorkoutExercise? = exercises.firstOrNull { it.openGroup() != null }

fun Workout.workingSetCount(): Int = exercises.sumOf { it.workingCount() }

/** Working sets on the routine's own planned exercises, the ones its saved targets are about. */
fun Workout.requiredWorkingSetCount(): Int = exercises.filterNot { it.optional }.sumOf { it.workingCount() }

fun Workout.warmupSetCount(): Int = exercises.sumOf { ex -> ex.sets.count { it.warmup } }

/**
 * Every required exercise reached its own target with real work: working sets, efforts each at least their minimum,
 * or cardio totals actually logged. Optional entries never block it, even with a goal, and a skipped exercise counts
 * only for the work actually logged. An extra workout has no targets to meet.
 */
fun Workout.targetsMet(): Boolean {
    val required = exercises.filterNot { it.optional }
    return templateId != null && required.isNotEmpty() && required.all { it.goalReached() }
}

/** Short of a target it has: a required target or an optional goal. An entry without one is never short. */
fun WorkoutExercise.belowTarget(): Boolean = hasGoal() && !goalReached()

/**
 * Where a resumed workout opens: an exercise with a set still in progress, else the first one short of its target
 * that the user hasn't skipped today. An extra workout otherwise opens on its latest exercise.
 */
fun Workout.resumeExercise(): WorkoutExercise? =
    openGroupExercise() ?: exercises.firstOrNull { !it.skipped && it.belowTarget() }
        ?: if (extra) exercises.lastOrNull() else exercises.firstOrNull { !it.skipped } ?: exercises.firstOrNull()

/** The next exercise after [index] in session order that isn't skipped today, for the Next action. */
fun Workout.nextAfter(index: Int): WorkoutExercise? = exercises.drop(index + 1).firstOrNull { !it.skipped }

enum class PrefillSource { LAST_TIME, THIS_WORKOUT }

/** Values offered for the next entry, never saved without a tap. Without [withReps] only the load is offered. */
data class EntrySuggestion(val set: LoggedSet, val source: PrefillSource, val withReps: Boolean = true)

/**
 * What to offer for the next entry in [unit]. Today's entry n pairs with last time's entry n in the same unit.
 * A normal set uses last time's entry only if it was a normal set, else repeats today's last normal set; a group's
 * stage is never offered as a normal set. A stage uses the same stage of last time's entry only if that entry was
 * the same method; otherwise a rest-pause mini-set offers this set's load (reps left blank) and a drop is left blank.
 * For histories without groups this is exactly the previous rule: last time's set n, else today's last set.
 */
fun nextEntrySuggestion(
    today: WorkoutExercise,
    previous: WorkoutExercise?,
    unit: LoadUnit,
    method: SetMethod?
): EntrySuggestion? {
    val todayEntries = today.workEntries().filter { it.unit == unit }
    val lastTime = previous?.workEntries()?.filter { it.unit == unit }.orEmpty()
    val open = today.openGroup()
    if (open != null) {
        val stages = today.stages(open.id)
        val ordinal = todayEntries.indexOfFirst { it is WorkEntry.Group && it.group.id == open.id }
        val comparable = (lastTime.getOrNull(ordinal) as? WorkEntry.Group)?.takeIf { it.group.method == open.method }
        comparable?.stages?.getOrNull(stages.size)?.takeIf { it.loadUnit == unit }
            ?.let { return EntrySuggestion(it, PrefillSource.LAST_TIME) }
        return if (open.method != SetMethod.REST_PAUSE) null
        else stages.lastOrNull()?.takeIf { it.loadUnit == unit }
            ?.let { EntrySuggestion(it, PrefillSource.THIS_WORKOUT, withReps = false) }
    }
    val paired = lastTime.getOrNull(todayEntries.size)
    if (method != null && paired is WorkEntry.Group && paired.group.method == method) {
        paired.stages.first().takeIf { it.loadUnit == unit }?.let { return EntrySuggestion(it, PrefillSource.LAST_TIME) }
    }
    if (paired is WorkEntry.Single) return EntrySuggestion(paired.set, PrefillSource.LAST_TIME)
    return todayEntries.lastOrNull { it is WorkEntry.Single }
        ?.let { EntrySuggestion((it as WorkEntry.Single).set, PrefillSource.THIS_WORKOUT) }
}
