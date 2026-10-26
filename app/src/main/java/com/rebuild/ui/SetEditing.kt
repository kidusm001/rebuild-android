package com.rebuild.ui

import com.rebuild.domain.takesLoad
import com.rebuild.domain.loadUnitOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.rebuild.domain.Action
import com.rebuild.domain.Exercise
import com.rebuild.domain.LoadingType
import com.rebuild.domain.Measure
import com.rebuild.domain.LoadUnit
import com.rebuild.domain.Equipment
import com.rebuild.domain.MuscleGroup
import com.rebuild.domain.LoggedSet
import com.rebuild.domain.WorkoutExercise
import com.rebuild.domain.stages

internal data class SetInput(val kg: Double?, val reps: Int, val rir: Double?, val plates: Int? = null)
internal enum class SetField { KG, REPS, RIR }
internal sealed interface SetValidation {
    data class Valid(val input: SetInput) : SetValidation
    data class Invalid(val field: SetField, val message: String) : SetValidation
}

/** Returns the parsed set or a user-readable reason it can't be saved. */
internal fun validateSet(exercise: Exercise, kgText: String, repsText: String, rirText: String,
    unit: LoadUnit = LoadUnit.KG): SetValidation {
    val reps = repsText.trim().toIntOrNull()
    if (reps == null || reps < 1) return SetValidation.Invalid(SetField.REPS, "Enter the reps you completed (1 or more).")
    if (reps > MAX_REPS) return SetValidation.Invalid(SetField.REPS, "Reps look too high. Check the number.")
    // Bodyweight sets never carry kg; a weighted variant is a separate external-load exercise.
    val plates = if (unit == LoadUnit.PLATES) kgText.trim().toIntOrNull() else null
    if (unit == LoadUnit.PLATES && (!exercise.supportsPlates || plates == null || plates !in 1..1000)) {
        return SetValidation.Invalid(SetField.KG, "Enter whole plates per stack, from 1 to 1000, for a cable or stack machine.")
    }
    // The amount is in the unit chosen for this entry, kilograms or pounds, and is kept exactly as typed.
    val mass = unit != LoadUnit.PLATES
    val symbol = unit.massSymbol()
    val kg = if (exercise.loading == LoadingType.BODYWEIGHT || !mass) null else parseDecimal(kgText)
    if (mass && exercise.loading != LoadingType.BODYWEIGHT && kgText.isNotBlank() && kg == null) {
        return SetValidation.Invalid(SetField.KG, "Weight isn't a number.")
    }
    if (mass && kg == null && exercise.loading == LoadingType.EXTERNAL) {
        return SetValidation.Invalid(SetField.KG, "Enter the weight in $symbol${if (exercise.perDumbbell) " per dumbbell" else ""}.")
    }
    if (kg == null && exercise.loading == LoadingType.ASSISTANCE) {
        return SetValidation.Invalid(SetField.KG, "Enter the assistance in $symbol (0 if none).")
    }
    val max = if (unit == LoadUnit.LB) MAX_LB else MAX_KG
    if (kg != null && (!kg.isFinite() || kg < 0 || kg > max)) {
        return SetValidation.Invalid(SetField.KG, "Weight must be between 0 and ${"%,d".format(max.toInt())} $symbol.")
    }
    val rir = parseDecimal(rirText)
    if (rirText.isNotBlank() && (rir == null || !rir.isFinite() || rir < 0 || rir > 10)) {
        return SetValidation.Invalid(SetField.RIR, "Reps in reserve must be 0–10, or leave it blank.")
    }
    return SetValidation.Valid(SetInput(kg, reps, rir, plates))
}

