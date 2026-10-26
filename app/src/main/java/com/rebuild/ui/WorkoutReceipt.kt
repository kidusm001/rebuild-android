package com.rebuild.ui

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.TextButton
import com.rebuild.domain.localDateOf
import com.rebuild.domain.weighInsOn
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rebuild.R
import com.rebuild.domain.AppState
import com.rebuild.domain.LoadUnit
import com.rebuild.domain.LoadingType
import com.rebuild.domain.LoggedSet
import com.rebuild.domain.Measure
import com.rebuild.domain.Workout
import com.rebuild.domain.WorkoutExercise
import com.rebuild.domain.completesRoutine
import com.rebuild.domain.previousComparable
import com.rebuild.domain.mainSets
import com.rebuild.domain.workingCount
import com.rebuild.domain.workingSetCount
import kotlin.math.roundToLong

/** One exercise's line on the receipt: its best normal working set, and what it beat from last time, if anything. */
internal data class ReceiptLine(val name: String, val best: String, val sets: Int, val gain: String?)

/**
 * The heaviest working set (then the most reps at that load). A set with drops counts by its own first stage; drops
 * and warm-ups never stand in for it.
 */
private fun WorkoutExercise.bestSet(unit: LoadUnit): LoggedSet? =
    mainSets().filter { it.loadUnit == unit }
        .maxWithOrNull(compareBy<LoggedSet>({ it.loadValue ?: 0.0 }, { it.reps }))

/**
 * Only real improvements over the last comparable session, in the same unit and loading: more load, or more reps at
 * the same load. Assisted loads are left out because less assistance and more load point opposite ways.
 */
internal fun gainOver(today: LoggedSet, last: LoggedSet?, we: WorkoutExercise): String? {
    last ?: return null
    val loading = we.exercise.loading
    val todayLoad = today.loadValue
    val lastLoad = last.loadValue
    if (loading != LoadingType.ASSISTANCE && loading != LoadingType.BODYWEIGHT && todayLoad != null && lastLoad != null &&
        todayLoad > lastLoad) {
        val delta = todayLoad - lastLoad
        return if (today.loadUnit == LoadUnit.PLATES) "+${countLabel(delta.roundToLong().toInt(), "plate")}"
        else "+${formatKg(delta)} ${today.loadUnit.massSymbol()}"
    }
    return if (todayLoad == lastLoad && today.reps > last.reps) "+${countLabel(today.reps - last.reps, "rep")}" else null
}

internal fun receiptLines(state: AppState, workout: Workout): List<ReceiptLine> =
    workout.exercises.filter { it.exercise.measure == Measure.REPS && it.workingCount() > 0 }.mapNotNull { we ->
        val best = we.bestSet(we.entryLoadUnit) ?: we.sets.firstOrNull { !it.warmup } ?: return@mapNotNull null
        val previous = state.previousComparable(we.exercise, workout.id, best.loadUnit, we.perLeg)
            ?.bestSet(best.loadUnit)
        ReceiptLine(we.exercise.name, loadRepsText(best, we.exercise), we.workingCount(), gainOver(best, previous, we))
    }

/**
 * What a just-finished workout saved, as a receipt rather than a score: when, how long, how many working sets, and
 * each exercise's best set with any real gain over last time. It ends the session with a confirming tick.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WorkoutReceipt(env: AppEnv, workout: Workout, onView: () -> Unit, onDismiss: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    var loggingWeight by rememberSaveable { mutableStateOf(false) }
    val large = LocalDensity.current.fontScale > 1.3f
    LaunchedEffect(workout.id) { haptics.performHapticFeedback(HapticFeedbackType.Confirm) }
    val finished = workout.finishedAt ?: return
    val minutes = ((finished - workout.startedAt) / 60_000).coerceAtLeast(1)
    val lines = receiptLines(env.state, workout)
    val efforts = effortCounts(workout)
    val week = when {
        workout.extra -> "Outside the plan"
        workout.completesRoutine() -> "Counts for this week"
        workout.countedForWeek -> "Counted for this week"
        else -> "Partial · you can count it in History"
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = MaterialTheme.shapes.large.copy(bottomStart = CornerSize(0.dp), bottomEnd = CornerSize(0.dp)),
        modifier = Modifier.testTag("workout-receipt")
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(
                    Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    RebuildIcon(R.drawable.ic_check, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Column(Modifier.weight(1f)) {
                    Text("${workout.name} saved", style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.semantics { heading() })
                    Text(
                        "${formatDate(workout.startedAt)} · ${formatTime(workout.startedAt)}–${formatTime(finished)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                listOf(
                    countLabel(workout.workingSetCount(), "working set"),
                    countLabel(workout.exercises.count { it.workingCount() + it.efforts.size > 0 }, "exercise"),
                    "$minutes min"
                ).joinToString(" · "),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.testTag("receipt-summary")
            )
            if (lines.isNotEmpty() || efforts.isNotEmpty()) {
                Column {
                    lines.forEachIndexed { i, line ->
                        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 56.dp).semantics(mergeDescendants = true) {},
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(line.name, style = MaterialTheme.typography.bodyLarge, maxLines = 2,
                                    overflow = TextOverflow.Ellipsis)
                                Text("${countLabel(line.sets, "set")} · best ${line.best}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (line.gain != null && large) Text(
                                    "${line.gain} vs last time",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            if (line.gain != null && !large) Text(
                                "${line.gain} vs last time",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    if (efforts.isNotEmpty()) Text(
                        efforts.joinToString(", ") + " logged",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
            Text(week, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("receipt-week"))
            // A workout day is when most people weigh in; one tap here, never a requirement.
            val weighIn = env.state.weighInsOn(localDateOf(finished)).firstOrNull()
            ListRow(
                headline = "Bodyweight",
                supporting = weighIn?.let { "${massText(it.value, it.unit)} at ${formatTime(it.at)}" } ?: "Not logged today",
                modifier = Modifier.testTag("receipt-bodyweight"),
                trailing = if (weighIn != null) null else {
                    { TextButton(onClick = { loggingWeight = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Log") } }
                }
            )
            @Composable fun View(modifier: Modifier) = OutlinedButton(onClick = onView,
                modifier = modifier.heightIn(min = 48.dp).testTag("receipt-view")) { Text("View workout") }
            @Composable fun Done(modifier: Modifier) = Button(onClick = onDismiss,
                modifier = modifier.heightIn(min = 48.dp).testTag("receipt-done")) { Text("Done") }
            if (large) {
                Done(Modifier.fillMaxWidth())
                View(Modifier.fillMaxWidth())
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    View(Modifier.weight(1f))
                    Done(Modifier.weight(1f))
                }
            }
        }
    }
    if (loggingWeight) BodyweightDialog(env, null, onDismiss = { loggingWeight = false })
}
