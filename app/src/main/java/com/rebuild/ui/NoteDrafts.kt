package com.rebuild.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.rebuild.domain.Action
import com.rebuild.domain.Workout

internal const val WORKOUT_NOTE_KEY = "__workout__"

/**
 * Unsaved note text for one workout, keyed by workout-exercise id (or [WORKOUT_NOTE_KEY]).
 * Each draft remembers the saved note it started from; if that note changes underneath it
 * (a save elsewhere or a restore) the draft is dropped instead of shown over newer data.
 */
internal class NoteDrafts(initial: Map<String, Pair<String, String>> = emptyMap()) {
    private val drafts = mutableStateMapOf<String, Pair<String, String>>().apply { putAll(initial) }

    fun text(key: String, saved: String): String = drafts[key]?.takeIf { it.first == saved }?.second ?: saved

    fun edit(key: String, saved: String, text: String) {
        if (text == saved) drafts.remove(key) else drafts[key] = saved to text
    }

    fun clear(key: String) {
        drafts.remove(key)
    }

    fun clearAll() = drafts.clear()

    /** Keys whose draft differs from the current saved note, in workout order. */
    fun dirty(workout: Workout): List<String> = savedNotes(workout).mapNotNull { (key, saved) ->
        key.takeIf { drafts[key]?.let { it.first == saved && it.second != saved } == true }
    }

    companion object {
        val Saver: Saver<NoteDrafts, List<String>> = Saver(
            save = { d -> d.drafts.flatMap { (k, v) -> listOf(k, v.first, v.second) } },
            restore = { list -> NoteDrafts(list.chunked(3).filter { it.size == 3 }.associate { it[0] to (it[1] to it[2]) }) }
        )
    }
}

internal fun savedNotes(workout: Workout): List<Pair<String, String>> =
    listOf(WORKOUT_NOTE_KEY to workout.notes) + workout.exercises.map { it.id to it.notes }

@Composable
internal fun rememberNoteDrafts(workoutId: String): NoteDrafts =
    rememberSaveable(workoutId, saver = NoteDrafts.Saver) { NoteDrafts() }

private fun noteAction(workoutId: String, key: String, text: String): Action =
    if (key == WORKOUT_NOTE_KEY) Action.WorkoutNote(workoutId, text) else Action.ExerciseNote(workoutId, key, text)

/** Saves the given drafts one after another; stops at the first error. */
internal fun saveNoteDrafts(
    env: AppEnv,
    workout: Workout,
    drafts: NoteDrafts,
    keys: List<String>,
    onError: (String) -> Unit,
    onDone: () -> Unit
) {
    val key = keys.firstOrNull() ?: return onDone()
    val saved = savedNotes(workout).firstOrNull { it.first == key }?.second ?: return onDone()
    env.runner.run(noteAction(workout.id, key, drafts.text(key, saved)), onError = onError) {
        drafts.clear(key)
        saveNoteDrafts(env, workout, drafts, keys.drop(1), onError, onDone)
    }
}

/** Asked before leaving a screen with unsaved notes, so typed notes never vanish silently. */
@Composable
internal fun UnsavedNotesDialog(
    env: AppEnv,
    workout: Workout,
    drafts: NoteDrafts,
    dirty: List<String>,
    onKeepEditing: () -> Unit,
    onProceed: () -> Unit
) {
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val names = dirty.map { key ->
        if (key == WORKOUT_NOTE_KEY) "the workout" else workout.exercises.find { it.id == key }?.exercise?.name ?: "an exercise"
    }
    AdaptiveDialog(
        onDismissRequest = { if (!env.runner.busy) onKeepEditing() },
        title = { Text("Unsaved notes") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Your notes for ${names.joinToString(", ")} aren't saved yet.")
                ErrorText(error)
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    error = null
                    saveNoteDrafts(env, workout, drafts, dirty, onError = { error = it }, onDone = onProceed)
                },
                enabled = !env.runner.busy,
                modifier = Modifier.testTag("save-notes")
            ) { Text("Save notes") }
        },
        dismissButton = {
            Column {
                TextButton(onClick = { drafts.clearAll(); onProceed() }, enabled = !env.runner.busy) {
                    Text("Discard", color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = onKeepEditing, enabled = !env.runner.busy) { Text("Keep editing") }
            }
        }
    )
}
