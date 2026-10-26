package com.rebuild.data

import com.rebuild.domain.*
import com.rebuild.data.SnapshotCodec.ensure
import java.util.UUID

internal object StateChanges {
    private fun newId() = UUID.randomUUID().toString()

    fun apply(state: AppState, action: Action): AppState = when (action) {
        is Action.SelectPhase -> {
            ensure(state.activeWorkoutId == null || state.phase == action.phase,
                "Finish or discard your active workout before changing phase.")
            state.copy(phase = action.phase)
        }
        is Action.StartWorkout -> {
            ensure(state.activeWorkoutId == null, "A workout is already active. Resume it, finish it, or discard it first.")
            val phase = state.phase ?: throw RepositoryException("Choose a training phase before starting a workout.")
            // Only an occurrence on this phase's week now; a removed one is never started or brought back.
            val template = state.scheduledRoutine(phase, action.templateId)
                ?: throw RepositoryException("This workout is not available in your selected phase.")
            SnapshotCodec.timestamp(action.now)
            SnapshotCodec.startDate(action.startDate, action.now)
            // The workout copies what this occurrence asks for now; later plan edits never reach it.
            val workout = Workout(newId(), template.id, template.name, phase, action.now,
                startDate = action.startDate,
                exercises = state.plannedExercises(template, phase).map { planned ->
                    val definition = library(state, planned.exerciseId)
                    WorkoutExercise(newId(), planned.slotId, definition.copy(),
                        planned.sets, planned.baselineSets, planned.upperSets, planned.minReps, planned.maxReps,
                        if (definition.measured) null else phase.targetRir, planned.restSeconds, planned.perLeg,
                        entryLoadUnit = definition.preferredLoadUnit).withGoal(planned.goal)
                })
            state.copy(workouts = state.workouts + workout, activeWorkoutId = workout.id, restTimer = null)
        }
        is Action.StartExtraWorkout -> {
            ensure(state.activeWorkoutId == null, "A workout is already active. Resume it, finish it, or discard it first.")
            val phase = state.phase ?: throw RepositoryException("Choose a training phase before starting a workout.")
            SnapshotCodec.timestamp(action.now)
            SnapshotCodec.startDate(action.startDate, action.now)
            val routine = action.routineId?.let { id ->
                state.routines.find { it.id == id } ?: throw RepositoryException("This routine no longer exists.")
            }
            // A routine started as an extra session lists its exercises as optional ones with its goals: real
            // training outside the plan that never counts for the week.
            val workout = Workout(newId(), null, routine?.name ?: EXTRA_WORKOUT_NAME, phase, action.now,
                startDate = action.startDate, exercises = routine?.exercises.orEmpty().map { goal ->
                    val definition = library(state, goal.exerciseId)
                    WorkoutExercise(newId(), null, definition.copy(), goal.sets, null, null, goal.minReps,
                        goal.maxReps, null, goal.restSeconds, goal.perLeg,
                        entryLoadUnit = definition.preferredLoadUnit, optional = true).withGoal(goal.measuredGoal)
                })
            state.copy(workouts = state.workouts + workout, activeWorkoutId = workout.id, restTimer = null)
        }
        is Action.SaveRoutine -> saveRoutine(state, action)
        is Action.DeleteRoutine -> if (state.routines.none { it.id == action.id }) state else state.copy(
            routines = state.routines.filterNot { it.id == action.id },
            // Copies keep everything; only the link that offered “Update from library” goes.
            placements = state.placements.map { if (it.routineId == action.id) it.copy(routineId = null) else it })
        is Action.ScheduleRoutine -> scheduleRoutine(state, action)
        is Action.MovePlacement -> {
            ensure(action.weekday in 1..7, "Choose a day from Monday to Sunday.")
            val placement = livePlacement(state, action.placementId)
            // Already there: a repeated request never takes another routine off that day.
            if (placement.weekday == action.weekday) state else settleDay(state, placement.phase, action.weekday,
                action.alongside, action.replacingId, movingId = placement.id).let { settled ->
                settled.copy(placements = settled.placements.map {
                    if (it.id == placement.id) it.copy(weekday = action.weekday) else it
                })
            }
        }
        is Action.RemoveFromWeeklyPlan -> {
            val week = state.scheduledRoutines(action.phase)
            val occurrence = week.find { it.id == action.occurrenceId }
            if (occurrence == null) state else {
                ensure(week.size > 1, "${occurrence.name} is the only routine on this phase's week. Add or restore " +
                    "another one first, or replace it.")
                takeOff(state, action.phase, occurrence)
            }
        }
        is Action.RestoreToWeeklyPlan -> {
            val template = state.availableTemplates(action.phase).find { it.id == action.templateId }
                ?: throw RepositoryException("This routine isn't part of that phase.")
            if (!state.scheduleRemoved(action.phase, template.id)) state else settleDay(state, action.phase,
                template.weekday, action.alongside, action.replacingId, movingId = null).let { settled ->
                settled.copy(scheduleRemovals = settled.scheduleRemovals.filterNot {
                    it.phase == action.phase && it.templateId == template.id
                })
            }
        }
        is Action.SavePlacement -> {
            val placement = livePlacement(state, action.placementId)
            replaceCopy(state, placement, action.name, action.exercises)
        }
        is Action.UpdatePlacementFromRoutine -> {
            val placement = livePlacement(state, action.placementId)
            val routine = state.routines.find { it.id == placement.routineId }
                ?: throw RepositoryException("This copy is no longer linked to a routine in your library.")
            replaceCopy(state, placement, routine.name, routine.exercises)
        }
        is Action.AddWorkoutExercise -> addWorkoutExercise(state, action)
        is Action.MoveWorkoutExercise -> {
            val workout = liveWorkout(state, action.workoutId, "Reorder exercises in the active workout.")
            val from = workout.exercises.indexOfFirst { it.id == action.entryId }
            ensure(from >= 0, "This exercise is not part of the workout.")
            ensure(action.toIndex in workout.exercises.indices, "That position is outside this workout.")
            // Only this session's order changes; the plan keeps its own order.
            if (from == action.toIndex) state else editWorkout(state, workout.id) {
                it.copy(exercises = it.exercises.toMutableList().apply { add(action.toIndex, removeAt(from)) })
            }
        }
        is Action.RemoveWorkoutExercise -> removeWorkoutExercise(state, action)
        is Action.SetSkipped -> setSkipped(state, action)
        is Action.SetEntryGoal -> setEntryGoal(state, action)
        is Action.SavePlanAddition -> savePlanAddition(state, action)
        is Action.RemovePlanAddition -> {
            val addition = state.planAdditions.find { it.id == action.id }
            if (addition == null) state else {
                val template: ScheduledRoutine = state.templates.find { it.id == addition.templateId }
                    ?: state.placements.find { it.id == addition.templateId }
                    ?: throw RepositoryException("This exercise's routine is no longer in your plan.")
                ensure(state.plannedExercises(template, addition.phase).size > 1,
                    "${template.name} needs at least one exercise in this phase. Add or restore another one first.")
                state.copy(planAdditions = state.planAdditions.filterNot { it.id == addition.id })
            }
        }
        is Action.SetPlanSlotRemoved -> setPlanSlotRemoved(state, action)
        is Action.LogBodyweight -> {
            SnapshotCodec.id(action.id)
            SnapshotCodec.timestamp(action.at)
            val entry = BodyweightEntry(action.id, action.at, action.date, action.value, action.unit,
                limitUserText(action.note, SnapshotCodec.MAX_NOTE_LENGTH))
            SnapshotCodec.validateBodyweight(entry)
            val others = state.bodyweights.filterNot { it.id == action.id }
            ensure(others.size < SnapshotCodec.MAX_BODY_RECORDS, "There are too many weigh-ins saved.")
            state.copy(bodyweights = (others + entry).sortedBy { it.at })
        }
        is Action.DeleteBodyweight -> state.copy(bodyweights = state.bodyweights.filterNot { it.id == action.id })
        is Action.SaveMeasurements -> {
            ensure(action.entries.isNotEmpty(), "Enter at least one measurement.")
            ensure(action.entries.map { it.id }.distinct().size == action.entries.size,
                "Each measurement needs its own ID. Please try again.")
            action.entries.forEach(SnapshotCodec::validateMeasurement)
            val ids = action.entries.map { it.id }.toSet()
            val others = state.measurements.filterNot { it.id in ids }
            ensure(others.size + action.entries.size <= SnapshotCodec.MAX_BODY_RECORDS,
                "There are too many measurements saved.")
            state.copy(measurements = (others + action.entries).sortedWith(compareBy({ it.at }, { it.site.ordinal })))
        }
        is Action.DeleteMeasurement -> state.copy(measurements = state.measurements.filterNot { it.id == action.id })
        is Action.SetMeasurementSites -> {
            ensure(action.sites.isNotEmpty(), "Keep at least one site to measure.")
            state.copy(measurementSites = action.sites.distinct())
        }
        is Action.SetLiftingUnit -> state.copy(
            liftingUnit = action.unit,
            exercises = state.exercises.map {
                if (it.measure != Measure.CARDIO && it.loading != LoadingType.BODYWEIGHT &&
                    it.preferredLoadUnit != LoadUnit.PLATES) it.copy(preferredLoadUnit = action.unit.loadUnit) else it
            }
        )
        is Action.MovePlannedExercise -> {
            val routine = state.scheduledRoutines(action.phase).find { it.id == action.routineId }
                ?: throw RepositoryException("This routine is no longer on this phase's week.")
            val ids = state.plannedExercises(routine, action.phase).map { it.slotId }.toMutableList()
            val from = ids.indexOf(action.slotId)
            ensure(from >= 0, "This exercise is no longer in ${routine.name}.")
            val to = action.toIndex.coerceIn(0, ids.lastIndex)
            if (from == to) state else {
                ids.add(to, ids.removeAt(from))
                state.copy(planOrders = state.planOrders.filterNot {
                    it.phase == action.phase && it.routineId == routine.id
                } + PlanOrder(action.phase, routine.id, ids))
            }
        }
        is Action.SaveSet -> {
            SnapshotCodec.id(action.setId)
            SnapshotCodec.timestamp(action.now)
            var timer = state.restTimer
            val changed = editExercise(state, action.workoutId, action.exerciseId) { exercise ->
                SnapshotCodec.validateSet(exercise.exercise, action.kg, action.reps, action.rir,
                    action.loadUnit, action.plates)
                val previous = exercise.sets.find { it.id == action.setId }
                if (previous == null) {
                    ensure(!state.recordIdUsed(action.setId),
                        "This set ID is already used by another exercise. Please try again with a new set.")
                    ensure(!exercise.skipped || state.activeWorkoutId != action.workoutId,
                        "${exercise.exercise.name} is skipped today. Undo skip to log it.")
                    state.workouts.find { it.id == action.workoutId }?.let(::requireNoOpenGroup)
                    requireNotTiming(state, action.workoutId)
                } else if (previous.groupId != null) {
                    ensure(!action.warmup, "A stage of a drop or rest-pause set cannot become a warm-up. " +
                        "Delete the stage instead if it was not part of the set.")
                }
                val set = LoggedSet.of(action.setId, action.kg, action.reps, action.rir, action.warmup,
                    previous?.completedAt ?: action.now, action.loadUnit, action.plates, previous?.groupId)
                if (previous == null && !set.warmup && state.activeWorkoutId == action.workoutId) {
                    timer = RestTimer(newId(), action.workoutId, action.exerciseId,
                        action.now + exercise.restSeconds * 1000L)
                }
                exercise.copy(sets = if (previous == null) exercise.sets + set else
                    exercise.sets.map { if (it.id == set.id) set else it })
            }
            changed.copy(restTimer = timer)
        }
        is Action.LogStage -> logStage(state, action)
        is Action.LogDrop -> logDrop(state, action)
        is Action.FinishGroup -> {
            SnapshotCodec.timestamp(action.now)
            val workout = activeWorkout(state, action.workoutId)
            val exercise = workout.exercises.find { it.id == action.exerciseId }
                ?: throw RepositoryException("This exercise is not part of the workout.")
            val group = exercise.setGroups.find { it.id == action.groupId }
                ?: throw RepositoryException("This drop or rest-pause set no longer exists.")
            // Repeating Finish set must not restart the rest or move the finish time.
            if (group.finishedAt != null) state else editExercise(state, workout.id, exercise.id) { current ->
                current.copy(setGroups = current.setGroups.map {
                    if (it.id == group.id) it.copy(finishedAt = action.now) else it
                })
            }.copy(restTimer = RestTimer(newId(), workout.id, exercise.id, action.now + exercise.restSeconds * 1000L))
        }
        is Action.AddStage -> {
            SnapshotCodec.id(action.setId)
            SnapshotCodec.timestamp(action.now)
            val workout = state.workouts.find { it.id == action.workoutId }
                ?: throw RepositoryException("This workout no longer exists.")
            val exercise = workout.exercises.find { it.id == action.exerciseId }
                ?: throw RepositoryException("This exercise is not part of the workout.")
            val group = exercise.setGroups.find { it.id == action.groupId }
                ?: throw RepositoryException("This drop or rest-pause set no longer exists.")
            SnapshotCodec.validateSet(exercise.exercise, action.kg, action.reps, action.rir, action.loadUnit, action.plates)
            val stage = LoggedSet.of(action.setId, action.kg, action.reps, action.rir, false, action.now,
                action.loadUnit, action.plates, group.id)
            if (isReplay(state, workout.id, exercise, stage)) state
            else editExercise(state, workout.id, exercise.id) { appendStage(state, it, group, stage) }
        }
        is Action.SetGroupMethod -> {
            var clearPause = false
            val changed = editExercise(state, action.workoutId, action.exerciseId) { exercise ->
                val group = exercise.setGroups.find { it.id == action.groupId }
                    ?: throw RepositoryException("This drop or rest-pause set no longer exists.")
                clearPause = group.finishedAt == null && action.method != SetMethod.REST_PAUSE
                exercise.copy(setGroups = exercise.setGroups.map {
                    if (it.id == group.id) it.copy(method = action.method) else it
                })
            }
            if (clearPause) clearShortPause(changed, action.exerciseId) else changed
        }
        is Action.DeleteGroup -> {
            val changed = editExercise(state, action.workoutId, action.exerciseId) { exercise ->
                ensure(exercise.setGroups.any { it.id == action.groupId }, "This drop or rest-pause set no longer exists.")
                exercise.copy(sets = exercise.sets.filterNot { it.groupId == action.groupId },
                    setGroups = exercise.setGroups.filterNot { it.id == action.groupId })
            }
            clearShortPause(changed, action.exerciseId)
        }
        is Action.JoinDropsToSet -> editExercise(state, action.workoutId, action.exerciseId) { exercise ->
            joinDropsToSet(exercise, action.groupId)
        }
        is Action.StartShortPause -> {
            SnapshotCodec.timestamp(action.now)
            SnapshotCodec.rest(action.seconds)
            val workout = activeWorkout(state, action.workoutId)
            val exercise = workout.exercises.find { it.id == action.exerciseId }
                ?: throw RepositoryException("This exercise is not part of the workout.")
            ensure(exercise.openGroup()?.method == SetMethod.REST_PAUSE,
                "A short pause is only available while a rest-pause set is in progress.")
            state.copy(restTimer = RestTimer(newId(), workout.id, exercise.id,
                action.now + action.seconds * 1000L, shortPause = true))
        }
        is Action.SaveEffort -> saveEffort(state, action)
        is Action.DeleteEffort -> editExercise(state, action.workoutId, action.exerciseId) { exercise ->
            exercise.copy(efforts = exercise.efforts.filterNot { it.id == action.effortId })
        }
        is Action.StartStopwatch -> startStopwatch(state, action)
        is Action.PauseStopwatch -> {
            val watch = state.watchAt(action.stopwatchId, action.step)?.takeIf { it.running }
            if (watch == null) state else when (val time = watch.timeAt(action.clock)) {
                is StopwatchTime.Measured -> state.copy(stopwatch = watch.copy(elapsedMs = time.ms,
                    runningSince = null, boot = null, session = null, step = watch.nextStep()))
                StopwatchTime.Stale -> state
                StopwatchTime.Untrusted -> state.copy(stopwatch = untrusted(watch))
            }
        }
        is Action.ResumeStopwatch -> {
            val watch = state.watchAt(action.stopwatchId, action.step)?.takeIf { !it.running }
            if (watch == null) state else {
                ensure(!watch.needsConfirmation, "This effort's time couldn't be measured. Enter its duration and " +
                    "log it, or discard it.")
                state.activeWorkout?.let(::requireNoOpenGroup)
                checkClock(action.clock)
                state.copy(stopwatch = watch.copy(runningSince = action.clock.elapsedMs, boot = action.clock.boot,
                    session = action.clock.session, stopped = false, step = watch.nextStep()), restTimer = null)
            }
        }
        is Action.StopStopwatch -> {
            val watch = state.watchAt(action.stopwatchId, action.step)?.takeIf { !it.stopped }
            // Stop always ends timing: an untrusted reading still stops, with the time left for the user to enter.
            if (watch == null) state
            else if (!watch.running) state.copy(stopwatch = watch.copy(stopped = true, step = watch.nextStep()))
            else when (val time = watch.timeAt(action.clock)) {
                is StopwatchTime.Measured -> state.copy(stopwatch = watch.copy(elapsedMs = time.ms,
                    runningSince = null, boot = null, session = null, stopped = true, step = watch.nextStep()))
                StopwatchTime.Stale -> state
                StopwatchTime.Untrusted -> state.copy(stopwatch = untrusted(watch))
            }
        }
        is Action.DiscardStopwatch -> {
            // Already logged or discarded: a repeated request changes nothing.
            val watch = state.stopwatch?.takeIf { it.id == action.stopwatchId }
            if (watch == null) state else {
                ensure(watch.step == action.step, "This effort changed since you chose to discard it. Check it, " +
                    "then discard it again if you still want to.")
                state.copy(stopwatch = null)
            }
        }
        is Action.RecoverStopwatch -> {
            val watch = state.watchAt(action.stopwatchId, action.step)?.takeIf { it.running }
            if (watch != null && watch.timeAt(action.clock) == StopwatchTime.Untrusted)
                state.copy(stopwatch = untrusted(watch)) else state
        }
        is Action.StartRest -> {
            SnapshotCodec.id(action.timerId)
            SnapshotCodec.timestamp(action.now)
            if (state.restTimer?.id == action.timerId) state else {
                val workout = liveWorkout(state, action.workoutId, "Rest is timed in the active workout.")
                val entry = workout.exercises.find { it.id == action.exerciseId }
                    ?: throw RepositoryException("This exercise is not part of the workout.")
                ensure(entry.exercise.measure == Measure.CARDIO,
                    "Rest starts by itself when you log a set of ${entry.exercise.name}.")
                requireNoOpenGroup(workout)
                requireNotTiming(state, workout.id)
                state.copy(restTimer = RestTimer(action.timerId, workout.id, entry.id,
                    action.now + entry.restSeconds * 1000L))
            }
        }
        is Action.SetWeekCredit -> editWorkout(state, action.workoutId) { workout ->
            ensure(workout.finishedAt != null, "Only a finished workout can count for its week.")
            if (action.counted) requireCreditable(workout)
            workout.copy(countedForWeek = action.counted)
        }
        is Action.SetLoadUnit -> {
            ensure(state.activeWorkoutId == action.workoutId && state.activeWorkout?.finishedAt == null,
                "Load entry units can only be changed in the active workout.")
            ensure(state.activeWorkout?.exercises?.find { it.id == action.exerciseId }?.openGroup() == null,
                "Finish the drop or rest-pause set before switching units. Its stages keep the unit they were logged in.")
            var definitionId: String? = null
            val changed = editExercise(state, action.workoutId, action.exerciseId) { exercise ->
                ensure(action.unit in state.loadUnitChoices(action.workoutId, action.exerciseId),
                    if (action.unit == LoadUnit.PLATES)
                        "Plate counts require a cable or stack-machine exercise with a matching library loading convention."
                    else "${exercise.exercise.name} doesn't record a weight.")
                if (action.unit == LoadUnit.PLATES) {
                    val definition = requireNotNull(state.loadUnitDefinition(action.workoutId, action.exerciseId))
                    definitionId = definition.id
                    exercise.copy(exercise = definition, entryLoadUnit = action.unit)
                } else {
                    // Kilograms and pounds only change how the next sets are entered; the snapshot stays as it is.
                    definitionId = exercise.exercise.id
                    exercise.copy(entryLoadUnit = action.unit)
                }
            }
            changed.copy(exercises = changed.exercises.map {
                if (it.id == definitionId) it.copy(preferredLoadUnit = action.unit) else it
            })
        }
        is Action.SetExerciseLoadUnit -> {
            val definition = library(state, action.exerciseId)
            SnapshotCodec.validateLoadUnit(definition, action.unit)
            state.copy(exercises = state.exercises.map {
                if (it.id == definition.id) it.copy(preferredLoadUnit = action.unit) else it
            })
        }
        is Action.DeleteSet -> {
            var emptiedGroup = false
            val changed = editExercise(state, action.workoutId, action.exerciseId) { exercise ->
                val groupId = exercise.sets.find { it.id == action.setId }?.groupId
                val sets = exercise.sets.filterNot { set -> set.id == action.setId }
                // A set with no stages left no longer exists; the other sets keep their IDs and times.
                emptiedGroup = groupId != null && sets.none { it.groupId == groupId }
                exercise.copy(sets = sets,
                    setGroups = if (emptiedGroup) exercise.setGroups.filterNot { it.id == groupId } else exercise.setGroups)
            }
            if (emptiedGroup) clearShortPause(changed, action.exerciseId) else changed
        }
        is Action.WorkoutNote -> {
            SnapshotCodec.note(action.note)
            editWorkout(state, action.workoutId) { it.copy(notes = action.note) }
        }
        is Action.ExerciseNote -> {
            SnapshotCodec.note(action.note)
            editExercise(state, action.workoutId, action.exerciseId) { it.copy(notes = action.note) }
        }
        is Action.EditPlan -> {
            val template = state.availableTemplates(action.phase).find { t -> t.slots.any { it.id == action.slotId } }
                ?: throw RepositoryException("This plan slot is not available in the selected phase.")
            val slot = template.slots.first { it.id == action.slotId }
            ensure(!state.slotRemoved(action.phase, slot.id),
                "This exercise is removed from this phase's plan. Restore it first.")
            val definition = library(state, action.exerciseId)
            ensure(definition.measure == Measure.REPS, "${slot.muscle} is planned in sets of reps, so swap it for " +
                "another rep-based exercise. Add ${definition.name} to the routine on its own instead.")
            requireNotAdded(state, template, action.phase, definition)
            // A swap never lists one exercise twice in a routine; keeping the current one is always fine.
            ensure(definition.id == state.targetFor(slot, action.phase).exerciseId ||
                state.plannedExercises(template, action.phase).none { it.exerciseId == definition.id },
                "${definition.name} is already in ${template.name} for this phase.")
            checkTarget(action.sets, action.restSeconds, slot.upperSets, action.allowAboveCap)
            override(state, PlanOverride(action.phase, action.slotId, action.exerciseId, action.sets, action.restSeconds))
        }
        is Action.EditToday -> editToday(state, action)
        is Action.AddExercise -> {
            SnapshotCodec.validateExercise(action.exercise)
            state.exercises.find { it.id == action.exercise.id }?.let {
                ensure(it.measure == action.exercise.measure, "How ${it.name} is measured can't change, so its " +
                    "records keep their meaning. Create a new exercise instead.")
            }
            state.copy(exercises = if (state.exercises.any { it.id == action.exercise.id })
                state.exercises.map { if (it.id == action.exercise.id) action.exercise.copy() else it }
            else state.exercises + action.exercise.copy())
        }
        is Action.FinishWorkout -> {
            val active = state.activeWorkout ?: throw RepositoryException("There is no active workout to finish.")
            SnapshotCodec.timestamp(action.now)
            ensure(action.now >= active.startedAt, "The workout finish time cannot be before its start time.")
            val open = active.openGroupExercise()
            ensure(open == null || action.closeOpenGroups,
                "${open?.exercise?.name} has a drop or rest-pause set in progress. Finish that set, or confirm " +
                    "closing it with the stages you logged.")
            state.stopwatch?.let { watch ->
                ensure(action.discardStopwatch, "The stopwatch on ${timedName(state, watch)} has an effort that " +
                    "isn't logged. Log it, or confirm discarding it.")
            }
            editWorkout(state, active.id) { workout ->
                val closed = workout.copy(finishedAt = action.now, exercises = workout.exercises.map { exercise ->
                    exercise.copy(setGroups = exercise.setGroups.map {
                        if (it.finishedAt == null) it.copy(finishedAt = action.now) else it
                    })
                })
                ensure(!closed.extra || closed.exercises.any { it.recordCount() > 0 },
                    "Log at least one set or effort to finish this extra workout, or discard it.")
                if (action.countForWeek) requireCreditable(closed)
                closed.copy(countedForWeek = action.countForWeek)
            }.copy(activeWorkoutId = null, restTimer = null, stopwatch = null)
        }
        Action.DiscardWorkout -> {
            val active = state.activeWorkout
            if (active == null) state else state.copy(
                workouts = state.workouts.filterNot { it.id == active.id && it.finishedAt == null },
                activeWorkoutId = null, restTimer = null, stopwatch = null)
        }
        is Action.AdjustRest -> {
            SnapshotCodec.timestamp(action.deadline)
            if (state.restTimer?.id == action.timerId)
                state.copy(restTimer = state.restTimer.copy(deadline = action.deadline)) else state
        }
        Action.SkipRest -> state.copy(restTimer = null)
        is Action.MarkBackup -> {
            ensure(action.revision in 0..state.revision, "The saved backup revision is invalid or newer than your data.")
            SnapshotCodec.timestamp(action.now)
            state.copy(lastBackupRevision = action.revision, lastBackupAt = action.now)
        }
    }

