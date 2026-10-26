package com.rebuild.domain

import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

@Serializable
enum class Phase(val title: String, val targetRir: Int, val fourDay: Boolean) {
    INTRO_ONE("Four days: return gently", 4, true),
    INTRO_TWO("Four days: build consistency", 3, true),
    FIVE_TWO("Five days: start with two sets", 3, false),
    BASELINE("Five days: established baseline", 2, false)
}

@Serializable
enum class LoadingType { EXTERNAL, BODYWEIGHT, ASSISTANCE }

/**
 * How a load was entered: kilograms, whole plates per stack (cable and stack machines), or pounds. Every value is kept
 * in the unit it was logged in and is never converted; sets are only ever compared with sets in the same unit.
 */
@Serializable
enum class LoadUnit { KG, PLATES, LB }

/** A mass unit for bodyweight and for the default lifting unit. */
@Serializable
enum class MassUnit(val symbol: String, val loadUnit: LoadUnit) {
    KG("kg", LoadUnit.KG), LB("lb", LoadUnit.LB);

    companion object {
        /** Exact, for display only: stored values keep the unit they were entered in. */
        const val KG_PER_LB = 0.45359237
    }
}

@Serializable
enum class Equipment(val title: String) {
    UNKNOWN("Unspecified"), BARBELL("Barbell"), DUMBBELL("Dumbbell"), CABLE("Cable"),
    MACHINE("Machine"), STACK_MACHINE("Stack machine"), BODYWEIGHT("Bodyweight")
}

@Serializable
enum class MuscleGroup(val title: String) {
    UNKNOWN("Unspecified"), CHEST("Chest"), BACK("Back"), SHOULDERS("Shoulders"),
    BICEPS("Biceps"), TRICEPS("Triceps"), QUADS("Quads"), HAMSTRINGS("Hamstrings"),
    GLUTES("Glutes"), CALVES("Calves"), CORE("Core"), FULL_BODY("Full body")
}

/**
 * How an exercise's work is recorded: sets of reps, timed sets (holds), distance sets (carries) or cardio efforts.
 * It never changes for an exercise ID, so every record of that exercise keeps one meaning.
 */
@Serializable
enum class Measure(val title: String) {
    REPS("Reps"), TIMED("Timed"), DISTANCE("Distance"), CARDIO("Cardio")
}

@Serializable
enum class DistanceUnit(val symbol: String, val micrometresPerThousandth: Long) {
    M("m", 1_000), KM("km", 1_000_000), MI("mi", 1_609_344)
}

/** A distance exactly as entered: [thousandths] of [unit], so 2.4 km is 2400 KM. It is never converted when stored. */
@Serializable
data class Distance(val thousandths: Long, val unit: DistanceUnit) {
    /**
     * The exact length in micrometres (a mile is exactly 1,609.344 m), only for comparing different units. A stored
     * distance is within [DistanceUnit.maxThousandths], so this and the sum of 10,000 of them fit; anything larger
     * throws rather than wrapping.
     */
    val micrometres: Long get() = Math.multiplyExact(thousandths, unit.micrometresPerThousandth)
}

/**
 * One timed, distance or cardio effort exactly as performed. A measurement that wasn't taken is null, never zero.
 * A load follows a set's rules ([kg] or [plates] in [loadUnit]); cardio never records one.
 */
@Serializable
data class Effort(
    val id: String,
    val durationSeconds: Int?,
    val distance: Distance?,
    val kg: Double?,
    val loadUnit: LoadUnit,
    val plates: Int?,
    val completedAt: Long,
    /** The load in pounds, for an effort logged in [LoadUnit.LB]; [kg] is then null. */
    val lb: Double? = null
) {
    /** The load as entered, in [loadUnit]: kilograms, pounds or a plate count. */
    val loadValue: Double? get() = loadValueOf(loadUnit, kg, lb, plates)
    /** The kilogram or pound amount as entered; null for plates or no load. */
    val amount: Double? get() = amountOf(loadUnit, kg, lb)

    companion object {
        /** An effort whose [amount] is stored in the field of its own unit. */
        fun of(id: String, durationSeconds: Int?, distance: Distance?, amount: Double?, loadUnit: LoadUnit, plates: Int?,
               completedAt: Long) = Effort(id, durationSeconds, distance, amount.takeUnless { loadUnit == LoadUnit.LB },
            loadUnit, plates, completedAt, lb = amount.takeIf { loadUnit == LoadUnit.LB })
    }
}

internal fun loadValueOf(unit: LoadUnit, kg: Double?, lb: Double?, plates: Int?): Double? = when (unit) {
    LoadUnit.KG -> kg
    LoadUnit.LB -> lb
    LoadUnit.PLATES -> plates?.toDouble()
}

