package com.rebuild.data

import com.rebuild.domain.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

class RepositoryException(message: String, cause: Throwable? = null) : Exception(message, cause)

@Serializable
private data class BackupEnvelope(
    val format: String,
    val version: Int,
    val state: AppState
)

object SnapshotCodec {
    const val MAX_BYTES = 10 * 1024 * 1024
    const val MAX_NOTE_LENGTH = 4096
    const val MAX_SETS = 1000
    const val MAX_REPS = 1000
    const val MAX_PLATES = 1000
    const val MAX_REST_SECONDS = 3600
    const val SCHEMA_VERSION = 7
    const val MAX_BODY_RECORDS = 20000
    private val plateCountToken = Regex("[1-9][0-9]*")
    private val timestampToken = Regex("0|[1-9][0-9]*")
    private val wholeNumberToken = Regex("-?(0|[1-9][0-9]*)")
    private val isoDate = Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = false
        isLenient = false
        allowSpecialFloatingPointValues = false
    }

    fun encode(state: AppState): String {
        ensure(state.schemaVersion == SCHEMA_VERSION, "Migrate older data before exporting a backup.")
        validate(state)
        val source = json.encodeToString(BackupEnvelope("rebuild-backup", SCHEMA_VERSION, state))
        checkSize(source)
        return source
    }

    fun decode(source: String): AppState {
        checkSize(source)
        checkStructure(source)
        val envelope = try {
            val document = json.parseToJsonElement(source) as? JsonObject
                ?: throw RepositoryException("This backup must be a Rebuild JSON object.")
            val version = document["version"] as? JsonPrimitive
            ensure(version != null && !version.isString && version.intOrNull?.let { it in 1..SCHEMA_VERSION } == true,
                "This backup version is not supported. Update Rebuild before importing it.")
            val payload = document["state"] as? JsonObject
                ?: throw RepositoryException("This backup has no valid data snapshot.")
            val schema = payload["schemaVersion"] as? JsonPrimitive
            val schemaVersion = if (schema == null && "schemaVersion" !in payload && version!!.int == 1) 1 else {
                ensure(schema != null && !schema.isString && schema.intOrNull?.let { it in 1..SCHEMA_VERSION } == true,
                    "This data version is not supported. Update Rebuild before importing it.")
                schema!!.int
            }
            ensure(schemaVersion == version!!.int, "The backup and data versions must match.")
            checkVersionedFields(payload, schemaVersion)
            json.decodeFromJsonElement<BackupEnvelope>(JsonObject(document +
                ("state" to JsonObject(payload + ("schemaVersion" to JsonPrimitive(schemaVersion))))))
        } catch (error: SerializationException) {
            throw RepositoryException("This backup is not valid Rebuild JSON, or uses an unsupported format.", error)
        } catch (error: IllegalArgumentException) {
            throw RepositoryException("This backup contains an invalid value.", error)
        }
        ensure(envelope.format == "rebuild-backup", "This file is not a Rebuild backup.")
        validate(envelope.state)
        return envelope.state
    }

    private fun checkVersionedFields(value: JsonElement, schemaVersion: Int) {
        when (value) {
            is JsonObject -> value.forEach { (key, child) ->
                ensure(schemaVersion != 1 || key !in versionTwoFields,
                    "Version-one backups cannot contain load-unit or catalog fields. Use a version-two backup.")
                ensure(schemaVersion >= 3 || key !in versionThreeFields,
                    "Older backups cannot contain drop-set, rest-pause or weekly fields. Use a version-three backup.")
                ensure(schemaVersion >= 4 || key !in versionFourFields,
                    "Older backups cannot contain added, skipped or removed plan exercises. Use a version-four backup.")
                ensure(schemaVersion >= 5 || key !in versionFiveFields,
                    "Older backups cannot contain saved routines or weekly schedule changes. Use a version-five backup.")
                ensure(schemaVersion >= 6 || key !in versionSixFields,
                    "Older backups cannot contain timed, distance or cardio records or a stopwatch. " +
                        "Use a version-six backup.")
                ensure(schemaVersion >= 7 || key !in versionSevenFields,
                    "Older backups cannot contain pounds, weigh-ins or body measurements. Use a version-seven backup.")
                when (key) {
                    "lb", "value" -> ensure(child == JsonNull || (child is JsonPrimitive && !child.isString &&
                        child.doubleOrNull != null), "Pounds and body values must be numbers or null.")
                    "at" -> ensure(child is JsonPrimitive && !child.isString && child.content.matches(timestampToken) &&
                        child.content.toLongOrNull() != null, "A weigh-in or measurement time must be a whole-number timestamp.")
                    "bodyweights", "measurements" -> ensure(child is JsonArray && child.all { it is JsonObject },
                        "Weigh-ins and measurements must be lists.")
                    "countedForWeek", "shortPause" -> ensure(child is JsonPrimitive && !child.isString &&
                        child.booleanOrNull != null, "Weekly and pause flags must be true or false.")
                    "optional", "skipped" -> ensure(child is JsonPrimitive && !child.isString &&
                        child.booleanOrNull != null, "Optional and skipped flags must be true or false.")
                    "groupId" -> ensure(child == JsonNull || (child is JsonPrimitive && child.isString),
                        "A set's group must be text or null.")
                    "startDate" -> ensure(child is JsonPrimitive && child.isString,
                        "A workout's start date must be a calendar date.")
                    "planAdditions" -> {
                        ensure(child is JsonArray && child.all { it is JsonObject },
                            "Exercises added to the plan must be a list.")
                        (child as JsonArray).forEach {
                            checkGoalTypes(it as JsonObject, "An added plan exercise's", schemaVersion)
                        }
                    }
                    "routines" -> {
                        ensure(child is JsonArray && child.all { it is JsonObject },
                            "Saved routines must be a list.")
                        (child as JsonArray).forEach { routine ->
                            val exercises = (routine as JsonObject)["exercises"]
                            ensure(exercises == null || (exercises is JsonArray && exercises.all { it is JsonObject }),
                                "A saved routine's exercises must be a list.")
                            (exercises as? JsonArray)?.forEach {
                                checkGoalTypes(it as JsonObject, "A saved routine exercise's", schemaVersion)
                            }
                        }
                    }
                    "placements" -> {
                        ensure(child is JsonArray && child.all { it is JsonObject },
                            "Routines scheduled on the week must be a list.")
                        (child as JsonArray).forEach { placement ->
                            (placement as JsonObject)["weekday"]?.let { day ->
                                ensure(day is JsonPrimitive && !day.isString && day.content.matches(wholeNumberToken) &&
                                    day.content.toIntOrNull() != null, "A scheduled routine's day must be a whole number.")
                            }
                        }
                    }
                    "scheduleRemovals" -> ensure(child is JsonArray && child.all { it is JsonObject },
                        "Routines taken off the week must be a list.")
                    "routineId" -> ensure(child == JsonNull || (child is JsonPrimitive && child.isString),
                        "A scheduled routine's library link must be text or null.")
                    "removedFromPlan" -> ensure(child is JsonPrimitive && !child.isString &&
                        child.booleanOrNull != null, "Removed-from-plan flags must be true or false.")
                    "planRemovals" -> ensure(child is JsonArray && child.all { it is JsonObject },
                        "Exercises removed from the plan must be a list.")
                    "efforts" -> {
                        ensure(child is JsonArray && child.all { it is JsonObject },
                            "A workout exercise's timed, distance or cardio efforts must be a list.")
                        (child as JsonArray).forEach { checkEffortTypes(it as JsonObject) }
                    }
                    "durationSeconds", "effortSeconds", "totalSeconds" -> ensure(child == JsonNull ||
                        (child is JsonPrimitive && !child.isString && child.content.matches(wholeNumberToken) &&
                            child.content.toIntOrNull() != null), "Durations must be whole seconds or null.")
                    "distance", "effortDistance", "totalDistance" -> ensure(child == JsonNull || child is JsonObject,
                        "A distance must be a value with its unit, or null.")
                    "thousandths" -> ensure(child is JsonPrimitive && !child.isString &&
                        child.content.matches(wholeNumberToken) && child.content.toLongOrNull() != null,
                        "A distance must be a whole number of thousandths of its unit.")
                    "unit", "measure" -> ensure(child is JsonPrimitive && child.isString,
                        "Distance units and measurement types must be names.")
                    "stopwatch" -> {
                        ensure(child == JsonNull || child is JsonObject, "The stopwatch must be a value or null.")
                        (child as? JsonObject)?.let(::checkStopwatchTypes)
                    }
                    "setGroups" -> {
                        ensure(child is JsonArray, "A workout exercise's set groups must be a list.")
                        (child as JsonArray).forEach { group ->
                            val finished = (group as? JsonObject)?.get("finishedAt")
                            ensure(finished == null || finished == JsonNull || (finished is JsonPrimitive &&
                                !finished.isString && finished.content.matches(timestampToken)),
                                "A drop or rest-pause set's finish time must be a whole-number timestamp.")
                        }
                    }
                }
                if (key == "plates" && child != JsonNull) {
                    ensure(child is JsonPrimitive && !child.isString &&
                        child.content.matches(plateCountToken) && child.content.toIntOrNull() != null,
                        "Plate counts must be whole-number JSON values, not text or decimals.")
                }
                checkVersionedFields(child, schemaVersion)
            }
            is JsonArray -> value.forEach { checkVersionedFields(it, schemaVersion) }
            else -> Unit
        }
    }

    private val versionTwoFields = setOf("catalogRevision", "equipment", "primaryMuscle", "aliases",
        "preferredLoadUnit", "entryLoadUnit", "loadUnit", "plates")
    private val versionThreeFields = setOf("groupId", "setGroups", "startDate", "countedForWeek", "shortPause")
    private val versionFourFields = setOf("planAdditions", "planRemovals", "optional", "skipped")
    private val versionFiveFields = setOf("routines", "placements", "scheduleRemovals", "routineId", "removedFromPlan")
    private val versionSevenFields = setOf("lb", "bodyweights", "measurements", "measurementSites", "liftingUnit")
    private val versionSixFields = setOf("measure", "efforts", "durationSeconds", "distance", "thousandths", "unit",
        "effortSeconds", "effortDistance", "totalSeconds", "totalDistance", "stopwatch", "elapsedMs", "runningSince",
        "boot", "session", "stopped", "needsConfirmation", "step")

    /**
     * Sets, reps and rest a user confirmed, and the per-leg choice, as JSON numbers and booleans, never text. From
     * version six, a timed, distance or cardio goal leaves the parts it doesn't use null.
     */
    private fun checkGoalTypes(goal: JsonObject, owner: String, schemaVersion: Int) = goal.forEach { (field, entry) ->
        when (field) {
            "sets", "minReps", "maxReps" -> ensure((schemaVersion >= 6 && entry == JsonNull) ||
                (entry is JsonPrimitive && !entry.isString && entry.content.matches(wholeNumberToken) &&
                    entry.content.toIntOrNull() != null),
                "$owner sets, reps and rest must be whole-number JSON values.")
            "restSeconds" -> ensure(entry is JsonPrimitive && !entry.isString &&
                entry.content.matches(wholeNumberToken) && entry.content.toIntOrNull() != null,
                "$owner sets, reps and rest must be whole-number JSON values.")
            "perLeg" -> ensure(entry is JsonPrimitive && !entry.isString && entry.booleanOrNull != null,
                "Per-leg flags must be true or false.")
        }
    }

    /** An effort's load is a JSON number (or null) and its time a whole-number timestamp, never text. */
    private fun checkEffortTypes(effort: JsonObject) = effort.forEach { (field, entry) ->
        when (field) {
            "kg" -> ensure(entry == JsonNull || (entry is JsonPrimitive && !entry.isString && entry.doubleOrNull != null),
                "An effort's load must be a number or null.")
            "completedAt" -> ensure(entry is JsonPrimitive && !entry.isString &&
                entry.content.matches(timestampToken) && entry.content.toLongOrNull() != null,
                "An effort's time must be a whole-number timestamp.")
        }
    }

    /**
     * The stopwatch's times, boot and step are whole-number JSON values, its flags true or false, its process token
     * text.
     */
    private fun checkStopwatchTypes(watch: JsonObject) = watch.forEach { (field, entry) ->
        when (field) {
            "elapsedMs", "startedAt" -> ensure(entry is JsonPrimitive && !entry.isString &&
                entry.content.matches(timestampToken) && entry.content.toLongOrNull() != null,
                "The stopwatch's times must be whole-number JSON values.")
            "runningSince" -> ensure(entry == JsonNull || (entry is JsonPrimitive && !entry.isString &&
                entry.content.matches(timestampToken) && entry.content.toLongOrNull() != null),
                "The stopwatch's start reading must be a whole number or null.")
            "boot" -> ensure(entry == JsonNull || (entry is JsonPrimitive && !entry.isString &&
                entry.content.matches(wholeNumberToken) && entry.content.toIntOrNull() != null),
                "The stopwatch's boot must be a whole number or null.")
            "step" -> ensure(entry is JsonPrimitive && !entry.isString && entry.content.matches(timestampToken) &&
                entry.content.toIntOrNull() != null, "The stopwatch's step must be a whole-number JSON value.")
            "session" -> ensure(entry == JsonNull || (entry is JsonPrimitive && entry.isString),
                "The stopwatch's process token must be text or null.")
            "stopped", "needsConfirmation" -> ensure(entry is JsonPrimitive && !entry.isString &&
                entry.booleanOrNull != null, "The stopwatch's flags must be true or false.")
        }
    }

    private fun checkSize(source: String) {
        ensure(source.length <= MAX_BYTES && source.toByteArray(Charsets.UTF_8).size <= MAX_BYTES,
            "The backup exceeds the 10 MiB size limit.")
        ensure(source.isNotBlank(), "The backup is empty.")
    }

    // Bound parser recursion and reject ambiguous duplicate keys before deserializing.
    private fun checkStructure(source: String) {
        data class Frame(val objectFrame: Boolean, val keys: MutableSet<String> = mutableSetOf())
        val frames = ArrayDeque<Frame>()
        var index = 0
        while (index < source.length) {
            when (source[index]) {
                '{', '[' -> {
                    frames.addLast(Frame(source[index] == '{'))
                    ensure(frames.size <= 48, "The backup is nested too deeply.")
                }
                '}', ']' -> if (frames.isNotEmpty()) frames.removeLast()
                '"' -> {
                    val start = index
                    index++
                    while (index < source.length) {
                        if (source[index] == '\\') {
                            index += 2
                            continue
                        }
                        if (source[index] == '"') break
                        index++
                    }
                    ensure(index < source.length, "The backup contains an incomplete string.")
                    var after = index + 1
                    while (after < source.length && source[after].isWhitespace()) after++
                    if (after < source.length && source[after] == ':' && frames.lastOrNull()?.objectFrame == true) {
                        val key = try {
                            json.decodeFromString<String>(source.substring(start, index + 1))
                        } catch (error: SerializationException) {
                            throw RepositoryException("The backup contains an invalid field name.", error)
                        }
                        ensure(frames.last().keys.add(key), "The backup contains a duplicate field: $key.")
                    }
                }
            }
            index++
        }
    }

    fun validate(state: AppState) {
        ensure(state.schemaVersion in 1..SCHEMA_VERSION, "This data version is not supported. Update Rebuild before importing it.")
        ensure(state.planRevision in 0..1, "This plan version is not supported. Update Rebuild before importing it.")
        ensure(state.catalogRevision in 0..ExerciseCatalog.REVISION,
            "This exercise catalog version is not supported. Update Rebuild before importing it.")
        if (state.schemaVersion == 1) {
            ensure(state.catalogRevision == 0, "Version-one data cannot contain a catalog revision.")
            val definitions = state.exercises + state.workouts.flatMap { w -> w.exercises.map { it.exercise } }
            ensure(definitions.all { it.equipment == Equipment.UNKNOWN && it.primaryMuscle == MuscleGroup.UNKNOWN &&
                it.aliases.isEmpty() && it.preferredLoadUnit == LoadUnit.KG },
                "Version-one data cannot contain exercise metadata.")
            ensure(state.workouts.all { w -> w.exercises.all { e ->
                e.entryLoadUnit == LoadUnit.KG && e.sets.all { it.loadUnit == LoadUnit.KG && it.plates == null }
            } }, "Version-one sets must record kilograms, never plate counts.")
        }
        if (state.schemaVersion < 3) {
            ensure(state.workouts.all { w -> w.startDate == null && !w.countedForWeek && w.exercises.all { e ->
                e.setGroups.isEmpty() && e.sets.all { it.groupId == null }
            } } && state.restTimer?.shortPause != true,
                "Older data cannot contain drop-set, rest-pause or weekly fields.")
        }
        if (state.schemaVersion < 4) {
            ensure(state.planAdditions.isEmpty() && state.planRemovals.isEmpty() && state.workouts.all { w ->
                w.templateId != null && w.exercises.all { e ->
                    e.slotId != null && !e.optional && !e.skipped && e.targetSets != null && e.baselineSets != null &&
                        e.minReps != null && e.maxReps != null && e.targetRir != null
                }
            }, "Older data cannot contain extra workouts, added or skipped exercises, or plan edits.")
        }
        if (state.schemaVersion < 5) {
            ensure(state.routines.isEmpty() && state.placements.isEmpty() && state.scheduleRemovals.isEmpty(),
                "Older data cannot contain saved routines or weekly schedule changes.")
        }
        if (state.schemaVersion < 7) {
            val sets = state.workouts.flatMap { w -> w.exercises.flatMap { it.sets } }
            val efforts = state.workouts.flatMap { w -> w.exercises.flatMap { it.efforts } }
            ensure(sets.none { it.loadUnit == LoadUnit.LB || it.lb != null } &&
                efforts.none { it.loadUnit == LoadUnit.LB || it.lb != null } &&
                state.workouts.none { w -> w.exercises.any { it.entryLoadUnit == LoadUnit.LB } } &&
                (state.exercises + state.workouts.flatMap { w -> w.exercises.map { it.exercise } })
                    .none { it.preferredLoadUnit == LoadUnit.LB } &&
                state.bodyweights.isEmpty() && state.measurements.isEmpty() &&
                state.measurementSites == listOf(BodySite.WAIST) && state.liftingUnit == MassUnit.KG,
                "Older data cannot contain pounds, weigh-ins or body measurements.")
        }
        if (state.schemaVersion < 6) {
            val definitions = state.exercises + state.workouts.flatMap { w -> w.exercises.map { it.exercise } }
            ensure(state.stopwatch == null &&
                definitions.all { it.measure == Measure.REPS && it.primaryMuscle != MuscleGroup.FULL_BODY } &&
                state.workouts.all { w -> w.exercises.all { it.efforts.isEmpty() && it.measuredGoal.empty } } &&
                state.planAdditions.all {
                    it.sets != null && it.minReps != null && it.maxReps != null && it.measuredGoal.empty
                } &&
                state.routines.all { routine -> routine.exercises.all {
                    it.sets != null && it.minReps != null && it.maxReps != null && it.measuredGoal.empty
                } },
                "Older data cannot contain timed, distance or cardio exercises, efforts or goals, or a stopwatch.")
        }
        ensure(state.revision >= 0, "The data revision must not be negative.")
        ensure(state.exercises.size in 1..1000, "The exercise library must contain 1–1,000 exercises.")
        ensure(state.templates.size in 1..100, "The plan must contain 1–100 workout templates.")
        ensure(state.workouts.size <= 10000, "A backup may contain at most 10,000 workouts.")
        unique(state.exercises.map { it.id }, "exercise")
        state.exercises.forEach(::validateExercise)
        val exerciseIds = state.exercises.map { it.id }.toSet()
        val measures = state.exercises.associate { it.id to it.measure }
        unique(state.templates.map { it.id }, "template")
        val allSlots = state.templates.flatMap { it.slots }
        unique(allSlots.map { it.id }, "plan slot")
        val slotsById = allSlots.associateBy { it.id }
        state.templates.forEach { template ->
            text(template.name, 120, "Workout template name")
            ensure(template.weekday in 1..7, "Workout weekdays must be Monday (1) through Sunday (7).")
            ensure(!(template.introOnly && template.establishedOnly), "A workout cannot be both introductory and established-only.")
            ensure(template.slots.size in 1..100, "A workout template must contain 1–100 exercises.")
            template.slots.forEach { slot ->
                ensure(slot.exerciseId in exerciseIds, "A plan slot refers to a missing exercise.")
                ensure(slot.alternatives.distinct().size == slot.alternatives.size &&
                    slot.exerciseId !in slot.alternatives, "Exercise alternatives must not be duplicated.")
                ensure(slot.alternatives.all { it in exerciseIds }, "An alternative refers to a missing exercise.")
                ensure((slot.alternatives + slot.exerciseId).all { measures[it] == Measure.REPS },
                    "An original routine's exercises and alternatives are counted in reps.")
                targets(slot.baselineSets, slot.upperSets, slot.minReps, slot.maxReps, slot.restSeconds)
                text(slot.muscle, 80, "Muscle group")
            }
        }
        validateSchedule(state, measures)
        Phase.entries.forEach { phase ->
            val schedule = state.availableTemplates(phase)
            ensure(schedule.isNotEmpty(), "Every phase must have at least one workout template.")
            ensure(schedule.map { it.weekday }.distinct().size == schedule.size,
                "A phase cannot schedule two workouts on the same weekday.")
            ensure(state.scheduledRoutines(phase).isNotEmpty(), "Every phase's week must keep at least one routine.")
        }
        ensure(state.overrides.size <= 40000, "There are too many saved plan changes.")
        ensure(state.overrides.map { it.phase to it.slotId }.distinct().size == state.overrides.size,
            "The backup contains duplicate plan changes for a phase and slot.")
        state.overrides.forEach { override ->
            ensure(override.slotId in slotsById, "A saved plan change refers to a missing slot.")
            ensure(override.exerciseId in exerciseIds, "A saved plan change refers to a missing exercise.")
            ensure(measures[override.exerciseId] == Measure.REPS,
                "An original routine's exercise can only be swapped for another rep-based exercise.")
            ensure(state.availableTemplates(override.phase).any { t -> t.slots.any { it.id == override.slotId } },
                "A saved plan change belongs to a workout unavailable in that phase.")
            setTarget(override.sets)
            rest(override.restSeconds)
        }
        validatePlanEdits(state, slotsById.keys, measures)
        ensure(state.bodyweights.size <= MAX_BODY_RECORDS, "There are too many weigh-ins saved.")
        unique(state.bodyweights.map { it.id }, "weigh-in")
        state.bodyweights.forEach(::validateBodyweight)
        ensure(state.measurements.size <= MAX_BODY_RECORDS, "There are too many measurements saved.")
        unique(state.measurements.map { it.id }, "measurement")
        state.measurements.forEach(::validateMeasurement)
        ensure(state.measurementSites.isNotEmpty() && state.measurementSites.distinct().size == state.measurementSites.size,
            "The weekly measuring form needs at least one site, each listed once.")
        ensure(state.planOrders.size <= 2000, "There are too many saved exercise orders.")
        ensure(state.planOrders.map { it.phase to it.routineId }.distinct().size == state.planOrders.size,
            "The backup contains duplicate exercise orders for a phase and routine.")
        state.planOrders.forEach { order ->
            id(order.routineId)
            ensure(order.ids.size <= 200 && order.ids.distinct().size == order.ids.size,
                "A saved exercise order must list each exercise once.")
            order.ids.forEach(::id)
        }
        val additionsById = state.planAdditions.associateBy { it.id }
        unique(state.workouts.map { it.id }, "workout")
        unique(state.workouts.flatMap { it.exercises }.map { it.id }, "workout exercise")
        unique(state.workouts.flatMap { it.exercises }.flatMap { it.sets }.map { it.id }, "set")
        unique(state.workouts.flatMap { it.exercises }.flatMap { e -> e.sets.map { it.id } + e.efforts.map { it.id } },
            "set or effort")
        unique(state.workouts.flatMap { it.exercises }.flatMap { it.setGroups }.map { it.id }, "drop or rest-pause set")
        val templatesById = state.templates.associateBy { it.id }
        val placementsById = state.placements.associateBy { it.id }
        state.workouts.forEach { workout ->
            val template = workout.templateId?.let { templatesById[it] }
            val placement = workout.templateId?.let { placementsById[it] }
            if (workout.extra) {
                ensure(!workout.countedForWeek, "An extra workout never counts for its week.")
                ensure(workout.exercises.all { it.optional }, "An extra workout contains only exercises added to it.")
                ensure(workout.exercises.size in (if (workout.finishedAt == null) 0 else 1)..MAX_WORKOUT_EXERCISES,
                    "A finished extra workout must contain 1–$MAX_WORKOUT_EXERCISES exercises.")
            } else {
                // An original routine of the phase, or a scheduled copy of that phase even after it left the week.
                ensure(template != null || placement != null, "A workout refers to a missing template.")
                ensure(if (template != null) state.availableTemplates(workout.phase).any { it.id == template.id }
                    else placement!!.phase == workout.phase,
                    "A workout's template is unavailable in its recorded phase.")
                ensure(workout.exercises.size in 1..MAX_WORKOUT_EXERCISES, "A recorded workout must contain 1–100 exercises.")
                ensure(workout.exercises.any { !it.optional },
                    "A planned workout must keep the planned exercises it started with.")
            }
            text(workout.name, 120, "Workout name")
            note(workout.notes)
            timestamp(workout.startedAt)
            workout.finishedAt?.let {
                timestamp(it)
                ensure(it >= workout.startedAt, "A workout cannot finish before it starts.")
            }
            if (state.schemaVersion >= 3) {
                startDate(workout.startDate, workout.startedAt)
                ensure(!workout.countedForWeek || workout.finishedAt != null,
                    "Only a finished workout can be counted for its week.")
                ensure(workout.exercises.count { it.openGroup() != null } <= 1,
                    "A workout can have only one drop or rest-pause set in progress.")
                ensure(workout.finishedAt == null || workout.exercises.all { it.openGroup() == null },
                    "A finished workout cannot have a drop or rest-pause set in progress.")
            }
            val required = workout.exercises.filterNot { it.optional }
            ensure(required.map { it.slotId }.distinct().size == required.size,
                "A workout contains duplicate plan slots.")
            workout.exercises.forEach { exercise ->
                val measure = exercise.exercise.measure
                if (exercise.optional) {
                    ensure(exercise.slotId == null && !exercise.skipped && exercise.baselineSets == null &&
                        exercise.upperSets == null && exercise.targetRir == null,
                        "An added exercise has no plan slot, prescription or skip.")
                    goal(measure, required = false, exercise.targetSets, exercise.minReps, exercise.maxReps,
                        exercise.measuredGoal)
                    rest(exercise.restSeconds)
                } else if (measure != Measure.REPS) {
                    // Only the user's own plan exercises can be timed, distance or cardio; they have no prescription.
                    val slotId = exercise.slotId
                    ensure(slotId != null && slotId !in slotsById && exercise.baselineSets == null &&
                        exercise.upperSets == null && exercise.targetRir == null,
                        "A planned timed, distance or cardio exercise comes from your own plan, with no prescription.")
                    id(slotId!!)
                    additionsById[slotId]?.let {
                        ensure(it.templateId == workout.templateId && it.phase == workout.phase,
                            "A workout exercise refers to another routine's added exercise.")
                    }
                    goal(measure, required = true, exercise.targetSets, exercise.minReps, exercise.maxReps,
                        exercise.measuredGoal)
                    rest(exercise.restSeconds)
                } else {
                    val slotId = exercise.slotId
                    ensure(slotId != null && exercise.targetSets != null && exercise.minReps != null &&
                        exercise.maxReps != null && exercise.targetRir != null,
                        "A planned exercise must keep its plan slot and target.")
                    ensure(exercise.measuredGoal.empty, "A rep-based exercise has no timed, distance or cardio goal.")
                    id(slotId!!)
                    if (slotId in slotsById || state.schemaVersion < 4) {
                        ensure(template != null && template.slots.any { it.id == slotId },
                            "A workout exercise refers to a slot outside its template.")
                        ensure(exercise.baselineSets != null, "A planned exercise must keep its prescribed baseline.")
                    } else {
                        // A plan addition may since have been edited or removed; the started snapshot stays valid.
                        additionsById[slotId]?.let {
                            ensure(it.templateId == workout.templateId && it.phase == workout.phase,
                                "A workout exercise refers to another routine's added exercise.")
                        }
                        ensure(exercise.baselineSets == null && exercise.upperSets == null,
                            "An exercise you added to the plan has no prescribed baseline or upper limit.")
                    }
                    targets(exercise.baselineSets, exercise.upperSets, exercise.minReps!!, exercise.maxReps!!,
                        exercise.restSeconds)
                    setTarget(exercise.targetSets!!)
                    ensure(exercise.targetRir!! in 0..10, "Target RIR must be between 0 and 10.")
                }
                ensure(exercise.exercise.id in exerciseIds, "A workout snapshot refers to a missing library exercise.")
                // How an exercise is measured never changes, so every record of it keeps one meaning.
                ensure(measures[exercise.exercise.id] == measure,
                    "A workout snapshot's measurement type differs from its library exercise.")
                validateExercise(exercise.exercise)
                validateLoadUnit(exercise.exercise, exercise.entryLoadUnit)
                note(exercise.notes)
                ensure(exercise.sets.size <= 10000, "An exercise may contain at most 10,000 logged sets.")
                exercise.sets.forEach { set ->
                    ensure(set.loadUnit == LoadUnit.LB || set.lb == null, "Only a set in pounds records pounds.")
                    ensure(set.loadUnit != LoadUnit.LB || set.kg == null, "A set in pounds has no kilogram weight.")
                    // A plate count never hides a weight beside it; the amount check below only sees kg or lb.
                    validateSet(exercise.exercise, if (set.loadUnit == LoadUnit.PLATES) set.kg else set.amount, set.reps,
                        set.rir, set.loadUnit, set.plates)
                    timestamp(set.completedAt)
                }
                validateGroups(exercise)
                if (measure == Measure.REPS) {
                    ensure(exercise.efforts.isEmpty(), "A rep-based exercise is logged in sets, not timed or distance efforts.")
                } else {
                    ensure(exercise.setGroups.isEmpty() && !exercise.perLeg,
                        "Timed, distance and cardio efforts are never drop or rest-pause sets or counted per leg.")
                }
                ensure(exercise.efforts.size <= MAX_EFFORTS,
                    "An exercise may contain at most ${"%,d".format(MAX_EFFORTS)} efforts.")
                exercise.efforts.forEach { effort ->
                    ensure(effort.loadUnit == LoadUnit.LB || effort.lb == null, "Only an effort in pounds records pounds.")
                    ensure(effort.loadUnit != LoadUnit.LB || effort.kg == null, "An effort in pounds has no kilogram weight.")
                    validateEffort(exercise.exercise, effort.durationSeconds, effort.distance,
                        if (effort.loadUnit == LoadUnit.PLATES) effort.kg else effort.amount, effort.loadUnit, effort.plates)
                    timestamp(effort.completedAt)
                }
            }
        }
        val unfinished = state.workouts.filter { it.finishedAt == null }
        ensure(unfinished.size <= 1, "Only one unfinished workout can exist.")
        ensure((unfinished.singleOrNull()?.id) == state.activeWorkoutId,
            "The active workout reference does not match the unfinished workout.")
        state.activeWorkout?.let {
            ensure(state.phase == it.phase, "The active workout must match the selected phase.")
        }
        state.restTimer?.let { timer ->
            id(timer.id)
            timestamp(timer.deadline)
            ensure(timer.workoutId == state.activeWorkoutId && state.activeWorkout != null,
                "A rest timer must belong to the active workout.")
            ensure(state.activeWorkout!!.exercises.any { it.id == timer.exerciseId },
                "A rest timer refers to a missing workout exercise.")
            ensure(!timer.shortPause || state.activeWorkout!!.exercises.find { it.id == timer.exerciseId }
                ?.openGroup()?.method == SetMethod.REST_PAUSE,
                "A short pause must belong to a rest-pause set in progress.")
        }
        state.stopwatch?.let { validateStopwatch(state, it) }
        state.lastBackupAt?.let(::timestamp)
        ensure((state.lastBackupAt == null) == (state.lastBackupRevision == null),
            "The backup timestamp and revision must be recorded together.")
        state.lastBackupRevision?.let {
            ensure(it in 0..state.revision, "A backup revision cannot be newer than the current data.")
        }
        state.unbackedUpSince?.let {
            timestamp(it)
            ensure(state.lastBackupRevision != state.revision,
                "Fully backed-up data cannot have an unbacked-up changes timestamp.")
        }
    }

    /** Stages exist only inside their group, in one unbroken run, as working sets; groups hold 1–20 stages. */
    private fun validateGroups(exercise: WorkoutExercise) {
        val groupIds = exercise.setGroups.map { it.id }.toSet()
        exercise.setGroups.forEach { group ->
            id(group.id)
            group.finishedAt?.let(::timestamp)
            val positions = exercise.sets.indices.filter { exercise.sets[it].groupId == group.id }
            ensure(positions.size in 1..MAX_GROUP_STAGES,
                "A drop or rest-pause set must have 1–$MAX_GROUP_STAGES stages.")
            ensure(positions.last() - positions.first() + 1 == positions.size,
                "The stages of a drop or rest-pause set must be kept together in order.")
        }
        exercise.sets.forEach { set ->
            if (set.groupId != null) {
                ensure(set.groupId in groupIds, "A stage refers to a drop or rest-pause set outside its exercise.")
                ensure(!set.warmup, "A drop or rest-pause stage cannot be a warm-up.")
            }
        }
    }

    /** A strict ISO calendar date that is the start's local date in some real time zone (UTC−12 to UTC+14). */
    fun startDate(value: String?, startedAt: Long) {
        ensure(value != null && value.matches(isoDate), "Every workout must record the calendar date it started on.")
        val date = try {
            java.time.LocalDate.parse(value)
        } catch (error: java.time.format.DateTimeParseException) {
            throw RepositoryException("A workout's start date is not a real calendar date.", error)
        }
        val utc = java.time.Instant.ofEpochMilli(startedAt).atZone(java.time.ZoneOffset.UTC).toLocalDate()
        ensure(kotlin.math.abs(date.toEpochDay() - utc.toEpochDay()) <= 1,
            "A workout's start date must match the time it started.")
    }

    fun validateExercise(exercise: Exercise) {
        id(exercise.id)
        text(exercise.name, 120, "Exercise name")
        ensure(!exercise.perDumbbell || exercise.loading == LoadingType.EXTERNAL,
            "Only externally loaded exercises can record weight per dumbbell.")
        ensure(exercise.measure != Measure.CARDIO || exercise.loading == LoadingType.BODYWEIGHT,
            "Cardio exercises don't record a load.")
        ensure(exercise.aliases.size <= 20, "An exercise may have at most 20 search aliases.")
        exercise.aliases.forEach { text(it, 120, "Search alias") }
        ensure(exercise.aliases.distinct().size == exercise.aliases.size, "Search aliases must not be duplicated.")
        validateLoadUnit(exercise, exercise.preferredLoadUnit)
    }

    fun validateLoadUnit(exercise: Exercise, loadUnit: LoadUnit) {
        ensure(loadUnit != LoadUnit.PLATES || exercise.supportsPlates,
            "Plate counts are only available for externally loaded cable or stack-machine exercises, not per dumbbell.")
    }

    fun validateSet(
        exercise: Exercise,
        kg: Double?,
        reps: Int,
        rir: Double?,
        loadUnit: LoadUnit = LoadUnit.KG,
        plates: Int? = null
    ) {
        ensure(exercise.measure == Measure.REPS,
            "${exercise.name} is ${exercise.measure.title.lowercase()} work, logged as efforts rather than sets of reps.")
        ensure(reps in 1..MAX_REPS, "Reps must be between 1 and $MAX_REPS.")
        ensure(rir == null || (rir.isFinite() && rir in 0.0..10.0), "RIR must be between 0 and 10, or left blank.")
        load(exercise, kg, loadUnit, plates)
    }

    /**
     * One timed, distance or cardio effort as performed. A timed hold needs its time; a carry its distance (its time is
     * optional); cardio a time, a distance or both. Holds and carries take a load like a set; cardio never does.
     */
    fun validateEffort(
        exercise: Exercise,
        durationSeconds: Int?,
        distance: Distance?,
        kg: Double?,
        loadUnit: LoadUnit,
        plates: Int?
    ) {
        when (exercise.measure) {
            Measure.REPS -> throw RepositoryException("${exercise.name} is logged in sets of reps.")
            Measure.TIMED -> {
                ensure(durationSeconds != null, "Enter how long ${exercise.name} lasted.")
                ensure(distance == null, "A timed set records a time, not a distance.")
            }
            Measure.DISTANCE -> ensure(distance != null, "Enter the distance covered.")
            Measure.CARDIO -> ensure(durationSeconds != null || distance != null,
                "Enter a duration, a distance or both.")
        }
        durationSeconds?.let(::checkDuration)
        distance?.let(::checkDistance)
        if (exercise.measure == Measure.CARDIO) {
            ensure(kg == null && plates == null && loadUnit == LoadUnit.KG, "Cardio doesn't record a load.")
        } else load(exercise, kg, loadUnit, plates)
    }

    /** A set's or effort's load: kilograms (or assistance) as the exercise is loaded, or a whole plate count. */
    private fun load(exercise: Exercise, kg: Double?, loadUnit: LoadUnit, plates: Int?) {
        validateLoadUnit(exercise, loadUnit)
        if (loadUnit == LoadUnit.PLATES) {
            ensure(kg == null, "Plate counts cannot also contain a kilogram weight.")
            ensure(plates != null && plates in 1..MAX_PLATES,
                "Plates must be a whole number between 1 and $MAX_PLATES.")
            return
        }
        ensure(plates == null, "Weight sets cannot also contain a plate count.")
        val pounds = loadUnit == LoadUnit.LB
        when (exercise.loading) {
            LoadingType.BODYWEIGHT -> ensure(kg == null, "Bodyweight sets do not accept a weight. Choose an externally loaded variant instead.")
            LoadingType.EXTERNAL, LoadingType.ASSISTANCE -> ensure(kg != null && kg.isFinite() &&
                kg in 0.0..(if (pounds) 22000.0 else 10000.0),
                if (pounds) "Weight or assistance must be a finite number between 0 and 22,000 lb."
                else "Weight or assistance must be a finite number between 0 and 10,000 kg.")
        }
    }

    fun note(value: String) = text(value, MAX_NOTE_LENGTH, "Notes", allowBlank = true)

    /** A plausible adult weigh-in, as entered: 20–400 kg or 44–880 lb, on a real local date. */
    fun validateBodyweight(entry: BodyweightEntry) {
        id(entry.id)
        timestamp(entry.at)
        bodyDate(entry.date, entry.at)
        val range = if (entry.unit == MassUnit.KG) 20.0..400.0 else 44.0..880.0
        ensure(entry.value.isFinite() && entry.value in range,
            "Enter a bodyweight between ${range.start.toInt()} and ${range.endInclusive.toInt()} ${entry.unit.symbol}.")
        note(entry.note)
    }

    /** A circumference as entered: 10–300 cm or 4–120 in. */
    fun validateMeasurement(entry: BodyMeasurement) {
        id(entry.id)
        timestamp(entry.at)
        bodyDate(entry.date, entry.at)
        val range = if (entry.unit == LengthUnit.CM) 10.0..300.0 else 4.0..120.0
        ensure(entry.value.isFinite() && entry.value in range,
            "Enter ${entry.site.title.lowercase()} between ${range.start.toInt()} and ${range.endInclusive.toInt()} ${entry.unit.symbol}.")
        note(entry.note)
    }

    private fun bodyDate(date: String, at: Long) = ensure(runCatching { startDate(date, at) }.isSuccess,
        "Every weigh-in and measurement must record the calendar date it was taken on.")

    /** A time of 1 second to 24 hours, for an effort or a goal. */
    fun checkDuration(seconds: Int) = ensure(seconds in 1..MAX_EFFORT_SECONDS,
        "A duration must be from 1 second to 24 hours.")

    /** More than zero and at most 1,000 km (or 1,000 mi), in thousandths of the unit it was entered in. */
    fun checkDistance(value: Distance) = ensure(value.inRange(),
        "A distance must be more than 0 and at most 1,000 ${if (value.unit == DistanceUnit.MI) "mi" else "km"}.")

    /**
     * A target or goal that fits how the exercise is measured. Reps: sets and a rep range (on an optional entry each
     * part is optional). Timed or distance: a set count with its per-set minimum, both or neither. Cardio: a total time,
     * a total distance or both. A [required] one always has what makes it reachable.
     */
    fun goal(measure: Measure, required: Boolean, sets: Int?, minReps: Int?, maxReps: Int?, goal: MeasuredGoal) {
        when (measure) {
            Measure.REPS -> {
                ensure(goal.empty, "A rep-based exercise has no timed, distance or cardio goal.")
                ensure(!required || (sets != null && minReps != null && maxReps != null),
                    "Enter the sets and the rep range.")
                sets?.let(::setTarget)
                repRange(minReps, maxReps)
            }
            Measure.TIMED, Measure.DISTANCE -> {
                val timed = measure == Measure.TIMED
                ensure(minReps == null && maxReps == null && goal.totalSeconds == null && goal.totalDistance == null &&
                    (if (timed) goal.effortDistance == null else goal.effortSeconds == null),
                    "A ${measure.title.lowercase()} goal is a number of sets and the minimum for each.")
                val minimum: Any? = if (timed) goal.effortSeconds else goal.effortDistance
                ensure((sets == null) == (minimum == null),
                    "Enter both the number of sets and the minimum for each, or neither.")
                ensure(!required || sets != null, "Enter the number of sets and the minimum for each.")
                sets?.let(::setTarget)
                goal.effortSeconds?.let(::checkDuration)
                goal.effortDistance?.let(::checkDistance)
            }
            Measure.CARDIO -> {
                ensure(sets == null && minReps == null && maxReps == null && goal.effortSeconds == null &&
                    goal.effortDistance == null, "A cardio goal is a total time or distance, not sets or reps.")
                ensure(!required || goal.totalSeconds != null || goal.totalDistance != null,
                    "Enter a total time, a total distance or both.")
                goal.totalSeconds?.let(::checkDuration)
                goal.totalDistance?.let(::checkDistance)
            }
        }
    }

    fun setTarget(value: Int) = ensure(value in 1..MAX_SETS, "The target must be between 1 and $MAX_SETS sets.")
    fun rest(value: Int) = ensure(value in 1..MAX_REST_SECONDS, "Rest must be between 1 and $MAX_REST_SECONDS seconds.")
    fun timestamp(value: Long) = ensure(value in 0..253402300799999L, "The date or timer deadline is outside the supported range.")
    fun id(value: String) {
        text(value, 128, "Identifiers")
        ensure(value.none { it.isISOControl() }, "Identifiers must contain 1–128 non-control characters.")
    }

    private fun targets(baseline: Int?, upper: Int?, min: Int, max: Int, restSeconds: Int) {
        baseline?.let(::setTarget)
        ensure(upper == null || (baseline != null && upper in baseline..MAX_SETS),
            "An advisory upper limit must be at least the baseline and no more than $MAX_SETS.")
        repRange(min, max)
        rest(restSeconds)
    }

    /** A rep range is both ends or neither; never one end alone. */
    fun repRange(min: Int?, max: Int?) {
        ensure((min == null) == (max == null), "Enter both ends of the rep range, or leave both blank.")
        if (min != null && max != null) {
            ensure(min in 1..MAX_REPS && max in min..MAX_REPS, "The target rep range is invalid.")
        }
    }

    /**
     * Future-plan edits: removed original slots and the user's own additions, each within a routine available in its
     * phase. A routine never lists one exercise twice and always keeps 1–100 exercises in every phase.
     */
    private fun validatePlanEdits(state: AppState, slotIds: Set<String>, measures: Map<String, Measure>) {
        ensure(state.planRemovals.size <= 40000 && state.planAdditions.size <= 40000,
            "There are too many saved plan changes.")
        ensure(state.planRemovals.distinct().size == state.planRemovals.size,
            "The backup lists a removed plan exercise twice.")
        state.planRemovals.forEach { removal ->
            id(removal.slotId)
            ensure(state.availableTemplates(removal.phase).any { t -> t.slots.any { it.id == removal.slotId } },
                "A removed plan exercise belongs to a workout unavailable in that phase.")
        }
        unique(state.planAdditions.map { it.id }, "added plan exercise")
        state.planAdditions.forEach { addition ->
            ensure(addition.id !in slotIds, "An added plan exercise cannot reuse an original slot ID.")
            ensure(state.availableTemplates(addition.phase).any { it.id == addition.templateId } ||
                state.placementsIn(addition.phase).any { it.id == addition.templateId },
                "An added plan exercise belongs to a workout unavailable in that phase.")
            val measure = measures[addition.exerciseId]
            ensure(measure != null, "An added plan exercise refers to a missing exercise.")
            goal(measure!!, required = true, addition.sets, addition.minReps, addition.maxReps, addition.measuredGoal)
            ensure(measure == Measure.REPS || !addition.perLeg, "Only reps can be counted per leg.")
            rest(addition.restSeconds)
        }
        Phase.entries.forEach { phase ->
            (state.availableTemplates(phase) + state.placementsIn(phase)).forEach { template ->
                val planned = state.plannedExercises(template, phase)
                ensure(planned.size in 1..MAX_WORKOUT_EXERCISES,
                    "Every routine must keep 1–$MAX_WORKOUT_EXERCISES exercises in each phase.")
                ensure(planned.filter { it.addition != null }.all { added ->
                    planned.count { it.exerciseId == added.exerciseId } == 1
                }, "A routine cannot list an exercise you added twice in one phase.")
            }
        }
    }

    fun routineName(value: String) = text(value, MAX_ROUTINE_NAME, "Routine name")

    /**
     * The user's library routines and weekly schedule changes. Library routines, scheduled copies and original routines
     * never share an ID; a copy taken off the week keeps no future exercises, and every phase keeps its own week.
     */
    private fun validateSchedule(state: AppState, measures: Map<String, Measure>) {
        ensure(state.routines.size <= MAX_ROUTINES, "A backup may contain at most $MAX_ROUTINES saved routines.")
        ensure(state.placements.size <= MAX_PLACEMENTS,
            "A backup may contain at most $MAX_PLACEMENTS scheduled routines.")
        unique(state.routines.map { it.id }, "saved routine")
        unique(state.placements.map { it.id }, "scheduled routine")
        val templateIds = state.templates.map { it.id }.toSet()
        val placementIds = state.placements.map { it.id }.toSet()
        val routineIds = state.routines.map { it.id }.toSet()
        ensure(placementIds.none { it in templateIds } && routineIds.none { it in templateIds || it in placementIds },
            "Saved routines, scheduled routines and plan workouts must not share an ID.")
        state.routines.forEach { routine ->
            id(routine.id)
            routineName(routine.name)
            ensure(routine.exercises.size in 1..MAX_WORKOUT_EXERCISES,
                "A saved routine must contain 1–$MAX_WORKOUT_EXERCISES exercises.")
            ensure(routine.exercises.map { it.exerciseId }.distinct().size == routine.exercises.size,
                "A saved routine cannot list an exercise twice.")
            routine.exercises.forEach { exercise ->
                val measure = measures[exercise.exerciseId]
                ensure(measure != null, "A saved routine refers to a missing exercise.")
                goal(measure!!, required = true, exercise.sets, exercise.minReps, exercise.maxReps, exercise.measuredGoal)
                ensure(measure == Measure.REPS || !exercise.perLeg, "Only reps can be counted per leg.")
                rest(exercise.restSeconds)
            }
        }
        state.placements.forEach { placement ->
            id(placement.id)
            routineName(placement.name)
            ensure(placement.weekday in 1..7, "Workout weekdays must be Monday (1) through Sunday (7).")
            ensure(placement.routineId == null || placement.routineId in routineIds,
                "A scheduled routine refers to a missing saved routine.")
            ensure(!placement.removedFromPlan || state.planAdditions.none { it.templateId == placement.id },
                "A routine taken off the week cannot keep future exercises.")
        }
        ensure(state.scheduleRemovals.distinct().size == state.scheduleRemovals.size,
            "The backup lists a routine taken off the week twice.")
        state.scheduleRemovals.forEach { removal ->
            id(removal.templateId)
            ensure(state.availableTemplates(removal.phase).any { it.id == removal.templateId },
                "A routine taken off the week belongs to a workout unavailable in that phase.")
        }
    }

    /**
     * The one stopwatch times a timed, distance or cardio exercise of the active workout. Running, it has the clock it
     * started on and no rest or open drop or rest-pause set alongside; stopped or needing its time confirmed, it holds
     * no clock. Its ID is the effort it becomes, so no record may already use it.
     */
    private fun validateStopwatch(state: AppState, watch: Stopwatch) {
        id(watch.id)
        timestamp(watch.startedAt)
        val workout = state.activeWorkout
        ensure(workout != null && workout.id == watch.workoutId, "The stopwatch must belong to the active workout.")
        val entry = workout!!.exercises.find { it.id == watch.exerciseId }
        ensure(entry != null && entry.exercise.measured && !entry.skipped,
            "The stopwatch must time a timed, distance or cardio exercise of the active workout.")
        ensure(watch.elapsedMs in 0..MAX_STOPWATCH_MS, "The stopwatch's time is outside the supported range.")
        ensure(watch.step in 0..MAX_STOPWATCH_STEP, "The stopwatch's step is outside the supported range.")
        ensure(!watch.needsConfirmation || watch.stopped, "A stopwatch whose time needs confirming is stopped.")
        if (watch.running) {
            ensure(!watch.stopped && watch.runningSince!! >= 0 && !watch.session.isNullOrEmpty(),
                "A running stopwatch must record the clock it started on.")
            id(watch.session!!)
            ensure(workout.openGroupExercise() == null && state.restTimer == null,
                "A rest or a drop or rest-pause set can't run while the stopwatch is timing an effort.")
        } else {
            ensure(watch.boot == null && watch.session == null, "A paused or stopped stopwatch holds no clock.")
        }
        ensure(state.workouts.none { w -> w.exercises.any { e ->
            e.sets.any { it.id == watch.id } || e.efforts.any { it.id == watch.id }
        } }, "The stopwatch's effort is already logged.")
    }

    private fun text(value: String, max: Int, label: String, allowBlank: Boolean = false) {
        ensure(isWellFormedUtf16(value),
            "$label must contain complete Unicode characters. Re-enter the affected text.")
        ensure(value.length <= max && (allowBlank || value.isNotBlank()),
            if (allowBlank) "$label must be $max characters or fewer."
            else "$label must contain 1–$max characters.")
    }

    private fun unique(ids: List<String>, label: String) {
        ids.forEach(::id)
        ensure(ids.toSet().size == ids.size, "The backup contains duplicate $label IDs.")
    }

    internal fun ensure(condition: Boolean, message: String) {
        if (!condition) throw RepositoryException(message)
    }
}