    private fun logStage(state: AppState, action: Action.LogStage): AppState {
        SnapshotCodec.id(action.setId)
        SnapshotCodec.id(action.groupId)
        SnapshotCodec.timestamp(action.now)
        val workout = activeWorkout(state, action.workoutId)
        val exercise = workout.exercises.find { it.id == action.exerciseId }
            ?: throw RepositoryException("This exercise is not part of the workout.")
        SnapshotCodec.validateSet(exercise.exercise, action.kg, action.reps, action.rir, action.loadUnit, action.plates)
        val stage = LoggedSet.of(action.setId, action.kg, action.reps, action.rir, false, action.now,
            action.loadUnit, action.plates, action.groupId)
        val existing = exercise.setGroups.find { it.id == action.groupId }
        // A retried tap or re-delivered request for a stage that is already saved changes nothing, not even the rest.
        if (existing?.method == action.method && isReplay(state, workout.id, exercise, stage)) return state
        ensure(!exercise.skipped, "${exercise.exercise.name} is skipped today. Undo skip to log it.")
        requireNotTiming(state, workout.id)
        val changed = if (existing == null) {
            ensure(state.workouts.none { w -> w.exercises.any { e -> e.setGroups.any { it.id == action.groupId } } },
                "This drop or rest-pause set ID is already used. Please try again.")
            requireNoOpenGroup(workout)
            requireNewSetId(state, action.setId)
            editExercise(state, workout.id, exercise.id) {
                it.copy(sets = it.sets + stage, setGroups = it.setGroups + SetGroup(action.groupId, action.method))
            }
        } else {
            ensure(existing.finishedAt == null,
                "This ${existing.method.title.lowercase()} is already finished. Add a missed stage from its details instead.")
            ensure(existing.method == action.method,
                "This set is a ${existing.method.title.lowercase()}. Change its type from the set's details.")
            ensure(action.loadUnit == exercise.stages(existing.id).last().loadUnit,
                "Every stage logged live uses the unit of the stage before it. Finish this set to log in the other unit.")
            editExercise(state, workout.id, exercise.id) { appendStage(state, it, existing, stage) }
        }
        // The next stage has begun, so any rest or short pause is over; the between-set rest starts on Finish set.
        return changed.copy(restTimer = null)
    }