internal fun amountOf(unit: LoadUnit, kg: Double?, lb: Double?): Double? = when (unit) {
    LoadUnit.KG -> kg
    LoadUnit.LB -> lb
    LoadUnit.PLATES -> null
}

/**
 * The one activity stopwatch, timing an effort on a measured exercise of the active workout. Performed time is
 * [elapsedMs] plus, while running, the phone's monotonic time since [runningSince]; paused time is never counted.
 * [boot] and [session] say which clock [runningSince] was read from. Stopped, it is an unsaved review draft that
 * becomes effort [id] only when the user logs it. [needsConfirmation] means the running part couldn't be trusted
 * (the phone restarted, for example): the user enters the duration, and nothing is estimated. [step] counts its
 * changes since it started; every request names the step it was made for, so a late or repeated one never acts on a
 * newer state.
 */
@Serializable
data class Stopwatch(
    val id: String,
    val workoutId: String,
    val exerciseId: String,
    val elapsedMs: Long,
    val runningSince: Long?,
    val boot: Int?,
    val session: String?,
    val stopped: Boolean,
    val needsConfirmation: Boolean,
    /** Wall-clock start, shown for context only; it never measures anything. */
    val startedAt: Long,
    val step: Int
) {
    val running: Boolean get() = runningSince != null
}

/** A reading of the phone's monotonic since-boot clock, with the boot and app process it was read in. */
data class Monotonic(val boot: Int?, val session: String, val elapsedMs: Long)

@Serializable
data class Exercise(
    val id: String,
    val name: String,
    val loading: LoadingType = LoadingType.EXTERNAL,
    val perDumbbell: Boolean = false,
    val custom: Boolean = false,
    val equipment: Equipment = Equipment.UNKNOWN,
    val primaryMuscle: MuscleGroup = MuscleGroup.UNKNOWN,
    val aliases: List<String> = emptyList(),
    val preferredLoadUnit: LoadUnit = LoadUnit.KG,
    val measure: Measure = Measure.REPS
) {
    val supportsPlates: Boolean get() = loading == LoadingType.EXTERNAL && !perDumbbell &&
        (equipment == Equipment.CABLE || equipment == Equipment.STACK_MACHINE)
}

@Serializable
data class PlanSlot(
    val id: String,
    val exerciseId: String,
    val alternatives: List<String> = emptyList(),
    val baselineSets: Int,
    val upperSets: Int?,
    val minReps: Int,
    val maxReps: Int,
    val restSeconds: Int,
    val perLeg: Boolean = false,
    val muscle: String
)

/**
 * A routine on a phase's weekly checklist, identified by [id] alone and never by its name: an original routine of the
 * prescription or a scheduled copy of one of the user's own routines. Workouts started from it record this [id].
 */
sealed interface ScheduledRoutine {
    val id: String
    val name: String
    val weekday: Int
}

@Serializable
data class WorkoutTemplate(
    override val id: String,
    override val name: String,
    override val weekday: Int,
    val introOnly: Boolean = false,
    val establishedOnly: Boolean = false,
    val slots: List<PlanSlot>
) : ScheduledRoutine

/**
 * One exercise of a saved routine with the targets the user confirmed: working sets and reps, timed or distance sets
 * with a per-set minimum, or a cardio total. Parts its measurement type doesn't use are null. Never a load.
 */
@Serializable
data class RoutineExercise(
    val exerciseId: String,
    val sets: Int?,
    val minReps: Int?,
    val maxReps: Int?,
    val restSeconds: Int,
    /** Reps are counted per leg. */
    val perLeg: Boolean,
    val effortSeconds: Int? = null,
    val effortDistance: Distance? = null,
    val totalSeconds: Int? = null,
    val totalDistance: Distance? = null
)

/**
 * One of the user's own routines in the library, shared by every phase and unscheduled until a copy of it is added to
 * a phase's week. Editing or deleting it never changes those copies or any workout.
 */
@Serializable
data class CustomRoutine(
    val id: String,
    val name: String,
    val exercises: List<RoutineExercise>
)

/**
 * A routine scheduled on one [weekday] of one [phase]'s week: an independent copy with its own name, and exercises
 * kept as plan additions of this [id]. [routineId] only remembers the library routine it was copied from, so the user
 * can explicitly update the copy. A copy that workouts refer to stays as a record when removed ([removedFromPlan]);
 * its ID and phase never change and are never reused.
 */
@Serializable
data class RoutinePlacement(
    override val id: String,
    val phase: Phase,
    override val weekday: Int,
    override val name: String,
    val routineId: String? = null,
    val removedFromPlan: Boolean = false
) : ScheduledRoutine

/** An original routine taken off [phase]'s week. Its plan stays as it was, so it can be put back. */
@Serializable
data class ScheduleRemoval(val phase: Phase, val templateId: String)

