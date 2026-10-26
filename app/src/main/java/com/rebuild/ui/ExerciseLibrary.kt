package com.rebuild.ui

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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
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
    measure: Measure? = null
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
                    Text(loadingDescription(exercise, exercise.preferredLoadUnit))
                    Text("To use it, add it to a workout or a routine.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (exercise.custom) SecondaryTextButton(onClick = { editingMetadata = exercise.id }) {
                        Text("Edit equipment and muscle")
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
    var equipment by rememberSaveable(exercise.id) { mutableStateOf(exercise.equipment) }
    var muscle by rememberSaveable(exercise.id) { mutableStateOf(exercise.primaryMuscle) }
    var error by rememberSaveable(exercise.id) { mutableStateOf<String?>(null) }
    AdaptiveDialog(
        onDismissRequest = onDismiss,
        title = { Text("Equipment and muscle") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(exercise.name)
                EnumChoice("Equipment: ${equipment.title}", Equipment.entries.filter {
                    !exercise.perDumbbell || it == Equipment.UNKNOWN || it == Equipment.DUMBBELL
                }, { it.title }) { equipment = it }
                EnumChoice("Primary muscle: ${muscle.title}", MuscleGroup.entries, { it.title }) { muscle = it }
                Text("Cable and Selectorized stack can log plates per stack.\nApplies to future workouts. Saved sets never change.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                ErrorText(error)
            }
        },
        confirmButton = {
            TextButton(enabled = !env.runner.busy, onClick = {
                var updated = exercise.copy(equipment = equipment, primaryMuscle = muscle)
                if (updated.preferredLoadUnit !in updated.loadUnitOptions()) updated = updated.copy(preferredLoadUnit = LoadUnit.KG)
                env.runner.run(Action.AddExercise(updated), onError = { error = it }, onSuccess = onDismiss)
            }) { Text("Save metadata") }
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
    onSelect: (Exercise) -> Unit
) {
    Dialog(onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
            KeyboardFirstBack(onDismiss, Modifier.fillMaxSize()) {
                ExerciseLibraryScreen(env, onDismiss, onSelect, title, marked, markedLabel, measure)
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
    onChoose: (Exercise) -> Unit,
    onAdd: () -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var muscle by rememberSaveable { mutableStateOf<MuscleGroup?>(null) }
    var equipment by rememberSaveable { mutableStateOf<Equipment?>(null) }
    var chosenMeasure by rememberSaveable { mutableStateOf<Measure?>(null) }
    // Where only one kind of exercise fits (swapping a planned one), the others are never offered.
    val measure = fixedMeasure ?: chosenMeasure
    val results = searchExercises(exercises, query, muscle, equipment, measure)
    LazyColumn(Modifier.fillMaxHeight().readableWidth().yieldsToFixedControls().testTag("library-results"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
        item {
            OutlinedTextField(
                query, { query = it },
                label = { Text("Search exercises") },
                leadingIcon = { RebuildIcon(R.drawable.ic_search, contentDescription = null) },
                trailingIcon = if (query.isEmpty()) null else {
                    { IconButton(onClick = { query = "" }) { RebuildIcon(R.drawable.ic_x, contentDescription = "Clear search") } }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("library-search")
            )
        }
        item {
            FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                FilterDropdown(muscle, "Muscle", listOf(null) + MuscleGroup.entries, { it?.title ?: "All muscles" },
                    Modifier.testTag("library-filter-muscle")) { muscle = it }
                FilterDropdown(equipment, "Equipment", listOf(null) + Equipment.entries, { it?.title ?: "All equipment" },
                    Modifier.testTag("library-filter-equipment")) { equipment = it }
                if (fixedMeasure == null) {
                    FilterDropdown(chosenMeasure, "Type", listOf(null) + Measure.entries, ::measureFilterTitle,
                        Modifier.testTag("library-filter-type")) { chosenMeasure = it }
                }
                if (muscle != null || equipment != null || chosenMeasure != null) {
                    SecondaryTextButton(onClick = { query = ""; muscle = null; equipment = null; chosenMeasure = null }) {
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
                SecondaryTextButton(onClick = onAdd, icon = R.drawable.ic_plus) { Text("Add custom exercise") }
            }
            HorizontalDivider()
        }
        if (results.isEmpty()) item {
            Column(Modifier.padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("No exercises match.", style = MaterialTheme.typography.bodyLarge)
                Text("Try another name or add a custom exercise.", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (query.isNotEmpty() || muscle != null || equipment != null || chosenMeasure != null) {
                    SecondaryTextButton(onClick = { query = ""; muscle = null; equipment = null; chosenMeasure = null }) {
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
    "${exercise.primaryMuscle.title} · ${exercise.equipment.title}" + if (exercise.custom) " · custom" else ""

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
        FilterChip(
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

@Composable
internal fun <T> EnumChoice(label: String, options: List<T>, title: (T) -> String, onSelect: (T) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    Column {
        OutlinedButton(
            onClick = { open = true },
            modifier = Modifier.heightIn(min = 48.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 12.dp)
        ) {
            Text(label)
            RebuildIcon(R.drawable.ic_chevron_down, contentDescription = null,
                modifier = Modifier.padding(start = 6.dp).size(18.dp))
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
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            FilterChip(selected = unit == option, onClick = { onSelect(option) }, enabled = enabled,
                label = { Text(if (option == LoadUnit.PLATES) "Plates per stack" else unitName(option)) },
                modifier = Modifier.testTag("unit-${option.name}")
                    .semantics { contentDescription = if (option == LoadUnit.LB) "Pounds" else if (option == LoadUnit.KG)
                        "Kilograms" else "Plates per stack" })
        }
    }
}