    private fun logDrop(state: AppState, action: Action.LogDrop): AppState {
        SnapshotCodec.id(action.setId)
        SnapshotCodec.id(action.groupId)
        SnapshotCodec.timestamp(action.now)
        val workout = activeWorkout(state, action.workoutId)
        val exercise = workout.exercises.find { it.id == action.exerciseId }
            ?: throw RepositoryException("This exercise is not part of the workout.")
        SnapshotCodec.validateSet(exercise.exercise, action.kg, action.reps, action.rir, action.loadUnit, action.plates)
        // A retried tap for a drop that is already saved changes nothing, not even the rest.
        exercise.sets.find { it.id == action.setId }?.let { saved ->
            ensure(saved.groupId != null && !saved.warmup && saved.amount == action.kg && saved.reps == action.reps &&
                saved.rir == action.rir && saved.loadUnit == action.loadUnit && saved.plates == action.plates,
                "This drop was already saved with different values. Edit it from the set's details instead.")
            return state
        }
        ensure(!exercise.skipped, "${exercise.exercise.name} is skipped today. Undo skip to log it.")
        requireNotTiming(state, workout.id)
        requireNoOpenGroup(workout)
        val target = exercise.dropTarget()
            ?: throw RepositoryException("Log a working set first; a drop continues the set before it.")
        val changed = when (target) {
            is WorkEntry.Single -> {
                ensure(state.workouts.none { w -> w.exercises.any { e -> e.setGroups.any { it.id == action.groupId } } },
                    "This drop set ID is already used. Please try again.")
                ensure(action.loadUnit == target.set.loadUnit,
                    "A drop uses the unit of the set before it. Log it as a new set to use the other unit.")
                val first = target.set.copy(groupId = action.groupId)
                editExercise(state, workout.id, exercise.id) {
                    appendStage(state, it.copy(
                        sets = it.sets.map { set -> if (set.id == first.id) first else set },
                        setGroups = it.setGroups + SetGroup(action.groupId, SetMethod.DROP, action.now)
                    ), SetGroup(action.groupId, SetMethod.DROP), LoggedSet.of(action.setId, action.kg, action.reps,
                        action.rir, false, action.now, action.loadUnit, action.plates, action.groupId))
                }
            }
            is WorkEntry.Group -> {
                ensure(action.loadUnit == target.stages.last().loadUnit,
                    "A drop uses the unit of the stage before it. Log it as a new set to use the other unit.")
                editExercise(state, workout.id, exercise.id) {
                    appendStage(state, it, target.group, LoggedSet.of(action.setId, action.kg, action.reps, action.rir,
                        false, action.now, action.loadUnit, action.plates, target.group.id)).let { updated ->
                        updated.copy(setGroups = updated.setGroups.map { g ->
                            if (g.id == target.group.id) g.copy(finishedAt = action.now) else g
                        })
                    }
                }
            }
        }
        // The set just got longer, so its rest starts again from the drop.
        return changed.copy(restTimer = RestTimer(newId(), workout.id, exercise.id,
            action.now + exercise.restSeconds * 1000L))
    }

