package com.rebuild.ui

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.rebuild.domain.Action
import com.rebuild.domain.Phase

@Composable
internal fun PhaseChoices(selected: Phase?, onSelect: (Phase) -> Unit) {
    Column(Modifier.selectableGroup()) {
        Phase.entries.forEachIndexed { index, phase ->
            if (index > 0) HorizontalDivider()
            RadioRow(
                text = phase.title,
                selected = selected == phase,
                onClick = { onSelect(phase) },
                supporting = phasePrescription(phase) + "\n" + phaseGuidance(phase),
                modifier = Modifier.testTag("phase-${phase.name}")
            )
        }
    }
}

@Composable
internal fun FirstRunScreen(env: AppEnv) {
    var choice by rememberSaveable { mutableStateOf<Phase?>(null) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    Column(
        Modifier
            .fillMaxHeight()
            .readableWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Choose where to start",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() }
        )
        Text(
            "Pick the phase that matches how training feels now.\nRebuild never changes it for you.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        PhaseChoices(choice) { choice = it }
        ErrorText(error)
        Button(
            onClick = {
                val phase = choice ?: return@Button
                error = null
                env.runner.run(Action.SelectPhase(phase), onError = { error = it })
            },
            enabled = choice != null && !env.runner.busy,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
        ) { Text(choice?.let { "Start with ${phaseShort(it)}" } ?: "Choose a phase") }
        Text(
            "Moving from another phone? Restore a backup instead.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedButton(
            onClick = { env.platform.chooseRestore() },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
        ) { Text("Restore a backup") }
    }
}

@Composable
internal fun PhaseSettingsScreen(env: AppEnv, onBack: () -> Unit) {
    val current = env.state.phase
    var choice by rememberSaveable { mutableStateOf(current) }
    var confirming by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    ScreenScaffold(title = "Training phase", onBack = onBack) {
        ScrollBody {
            Column(Modifier.semantics(mergeDescendants = true) {}) {
                Text("Current phase", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(current?.title ?: "Not chosen", style = MaterialTheme.typography.titleMedium)
            }
            Text(
                "New workouts use the new phase's targets.\nFinished workouts and plan changes stay as they are.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val active = env.state.activeWorkout
            if (active != null) {
                Text(
                    "${active.name} is in progress. Finish or discard it before changing phase.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("phase-locked")
                )
            }
            PhaseChoices(choice) { choice = it }
            ErrorText(error)
            Button(
                onClick = { confirming = true },
                enabled = active == null && choice != null && choice != current && !env.runner.busy,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
            ) { Text("Change phase") }
        }
    }
    val target = choice
    if (confirming && target != null && env.state.activeWorkout == null) {
        ConfirmDialog(
            title = "Switch to ${phaseShort(target)}?",
            text = "${target.title}: ${phasePrescription(target)}. Nothing already logged changes.",
            confirmLabel = "Switch phase",
            busy = env.runner.busy,
            error = error,
            onDismiss = { confirming = false },
            onConfirm = {
                error = null
                env.runner.run(Action.SelectPhase(target), onError = { error = it }) { confirming = false }
            }
        )
    }
}
