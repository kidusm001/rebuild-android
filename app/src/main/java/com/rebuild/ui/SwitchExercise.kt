package com.rebuild.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.rebuild.R
import com.rebuild.domain.Action
import com.rebuild.domain.Equipment
import com.rebuild.domain.Gear
import com.rebuild.domain.MuscleGroup
import com.rebuild.domain.Stopwatch
import com.rebuild.domain.SwapGroup
import com.rebuild.domain.SwapSuggestion
import com.rebuild.domain.Workout
import com.rebuild.domain.WorkoutExercise
import com.rebuild.domain.planSourceOf
import com.rebuild.domain.swapSuggestions

/** Plain-text supporting line for a swap suggestion: match reason, equipment, loading if unusual, history, custom. */
internal fun swapSuggestionSupporting(suggestion: SwapSuggestion): String {
    val ex = suggestion.exercise
    val equipment = ex.equipment.takeIf { it != Equipment.UNKNOWN }?.title
    val loading = loadingShort(ex).takeIf { it != "external" && !it.equals(equipment, ignoreCase = true) }
    return listOfNotNull(
        suggestion.reason,
        equipment,
        loading,
        "logged before".takeIf { suggestion.loggedBefore },
        "custom".takeIf { ex.custom }
    ).joinToString(" · ")
}

/**
 * Why this exercise cannot be switched right now, or null. Logged history never moves to another exercise, and a
 * running or stopped stopwatch effort belongs to the exercise it timed.
 */
internal fun switchBlockedReason(we: WorkoutExercise, watch: Stopwatch?): String? = when {
    we.optional -> "Added today"
    we.sets.isNotEmpty() -> "Sets already logged"
    we.efforts.isNotEmpty() -> "${effortNoun(we.exercise.measure).replaceFirstChar { it.uppercase() }}s already logged"
    watch?.exerciseId == we.id -> "Stopwatch is holding it"
    else -> null
}

/**
 * In-workout exercise switcher: shows grouped suggestions (Plan alternatives, Same movement, More for <muscle>),
 * lets the lifter optionally save the swap to future workouts of this routine, and offers "Browse all" pre-filtered
 * to the muscle. When sets are already logged, explains the lock and offers adding an exercise for that muscle instead.
 */