    /**
     * The drop set [groupId] becomes drops of the working set right before it: its stages join that set, which keeps
     * its own first stage and simply gets longer. Refused unless the two are next to each other, finished, in one unit,
     * and still within a set's stage limit; a set that already has rest-pause stages takes no drops.
     */
    private fun joinDropsToSet(exercise: WorkoutExercise, groupId: String): WorkoutExercise {
        val entries = exercise.workEntries()
        val at = entries.indexOfFirst { it is WorkEntry.Group && it.group.id == groupId }
        ensure(at >= 0, "This drop set no longer exists.")
        val drops = entries[at] as WorkEntry.Group
        ensure(drops.group.method == SetMethod.DROP, "Only a drop set can become drops of the set before it.")
        ensure(!drops.open, "Finish this set first.")
        val before = entries.getOrNull(at - 1) ?: throw RepositoryException("There is no set before this one to add the drops to.")
        ensure(before.unit == drops.unit, "Drops use the unit of the set before them; this one was logged in another unit.")
        val beforeStages = if (before is WorkEntry.Group) before.stages else listOf((before as WorkEntry.Single).set)
        ensure(beforeStages.size + drops.stages.size <= MAX_GROUP_STAGES,
            "Together they would have more than $MAX_GROUP_STAGES stages.")
        // Stages stay in one unbroken run, so a warm-up logged between the two keeps them apart.
        val lastBefore = exercise.sets.indexOfFirst { it.id == beforeStages.last().id }
        val firstDrop = exercise.sets.indexOfFirst { it.id == drops.stages.first().id }
        ensure(firstDrop == lastBefore + 1, "A warm-up was logged between these sets, so they can't be joined.")
        return when (before) {
            is WorkEntry.Single -> exercise.copy(
                sets = exercise.sets.map { if (it.id == before.set.id) it.copy(groupId = groupId) else it })
            is WorkEntry.Group -> {
                ensure(before.group.method == SetMethod.DROP && !before.open,
                    "The set before this one is a rest-pause set or still in progress, so it can't take drops.")
                val finished = listOfNotNull(before.group.finishedAt, drops.group.finishedAt).maxOrNull()
                exercise.copy(
                    sets = exercise.sets.map { if (it.groupId == groupId) it.copy(groupId = before.group.id) else it },
                    setGroups = exercise.setGroups.filterNot { it.id == groupId }
                        .map { if (it.id == before.group.id) it.copy(finishedAt = finished) else it }
                )
            }
        }
    }