/** kg / reps / RIR row shared by live logging and corrections. */
@Composable
internal fun SetFields(
    exercise: Exercise,
    kg: String,
    onKg: (String) -> Unit,
    reps: String,
    onReps: (String) -> Unit,
    rir: String,
    onRir: (String) -> Unit,
    onDone: () -> Unit,
    tagPrefix: String,
    failure: SetValidation.Invalid? = null,
    validationAttempt: Int = 0,
    showRirHint: Boolean = true,
    unit: LoadUnit = LoadUnit.KG,
    // Dialogs stack the fields to show the message inside the failing one; live entry keeps its row and
    // shows the same message once below it.
    stackOnError: Boolean = true
) {
    if (exercise.loading == LoadingType.BODYWEIGHT) {
        Text(
            "Bodyweight exercise: no weight to enter.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    @Composable fun Fields(modifier: Modifier) {
        if (exercise.loading != LoadingType.BODYWEIGHT) {
            val symbol = unit.massSymbol()
            val label = when {
                unit == LoadUnit.PLATES -> "Plates"
                exercise.loading == LoadingType.ASSISTANCE -> "Assist $symbol"
                exercise.perDumbbell -> "$symbol/DB"
                else -> symbol
            }
            val mass = unit != LoadUnit.PLATES
            SetNumberField(kg, onKg, label, SetField.KG, failure, validationAttempt,
                modifier.testTag("$tagPrefix-${if (mass) "kg" else "plates"}"),
                decimal = mass, filterNumericInput = mass, showMessage = stackOnError)
        }
        SetNumberField(reps, onReps, "Reps", SetField.REPS, failure, validationAttempt,
            modifier.testTag("$tagPrefix-reps"), decimal = false, showMessage = stackOnError)
        SetNumberField(
            rir, onRir, "RIR", SetField.RIR, failure, validationAttempt, modifier.testTag("$tagPrefix-rir"),
            imeAction = ImeAction.Done, onDone = onDone, showMessage = stackOnError
        )
    }
    if (failure != null && stackOnError) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Fields(Modifier.fillMaxWidth()) }
    } else {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { Fields(Modifier.weight(1f)) }
    }
    // The failing field is focused and carries the error itself, so this visible copy is not a live region.
    if (failure != null && !stackOnError) Text(
        failure.message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(top = 4.dp)
    )
    if (showRirHint) Text("RIR optional", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun SetNumberField(
    value: String, onValue: (String) -> Unit, label: String, field: SetField,
    failure: SetValidation.Invalid?, validationAttempt: Int, modifier: Modifier,
    decimal: Boolean = true, imeAction: ImeAction = ImeAction.Next, onDone: () -> Unit = {},
    filterNumericInput: Boolean = true, showMessage: Boolean = true
) {
    val focus = androidx.compose.runtime.remember { androidx.compose.ui.focus.FocusRequester() }
    val bring = androidx.compose.runtime.remember { androidx.compose.foundation.relocation.BringIntoViewRequester() }
    val message = failure?.takeIf { it.field == field }?.message
    NumberField(value, onValue, label,
        modifier.focusRequester(focus).bringIntoViewRequester(bring),
        decimal = decimal, isError = message != null, supportingText = message?.takeIf { showMessage },
        imeAction = imeAction, onDone = onDone, filterNumericInput = filterNumericInput, errorSemantics = message)
    LaunchedEffect(validationAttempt) {
        if (message != null) {
            focus.requestFocus()
            bring.bringIntoView()
        }
    }
}

/**
 * Edit an existing set, or add a missing one ([set] == null) to a finished workout. With [groupId] it edits or
 * adds a stage of that drop or rest-pause set instead; stages are never warm-ups.
 * Existing sets keep their original time, so corrections never start a rest timer.
 */
@Composable
internal fun SetEditDialog(
    env: AppEnv,
    workoutId: String,
    workoutExercise: WorkoutExercise,
    set: LoggedSet?,
    newSetTime: Long,
    newSetId: String? = null,
    groupId: String? = set?.groupId,
    onDismiss: () -> Unit
) {
    // key() makes the saved draft specific to this set's current content, so a draft saved before a
    // background change is not restored over newer data after process recreation.
    val draftKey = set?.let { listOf(it.id, it.loadUnit, it.kg, it.lb, it.plates, it.reps, it.rir, it.warmup, it.groupId).joinToString("|") }
        ?: "${requireNotNull(newSetId)}|${workoutExercise.entryLoadUnit}|$groupId"
    key(draftKey) { SetEditDialogContent(env, workoutId, workoutExercise, set, newSetTime, newSetId, groupId, onDismiss) }
}

@Composable
private fun SetEditDialogContent(
    env: AppEnv,
    workoutId: String,
    workoutExercise: WorkoutExercise,
    set: LoggedSet?,
    newSetTime: Long,
    newSetId: String?,
    groupId: String?,
    onDismiss: () -> Unit
) {
    val exercise = workoutExercise.exercise
    val group = groupId?.let { id -> workoutExercise.setGroups.find { it.id == id } }
    val stages = groupId?.let { workoutExercise.stages(it) }.orEmpty()
    val stageNumber = set?.let { stages.indexOf(it) + 1 }?.takeIf { it > 0 } ?: (stages.size + 1)
    // The first stage is the set itself; the ones after it are its drops (or pauses), numbered from 1.
    val stageName = group?.let { if (stageNumber == 1) "set" else "${followUpLabel(it.method).lowercase()} ${stageNumber - 1}" }
    var unit by rememberSaveable {
        mutableStateOf(set?.loadUnit ?: stages.lastOrNull()?.loadUnit ?: workoutExercise.entryLoadUnit)
    }
    var kg by rememberSaveable { mutableStateOf(set?.loadValue?.let(::formatKg) ?: "") }
    var reps by rememberSaveable { mutableStateOf(set?.reps?.toString() ?: "") }
    var rir by rememberSaveable { mutableStateOf(set?.rir?.let(::formatKg) ?: "") }
    var warmup by rememberSaveable { mutableStateOf(set?.warmup ?: false) }
    val setId = set?.id ?: requireNotNull(newSetId)
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var validation by remember { mutableStateOf<SetValidation.Invalid?>(null) }
    var validationAttempt by remember { mutableStateOf(0) }
    // A restored "add missed set" dialog whose set already committed is finished; saving again would overwrite it.
    val alreadyAdded = set == null && workoutExercise.sets.any { it.id == setId }
    // A stage whose set was deleted underneath has nothing left to belong to.
    val groupGone = groupId != null && group == null
    LaunchedEffect(alreadyAdded, groupGone) { if (alreadyAdded || groupGone) onDismiss() }
    if (alreadyAdded || groupGone) return

    if (confirmDelete && set != null) {
        ConfirmDialog(
            title = if (stageName != null) "Delete $stageName?" else "Delete this set?",
            text = if (group != null) {
                "${setText(set, exercise)} will be removed from this ${group.method.title.lowercase()}. The other " +
                    "stages keep their values and times." + (if (stages.size == 1) " It is the only stage, so the set is removed." else "") +
                    " This can't be undone."
            } else "${setText(set, exercise)} will be removed from ${exercise.name}. This can't be undone.",
            confirmLabel = if (group != null) "Delete stage" else "Delete set",
            destructive = true,
            busy = env.runner.busy,
            error = error,
            onDismiss = { confirmDelete = false },
            onConfirm = {
                env.runner.run(
                    Action.DeleteSet(workoutId, workoutExercise.id, set.id),
                    onError = { error = it }
                ) { onDismiss() }
            }
        )
        return
    }

    fun save() {
        val parsed = validateSet(exercise, kg, reps, rir, unit)
        val input = when (parsed) {
            is SetValidation.Invalid -> { validation = parsed; validationAttempt++; return }
            is SetValidation.Valid -> parsed.input
        }
        validation = null
        error = null
        val action = if (set == null && groupId != null) {
            Action.AddStage(workoutId, workoutExercise.id, groupId, setId, input.kg, input.reps, input.rir, newSetTime,
                unit, input.plates)
        } else {
            Action.SaveSet(
                workoutId = workoutId,
                exerciseId = workoutExercise.id,
                setId = setId,
                kg = input.kg,
                reps = input.reps,
                rir = input.rir,
                warmup = warmup && groupId == null,
                now = set?.completedAt ?: newSetTime,
                loadUnit = unit,
                plates = input.plates
            )
        }
        env.runner.run(action, onError = { error = it }) { onDismiss() }
    }

    AdaptiveDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(when {
                group != null && set == null -> "Add a missed ${followUpLabel(group.method).lowercase()}"
                group != null -> "Edit $stageName"
                set == null -> "Add a missed set"
                else -> "Edit set"
            })
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("${exercise.name} · ${loadingDescription(exercise, unit)}", style = MaterialTheme.typography.bodyMedium)
                if (exercise.takesLoad) {
                    LoadUnitSelector(unit, exercise.loadUnitOptions(), enabled = !env.runner.busy) {
                        if (it != unit) { unit = it; kg = ""; validation = null }
                    }
                    if (unit == LoadUnit.PLATES) Text(PLATES_HELP, style = MaterialTheme.typography.bodySmall)
                }
                SetFields(exercise, kg, { kg = it }, reps, { reps = it }, rir, { rir = it }, ::save, "edit",
                    validation, validationAttempt, unit = unit)
                if (group == null) {
                    CheckRow("Warm-up set", warmup, { warmup = it })
                }
                ErrorText(error)
                if (set != null) {
                    TextButton(
                        onClick = { confirmDelete = true },
                        enabled = !env.runner.busy,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) { Text(if (group != null) "Delete stage" else "Delete set") }
                }
            }
        },
        confirmButton = { TextButton(onClick = ::save, enabled = !env.runner.busy) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
internal fun CustomExerciseDialog(
    env: AppEnv,
    onDismiss: () -> Unit,
    measure: Measure? = null,
    onCreated: (Exercise) -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    var chosenMeasure by rememberSaveable { mutableStateOf(measure ?: Measure.REPS) }
    val how = measure ?: chosenMeasure
    var loading by rememberSaveable { mutableStateOf(LoadingType.EXTERNAL) }
    var perDumbbell by rememberSaveable { mutableStateOf(false) }
    var equipment by rememberSaveable { mutableStateOf(Equipment.UNKNOWN) }
    var muscle by rememberSaveable { mutableStateOf(MuscleGroup.UNKNOWN) }
    val id by rememberSaveable { mutableStateOf("custom-${newId()}") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    fun save() {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            error = "Give the exercise a name."
            return
        }
        // Cardio never records a load, so it is saved as bodyweight.
        val loadedAs = if (how == Measure.CARDIO) LoadingType.BODYWEIGHT else loading
        val exercise = Exercise(id, trimmed, loadedAs, perDumbbell && loadedAs == LoadingType.EXTERNAL, custom = true,
            equipment = equipment, primaryMuscle = muscle, measure = how,
            // New exercises start in the lifting unit chosen in More; plates are a choice made per machine.
            preferredLoadUnit = env.state.liftingUnit.loadUnit)
        error = null
        env.runner.run(Action.AddExercise(exercise), onError = { error = it }) { onCreated(exercise) }
    }

    AdaptiveDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom exercise") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("How is it measured?", style = MaterialTheme.typography.labelLarge)
                if (measure == null) {
                    Column(Modifier.selectableGroup().testTag("custom-measure")) {
                        RadioRow("Sets of reps", how == Measure.REPS, { chosenMeasure = Measure.REPS },
                            supporting = "Reps with a load, like most strength exercises")
                        RadioRow("Timed sets", how == Measure.TIMED, { chosenMeasure = Measure.TIMED },
                            supporting = "Holds such as a plank: each set's time, with a load if it has one")
                        RadioRow("Distance sets", how == Measure.DISTANCE, { chosenMeasure = Measure.DISTANCE },
                            supporting = "Carries and sled pushes: each set's distance, with its time and load if wanted")
                        RadioRow("Cardio", how == Measure.CARDIO, { chosenMeasure = Measure.CARDIO },
                            supporting = "Running, cycling or rowing: time, distance or both, never a load")
                    }
                } else {
                    Text("${customMeasureTitle(measure)}, like the exercise it replaces.",
                        style = MaterialTheme.typography.bodyMedium)
                }
                if (how != Measure.CARDIO) {
                    Text("How is it loaded?", style = MaterialTheme.typography.labelLarge)
                    Column(Modifier.selectableGroup()) {
                        RadioRow("External weight", loading == LoadingType.EXTERNAL, { loading = LoadingType.EXTERNAL },
                            supporting = "Kg, or plates for a cable or selectorized stack")
                        RadioRow("Bodyweight", loading == LoadingType.BODYWEIGHT, { loading = LoadingType.BODYWEIGHT },
                            supporting = if (how == Measure.REPS) {
                                "Reps only; add a separate external exercise for weighted versions"
                            } else "No weight recorded; add a separate external exercise for weighted versions")
                        RadioRow("Assisted", loading == LoadingType.ASSISTANCE, { loading = LoadingType.ASSISTANCE },
                            supporting = "Log the assistance kg; less assistance is harder")
                    }
                }
                EnumChoice("Equipment: ${equipment.title}", Equipment.entries, { it.title }) {
                    equipment = it
                    if (it != Equipment.DUMBBELL && it != Equipment.UNKNOWN) perDumbbell = false
                }
                EnumChoice("Primary muscle: ${muscle.title}", MuscleGroup.entries, { it.title }) { muscle = it }
                Text("Choose Cable or Selectorized stack only for physical stack plates. " +
                    "Leave metadata unknown if unsure.", style = MaterialTheme.typography.bodySmall)
                if (how != Measure.CARDIO) CheckRow(
                    "kg per dumbbell", perDumbbell && loading == LoadingType.EXTERNAL, { perDumbbell = it },
                    enabled = loading == LoadingType.EXTERNAL &&
                        (equipment == Equipment.UNKNOWN || equipment == Equipment.DUMBBELL),
                    supporting = "Log the weight of one dumbbell"
                )
                Text(
                    "How it is measured can't change later. Each loading type keeps its own history so results stay " +
                        "comparable.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ErrorText(error)
            }
        },
        confirmButton = { TextButton(onClick = ::save, enabled = !env.runner.busy) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/**
 * Shared editor for today's changes and saved plan changes: exercise choice, set count,
 * rest, and an explicit acknowledgement when going above the advisory upper.
 */
@Composable
internal fun TargetEditorFields(
    env: AppEnv,
    options: List<Exercise>,
    selectedId: String,
    onSelect: (String) -> Unit,
    swapLocked: String?,
    sets: Int,
    onSets: (Int) -> Unit,
    rest: String,
    onRest: (String) -> Unit,
    baselineSets: Int?,
    upperSets: Int?,
    allowAboveCap: Boolean,
    onAllowAboveCap: (Boolean) -> Unit,
    measure: Measure = Measure.REPS
) {
    var addingCustom by rememberSaveable { mutableStateOf(false) }
    var browsing by rememberSaveable { mutableStateOf(false) }
    Text("Exercise", style = MaterialTheme.typography.labelLarge)
    if (swapLocked != null) {
        Text(swapLocked, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Column(Modifier.selectableGroup()) {
        // Only exercises measured the same way can take its place.
        (options + listOfNotNull(env.exercise(selectedId))).distinctBy { it.id }
            .filter { it.measure == measure || it.id == selectedId }.forEach { option ->
            RadioRow(
                text = option.name,
                supporting = loadingShort(option) + if (option.custom) " · custom" else "",
                selected = option.id == selectedId,
                enabled = swapLocked == null || option.id == selectedId,
                onClick = { onSelect(option.id) }
            )
        }
    }
    if (swapLocked == null) {
        OutlinedButton(onClick = { browsing = true }) { Text("All exercises") }
        TextButton(onClick = { addingCustom = true }) { Text("Add custom exercise") }
    }
    HorizontalDivider()
    // Cardio has a total time or distance instead of sets.
    if (measure != Measure.CARDIO) {
        Stepper(
            when (measure) {
                Measure.TIMED -> "Timed sets"
                Measure.DISTANCE -> "Distance sets"
                else -> "Working sets"
            },
            sets, TARGET_SETS_RANGE, onSets
        )
        if (baselineSets != null) Text(
            "Baseline $baselineSets" + (upperSets?.let { " · advisory upper $it (optional, not a target)" } ?: ""),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (upperSets != null && sets > upperSets) {
            Text(
                "$sets sets is above the advisory upper of $upperSets. More volume isn't needed for progress and adds fatigue.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
            CheckRow("I understand: allow $sets sets", allowAboveCap, onAllowAboveCap, modifier = Modifier.testTag("allow-above-cap"))
        }
    }
    NumberField(rest, onRest, if (measure == Measure.CARDIO) "Rest when you start one (seconds)" else "Rest between sets (seconds)",
        Modifier.fillMaxWidth(), decimal = false, imeAction = ImeAction.Done)
    if (addingCustom) {
        CustomExerciseDialog(env, onDismiss = { addingCustom = false }, measure = measure) {
            addingCustom = false
            onSelect(it.id)
        }
    }
    if (browsing) ExerciseLibraryPicker(env, onDismiss = { browsing = false }, measure = measure) {
        browsing = false
        onSelect(it.id)
    }
}

/** "Sets of reps", "Timed sets", "Distance sets" or "Cardio": how an exercise is measured, as the library says it. */
internal fun customMeasureTitle(measure: Measure): String = when (measure) {
    Measure.REPS -> "Sets of reps"
    Measure.TIMED -> "Timed sets"
    Measure.DISTANCE -> "Distance sets"
    Measure.CARDIO -> "Cardio"
}

internal fun parseRest(text: String): Int? = text.trim().toIntOrNull()?.takeIf { it in REST_SECONDS_RANGE }