@Serializable
data class PlanOverride(
    val phase: Phase,
    val slotId: String,
    val exerciseId: String,
    val sets: Int,
    val restSeconds: Int
)

/**
 * The user's own exercise in one routine's future workouts of one [phase]: a customization with targets the user
 * entered, never a recommendation. Its [id] is the plan slot of the required entry it creates in later workouts.
 */
@Serializable
data class PlanAddition(
    val id: String,
    val phase: Phase,
    val templateId: String,
    val exerciseId: String,
    /** Working sets, or timed or distance sets; null for cardio. */
    val sets: Int?,
    val minReps: Int?,
    val maxReps: Int?,
    val restSeconds: Int,
    /** Reps are counted per leg, as the user chose when saving it. */
    val perLeg: Boolean,
    val effortSeconds: Int? = null,
    val effortDistance: Distance? = null,
    val totalSeconds: Int? = null,
    val totalDistance: Distance? = null
)

/**
 * The user's own exercise order for future workouts of one routine in one [phase]: plan slot and addition IDs, first
 * to last. Exercises it doesn't list keep their place after the listed ones; IDs no longer planned are ignored.
 */
@Serializable
data class PlanOrder(val phase: Phase, val routineId: String, val ids: List<String>)

/** An originally prescribed slot left out of future workouts of [phase]. The slot stays as a reference to restore. */
@Serializable
data class PlanRemoval(val phase: Phase, val slotId: String)

/** How the stages of one grouped working set were performed. Each stage is logged exactly as performed. */
@Serializable
enum class SetMethod(val title: String, val stageNoun: String) {
    DROP("Drop set", "drop"),
    REST_PAUSE("Rest-pause", "mini-set")
}

/**
 * A drop set or rest-pause set: one planned working set made of 1–20 stages. Its stages are [LoggedSet]s carrying
 * this [id] as their groupId, kept contiguous in the exercise's set list. [finishedAt] is null while it is open.
 */
@Serializable
data class SetGroup(
    val id: String,
    val method: SetMethod,
    val finishedAt: Long? = null
)

@Serializable
data class LoggedSet(
    val id: String,
    val kg: Double?,
    val reps: Int,
    val rir: Double?,
    val warmup: Boolean,
    val completedAt: Long,
    val loadUnit: LoadUnit = LoadUnit.KG,
    val plates: Int? = null,
    val groupId: String? = null,
    /** The load in pounds, for a set logged in [LoadUnit.LB]; [kg] is then null. */
    val lb: Double? = null
) {
    /** The load as entered, in [loadUnit]: kilograms, pounds or a plate count. */
    val loadValue: Double? get() = loadValueOf(loadUnit, kg, lb, plates)
    /** The kilogram or pound amount as entered; null for plates, bodyweight or no load. */
    val amount: Double? get() = amountOf(loadUnit, kg, lb)

    companion object {
        /** A set whose [amount] (kilograms or pounds, as [loadUnit] says) is stored in the field of its own unit. */
        fun of(id: String, amount: Double?, reps: Int, rir: Double?, warmup: Boolean, completedAt: Long,
               loadUnit: LoadUnit = LoadUnit.KG, plates: Int? = null, groupId: String? = null) =
            LoggedSet(id, amount.takeUnless { loadUnit == LoadUnit.LB }, reps, rir, warmup, completedAt, loadUnit, plates,
                groupId, lb = amount.takeIf { loadUnit == LoadUnit.LB })
    }
}

/** A weigh-in exactly as entered: its value and unit, the moment it was taken and that moment's local date. */
@Serializable
data class BodyweightEntry(
    val id: String,
    val at: Long,
    /** ISO local date in the phone's zone when it was logged; it decides the entry's day and week. */
    val date: String,
    val value: Double,
    val unit: MassUnit,
    val note: String = ""
)

@Serializable
enum class LengthUnit(val symbol: String) {
    CM("cm"), IN("in");

    companion object {
        const val CM_PER_IN = 2.54
    }
}

/**
 * Where a circumference is taken. Each landmark is its own site, never merged with another: a waist at the navel and
 * a waist at the WHO midpoint are different measurements.
 */
@Serializable
enum class BodySite(val title: String, val landmark: String) {
    WAIST("Waist", "At the navel, relaxed, after a normal breath out"),
    WAIST_MIDPOINT("Waist (WHO midpoint)", "Midway between the lowest rib and the top of the hip bone"),
    NECK("Neck", "Just below the larynx"),
    SHOULDERS("Shoulders", "Widest point, arms relaxed"),
    CHEST("Chest", "Fullest part, under the armpits"),
    HIPS("Hips", "Widest point of the buttocks"),
    UPPER_ARM("Upper arm", "Widest point, relaxed, same side each time"),
    THIGH("Thigh", "Widest point, same side each time"),
    CALF("Calf", "Widest point, same side each time")
}

