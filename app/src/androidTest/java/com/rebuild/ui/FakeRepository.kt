package com.rebuild.ui

import com.rebuild.domain.Action
import com.rebuild.domain.AppRepository
import com.rebuild.domain.AppState
import com.rebuild.domain.BackupDocument
import com.rebuild.domain.BackupPreview
import com.rebuild.domain.Exercise
import com.rebuild.domain.LoggedSet
import com.rebuild.domain.Phase
import com.rebuild.domain.PlanSlot
import com.rebuild.domain.Workout
import com.rebuild.domain.WorkoutExercise
import com.rebuild.domain.WorkoutTemplate
import com.rebuild.domain.plannedExercises
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Collections

/** Tuesday 22 Sep 2026, 10:00 local. */
val FIXED_NOW: Long = LocalDateTime.of(2026, 9, 22, 10, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

val BENCH = Exercise("bench", "Bench press")
val DB_PRESS = Exercise("db-press", "Dumbbell press", perDumbbell = true)

fun seedState(phase: Phase? = null, workouts: List<Workout> = emptyList()) = AppState(
    phase = phase,
    exercises = listOf(BENCH, DB_PRESS),
    templates = listOf(
        WorkoutTemplate(
            id = "upper-a", name = "Upper A", weekday = 2,
            slots = listOf(
                PlanSlot("s-bench", "bench", listOf("db-press"), baselineSets = 3, upperSets = 4,
                    minReps = 6, maxReps = 10, restSeconds = 180, muscle = "Chest"),
                PlanSlot("s-fly", "db-press", baselineSets = 2, upperSets = 3,
                    minReps = 10, maxReps = 15, restSeconds = 90, muscle = "Chest")
            )
        ),
        WorkoutTemplate(
            id = "lower-a", name = "Lower A", weekday = 4,
            slots = listOf(
                PlanSlot("s-db", "db-press", baselineSets = 2, upperSets = 3,
                    minReps = 8, maxReps = 12, restSeconds = 90, muscle = "Chest")
            )
        )
    ),
    workouts = workouts
)

class FakeRepository(initial: AppState) : AppRepository {
    private val flow = MutableStateFlow(initial)
    override val state: StateFlow<AppState> = flow
    val actions: MutableList<Action> = Collections.synchronizedList(mutableListOf())

    /** When set, execute() suspends until completed, to simulate a slow save. */
    @Volatile var gate: CompletableDeferred<Unit>? = null

    override suspend fun execute(action: Action) {
        actions += action
        gate?.await()
        val s = flow.value
        flow.value = when (action) {
            is Action.SelectPhase -> s.copy(phase = action.phase)
            is Action.StartWorkout -> {
                check(s.activeWorkoutId == null) { "A workout is already in progress." }
                val phase = requireNotNull(s.phase)
                val template = s.templates.first { it.id == action.templateId }
                // Like the real start: the phase's planned exercises (kept slots, then the user's additions).
                val workout = Workout(
                    id = "w-${action.now}", templateId = template.id, name = template.name, phase = phase,
                    startedAt = action.now, startDate = action.startDate,
                    exercises = s.plannedExercises(template, phase).map { p ->
                        val definition = s.exercises.first { it.id == p.exerciseId }
                        WorkoutExercise(
                            id = "we-${p.slotId}", slotId = p.slotId, exercise = definition,
                            targetSets = p.sets, baselineSets = p.baselineSets, upperSets = p.upperSets,
                            minReps = p.minReps, maxReps = p.maxReps, targetRir = phase.targetRir,
                            restSeconds = p.restSeconds, perLeg = p.perLeg,
                            entryLoadUnit = definition.preferredLoadUnit
                        )
                    }
                )
                s.copy(workouts = s.workouts + workout, activeWorkoutId = workout.id)
            }
            is Action.SaveSet -> s.mapExercise(action.workoutId, action.exerciseId) { we ->
                val existing = we.sets.indexOfFirst { it.id == action.setId }
                // A corrected stage stays in its drop or rest-pause set.
                val set = LoggedSet(action.setId, action.kg, action.reps, action.rir, action.warmup, action.now,
                    loadUnit = action.loadUnit, plates = action.plates, groupId = we.sets.getOrNull(existing)?.groupId)
                we.copy(sets = if (existing >= 0) we.sets.toMutableList().also { it[existing] = set } else we.sets + set)
            }
            // Grouped sets, weekly credit, finishing and flexible workouts use the real reducer, so UI tests exercise
            // its rules (replays, unit and stage guards, closing open sets, credit only with real working sets,
            // optional and skipped exercises, plan additions and removals).
            is Action.DeleteSet, is Action.FinishWorkout, is Action.LogStage, is Action.LogDrop, is Action.FinishGroup, is Action.AddStage,
            is Action.SetGroupMethod, is Action.DeleteGroup, is Action.JoinDropsToSet, is Action.StartShortPause, is Action.SetWeekCredit,
            is Action.StartExtraWorkout, is Action.AddWorkoutExercise, is Action.MoveWorkoutExercise,
            is Action.RemoveWorkoutExercise, is Action.SetSkipped, is Action.SetEntryGoal, is Action.SavePlanAddition,
            is Action.RemovePlanAddition, is Action.SetPlanSlotRemoved, is Action.MovePlannedExercise ->
                com.rebuild.data.StateChanges.apply(s, action)
            Action.DiscardWorkout -> s.copy(workouts = s.workouts.filterNot { it.id == s.activeWorkoutId }, activeWorkoutId = null)
            else -> s
        }.let { it.copy(revision = it.revision + 1) }
    }

    private fun AppState.mapExercise(workoutId: String, exerciseId: String, f: (WorkoutExercise) -> WorkoutExercise) =
        copy(workouts = workouts.map { w ->
            if (w.id != workoutId) w else w.copy(exercises = w.exercises.map { if (it.id == exerciseId) f(it) else it })
        })

    /** Simulates a restore replacing the whole state underneath the UI. */
    fun replace(state: AppState) { flow.value = state }

    override suspend fun exportBackup() = BackupDocument(flow.value.revision, "{}")
    override fun previewBackup(json: String): BackupPreview = BackupPreview(flow.value, "test")
    override suspend fun restoreBackup(preview: BackupPreview, expectedRevision: Long) = Unit
    override suspend fun consumeRestTimer(timerId: String, now: Long) = false
}

class FakePlatform(status: PlatformStatus = PlatformStatus()) : PlatformActions {
    override val status = MutableStateFlow(status)
    val calls = mutableListOf<String>()
    override fun requestNotifications() { calls += "notifications" }
    override fun requestExactAlarms() { calls += "exactAlarms" }
    override fun openBatterySettings() { calls += "battery" }
    override fun exportBackup() { calls += "export" }
    override fun shareBackup() { calls += "share" }
    override fun chooseRestore() { calls += "restore" }
}