    private fun addWorkoutExercise(state: AppState, action: Action.AddWorkoutExercise): AppState {
        SnapshotCodec.id(action.entryId)
        val workout = state.workouts.find { it.id == action.workoutId }
            ?: throw RepositoryException("This workout no longer exists.")
        workout.exercises.find { it.id == action.entryId }?.let { saved ->
            // A retried or re-delivered request for an entry that is already added changes nothing.
            ensure(saved.optional && saved.exercise.id == action.exerciseId &&
                (action.perLeg == null || action.perLeg == saved.perLeg),
                "This exercise ID is already used. Please try again.")
            return state
        }
        ensure(state.workouts.none { w -> w.exercises.any { it.id == action.entryId } },
            "This exercise ID is already used. Please try again.")
        val definition = library(state, action.exerciseId)
        workout.exercises.find { it.exercise.id == definition.id }?.let {
            throw RepositoryException("${it.exercise.name} is already in this workout.")
        }
        ensure(workout.exercises.size < MAX_WORKOUT_EXERCISES,
            "A workout can hold at most $MAX_WORKOUT_EXERCISES exercises.")
        // Reps are never converted, so the plan's way of counting them always wins over an answer.
        val convention = state.perLegConvention(definition.id)
        ensure(action.perLeg == null || convention == null || action.perLeg == convention,
            "Your plan counts ${definition.name} reps " + if (convention == true) "per leg." else "without a per-leg label.")
        ensure(!definition.measured || action.perLeg != true, "Only reps can be counted per leg.")
        ensure(!definition.measured || action.sets == null,
            "Set a timed, distance or cardio goal from the exercise's goal options.")
        action.sets?.let(SnapshotCodec::setTarget)
        val entry = WorkoutExercise(action.entryId, null, definition.copy(), null, null, null, null, null, null,
            state.restSuggestion(definition.id, workout.phase).seconds,
            perLeg = !definition.measured && (convention ?: action.perLeg ?: state.lastLoggedPerLeg(definition.id) ?: false),
            entryLoadUnit = definition.preferredLoadUnit, optional = true).copy(targetSets = action.sets)
        return editWorkout(state, workout.id) { it.copy(exercises = it.exercises + entry) }
    }

    private fun removeWorkoutExercise(state: AppState, action: Action.RemoveWorkoutExercise): AppState {
        val workout = state.workouts.find { it.id == action.workoutId }
            ?: throw RepositoryException("This workout no longer exists.")
        val entry = workout.exercises.find { it.id == action.entryId } ?: return state
        ensure(entry.optional, "${entry.exercise.name} is part of this routine's plan. Skip it today instead; " +
            "its target stays. Remove it from future workouts in Plan.")
        ensure(action.confirmedRecords == entry.recordCount(),
            "${entry.exercise.name} changed since you confirmed. Review what it holds and try again.")
        requireNotStopwatchEntry(state, workout.id, entry, "removing it")
        ensure(workout.finishedAt == null || workout.exercises.size > 1,
            "A finished workout keeps at least one exercise. Delete its sets instead.")
        val changed = editWorkout(state, workout.id) { it.copy(exercises = it.exercises.filterNot { e -> e.id == entry.id }) }
        val timer = state.restTimer
        return if (timer != null && timer.workoutId == workout.id && timer.exerciseId == entry.id)
            changed.copy(restTimer = null) else changed
    }

    private fun setSkipped(state: AppState, action: Action.SetSkipped): AppState {
        val workout = liveWorkout(state, action.workoutId,
            "Skip exercises in the active workout. A finished workout keeps what was logged.")
        val entry = workout.exercises.find { it.id == action.entryId }
            ?: throw RepositoryException("This exercise is not part of the workout.")
        ensure(!entry.optional, "${entry.exercise.name} was added today and is already optional. " +
            "Remove it instead if you won't do it.")
        if (entry.skipped == action.skipped) return state
        if (action.skipped) {
            entry.openGroup()?.let {
                throw RepositoryException("Finish the ${it.method.title.lowercase()} on ${entry.exercise.name} first. " +
                    "Its stages are saved.")
            }
            ensure(entry.belowTarget(), "${entry.exercise.name} already reached its target today.")
            requireNotStopwatchEntry(state, workout.id, entry, "skipping it")
        }
        return editExercise(state, workout.id, entry.id) { it.copy(skipped = action.skipped) }
    }

    private fun setEntryGoal(state: AppState, action: Action.SetEntryGoal): AppState {
        val workout = liveWorkout(state, action.workoutId, "Set an optional goal in the active workout.")
        val entry = workout.exercises.find { it.id == action.entryId }
            ?: throw RepositoryException("This exercise is not part of the workout.")
        ensure(entry.optional, "Use Adjust today to change a planned exercise's target.")
        val goal = MeasuredGoal(action.effortSeconds, action.effortDistance, action.totalSeconds, action.totalDistance)
        SnapshotCodec.goal(entry.exercise.measure, required = false, action.sets, action.minReps, action.maxReps, goal)
        SnapshotCodec.rest(action.restSeconds)
        ensure(!entry.exercise.measured || !action.perLeg, "Only reps can be counted per leg.")
        if (action.perLeg != entry.perLeg) {
            ensure(state.perLegConvention(entry.exercise.id) == null,
                "Your plan already says how ${entry.exercise.name} reps are counted.")
            ensure(entry.sets.isEmpty(), "Reps are already logged for ${entry.exercise.name}, so how they're " +
                "counted can't change now.")
        }
        return editExercise(state, workout.id, entry.id) {
            it.copy(targetSets = action.sets, minReps = action.minReps, maxReps = action.maxReps,
                restSeconds = action.restSeconds, perLeg = action.perLeg).withGoal(goal)
        }
    }

    private fun savePlanAddition(state: AppState, action: Action.SavePlanAddition): AppState {
        SnapshotCodec.id(action.id)
        val template = state.scheduledRoutine(action.phase, action.templateId)
            ?: throw RepositoryException("This workout is not available in the selected phase.")
        val definition = library(state, action.exerciseId)
        val goal = MeasuredGoal(action.effortSeconds, action.effortDistance, action.totalSeconds, action.totalDistance)
        SnapshotCodec.goal(definition.measure, required = true, action.sets, action.minReps, action.maxReps, goal)
        SnapshotCodec.rest(action.restSeconds)
        ensure(!definition.measured || !action.perLeg, "Only reps can be counted per leg.")
        state.perLegConvention(definition.id, excludingAdditionId = action.id)?.let { known ->
            ensure(action.perLeg == known, "Your plan already counts ${definition.name} reps " +
                if (known) "per leg." else "without a per-leg label.")
        }
        val existing = state.planAdditions.find { it.id == action.id }
        if (existing != null) {
            ensure(existing.phase == action.phase && existing.templateId == action.templateId &&
                existing.exerciseId == definition.id,
                "This saved exercise belongs to another routine or exercise. Add it again instead.")
        } else {
            // Workouts started from a since-removed addition keep its ID; a new addition must never take it over.
            ensure(state.templates.none { t -> t.slots.any { it.id == action.id } } &&
                state.workouts.none { w -> w.exercises.any { it.slotId == action.id } },
                "This plan ID is already used. Please try again.")
            ensure(state.planAdditions.size < MAX_PLAN_ADDITIONS, "There are too many saved plan changes.")
            ensure(!state.inPlan(template, action.phase, definition.id),
                "${definition.name} is already in ${template.name} for this phase.")
            ensure(state.plannedExercises(template, action.phase).size < MAX_WORKOUT_EXERCISES,
                "A routine can hold at most $MAX_WORKOUT_EXERCISES exercises.")
        }
        val addition = PlanAddition(action.id, action.phase, template.id, definition.id, action.sets,
            action.minReps, action.maxReps, action.restSeconds, action.perLeg, goal.effortSeconds,
            goal.effortDistance, goal.totalSeconds, goal.totalDistance)
        return state.copy(planAdditions = if (existing == null) state.planAdditions + addition
            else state.planAdditions.map { if (it.id == addition.id) addition else it })
    }