/** One circumference exactly as entered, in its own unit. */
@Serializable
data class BodyMeasurement(
    val id: String,
    val at: Long,
    val date: String,
    val site: BodySite,
    val value: Double,
    val unit: LengthUnit,
    val note: String = ""
)

/**
 * One exercise in a workout. A required entry comes from a plan slot and keeps the target it started with. An
 * [optional] entry was added during the session or as a History correction: it has no slot, never blocks
 * completion, and has a target only if the user set an optional goal. Nothing here is ever a fabricated zero.
 */
@Serializable
data class WorkoutExercise(
    val id: String,
    /** The template slot or plan addition this required entry was started from; null for an optional entry. */
    val slotId: String?,
    val exercise: Exercise,
    val targetSets: Int?,
    /** The prescription's baseline; null for the user's own plan additions and optional entries. */
    val baselineSets: Int?,
    val upperSets: Int?,
    val minReps: Int?,
    val maxReps: Int?,
    val targetRir: Int?,
    val restSeconds: Int,
    val perLeg: Boolean = false,
    val notes: String = "",
    val sets: List<LoggedSet> = emptyList(),
    val entryLoadUnit: LoadUnit = LoadUnit.KG,
    val setGroups: List<SetGroup> = emptyList(),
    val optional: Boolean = false,
    /** A required exercise the user chose to skip today. Its target and anything already logged stay as they are. */
    val skipped: Boolean = false,
    /** Timed, distance or cardio efforts of a measured exercise; a rep-based exercise never has any. */
    val efforts: List<Effort> = emptyList(),
    /** Timed goal: each of [targetSets] efforts lasts at least this many seconds. */
    val effortSeconds: Int? = null,
    /** Distance goal: each of [targetSets] efforts covers at least this distance. */
    val effortDistance: Distance? = null,
    /** Cardio goal: the efforts' recorded durations add up to at least this. */
    val totalSeconds: Int? = null,
    /** Cardio goal: the efforts' recorded distances add up to at least this. */
    val totalDistance: Distance? = null
)

@Serializable
data class Workout(
    val id: String,
    /**
     * The weekly-plan occurrence this workout was started from (an original routine's or a scheduled copy's ID), or
     * null for an extra workout outside the plan.
     */
    val templateId: String?,
    val name: String,
    val phase: Phase,
    val startedAt: Long,
    val finishedAt: Long? = null,
    val notes: String = "",
    val exercises: List<WorkoutExercise>,
    /** ISO local date the workout started on, in the phone's zone at that moment; it decides the workout's week. */
    val startDate: String? = null,
    /** Explicit choice to count a partial workout as this routine's one for its week. Never set automatically. */
    val countedForWeek: Boolean = false
) {
    /** Real training outside the plan: it shows in History and progress but never counts on the weekly checklist. */
    val extra: Boolean get() = templateId == null
}

@Serializable
data class RestTimer(
    val id: String,
    val workoutId: String,
    val exerciseId: String,
    val deadline: Long,
    /** A short pause the user asked for inside a rest-pause set, not the rest between sets. */
    val shortPause: Boolean = false
)

@Serializable
data class AppState(
    val schemaVersion: Int = 7,
    val planRevision: Int = 0,
    val revision: Long = 0,
    val phase: Phase? = null,
    val exercises: List<Exercise>,
    val templates: List<WorkoutTemplate>,
    val overrides: List<PlanOverride> = emptyList(),
    val workouts: List<Workout> = emptyList(),
    val activeWorkoutId: String? = null,
    val restTimer: RestTimer? = null,
    val lastBackupAt: Long? = null,
    val lastBackupRevision: Long? = null,
    val unbackedUpSince: Long? = null,
    val catalogRevision: Int = 0,
    val planAdditions: List<PlanAddition> = emptyList(),
    val planRemovals: List<PlanRemoval> = emptyList(),
    val routines: List<CustomRoutine> = emptyList(),
    val placements: List<RoutinePlacement> = emptyList(),
    val scheduleRemovals: List<ScheduleRemoval> = emptyList(),
    val stopwatch: Stopwatch? = null,
    val planOrders: List<PlanOrder> = emptyList(),
    val bodyweights: List<BodyweightEntry> = emptyList(),
    val measurements: List<BodyMeasurement> = emptyList(),
    /** The sites offered on the weekly measuring form, in order. */
    val measurementSites: List<BodySite> = listOf(BodySite.WAIST),
    /** The mass unit new exercises start in, and the one the last "all exercises" switch chose. */
    val liftingUnit: MassUnit = MassUnit.KG
) {
    val activeWorkout: Workout? get() = workouts.find { it.id == activeWorkoutId }
}

