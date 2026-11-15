package com.rebuild.ui

import android.content.res.Configuration
import android.os.Build
import android.widget.NumberPicker
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/** Common rests, one tap each. */
private val QUICK_RESTS = listOf(30, 60, 90, 120, 180, 240, 300)
private const val SECOND_STEP = 5
private val SECOND_STOPS = (0 until 60 step SECOND_STEP).toList()

/**
 * A rest or other duration chosen with the platform's own wheels (minutes and seconds) instead of typing a count of
 * seconds. [seconds] stays text, exactly what the callers already parse and validate: blank means "not set" (an
 * optional value), so nothing about saving changes. Shows as a read-only field reading "1:30"; tapping it opens
 * [DurationPickerDialog].
 */
@Composable
internal fun DurationPickerField(
    seconds: String,
    onSeconds: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    maxSeconds: Int = REST_SECONDS_RANGE.last,
    allowBlank: Boolean = true,
    enabled: Boolean = true
) {
    var picking by rememberSaveable { mutableStateOf(false) }
    val value = seconds.trim().toIntOrNull()
    val shown = value?.let { if (it == 0) "0:00" else formatRest(it) } ?: ""
    // A read-only field keeps the familiar outlined shape, label and supporting text; any press opens the wheels.
    val presses = remember { MutableInteractionSource() }
    LaunchedEffect(presses) {
        presses.interactions.collect { if (it is PressInteraction.Release && enabled) picking = true }
    }
    OutlinedTextField(
        value = shown,
        onValueChange = {},
        readOnly = true,
        enabled = enabled,
        singleLine = true,
        label = { Text(label, maxLines = 2) },
        placeholder = { Text("Choose") },
        supportingText = supportingText?.let { { Text(it) } },
        trailingIcon = { RebuildIcon(com.rebuild.R.drawable.ic_rest, contentDescription = null) },
        textStyle = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"),
        interactionSource = presses,
        modifier = modifier.semantics {
            role = Role.Button
            contentDescription = label
            stateDescription = value?.let { spokenDuration(it) } ?: "not set"
            onClick(label = "Choose time") { if (enabled) picking = true; true }
        }
    )
    if (picking) DurationPickerDialog(
        title = label,
        initialSeconds = value,
        maxSeconds = maxSeconds,
        allowBlank = allowBlank,
        onDismiss = { picking = false },
        onPick = { picked ->
            onSeconds(picked?.toString() ?: "")
            picking = false
        }
    )
}

/**
 * Minute and second wheels (seconds in steps of [SECOND_STEP]) with one-tap common rests. [onPick] gets the total in
 * seconds, or null for "Clear" when [allowBlank].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DurationPickerDialog(
    title: String,
    initialSeconds: Int?,
    maxSeconds: Int,
    allowBlank: Boolean,
    onDismiss: () -> Unit,
    onPick: (Int?) -> Unit,
    confirmLabel: String = "Set",
    quickChoices: List<Int> = QUICK_RESTS
) {
    val start = (initialSeconds ?: 90).coerceIn(0, maxSeconds)
    var minutes by rememberSaveable { mutableIntStateOf(start / 60) }
    var secondsIndex by rememberSaveable { mutableIntStateOf(((start % 60) / SECOND_STEP).coerceIn(0, SECOND_STOPS.lastIndex)) }
    // Bumped by a quick choice so the wheels move to it.
    var revision by remember { mutableIntStateOf(0) }
    val total = minutes * 60 + SECOND_STOPS[secondsIndex]
    val maxMinutes = maxSeconds / 60
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()

    AdaptiveDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            @Composable fun Wheels(modifier: Modifier) = Row(
                modifier.testTag("duration-wheels"),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Wheel(0..maxMinutes, minutes, revision, textColor, "Minutes", { "$it" }) { minutes = it }
                Text(" min ", style = MaterialTheme.typography.titleMedium)
                Wheel(SECOND_STOPS.indices, secondsIndex, revision, textColor, "Seconds",
                    { "%02d".format(SECOND_STOPS[it]) }) { secondsIndex = it }
                Text(" s", style = MaterialTheme.typography.titleMedium)
            }
            @Composable fun Chip(choice: Int) = FilterChip(
                selected = total == choice,
                onClick = {
                    minutes = choice / 60
                    secondsIndex = (choice % 60) / SECOND_STEP
                    revision++
                },
                label = { Text(formatRest(choice)) },
                modifier = Modifier.heightIn(min = 48.dp).testTag("duration-quick-$choice")
                    .semantics { contentDescription = spokenDuration(choice) }
            )
            val choices = quickChoices.filter { it <= maxSeconds }
            if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                // A short landscape window keeps the choices to one scrolling row above the wheels, so both fit.
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        choices.forEach { Chip(it) }
                    }
                    Wheels(Modifier.fillMaxWidth())
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Wheels(Modifier.fillMaxWidth())
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        choices.forEach { Chip(it) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onPick(total) }, modifier = Modifier.testTag("duration-set")) { Text(confirmLabel) }
        },
        dismissButton = {
            Row {
                if (allowBlank && initialSeconds != null) {
                    TextButton(onClick = { onPick(null) }, modifier = Modifier.testTag("duration-clear")) { Text("Clear") }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

/** The platform NumberPicker: flings, snaps, accepts typed digits and speaks its value with TalkBack. */
@Composable
private fun Wheel(
    range: IntRange,
    value: Int,
    revision: Int,
    textColor: Int,
    description: String,
    label: (Int) -> String,
    onValue: (Int) -> Unit
) {
    AndroidView(
        factory = { context ->
            NumberPicker(context).apply {
                minValue = range.first
                maxValue = range.last
                displayedValues = range.map(label).toTypedArray()
                wrapSelectorWheel = range.count() > 3
                contentDescription = description
                setOnValueChangedListener { _, _, new -> onValue(new) }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) this.textColor = textColor
            }
        },
        update = { picker ->
            // Only a quick choice or a restored state moves the wheel; scrolling already set it.
            if (picker.value != value) picker.value = value
            picker.tag = revision
        },
        modifier = Modifier.width(72.dp)
    )
}