    private fun setPlanSlotRemoved(state: AppState, action: Action.SetPlanSlotRemoved): AppState {
        val template = state.availableTemplates(action.phase).find { t -> t.slots.any { it.id == action.slotId } }
            ?: throw RepositoryException("This plan slot is not available in the selected phase.")
        val slot = template.slots.first { it.id == action.slotId }
        if (state.slotRemoved(action.phase, slot.id) == action.removed) return state
        val planned = state.plannedExercises(template, action.phase)
        return if (action.removed) {
            ensure(planned.size > 1,
                "${template.name} needs at least one exercise in this phase. Add another one before removing this.")
            state.copy(planRemovals = state.planRemovals + PlanRemoval(action.phase, slot.id))
        } else {
            val exercise = library(state, state.targetFor(slot, action.phase).exerciseId)
            ensure(planned.none { it.exerciseId == exercise.id },
                "${exercise.name} is already in ${template.name} as your own exercise. Remove that one first.")
            ensure(planned.size < MAX_WORKOUT_EXERCISES, "A routine can hold at most $MAX_WORKOUT_EXERCISES exercises.")
            state.copy(planRemovals = state.planRemovals.filterNot { it.phase == action.phase && it.slotId == slot.id })
        }
    }

    private fun editToday(state: AppState, action: Action.EditToday): AppState {
        val workout = state.activeWorkout ?: throw RepositoryException("Start or resume a workout before editing today's plan.")
        val replacement = library(state, action.replacementId)
        val entry = workout.exercises.find { it.id == action.exerciseId }
            ?: throw RepositoryException("This exercise is not part of the workout.")
        ensure(!entry.optional, "${entry.exercise.name} was added today. Edit its optional goal instead.")
        val substituted = replacement.id != entry.exercise.id
        val measure = entry.exercise.measure
        ensure(replacement.measure == measure, "${entry.exercise.name} is planned as " +
            measure.title.lowercase() + " work, so swap it for another exercise measured the same way.")
        ensure(!substituted || entry.sets.isEmpty(),
            "Remove this exercise's logged sets before substituting it, or log another exercise instead. Logged variants cannot be converted.")
        ensure(!substituted || entry.efforts.isEmpty(), "Remove this exercise's logged efforts before substituting " +
            "it, or log another exercise instead. Logged efforts cannot be converted.")
        if (substituted) requireNotStopwatchEntry(state, workout.id, entry, "swapping it")
        if (measure == Measure.CARDIO) {
            ensure(action.sets == null, "Cardio has a total time or distance instead of sets.")
            SnapshotCodec.rest(action.restSeconds)
        } else {
            ensure(action.sets != null, "Enter the number of sets.")
            checkTarget(action.sets!!, action.restSeconds, entry.upperSets, action.allowAboveCap)
        }
        // Even library edits must not alter the loading convention of an existing snapshot.
        var changed = editExercise(state, workout.id, entry.id) {
            it.copy(exercise = if (substituted) replacement.copy() else it.exercise,
                targetSets = action.sets, restSeconds = action.restSeconds,
                entryLoadUnit = if (substituted) replacement.preferredLoadUnit else it.entryLoadUnit)
        }
        if (action.saveToPlan) {
            // A removed occurrence has no future workouts, so a change made today never brings it back.
            val template = state.scheduledRoutine(workout.phase, workout.templateId)
                ?: throw RepositoryException("This workout isn't in your plan, so there's nothing to save to.")
            val slot = (template as? WorkoutTemplate)?.slots?.find { it.id == entry.slotId }
            if (slot != null) {
                ensure(!state.slotRemoved(workout.phase, slot.id),
                    "${entry.exercise.name} was removed from this phase's plan. Restore it in Plan to change it.")
                requireNotAdded(state, template, workout.phase, replacement)
                changed = override(changed, PlanOverride(workout.phase, slot.id, replacement.id, action.sets!!,
                    action.restSeconds))
            } else {
                // A removed addition is never brought back by a change made to today's copy of it.
                val addition = state.planAdditions.find { it.id == entry.slotId && it.phase == workout.phase }
                    ?: throw RepositoryException("${entry.exercise.name} is no longer in your plan. " +
                        "This change applies to today only.")
                ensure(!substituted || !state.inPlan(template, workout.phase, replacement.id),
                    "${replacement.name} is already in ${template.name} for this phase.")
                // The saved way of counting reps belongs to the exercise; a swap never relabels another one's reps.
                if (substituted) state.perLegConvention(replacement.id, excludingAdditionId = addition.id)?.let { known ->
                    ensure(known == addition.perLeg, "Your plan counts ${replacement.name} reps " +
                        (if (known) "per leg" else "without a per-leg label") + ". Change it for today only, or " +
                        "add it to the plan on its own.")
                }
                changed = changed.copy(planAdditions = changed.planAdditions.map {
                    if (it.id == addition.id) it.copy(exerciseId = replacement.id, sets = action.sets,
                        restSeconds = action.restSeconds) else it
                })
            }
        }
        return changed
    }

    /** A routine never lists one of the user's own exercises twice. */
    private fun requireNotAdded(state: AppState, template: ScheduledRoutine, phase: Phase, exercise: Exercise) = ensure(
        state.planAdditions.none { it.phase == phase && it.templateId == template.id && it.exerciseId == exercise.id },
        "${exercise.name} is already in ${template.name} as your own exercise.")

    /**
     * Explicit weekly credit needs a planned workout with real work on any of its exercises: a working set, or a timed,
     * distance or cardio effort even if it fell short of its goal. Warm-ups and a set still in progress are not
     * working sets. An extra workout never counts.
     */
    private fun requireCreditable(workout: Workout) {
        ensure(!workout.extra, "Extra workouts never count on the weekly checklist.")
        ensure(workout.performedCount() > 0,
            "Log at least one working set or effort before counting this workout for its week.")
    }

    private fun saveRoutine(state: AppState, action: Action.SaveRoutine): AppState {
        SnapshotCodec.id(action.id)
        SnapshotCodec.routineName(action.name)
        val existing = state.routines.find { it.id == action.id }
        if (existing == null) {
            ensure(state.templates.none { it.id == action.id } && state.placements.none { it.id == action.id },
                "This routine ID is already used. Please try again.")
            ensure(state.routines.size < MAX_ROUTINES, "Your library can hold $MAX_ROUTINES routines. Delete one first.")
        }
        checkRoutineExercises(state, action.exercises, ownerId = action.id)
        val routine = CustomRoutine(action.id, action.name, action.exercises)
        return state.copy(routines = if (existing == null) state.routines + routine
            else state.routines.map { if (it.id == routine.id) routine else it })
    }

    /** Confirmed targets for every exercise, each listed once, counting reps the way the rest of the plan does. */
    private fun checkRoutineExercises(state: AppState, exercises: List<RoutineExercise>, ownerId: String) {
        ensure(exercises.isNotEmpty(), "Add at least one exercise.")
        ensure(exercises.size <= MAX_WORKOUT_EXERCISES, "A routine can hold at most $MAX_WORKOUT_EXERCISES exercises.")
        val seen = mutableSetOf<String>()
        exercises.forEach { exercise ->
            val definition = library(state, exercise.exerciseId)
            ensure(seen.add(definition.id), "${definition.name} is listed twice. Keep one of them.")
            SnapshotCodec.goal(definition.measure, required = true, exercise.sets, exercise.minReps, exercise.maxReps,
                exercise.measuredGoal)
            SnapshotCodec.rest(exercise.restSeconds)
            ensure(!definition.measured || !exercise.perLeg, "Only reps can be counted per leg.")
            state.perLegConvention(definition.id, excludingOwnerId = ownerId)?.let { known ->
                ensure(exercise.perLeg == known, "Your plan counts ${definition.name} reps " +
                    if (known) "per leg." else "without a per-leg label.")
            }
        }
    }