sealed interface Action {
    data class SelectPhase(val phase: Phase) : Action
    data class StartWorkout(
        val templateId: String,
        val now: Long,
        val startDate: String = localDateOf(now).toString()
    ) : Action
    /**
     * Starts an extra workout outside the plan. Empty, exercises are added while logging; from library routine
     * [routineId], it is named after it and lists its exercises as optional ones with the routine's goals.
     */
    data class StartExtraWorkout(
        val now: Long,
        val startDate: String = localDateOf(now).toString(),
        val routineId: String? = null
    ) : Action
    /** Creates or replaces library routine [id]. Scheduled copies and workouts never change with it. */
    data class SaveRoutine(val id: String, val name: String, val exercises: List<RoutineExercise>) : Action
    /** Deletes a library routine. Its scheduled copies and every workout stay exactly as they are. */
    data class DeleteRoutine(val id: String) : Action
    /**
     * Puts a copy of library routine [routineId] on [weekday] of [phase]'s week as occurrence [placementId]. When that
     * day already holds a routine, the request must either keep it ([alongside]) or name the one occurrence it is
     * [replacingId]. Repeating the same request changes nothing.
     */
    data class ScheduleRoutine(
        val placementId: String,
        val phase: Phase,
        val weekday: Int,
        val routineId: String,
        val alongside: Boolean = false,
        val replacingId: String? = null
    ) : Action
    /** Moves scheduled copy [placementId] to [weekday] with the same explicit choice; its ID and credit stay. */
    data class MovePlacement(
        val placementId: String,
        val weekday: Int,
        val alongside: Boolean = false,
        val replacingId: String? = null
    ) : Action
    /** Takes one occurrence off [phase]'s week. Workouts keep exactly what they recorded. */
    data class RemoveFromWeeklyPlan(val phase: Phase, val occurrenceId: String) : Action
    /** Puts an original routine back on its own day of [phase]'s week, with the same explicit choice. */
    data class RestoreToWeeklyPlan(
        val phase: Phase,
        val templateId: String,
        val alongside: Boolean = false,
        val replacingId: String? = null
    ) : Action
    /** Saves a scheduled copy's own name and exercises in order; only its future workouts change. */
    data class SavePlacement(val placementId: String, val name: String, val exercises: List<RoutineExercise>) : Action
    /** Explicitly replaces a scheduled copy's name and exercises with its library routine's current ones. */
    data class UpdatePlacementFromRoutine(val placementId: String) : Action
    /**
     * Appends [exerciseId] as optional entry [entryId]: live in the active workout, or as a History correction of a
     * finished one. Never starts a timer and never changes the plan. Repeating the same request changes nothing.
     * [perLeg] is the user's answer when nothing settles how the exercise's reps are counted; null uses the settled way.
     * [sets] is an optional working-set goal for a rep-based exercise, set as it is added.
     */
    data class AddWorkoutExercise(
        val workoutId: String,
        val entryId: String,
        val exerciseId: String,
        val perLeg: Boolean? = null,
        val sets: Int? = null
    ) : Action
    /** Moves an entry of the active workout to [toIndex], for this session only. */
    data class MoveWorkoutExercise(val workoutId: String, val entryId: String, val toIndex: Int) : Action
    /** Removes an optional entry and its records; [confirmedRecords] must equal the sets, stages and efforts it holds now. */
    data class RemoveWorkoutExercise(val workoutId: String, val entryId: String, val confirmedRecords: Int) : Action
    /** Marks a required exercise of the active workout as skipped today, or undoes that. Its target never changes. */
    data class SetSkipped(val workoutId: String, val entryId: String, val skipped: Boolean) : Action
    /**
     * An optional entry's own goal, rest and rep-counting label, for the active workout only. A rep goal's parts are
     * each optional; a timed or distance goal is a count with its per-set minimum, or neither; a cardio goal is a total
     * time, a total distance, both or neither.
     */
    data class SetEntryGoal(
        val workoutId: String,
        val entryId: String,
        val sets: Int?,
        val minReps: Int?,
        val maxReps: Int?,
        val restSeconds: Int,
        val perLeg: Boolean,
        val effortSeconds: Int? = null,
        val effortDistance: Distance? = null,
        val totalSeconds: Int? = null,
        val totalDistance: Distance? = null
    ) : Action
    /** Adds (or edits) the user's own required exercise in future workouts of one routine in one phase. */
    data class SavePlanAddition(
        val id: String,
        val phase: Phase,
        val templateId: String,
        val exerciseId: String,
        val sets: Int?,
        val minReps: Int?,
        val maxReps: Int?,
        val restSeconds: Int,
        val perLeg: Boolean,
        val effortSeconds: Int? = null,
        val effortDistance: Distance? = null,
        val totalSeconds: Int? = null,
        val totalDistance: Distance? = null
    ) : Action
    data class RemovePlanAddition(val id: String) : Action
    /** Adds weigh-in [id], or corrects it in place. Nothing is converted or rounded. */
    data class LogBodyweight(val id: String, val at: Long, val value: Double, val unit: MassUnit, val note: String = "",
        val date: String = localDateOf(at).toString()) : Action
    data class DeleteBodyweight(val id: String) : Action
    /** Adds or corrects each measurement by its id, in one commit, as one weekly measuring session. */
    data class SaveMeasurements(val entries: List<BodyMeasurement>) : Action
    data class DeleteMeasurement(val id: String) : Action
    /** The sites the weekly form asks for, in order; at least one. Recorded measurements are never removed. */
    data class SetMeasurementSites(val sites: List<BodySite>) : Action
    /**
     * Switches every exercise that is remembered in kilograms or pounds to [unit] for its next sets, and makes it the
     * unit new exercises start in. Plate-count exercises keep plates; nothing logged changes or is converted.
     */
    data class SetLiftingUnit(val unit: MassUnit) : Action
    /** Moves one exercise of a routine's future workouts in [phase] to [toIndex]. Started workouts keep their order. */
    data class MovePlannedExercise(val phase: Phase, val routineId: String, val slotId: String, val toIndex: Int) : Action
    /** Leaves an original slot out of [phase]'s future workouts, or restores it. Snapshots never change. */
    data class SetPlanSlotRemoved(val phase: Phase, val slotId: String, val removed: Boolean) : Action
    /** Logs or corrects set [setId]. [kg] is the load amount in [loadUnit]: kilograms, or pounds for [LoadUnit.LB]. */
    data class SaveSet(
        val workoutId: String,
        val exerciseId: String,
        val setId: String,
        val kg: Double?,
        val reps: Int,
        val rir: Double?,
        val warmup: Boolean,
        val now: Long,
        val loadUnit: LoadUnit = LoadUnit.KG,
        val plates: Int? = null
    ) : Action
    /**
     * Live logging of one performed stage: starts group [groupId] with [method] as its first stage, or appends to it
     * while it is open in the active workout. Never edits an existing stage and never starts the between-set rest.
     */
    data class LogStage(
        val workoutId: String,
        val exerciseId: String,
        val groupId: String,
        val method: SetMethod,
        val setId: String,
        val kg: Double?,
        val reps: Int,
        val rir: Double?,
        val now: Long,
        val loadUnit: LoadUnit = LoadUnit.KG,
        val plates: Int? = null
    ) : Action
    /**
     * Live logging of a drop right after a working set: the last normal working set becomes the first stage of drop
     * set [groupId] (finished), or the last finished drop set gets one more stage. It stays one working set, and the
     * between-set rest starts again from here. Repeating the same request changes nothing.
     */
    data class LogDrop(
        val workoutId: String,
        val exerciseId: String,
        val groupId: String,
        val setId: String,
        val kg: Double?,
        val reps: Int,
        val rir: Double?,
        val now: Long,
        val loadUnit: LoadUnit = LoadUnit.KG,
        val plates: Int? = null
    ) : Action
    /** Closes an open group; the between-set rest starts once, here. Repeating it changes nothing. */
    data class FinishGroup(val workoutId: String, val exerciseId: String, val groupId: String, val now: Long) : Action
    /** Correction: appends a missed stage after the group's last stage, open or finished. Starts no timer. */
    data class AddStage(
        val workoutId: String,
        val exerciseId: String,
        val groupId: String,
        val setId: String,
        val kg: Double?,
        val reps: Int,
        val rir: Double?,
        val now: Long,
        val loadUnit: LoadUnit = LoadUnit.KG,
        val plates: Int? = null
    ) : Action
    data class SetGroupMethod(val workoutId: String, val exerciseId: String, val groupId: String, val method: SetMethod) : Action
    data class DeleteGroup(val workoutId: String, val exerciseId: String, val groupId: String) : Action
    /**
     * Correction: a drop set logged as a set of its own (the old "Drop set" type) becomes drops of the set right before
     * it, so it stops counting as a working set. Its stages keep their values, order and times; nothing else changes.
     */
    data class JoinDropsToSet(val workoutId: String, val exerciseId: String, val groupId: String) : Action
    /** An explicitly requested short pause inside an open rest-pause set; nothing about it is recorded in history. */
    data class StartShortPause(val workoutId: String, val exerciseId: String, val seconds: Int, val now: Long) : Action
    /**
     * Logs a new timed, distance or cardio effort [effortId] exactly as entered, or corrects an existing one (live or
     * in History). Logging the stopwatch's effort (its ID) needs it stopped, at the [watchStep] whose time the user
     * checked, and clears it. A request naming a [watchStep] only ever logs that stopwatch's effort: once it is
     * discarded, or logged and later deleted, nothing is logged in its place. A new timed or distance effort in the
     * active workout starts the rest; cardio never does. Repeating the same request changes nothing.
     */
    data class SaveEffort(
        val workoutId: String,
        val exerciseId: String,
        val effortId: String,
        val durationSeconds: Int?,
        val distance: Distance?,
        val kg: Double?,
        val now: Long,
        val loadUnit: LoadUnit = LoadUnit.KG,
        val plates: Int? = null,
        val watchStep: Int? = null
    ) : Action
    data class DeleteEffort(val workoutId: String, val exerciseId: String, val effortId: String) : Action
    /** Starts timing effort [stopwatchId] on a measured exercise of the active workout; any rest is over. */
    data class StartStopwatch(
        val stopwatchId: String,
        val workoutId: String,
        val exerciseId: String,
        val clock: Monotonic,
        val now: Long
    ) : Action
    /**
     * Pause, Resume, Stop, Discard and Recover name the stopwatch and the [Stopwatch.step] they were made for. For any
     * other stopwatch, or another step of this one, the request is late or repeated: it changes nothing, so it never
     * touches a newer stopwatch or a newer state of the same one. A Discard says so instead, as the user confirmed
     * discarding what is no longer there.
     */
    data class PauseStopwatch(val stopwatchId: String, val step: Int, val clock: Monotonic) : Action
    data class ResumeStopwatch(val stopwatchId: String, val step: Int, val clock: Monotonic) : Action
    /** Ends timing: the effort becomes an unsaved review draft. Nothing is logged and no rest starts. */
    data class StopStopwatch(val stopwatchId: String, val step: Int, val clock: Monotonic) : Action
    data class DiscardStopwatch(val stopwatchId: String, val step: Int) : Action
    /**
     * Re-checks running stopwatch [stopwatchId] against the current clock; one that can't be trusted (the phone
     * restarted, say) stops and needs its time confirmed. Any other stopwatch or step is left alone.
     */
    data class RecoverStopwatch(val stopwatchId: String, val step: Int, val clock: Monotonic) : Action
    /** An explicitly requested rest after a cardio effort, for intervals; cardio never starts one by itself. */
    data class StartRest(val timerId: String, val workoutId: String, val exerciseId: String, val now: Long) : Action
    data class SetWeekCredit(val workoutId: String, val counted: Boolean) : Action
    data class SetLoadUnit(val workoutId: String, val exerciseId: String, val unit: LoadUnit) : Action
    data class SetExerciseLoadUnit(val exerciseId: String, val unit: LoadUnit) : Action
    data class DeleteSet(val workoutId: String, val exerciseId: String, val setId: String) : Action
    data class WorkoutNote(val workoutId: String, val note: String) : Action
    data class ExerciseNote(val workoutId: String, val exerciseId: String, val note: String) : Action
    data class EditPlan(
        val phase: Phase,
        val slotId: String,
        val exerciseId: String,
        val sets: Int,
        val restSeconds: Int,
        val allowAboveCap: Boolean = false
    ) : Action
    data class EditToday(
        val exerciseId: String,
        val replacementId: String,
        /** Working, timed or distance sets; null for a cardio exercise, which has a total instead. */
        val sets: Int?,
        val restSeconds: Int,
        val saveToPlan: Boolean = false,
        val allowAboveCap: Boolean = false
    ) : Action
    data class AddExercise(val exercise: Exercise) : Action
    /**
     * An open drop or rest-pause set is only closed (never deleted) when [closeOpenGroups] confirms it, and the
     * stopwatch's unsaved time is only dropped when [discardStopwatch] confirms that.
     */
    data class FinishWorkout(
        val now: Long,
        val closeOpenGroups: Boolean = false,
        val countForWeek: Boolean = false,
        val discardStopwatch: Boolean = false
    ) : Action
    data object DiscardWorkout : Action
    data class AdjustRest(val timerId: String, val deadline: Long) : Action
    data object SkipRest : Action
    data class MarkBackup(val revision: Long, val now: Long) : Action
}

