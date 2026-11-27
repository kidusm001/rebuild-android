package com.rebuild.ui

import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import com.rebuild.domain.takesLoad
import com.rebuild.domain.loadUnitOptions
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.rebuild.R
import com.rebuild.domain.Action
import com.rebuild.domain.Equipment
import com.rebuild.domain.Exercise
import com.rebuild.domain.LoadUnit
import com.rebuild.domain.Measure
import com.rebuild.domain.MuscleGroup
import com.rebuild.domain.measured
import com.rebuild.domain.searchExercises

internal const val PLATES_HELP = "Count physical plates per stack, not both stacks added together. " +
    "Use the same machine for comparisons: machines vary and Rebuild never converts plates to kg."

@Composable
internal fun ExerciseLibraryScreen(
    env: AppEnv,
    onBack: () -> Unit,
    onSelect: ((Exercise) -> Unit)? = null,
    title: String? = null,
    marked: Set<String> = emptySet(),
    markedLabel: String = "in this workout",
    measure: Measure? = null,
    initialMuscle: MuscleGroup? = null
) {
    var detailId by rememberSaveable { mutableStateOf<String?>(null) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var editingMetadata by rememberSaveable { mutableStateOf<String?>(null) }
    var unitError by rememberSaveable(detailId) { mutableStateOf<String?>(null) }
    ScreenScaffold(title = title ?: if (onSelect == null) "Exercises" else "Choose exercise", onBack = onBack) {
        ExerciseLibraryContent(
            env.state.exercises,
            marked,
            markedLabel,
            fixedMeasure = measure,
            initialMuscle = initialMuscle,
            onChoose = { if (onSelect != null) onSelect(it) else detailId = it.id },
            onAdd = { adding = true }
        )
    }
    env.exercise(detailId.orEmpty())?.let { exercise ->
        AdaptiveDialog(
            onDismissRequest = { detailId = null },
            title = { Text(exercise.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(exerciseMetadata(exercise))
                    if (exercise.custom && !exercise.hasMetadata) Text(
                        "Set its muscle and equipment so it shows up beside similar exercises when you swap.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(loadingDescription(exercise, exercise.preferredLoadUnit))
                    Text("To use it, add it to a workout or a routine.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (exercise.custom) SecondaryButton(onClick = { editingMetadata = exercise.id }) {
                        Text(if (exercise.hasMetadata) "Edit muscle and equipment" else "Set muscle and equipment")
                    }
                    if (exercise.takesLoad) {
                        Text("Load unit for future workouts", style = MaterialTheme.typography.labelLarge)
                        LoadUnitSelector(exercise.preferredLoadUnit, exercise.loadUnitOptions(), enabled = !env.runner.busy) {
                            unitError = null
                            env.runner.run(Action.SetExerciseLoadUnit(exercise.id, it), onError = { unitError = it })
                        }
                        if (exercise.supportsPlates) Text(PLATES_HELP, style = MaterialTheme.typography.bodySmall)
                        ErrorText(unitError)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { detailId = null }) { Text("Done") } },
            dismissButton = {}
        )
    }
    if (adding) CustomExerciseDialog(env, onDismiss = { adding = false }, measure = measure) {
        adding = false
        if (onSelect != null) onSelect(it) else detailId = it.id
    }
    env.exercise(editingMetadata.orEmpty())?.let { exercise ->
        CustomMetadataDialog(env, exercise) { editingMetadata = null }
    }
}

@Composable
private fun CustomMetadataDialog(env: AppEnv, exercise: Exercise, onDismiss: () -> Unit) {
    // Weight per dumbbell only makes sense on a dumbbell, so an older exercise saved that way starts there.
    var equipment by rememberSaveable(exercise.id) {
        mutableStateOf(if (exercise.perDumbbell && exercise.equipment == Equipment.UNKNOWN) Equipment.DUMBBELL
        else exercise.equipment)
    }
    var muscle by rememberSaveable(exercise.id) { mutableStateOf(exercise.primaryMuscle) }
    var error by rememberSaveable(exercise.id) { mutableStateOf<String?>(null) }
    AdaptiveDialog(
        onDismissRequest = onDismiss,
        title = { Text("Muscle and equipment") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(exercise.name)
                Column {
                    EnumChoice("Primary muscle", muscleChoice(muscle), knownMuscles, { it.title }, index = 0, count = 2,
                        unset = muscle == MuscleGroup.UNKNOWN, missing = error != null) {
                        muscle = it
                        if (error != null) error = metadataMissing(equipment, it)
                    }
                    EnumChoice("Equipment", equipmentChoice(equipment), knownEquipment.filter {
                        !exercise.perDumbbell || it == Equipment.DUMBBELL
                    }, { it.title }, index = 1, count = 2, unset = equipment == Equipment.UNKNOWN,
                        missing = error != null) {
                        equipment = it
                        if (error != null) error = metadataMissing(it, muscle)
                    }
                    if (error != null) androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 6.dp))
                    ErrorText(error)
                }
                Text((if (equipment == Equipment.CABLE || equipment == Equipment.STACK_MACHINE)
                    "Cable and Stack machine can log plates per stack.\n" else "") +
                    "Applies to future workouts. Saved sets never change.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(enabled = !env.runner.looksBusy(), onClick = {
                metadataMissing(equipment, muscle)?.let { error = it; return@TextButton }
                var updated = exercise.copy(equipment = equipment, primaryMuscle = muscle)
                if (updated.preferredLoadUnit !in updated.loadUnitOptions()) {
                    // Leaving plates falls back to the lifting unit chosen in More, as a new exercise starts.
                    updated = updated.copy(preferredLoadUnit = env.state.liftingUnit.loadUnit)
                }
                env.runner.run(Action.AddExercise(updated), onError = { error = it }, onSuccess = onDismiss)
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
internal fun ExerciseLibraryPicker(
    env: AppEnv,
    onDismiss: () -> Unit,
    title: String? = null,
    marked: Set<String> = emptySet(),
    markedLabel: String = "in this workout",
    measure: Measure? = null,
    initialMuscle: MuscleGroup? = null,
    onSelect: (Exercise) -> Unit
) {
    Dialog(onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
            KeyboardFirstBack(onDismiss, Modifier.fillMaxSize()) {
                ExerciseLibraryScreen(env, onDismiss, onSelect, title, marked, markedLabel, measure, initialMuscle)
            }
        }
    }
}

/** A type filter's choice: how the exercises it keeps are measured. */
private fun measureFilterTitle(measure: Measure?): String = when (measure) {
    null -> "All types"
    Measure.REPS -> "Sets of reps"
    Measure.TIMED -> "Timed"
    Measure.DISTANCE -> "Distance"
    Measure.CARDIO -> "Cardio"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExerciseLibraryContent(
    exercises: List<Exercise>,
    marked: Set<String>,
    markedLabel: String,
    fixedMeasure: Measure?,
    initialMuscle: MuscleGroup? = null,
    onChoose: (Exercise) -> Unit,
    onAdd: () -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var muscle by rememberSaveable(initialMuscle) { mutableStateOf(initialMuscle) }
    var equipment by rememberSaveable { mutableStateOf<Equipment?>(null) }
    var chosenMeasure by rememberSaveable { mutableStateOf<Measure?>(null) }
    // Where only one kind of exercise fits (swapping a planned one), the others are never offered.
    val measure = fixedMeasure ?: chosenMeasure
    // Older custom exercises still missing a muscle or equipment; in a picker, only those it could offer.
    var unfinishedOnly by rememberSaveable { mutableStateOf(false) }
    val needing = exercises.filter { !it.hasMetadata && (fixedMeasure == null || it.measure == fixedMeasure) }
    val unfinished = needing.size
    val noMuscle = exercises.count { it.primaryMuscle == MuscleGroup.UNKNOWN }
    if (unfinishedOnly && unfinished == 0) LaunchedEffect(Unit) { unfinishedOnly = false }
    // A "not set" filter goes away with its last exercise, so the list never strands on an option that's gone.
    if (muscle == MuscleGroup.UNKNOWN && noMuscle == 0) LaunchedEffect(Unit) { muscle = null }
    if (equipment == Equipment.UNKNOWN && exercises.none { it.equipment == Equipment.UNKNOWN }) {
        LaunchedEffect(Unit) { equipment = null }
    }
    val results = if (unfinishedOnly) needing.sortedBy { it.name.lowercase() }
    else searchExercises(exercises, query, muscle, equipment, measure)
    LazyColumn(Modifier.fillMaxHeight().readableWidth().yieldsToFixedControls().testTag("library-results"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
        item {
            // A filled search pill, like the app's other fields, rather than a lone outlined box.
            val fill = tonalFill()
            androidx.compose.material3.TextField(
                query, { query = it; unfinishedOnly = false },
                placeholder = { Text("Search exercises") },
                shape = androidx.compose.foundation.shape.CircleShape,
                colors = androidx.compose.material3.TextFieldDefaults.colors(
                    focusedContainerColor = fill, unfocusedContainerColor = fill,
                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    cursorColor = accentText()
                ),
                leadingIcon = { RebuildIcon(R.drawable.ic_search, contentDescription = null) },
                trailingIcon = if (query.isEmpty()) null else {
                    { IconButton(onClick = { query = "" }) { RebuildIcon(R.drawable.ic_x, contentDescription = "Clear search") } }
                },
                singleLine = true,
                // A placeholder disappears once typing starts, so the field keeps its name for screen readers.
                modifier = Modifier.fillMaxWidth().testTag("library-search")
                    .semantics { contentDescription = "Search exercises" }
            )
        }
        // Older custom exercises made before muscle and equipment were required: one quiet line until they're set.
        // Show lists every one of them, whichever field it lacks, clearing the search and filters that could hide one.
        if (unfinished > 0 && (unfinishedOnly || muscle == null && equipment == null)) item {
            Row(
                Modifier.padding(top = 8.dp).fillMaxWidth().heightIn(min = 56.dp).clip(MaterialTheme.shapes.large)
                    .background(if (unfinishedOnly) MaterialTheme.colorScheme.secondaryContainer else tonalFill())
                    .clickable(role = Role.Button, onClickLabel = if (unfinishedOnly) "Show all exercises" else "Show them") {
                        if (!unfinishedOnly) {
                            query = ""; muscle = null; equipment = null; chosenMeasure = null
                        }
                        unfinishedOnly = !unfinishedOnly
                    }
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .testTag("library-unfinished"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val missing = when {
                    needing.all { it.equipment != Equipment.UNKNOWN } -> "a muscle"
                    needing.all { it.primaryMuscle != MuscleGroup.UNKNOWN } -> "equipment"
                    else -> "a muscle or equipment"
                }
                Text(
                    if (unfinished == 1) "1 exercise needs $missing" else "$unfinished exercises need $missing",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(if (unfinishedOnly) "Show all" else "Show", style = MaterialTheme.typography.labelLarge, color = accentText(),
                    modifier = Modifier.padding(start = 12.dp))
            }
        }
        item {
            FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                FilterDropdown(muscle, "Muscle", listOf(null) + knownMuscles +
                    listOfNotNull(MuscleGroup.UNKNOWN.takeIf { exercises.any { it.primaryMuscle == MuscleGroup.UNKNOWN } }),
                    { if (it == MuscleGroup.UNKNOWN) "Muscle not set" else it?.title ?: "All muscles" },
                    Modifier.testTag("library-filter-muscle")) { muscle = it; unfinishedOnly = false }
                FilterDropdown(equipment, "Equipment", listOf(null) + knownEquipment +
                    listOfNotNull(Equipment.UNKNOWN.takeIf { exercises.any { it.equipment == Equipment.UNKNOWN } }),
                    { if (it == Equipment.UNKNOWN) "Equipment not set" else it?.title ?: "All equipment" },
                    Modifier.testTag("library-filter-equipment")) { equipment = it; unfinishedOnly = false }
                if (fixedMeasure == null) {
                    FilterDropdown(chosenMeasure, "Type", listOf(null) + Measure.entries, ::measureFilterTitle,
                        Modifier.testTag("library-filter-type")) { chosenMeasure = it; unfinishedOnly = false }
                }
                if (muscle != null || equipment != null || chosenMeasure != null) {
                    SecondaryButton(onClick = { query = ""; muscle = null; equipment = null; chosenMeasure = null }) {
                        Text("Reset filters")
                    }
                }
            }
            if (fixedMeasure != null) Text(
                (if (fixedMeasure == Measure.REPS) "Sets of reps only" else "${fixedMeasure.title} only") +
                    ": a swap is measured the same way.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp).testTag("library-measure-note")
            )
        }
        item {
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                itemVerticalAlignment = Alignment.CenterVertically
            ) {
                Text(countLabel(results.size, "exercise"), style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                SecondaryButton(onClick = onAdd, icon = R.drawable.ic_plus) { Text("New exercise") }
            }
            HorizontalDivider()
        }
        if (results.isEmpty()) item {
            Column(Modifier.padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("No exercises match.", style = MaterialTheme.typography.bodyLarge)
                Text("Try another name or add a custom exercise.", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (query.isNotEmpty() || muscle != null || equipment != null || chosenMeasure != null) {
                    SecondaryButton(onClick = { query = ""; muscle = null; equipment = null; chosenMeasure = null }) {
                        Text("Reset filters")
                    }
                }
            }
        }
        items(results, key = { it.id }) { exercise ->
            // Choosing one that is already there opens it instead of adding it twice.
            val metadata = libraryRowMetadata(exercise) + if (exercise.id in marked) " · $markedLabel" else ""
            ListRow(exercise.name, supporting = metadata, label = "${exercise.name}, $metadata",
                modifier = Modifier.testTag("library-${exercise.id}"), onClick = { onChoose(exercise) })
            // A long flat list of two-line rows: the hairlines show where one exercise ends and the next begins.
            HorizontalDivider()
        }
    }
}

/**
 * Library rows also say how a timed, distance or cardio exercise is measured, and when a load can be recorded as
 * whole plates per stack instead of kg.
 */
private fun libraryRowMetadata(exercise: Exercise): String =
    exerciseMetadata(exercise) + (if (exercise.measured) " · ${exercise.measure.title.lowercase()}" else "") +
        if (exercise.supportsPlates) " · kg or plates" else ""

internal fun exerciseMetadata(exercise: Exercise): String =
    (if (exercise.hasMetadata) "${exercise.primaryMuscle.title} · ${exercise.equipment.title}"
    else listOfNotNull(exercise.primaryMuscle.takeIf { it != MuscleGroup.UNKNOWN }?.title,
        exercise.equipment.takeIf { it != Equipment.UNKNOWN }?.title, "needs ${missingMetadata(exercise)}")
        .joinToString(" · ")) + if (exercise.custom) " · custom" else ""

/** Every exercise names its muscle and equipment; only custom ones made before both were required can lack them. */
internal val Exercise.hasMetadata: Boolean
    get() = primaryMuscle != MuscleGroup.UNKNOWN && equipment != Equipment.UNKNOWN

private fun missingMetadata(exercise: Exercise): String = when {
    exercise.primaryMuscle == MuscleGroup.UNKNOWN && exercise.equipment == Equipment.UNKNOWN -> "muscle and equipment"
    exercise.primaryMuscle == MuscleGroup.UNKNOWN -> "a muscle"
    else -> "equipment"
}

/** The choices offered for a new or edited exercise: never "not set". */
internal val knownEquipment: List<Equipment> = Equipment.entries.filter { it != Equipment.UNKNOWN }
internal val knownMuscles: List<MuscleGroup> = MuscleGroup.entries.filter { it != MuscleGroup.UNKNOWN }
internal fun equipmentChoice(equipment: Equipment) = if (equipment == Equipment.UNKNOWN) "Choose" else equipment.title
internal fun muscleChoice(muscle: MuscleGroup) = if (muscle == MuscleGroup.UNKNOWN) "Choose" else muscle.title

/** Why an exercise can't be saved yet, or null once both its muscle and equipment are chosen. */
internal fun metadataMissing(equipment: Equipment, muscle: MuscleGroup): String? = when {
    muscle == MuscleGroup.UNKNOWN && equipment == Equipment.UNKNOWN -> "Choose its primary muscle and equipment."
    muscle == MuscleGroup.UNKNOWN -> "Choose its primary muscle."
    equipment == Equipment.UNKNOWN -> "Choose its equipment."
    else -> null
}

/**
 * A library filter as a chip: selected while it narrows the list, with a chevron that opens its choices. The label is
 * the active choice, or the filter's name while it is off; its menu starts with the "All …" choice that turns it off.
 */
@Composable
private fun <T> FilterDropdown(
    value: T?,
    allLabel: String,
    options: List<T?>,
    title: (T?) -> String,
    modifier: Modifier = Modifier,
    onSelect: (T?) -> Unit
) {
    var open by rememberSaveable { mutableStateOf(false) }
    Box {
        TonalChip(
            selected = value != null,
            onClick = { open = true },
            label = { Text(if (value == null) allLabel else title(value)) },
            trailingIcon = {
                RebuildIcon(R.drawable.ic_chevron_down, contentDescription = null, modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            },
            // The chip keeps Material's 48 dp touch target around its 32 dp body.
            modifier = modifier.semantics { contentDescription = "$allLabel filter: ${title(value)}" }
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(title(option)) }, onClick = { open = false; onSelect(option) })
            }
        }
    }
}

/**
 * One setting with a few fixed choices, as a row of a grouped list ([index] of [count]): its name, its value in the
 * accent and a chevron; a tap opens the choices beneath it. Spoken as "Name: value".
 */
@Composable
internal fun <T> EnumChoice(
    name: String,
    value: String,
    options: List<T>,
    title: (T) -> String,
    modifier: Modifier = Modifier,
    index: Int = 0,
    count: Int = 1,
    /** Nothing is chosen yet: the value reads "Choose" in the muted colour instead of the accent. */
    unset: Boolean = false,
    /** Saving was tried with nothing chosen: "Choose" turns the error colour. */
    missing: Boolean = false,
    onSelect: (T) -> Unit
) {
    var open by rememberSaveable { mutableStateOf(false) }
    val valueColor = when {
        unset && missing -> MaterialTheme.colorScheme.error
        unset -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> accentText()
    }
    val scheme = MaterialTheme.colorScheme
    Box(modifier.padding(top = if (index > 0) 2.dp else 0.dp)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(groupShape(index, count - 1)).background(tonalFill())
                .clickable(role = androidx.compose.ui.semantics.Role.Button, onClickLabel = "Choose $name") { open = true }
                .clearAndSetSemantics { contentDescription = if (unset) "$name: not chosen" else "$name: $value"
                    if (unset && missing) error("Required") }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Above 130% text the value sits under its name, so neither is cut to fit beside the other.
            if (androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.3f) {
                Column(Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.bodyLarge, color = scheme.onSurface)
                    Text(value, style = MaterialTheme.typography.titleSmall, color = valueColor)
                }
            } else {
                Text(name, style = MaterialTheme.typography.bodyLarge, color = scheme.onSurface, modifier = Modifier.weight(1f))
                Text(value, style = MaterialTheme.typography.titleSmall, color = valueColor, maxLines = 1,
                    modifier = Modifier.padding(start = 12.dp))
            }
            RebuildIcon(R.drawable.ic_chevron_down, contentDescription = null, tint = scheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp).size(18.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(title(option)) }, onClick = { open = false; onSelect(option) })
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LoadUnitSelector(
    unit: LoadUnit,
    options: List<LoadUnit>,
    enabled: Boolean = true,
    onSelect: (LoadUnit) -> Unit
) {
    // One choice from a small set: pills in a tonal track; the chosen one is filled ink (amber in dark).
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clip(CircleShape).background(colors.surfaceContainerHigh).padding(4.dp).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEach { option ->
            val selected = unit == option
            Box(
                Modifier.weight(1f).heightIn(min = 48.dp).clip(CircleShape)
                    .background(if (selected) colors.secondaryContainer else Color.Transparent)
                    .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, interactionSource = null,
                        indication = com.rebuild.ui.theme.FocusHighlight) { onSelect(option) }
                    .testTag("unit-${option.name}")
                    .semantics { contentDescription = if (option == LoadUnit.LB) "Pounds" else if (option == LoadUnit.KG)
                        "Kilograms" else "Plates per stack" },
                contentAlignment = Alignment.Center
            ) {
                Text(if (option == LoadUnit.PLATES) "Plates" else unitName(option), maxLines = 1,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) colors.onSecondaryContainer else colors.onSurface)
            }
        }
    }
}