    private fun scheduleRoutine(state: AppState, action: Action.ScheduleRoutine): AppState {
        SnapshotCodec.id(action.placementId)
        state.placements.find { it.id == action.placementId }?.let { saved ->
            // Already scheduled by this request: nothing is added or replaced again, and an edited or removed copy is
            // kept as it is. The same ID for another phase, day or routine is refused, never a new schedule.
            ensure(saved.phase == action.phase && saved.weekday == action.weekday &&
                (saved.routineId == null || saved.routineId == action.routineId),
                "This schedule ID is already used. Please try again.")
            return state
        }
        // Workouts of a since-deleted copy keep its ID; a new copy must never take them over.
        ensure(state.templates.none { it.id == action.placementId } &&
            state.routines.none { it.id == action.placementId } &&
            state.workouts.none { it.templateId == action.placementId },
            "This schedule ID is already used. Please try again.")
        ensure(action.weekday in 1..7, "Choose a day from Monday to Sunday.")
        val routine = state.routines.find { it.id == action.routineId }
            ?: throw RepositoryException("This routine no longer exists.")
        ensure(state.placements.size < MAX_PLACEMENTS, "There are too many scheduled routines. Remove some first.")
        ensure(state.planAdditions.size + routine.exercises.size <= MAX_PLAN_ADDITIONS,
            "There are too many saved plan changes.")
        val settled = settleDay(state, action.phase, action.weekday, action.alongside, action.replacingId, movingId = null)
        val placement = RoutinePlacement(action.placementId, action.phase, action.weekday, routine.name, routine.id)
        return settled.copy(placements = settled.placements + placement,
            planAdditions = settled.planAdditions + routine.exercises.map {
                PlanAddition(newId(), action.phase, placement.id, it.exerciseId, it.sets, it.minReps, it.maxReps,
                    it.restSeconds, it.perLeg, it.effortSeconds, it.effortDistance, it.totalSeconds, it.totalDistance)
            })
    }

    /**
     * Makes room on [weekday] of [phase]'s week exactly as the request chose: [alongside] keeps what is there, and
     * [replacingId] takes that one occurrence (live on that day) off the week. A taken day without a choice is refused.
     */
    private fun settleDay(
        state: AppState,
        phase: Phase,
        weekday: Int,
        alongside: Boolean,
        replacingId: String?,
        movingId: String?
    ): AppState {
        ensure(!(alongside && replacingId != null), "Choose either Add alongside or Replace, not both.")
        val there = state.scheduledRoutines(phase).filter { it.weekday == weekday && it.id != movingId }
        if (replacingId == null) {
            ensure(there.isEmpty() || alongside, "That day already has ${there.joinToString { it.name }}. " +
                "Choose Add alongside or Replace.")
            return state
        }
        val replaced = there.find { it.id == replacingId }
            ?: throw RepositoryException("The routine to replace is no longer on that day. Review the day and try again.")
        return takeOff(state, phase, replaced)
    }

    /**
     * Takes one occurrence off [phase]'s week. An original routine is only marked, so it can be put back; a copy loses
     * its future exercises and stays as a record only while workouts refer to it. Workouts never change.
     */
    private fun takeOff(state: AppState, phase: Phase, occurrence: ScheduledRoutine): AppState = when (occurrence) {
        is WorkoutTemplate -> state.copy(scheduleRemovals = state.scheduleRemovals + ScheduleRemoval(phase, occurrence.id))
        is RoutinePlacement -> {
            val referenced = state.workouts.any { it.templateId == occurrence.id }
            state.copy(
                placements = if (referenced) state.placements.map {
                    if (it.id == occurrence.id) it.copy(removedFromPlan = true) else it
                } else state.placements.filterNot { it.id == occurrence.id },
                planAdditions = state.planAdditions.filterNot { it.templateId == occurrence.id })
        }
    }

    private fun livePlacement(state: AppState, id: String): RoutinePlacement =
        state.placements.find { it.id == id && !it.removedFromPlan }
            ?: throw RepositoryException("This routine is no longer on your weekly plan.")

    /** A copy's own name and exercises in order; kept exercises keep their plan IDs, so past workouts stay linked. */
    private fun replaceCopy(state: AppState, placement: RoutinePlacement, name: String, exercises: List<RoutineExercise>): AppState {
        SnapshotCodec.routineName(name)
        checkRoutineExercises(state, exercises, ownerId = placement.id)
        val current = state.planAdditions.filter { it.templateId == placement.id }
        val others = state.planAdditions.filterNot { it.templateId == placement.id }
        ensure(others.size + exercises.size <= MAX_PLAN_ADDITIONS, "There are too many saved plan changes.")
        return state.copy(
            placements = state.placements.map { if (it.id == placement.id) it.copy(name = name) else it },
            // The copy's exercises now come in the order just saved.
            planOrders = state.planOrders.filterNot { it.routineId == placement.id },
            planAdditions = others + exercises.map { exercise ->
                PlanAddition(current.find { it.exerciseId == exercise.exerciseId }?.id ?: newId(), placement.phase,
                    placement.id, exercise.exerciseId, exercise.sets, exercise.minReps, exercise.maxReps,
                    exercise.restSeconds, exercise.perLeg, exercise.effortSeconds, exercise.effortDistance,
                    exercise.totalSeconds, exercise.totalDistance)
            })
    }

    /**
     * A new effort, logged live or added to History, or a correction to a logged one. The stopwatch's own effort is
     * logged only once it is stopped, and logging it clears the stopwatch in the same change. A new timed or distance
     * effort in the active workout starts its rest; cardio and History corrections never touch rest or the stopwatch.
     */
    private fun saveEffort(state: AppState, action: Action.SaveEffort): AppState {
        SnapshotCodec.id(action.effortId)
        SnapshotCodec.timestamp(action.now)
        val workout = state.workouts.find { it.id == action.workoutId }
            ?: throw RepositoryException("This workout no longer exists.")
        val entry = workout.exercises.find { it.id == action.exerciseId }
            ?: throw RepositoryException("This exercise is not part of the workout.")
        SnapshotCodec.validateEffort(entry.exercise, action.durationSeconds, action.distance, action.kg,
            action.loadUnit, action.plates)
        entry.efforts.find { it.id == action.effortId }?.let { previous ->
            // The stopwatch's Log only ever creates its effort: repeated once logged, it changes nothing, not even a
            // later correction.
            if (action.watchStep != null) return state
            val edited = Effort.of(previous.id, action.durationSeconds, action.distance, action.kg, action.loadUnit,
                action.plates, previous.completedAt)
            // A repeated request for an effort that is already saved changes nothing, not the rest or the stopwatch.
            return if (edited == previous) state else editExercise(state, workout.id, entry.id) { current ->
                current.copy(efforts = current.efforts.map { if (it.id == edited.id) edited else it })
            }
        }
        ensure(!state.recordIdUsed(action.effortId), "This effort ID is already used. Please try again.")
        val live = state.activeWorkoutId == workout.id && workout.finishedAt == null
        val watch = state.stopwatch?.takeIf { it.id == action.effortId }
        // The stopwatch's Log logs only the stopped effort it was checked for, never a typed one in its place: once
        // that effort was discarded, or logged and since deleted, nothing is logged.
        ensure(watch != null || action.watchStep == null,
            "This stopwatch effort was discarded or deleted, so it wasn't logged.")
        if (watch != null) {
            ensure(watch.workoutId == workout.id && watch.exerciseId == entry.id,
                "This stopwatch is timing another exercise.")
            ensure(watch.stopped, "Stop the stopwatch before logging its time.")
            ensure(action.watchStep == watch.step, "The stopwatch changed since you checked its time. Check it, " +
                "then log it.")
        }
        if (live) {
            ensure(!entry.skipped, "${entry.exercise.name} is skipped today. Undo skip to log it.")
            requireNoOpenGroup(workout)
            requireNotTiming(state, workout.id)
        }
        ensure(entry.efforts.size < MAX_EFFORTS, "An exercise can hold at most $MAX_EFFORTS efforts in a workout.")
        val effort = Effort.of(action.effortId, action.durationSeconds, action.distance, action.kg, action.loadUnit,
            action.plates, action.now)
        val changed = editExercise(state, workout.id, entry.id) { it.copy(efforts = it.efforts + effort) }
            .let { if (watch != null) it.copy(stopwatch = null) else it }
        return if (live && entry.exercise.measure != Measure.CARDIO) changed.copy(restTimer = RestTimer(newId(),
            workout.id, entry.id, action.now + entry.restSeconds * 1000L)) else changed
    }