data class BackupDocument(val revision: Long, val json: String)
/**
 * [dataUpdated] means the file is in an older format and is updated as it is restored; [contentUpdated] narrows that to
 * cases where library exercises or workout start dates are actually added, rather than only the format changing, and
 * [startDatesAdded] to the files whose workouts are given the start date the weekly checklist uses.
 */
data class BackupPreview(
    val state: AppState,
    val source: String,
    val futurePlanUpdated: Boolean = false,
    val dataUpdated: Boolean = false,
    val contentUpdated: Boolean = false,
    val startDatesAdded: Boolean = false
)

interface AppRepository {
    val state: StateFlow<AppState>
    suspend fun execute(action: Action)
    suspend fun exportBackup(): BackupDocument
    fun previewBackup(json: String): BackupPreview
    suspend fun restoreBackup(preview: BackupPreview, expectedRevision: Long)
    suspend fun consumeRestTimer(timerId: String, now: Long): Boolean
}

fun AppState.availableTemplates(phase: Phase = requireNotNull(this.phase)): List<WorkoutTemplate> =
    templates.filter { if (phase.fourDay) !it.establishedOnly else !it.introOnly }

fun AppState.scheduledTemplate(weekday: Int): WorkoutTemplate? =
    phase?.let { availableTemplates(it).find { template -> template.weekday == weekday } }