@Composable
internal fun SwitchExerciseSheet(
    env: AppEnv,
    workout: Workout,
    we: WorkoutExercise,
    onClosed: () -> Unit,
    onAddInstead: (MuscleGroup?) -> Unit
) {
    val slot = env.state.templates.find { it.id == workout.templateId }?.slots?.find { it.id == we.slotId }
    val planSource = env.state.planSourceOf(workout, we)
    val gear = env.state.gear
    val otherIds = workout.exercises.filter { it.id != we.id }.map { it.exercise.id }.toSet()
    val groups = env.state.swapSuggestions(we.exercise.id, slot = slot, gear = gear, excluding = otherIds)
    val swapMuscle = (slot?.exerciseFor(gear)?.let(env::exercise) ?: we.exercise).primaryMuscle
    val muscleSubtitle = slot?.muscle?.takeIf { it.isNotBlank() }
        ?: swapMuscle.takeIf { it != MuscleGroup.UNKNOWN }?.title
    val hasLogged = we.sets.isNotEmpty() || we.efforts.isNotEmpty()
    val stopwatchHeld = env.state.stopwatch.timesEntry(workout, we)

    var saveToPlan by rememberSaveable(we.id) { mutableStateOf(false) }
    var error by rememberSaveable(we.id) { mutableStateOf<String?>(null) }
    var browsing by rememberSaveable(we.id) { mutableStateOf(false) }
    var addingCustom by rememberSaveable(we.id) { mutableStateOf(false) }
    // Freeze the displayed exercise and suggestions once a switch commits so the sheet doesn't rewrite itself mid-exit.
    var frozen by remember(we.id) { mutableStateOf<Pair<WorkoutExercise, List<SwapGroup>>?>(null) }
    val shownWe = frozen?.first ?: we
    val shownGroups = frozen?.second ?: groups
    val scrollState = rememberScrollState()
    LaunchedEffect(error) {
        if (error != null) scrollState.animateScrollTo(0)
    }

    AppSheet(
        key = "switch-${workout.id}-${we.id}",
        paneTitle = "Switch ${shownWe.exercise.name}",
        onClosed = onClosed,
        modifier = Modifier.testTag("switch-exercise-sheet")
    ) { close ->
        fun applySwitch(replacementId: String) {
            if (frozen != null) return
            if (replacementId == we.exercise.id) {
                close()
                return
            }
            error = null
            frozen = we to groups
            val aboveCap = we.upperSets != null && (we.targetSets ?: 0) > we.upperSets
            env.runner.run(
                Action.EditToday(
                    exerciseId = we.id,
                    replacementId = replacementId,
                    sets = we.targetSets,
                    restSeconds = we.restSeconds,
                    saveToPlan = saveToPlan && planSource != null,
                    allowAboveCap = aboveCap,
                    keepPlanTargets = true
                ),
                onError = {
                    frozen = null
                    error = it
                }
            ) { close() }
        }

        val scheme = MaterialTheme.colorScheme
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp)
                .padding(bottom = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Switch exercise", style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.semantics { heading() })
                Text(
                    listOfNotNull(shownWe.exercise.name, muscleSubtitle).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant
                )
            }

            if (hasLogged || stopwatchHeld) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(tonalFill())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        if (stopwatchHeld) {
                            "The stopwatch is holding an effort for ${we.exercise.name}. Log or discard it before switching."
                        } else {
                            "${we.exercise.name} already has ${recordsSummary(we)} logged today. Switching is locked " +
                                "so your history stays under the right exercise."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurface
                    )
                }
                if (hasLogged && !stopwatchHeld) {
                    val addLabel = if (swapMuscle != MuscleGroup.UNKNOWN) {
                        "Add ${swapMuscle.title.lowercase()} exercise instead"
                    } else "Add another exercise instead"
                    Button(
                        onClick = {
                            val muscle = swapMuscle.takeIf { it != MuscleGroup.UNKNOWN }
                            close()
                            onAddInstead(muscle)
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("switch-add-instead")
                    ) { Text(addLabel) }
                }
                SecondaryButton(
                    onClick = close,
                    modifier = Modifier.fillMaxWidth().testTag("switch-close")
                ) { Text("Close") }
            } else {
                if (planSource != null) {
                    CheckRow(
                        text = "Also use in future ${workout.name} workouts",
                        checked = saveToPlan,
                        onCheckedChange = { saveToPlan = it },
                        supporting = "Updates your ${phaseShort(workout.phase)} plan too",
                        modifier = Modifier.testTag("switch-save-to-plan")
                    )
                } else {
                    Text(
                        "Applies to today only.",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant
                    )
                }
                ErrorText(error)

                if (shownGroups.isEmpty()) {
                    Text(
                        if (gear != Gear.GYM) "No matching suggestions for your equipment."
                        else "No close matches in your library.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant
                    )
                } else {
                    shownGroups.forEach { group ->
                        Column(Modifier.fillMaxWidth()) {
                            Text(
                                group.title,
                                style = MaterialTheme.typography.labelLarge,
                                color = scheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp, bottom = 6.dp).semantics { heading() }
                            )
                            group.suggestions.forEachIndexed { i, suggestion ->
                                SwitchSuggestionRow(
                                    suggestion = suggestion,
                                    index = i,
                                    count = group.suggestions.size,
                                    enabled = frozen == null && !env.runner.looksBusy(),
                                    onClick = { applySwitch(suggestion.exercise.id) }
                                )
                            }
                        }
                    }
                }

                Column(Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    EditorActionRow(
                        icon = R.drawable.ic_search,
                        label = "Browse all",
                        shape = groupShape(0, 1),
                        chevron = true,
                        modifier = Modifier.testTag("switch-browse-all")
                    ) { browsing = true }
                    EditorActionRow(
                        icon = R.drawable.ic_plus,
                        label = "New exercise",
                        shape = groupShape(1, 1),
                        modifier = Modifier.testTag("switch-new-exercise")
                    ) { addingCustom = true }
                }
                SecondaryButton(
                    onClick = close,
                    modifier = Modifier.fillMaxWidth().testTag("switch-cancel")
                ) { Text("Cancel") }
            }
        }

        if (browsing) {
            ExerciseLibraryPicker(
                env = env,
                onDismiss = { browsing = false },
                title = "Switch exercise",
                marked = otherIds,
                markedLabel = "in this workout",
                measure = we.exercise.measure,
                initialMuscle = swapMuscle.takeIf { it != MuscleGroup.UNKNOWN }
            ) { picked ->
                browsing = false
                applySwitch(picked.id)
            }
        }
        if (addingCustom) {
            CustomExerciseDialog(
                env = env,
                onDismiss = { addingCustom = false },
                measure = we.exercise.measure,
                muscleHint = swapMuscle
            ) { created ->
                addingCustom = false
                applySwitch(created.id)
            }
        }
    }
}

@Composable
private fun SwitchSuggestionRow(
    suggestion: SwapSuggestion,
    index: Int,
    count: Int,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .padding(top = if (index > 0) 2.dp else 0.dp)
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(groupShape(index, count - 1))
            .background(tonalFill())
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .testTag("switch-option-${suggestion.exercise.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(suggestion.exercise.name, style = MaterialTheme.typography.bodyLarge, color = scheme.onSurface)
            Text(
                swapSuggestionSupporting(suggestion),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant
            )
        }
        RebuildIcon(
            R.drawable.ic_chevron_right,
            contentDescription = null,
            tint = scheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 12.dp)
        )
    }
}