    private fun startStopwatch(state: AppState, action: Action.StartStopwatch): AppState {
        SnapshotCodec.id(action.stopwatchId)
        SnapshotCodec.timestamp(action.now)
        val workout = liveWorkout(state, action.workoutId,
            "The stopwatch times efforts in the active workout. Enter a missed effort's measurements instead.")
        val entry = workout.exercises.find { it.id == action.exerciseId }
            ?: throw RepositoryException("This exercise is not part of the workout.")
        // A repeated Start for this effort changes nothing, even after it was logged.
        if (entry.efforts.any { it.id == action.stopwatchId }) return state
        state.stopwatch?.let { current ->
            if (current.id == action.stopwatchId && current.workoutId == workout.id && current.exerciseId == entry.id) {
                return state
            }
            throw RepositoryException(if (current.exerciseId == entry.id && current.workoutId == workout.id)
                "${entry.exercise.name} already has a timed effort. Log it or discard it first."
            else "The stopwatch has an effort on ${timedName(state, current)}. Log it or discard it first.")
        }
        ensure(entry.exercise.measured, "${entry.exercise.name} is logged in sets of reps.")
        ensure(!entry.skipped, "${entry.exercise.name} is skipped today. Undo skip to time it.")
        requireNoOpenGroup(workout)
        ensure(entry.efforts.size < MAX_EFFORTS, "An exercise can hold at most $MAX_EFFORTS efforts in a workout.")
        ensure(!state.recordIdUsed(action.stopwatchId), "This effort ID is already used. Please try again.")
        checkClock(action.clock)
        return state.copy(stopwatch = Stopwatch(action.stopwatchId, workout.id, entry.id, 0, action.clock.elapsedMs,
            action.clock.boot, action.clock.session, stopped = false, needsConfirmation = false,
            startedAt = action.now, step = 0), restTimer = null)
    }

    private fun checkClock(clock: Monotonic) {
        ensure(clock.elapsedMs >= 0, "The phone's clock reading is invalid.")
        SnapshotCodec.id(clock.session)
    }

    /** The stopwatch exactly as a request saw it: the same effort at the same step. */
    private fun AppState.watchAt(id: String, step: Int): Stopwatch? =
        stopwatch?.takeIf { it.id == id && it.step == step }

    /** The running time can't be trusted: the stopwatch stops and its duration is left for the user to enter. */
    private fun untrusted(watch: Stopwatch) = watch.copy(runningSince = null, boot = null, session = null,
        stopped = true, needsConfirmation = true, step = watch.nextStep())

    /** Nothing new is logged in the active workout while the stopwatch is timing an effort; its time is kept. */
    private fun requireNotTiming(state: AppState, workoutId: String) {
        val watch = state.stopwatch?.takeIf { it.running && it.workoutId == workoutId } ?: return
        throw RepositoryException("The stopwatch is timing ${timedName(state, watch)}. Pause or stop it first; " +
            "its time is kept.")
    }

    private fun requireNotStopwatchEntry(state: AppState, workoutId: String, entry: WorkoutExercise, doing: String) {
        val watch = state.stopwatch ?: return
        ensure(watch.workoutId != workoutId || watch.exerciseId != entry.id,
            "${entry.exercise.name} has a timed effort that isn't logged. Log it or discard it before $doing.")
    }

    private fun timedName(state: AppState, watch: Stopwatch): String =
        state.workouts.find { it.id == watch.workoutId }?.exercises?.find { it.id == watch.exerciseId }
            ?.exercise?.name ?: "another exercise"

    private fun liveWorkout(state: AppState, workoutId: String, message: String): Workout {
        val workout = state.activeWorkout
        ensure(workout != null && workout.id == workoutId && workout.finishedAt == null, message)
        return workout!!
    }

    private fun appendStage(state: AppState, exercise: WorkoutExercise, group: SetGroup, stage: LoggedSet): WorkoutExercise {
        requireNewSetId(state, stage.id)
        val stages = exercise.stages(group.id)
        ensure(stages.size < MAX_GROUP_STAGES,
            "A drop or rest-pause set can have at most $MAX_GROUP_STAGES stages. Finish it and log the rest as a new set.")
        // Stages stay together in the order performed, right after the group's current last stage.
        val last = exercise.sets.indexOfLast { it.groupId == group.id }
        return exercise.copy(sets = exercise.sets.take(last + 1) + stage + exercise.sets.drop(last + 1))
    }

    /** True when exactly this stage is already saved in this group, so a repeated request can be ignored. */
    private fun isReplay(state: AppState, workoutId: String, exercise: WorkoutExercise, stage: LoggedSet): Boolean {
        val saved = state.workouts.flatMap { w -> w.exercises.flatMap { e -> e.sets.map { Triple(w, e, it) } } }
            .find { it.third.id == stage.id } ?: return false
        val (w, e, set) = saved
        ensure(w.id == workoutId && e.id == exercise.id && set.groupId == stage.groupId && !set.warmup &&
            set.kg == stage.kg && set.lb == stage.lb && set.reps == stage.reps && set.rir == stage.rir &&
            set.loadUnit == stage.loadUnit && set.plates == stage.plates,
            "This stage was already saved with different values. Edit it from the set's details instead.")
        return true
    }

    private fun requireNewSetId(state: AppState, id: String) = ensure(!state.recordIdUsed(id),
        "This set ID is already used. Please try again with a new set.")

    /** Sets, stages and efforts share one ID space, so a request can never be mistaken for another record. */
    private fun AppState.recordIdUsed(id: String): Boolean =
        workouts.any { w -> w.exercises.any { e -> e.sets.any { it.id == id } || e.efforts.any { it.id == id } } }

    private fun requireNoOpenGroup(workout: Workout) {
        val open = workout.openGroupExercise() ?: return
        val group = requireNotNull(open.openGroup())
        throw RepositoryException("Finish the ${group.method.title.lowercase()} on ${open.exercise.name} first. " +
            "Its stages are saved; new sets start after it.")
    }

    private fun activeWorkout(state: AppState, workoutId: String): Workout {
        val workout = state.activeWorkout
        ensure(workout != null && workout.id == workoutId && workout.finishedAt == null,
            "Drop and rest-pause sets are logged live in the active workout. Correct finished workouts from History.")
        return workout!!
    }

    private fun clearShortPause(state: AppState, exerciseId: String): AppState {
        val timer = state.restTimer ?: return state
        val exercise = state.activeWorkout?.exercises?.find { it.id == exerciseId }
        return if (timer.shortPause && timer.exerciseId == exerciseId &&
            exercise?.openGroup()?.method != SetMethod.REST_PAUSE) state.copy(restTimer = null) else state
    }

    private fun library(state: AppState, id: String): Exercise =
        state.exercises.find { it.id == id } ?: throw RepositoryException("The selected exercise no longer exists.")

    private fun checkTarget(sets: Int, rest: Int, upper: Int?, allowAbove: Boolean) {
        SnapshotCodec.setTarget(sets)
        SnapshotCodec.rest(rest)
        ensure(allowAbove || upper == null || sets <= upper,
            "This exceeds the suggested upper limit of $upper sets. Confirm “allow above cap” to save this target; logging extra sets is always allowed.")
    }

    private fun override(state: AppState, target: PlanOverride) =
        state.copy(overrides = state.overrides.filterNot { it.phase == target.phase && it.slotId == target.slotId } + target)

    private fun editWorkout(state: AppState, id: String, edit: (Workout) -> Workout): AppState {
        ensure(state.workouts.any { it.id == id }, "This workout no longer exists.")
        return state.copy(workouts = state.workouts.map { if (it.id == id) edit(it) else it })
    }

    private fun editExercise(state: AppState, workoutId: String, exerciseId: String,
                             edit: (WorkoutExercise) -> WorkoutExercise): AppState =
        editWorkout(state, workoutId) { workout ->
            ensure(workout.exercises.any { it.id == exerciseId }, "This exercise is not part of the workout.")
            workout.copy(exercises = workout.exercises.map { if (it.id == exerciseId) edit(it) else it })
        }
}