fun AppState.canSetLoadUnit(workoutId: String, exerciseId: String): Boolean =
    loadUnitChoices(workoutId, exerciseId).size > 1

/** The units a load of this exercise can be entered in: kg and lb, and plates per stack on a matching machine. */
fun Exercise.loadUnitOptions(): List<LoadUnit> = if (!takesLoad) emptyList()
    else listOf(LoadUnit.KG, LoadUnit.LB) + if (supportsPlates) listOf(LoadUnit.PLATES) else emptyList()

/** A load is recorded for sets of reps and for timed or distance work that carries one; never for bodyweight or cardio. */
val Exercise.takesLoad: Boolean
    get() = loading != LoadingType.BODYWEIGHT && (measure == Measure.REPS || measure.recordsLoad)

/**
 * The units the next entry of [exerciseId] can switch between in the active workout: kilograms and pounds for any
 * loaded exercise, and plates per stack too where the library confirms a matching cable or stack machine.
 */
fun AppState.loadUnitChoices(workoutId: String, exerciseId: String): List<LoadUnit> {
    val workout = activeWorkout?.takeIf { it.id == workoutId && it.finishedAt == null } ?: return emptyList()
    val entry = workout.exercises.find { it.id == exerciseId } ?: return emptyList()
    if (!entry.exercise.takesLoad) return emptyList()
    return listOf(LoadUnit.KG, LoadUnit.LB) +
        if (loadUnitDefinition(workoutId, exerciseId) != null) listOf(LoadUnit.PLATES) else emptyList()
}

internal fun AppState.loadUnitDefinition(workoutId: String, exerciseId: String): Exercise? {
    val workout = activeWorkout?.takeIf { it.id == workoutId && it.finishedAt == null } ?: return null
    val snapshot = workout.exercises.find { it.id == exerciseId }?.exercise ?: return null
    val definition = exercises.find { it.id == snapshot.id } ?: return null
    if (!definition.supportsPlates || definition.loading != snapshot.loading ||
        definition.perDumbbell != snapshot.perDumbbell) return null
    if (snapshot.supportsPlates) return snapshot
    // Explicit equipment confirmation is additive, never a substitution or a load conversion.
    return if (snapshot.equipment == Equipment.UNKNOWN && snapshot.name == definition.name &&
        snapshot.custom == definition.custom) snapshot.copy(equipment = definition.equipment) else null
}

fun AppState.targetFor(slot: PlanSlot, phase: Phase): PlanOverride =
    overrides.find { it.phase == phase && it.slotId == slot.id } ?: PlanOverride(
        phase, slot.id, slot.exerciseId,
        when (phase) {
            Phase.INTRO_ONE -> 1
            Phase.INTRO_TWO, Phase.FIVE_TWO -> 2
            Phase.BASELINE -> slot.baselineSets
        },
        slot.restSeconds
    )

/** The latest finished entry comparable with [exercise] in [loadUnit]; with [perLeg], only reps counted the same way. */
fun AppState.previousComparable(
    exercise: Exercise,
    excludingWorkoutId: String,
    loadUnit: LoadUnit = LoadUnit.KG,
    perLeg: Boolean? = null
): WorkoutExercise? =
    workouts.asSequence()
        .filter { it.id != excludingWorkoutId && it.finishedAt != null }
        .sortedByDescending { it.startedAt }
        .flatMap { it.exercises.asSequence() }
        .firstOrNull {
            it.exercise.id == exercise.id && it.exercise.loading == exercise.loading &&
                it.exercise.perDumbbell == exercise.perDumbbell && (perLeg == null || it.perLeg == perLeg) &&
                it.sets.any { set -> !set.warmup && set.loadUnit == loadUnit }
        }?.let { it.copy(sets = it.sets.filter { set -> set.loadUnit == loadUnit }) }

fun AppState.backupDue(now: Long): Boolean {
    val since = unbackedUpSince ?: lastBackupAt ?: workouts.minOfOrNull { it.startedAt } ?: return false
    return revision > (lastBackupRevision ?: -1) && now >= since &&
        now - since >= 7L * 24 * 60 * 60 * 1000
}
