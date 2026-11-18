package com.rebuild.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rebuild.R
import com.rebuild.domain.Action
import com.rebuild.domain.AppState
import com.rebuild.domain.Experience
import com.rebuild.domain.Gear
import com.rebuild.domain.MassUnit
import com.rebuild.domain.Phase
import com.rebuild.domain.Split
import com.rebuild.domain.SplitChoice
import com.rebuild.domain.Splits
import com.rebuild.domain.availableTemplates
import com.rebuild.domain.localDateOf
import com.rebuild.domain.plannedExercises
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/** The steps of first-run setup, in order. Back goes one step back with every answer kept. */
internal enum class SetupStep { WELCOME, EXPERIENCE, DAYS, EQUIPMENT, PLAN }

internal fun Experience.title(): String = when (this) {
    Experience.NEW -> "New to lifting"
    Experience.RETURNING -> "Coming back after a break"
    Experience.REGULAR -> "Training regularly"
    Experience.EXPERIENCED -> "Experienced"
}

internal fun Experience.detail(): String = when (this) {
    Experience.NEW -> "Little or no time with weights."
    Experience.RETURNING -> "You've trained before, but not for a few months."
    Experience.REGULAR -> "Most weeks for the past few months."
    Experience.EXPERIENCED -> "Years of steady training. You know the main lifts."
}

internal fun Gear.title(): String = when (this) {
    Gear.GYM -> "A gym"
    Gear.DUMBBELLS -> "Dumbbells and a bench"
    Gear.BODYWEIGHT -> "Just your body"
}

internal fun Gear.detail(): String = when (this) {
    Gear.GYM -> "Machines, cables, barbells and dumbbells."
    Gear.DUMBBELLS -> "A pair of dumbbells you can change, and a bench. At home or a small gym."
    Gear.BODYWEIGHT -> "No weights. A pull-up bar or a sturdy table helps for pulling."
}

/** The short form in the plan summary: "Gym", "Dumbbells", "Bodyweight". */
internal fun Gear.short(): String = when (this) {
    Gear.GYM -> "Gym"
    Gear.DUMBBELLS -> "Dumbbells"
    Gear.BODYWEIGHT -> "Bodyweight"
}

/** Pounds where they're the everyday unit, kilograms everywhere else. Only a starting choice. */
internal fun defaultMassUnit(locale: Locale = Locale.getDefault()): MassUnit =
    if (locale.country in setOf("US", "LR", "MM")) MassUnit.LB else MassUnit.KG

/**
 * [state] as it would be with [split] chosen: the plan setup and Change split preview, exactly as it will be saved.
 * The phase stays when there is one (Change split) and is the starting phase otherwise.
 */
internal fun previewState(state: AppState, split: Split, gear: Gear, experience: Experience): AppState = state.copy(
    phase = state.phase ?: Splits.startingPhase(experience),
    templates = if (state.templates.any { it.splitId == split.id }) state.templates else state.templates + split.templates,
    split = SplitChoice(split.id, gear, experience, "2000-01-01")
)

/**
 * First-run setup: a welcome, three questions and the plan they lead to. Nothing is saved until "Start with this
 * plan"; then [onPlanSaved] shows the last, optional step (rest alerts).
 */
@Composable
internal fun SetupFlow(env: AppEnv, onPlanSaved: () -> Unit) {
    var stepName by rememberSaveable { mutableStateOf(SetupStep.WELCOME.name) }
    var experienceName by rememberSaveable { mutableStateOf<String?>(null) }
    var days by rememberSaveable { mutableStateOf<Int?>(null) }
    var gearName by rememberSaveable { mutableStateOf<String?>(null) }
    var unitName by rememberSaveable { mutableStateOf(defaultMassUnit().name) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    // The plan step's "Change" links come back to the plan once the answer is changed.
    var returnToPlan by rememberSaveable { mutableStateOf(false) }
    val step = SetupStep.valueOf(stepName)
    val experience = experienceName?.let(Experience::valueOf)
    val gear = gearName?.let(Gear::valueOf)
    val unit = MassUnit.valueOf(unitName)

    fun go(to: SetupStep) { stepName = to.name; error = null }
    fun next() = go(if (returnToPlan) SetupStep.PLAN else SetupStep.entries[step.ordinal + 1])
    fun change(to: SetupStep) { returnToPlan = true; go(to) }
    BackHandler(enabled = step != SetupStep.WELCOME) {
        if (returnToPlan && step != SetupStep.PLAN) go(SetupStep.PLAN) else go(SetupStep.entries[step.ordinal - 1])
    }

    when (step) {
        SetupStep.WELCOME -> Welcome(onStart = { go(SetupStep.EXPERIENCE) }, onRestore = { env.platform.chooseRestore() })
        SetupStep.EXPERIENCE -> SetupPage(1, "How much have you trained?", onBack = { go(SetupStep.WELCOME) },
            action = "Continue", enabled = experience != null, onAction = ::next) {
            Text("This picks your starting plan. You can change everything later.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Options(Experience.entries, experience, "setup-experience", { it.title() }, { it.detail() }) {
                experienceName = it.name
            }
        }
        SetupStep.DAYS -> SetupPage(2, "How many days a week?", onBack = { go(SetupStep.EXPERIENCE) },
            action = "Continue", enabled = days != null, onAction = ::next) {
            Text("Pick what fits a normal week. Rebuild spreads them out so you rest in between.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Options((2..6).toList(), days, "setup-days", { "$it days a week" }, { null }) { days = it }
            if (experience != null && days != null && Splits.manyDaysForABeginner(experience, days!!)) {
                Text("3–4 days is plenty when you're starting. You can change days any time.",
                    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("setup-days-note"))
            }
        }
        SetupStep.EQUIPMENT -> SetupPage(3, "What will you train with?", onBack = { go(SetupStep.DAYS) },
            action = "See your plan", enabled = gear != null, onAction = ::next) {
            Options(Gear.entries, gear, "setup-gear", { it.title() }, { it.detail() }) { gearName = it.name }
            Text("Weight unit", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(top = 8.dp).semantics { heading() })
            PillChoice(MassUnit.entries, unit, { unitName = it.name }, { it.symbol },
                tag = { "setup-unit-${it.name.lowercase()}" })
            Text("Each set keeps the unit you log it in. You can switch any time.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SetupStep.PLAN -> {
            val split = Splits.recommend(experience!!, days!!, gear!!)
            SetupPage(4, "Your plan", onBack = { go(SetupStep.EQUIPMENT) }, action = "Start with this plan",
                enabled = !env.runner.busy, onAction = {
                    env.runner.run(Action.CompleteSetup(experience, split.id, gear, unit,
                        localDateOf(env.clock()).toString()), onError = { error = it }, onSuccess = onPlanSaved)
                }) {
                Answers(listOf(
                    Triple("${days} days", "Change days") { change(SetupStep.DAYS) },
                    Triple(gear.short(), "Change equipment") { change(SetupStep.EQUIPMENT) },
                    Triple(unit.symbol, "Change weight unit") { change(SetupStep.EQUIPMENT) },
                    Triple(experience.title(), "Change experience") { change(SetupStep.EXPERIENCE) }
                ))
                SplitPreview(previewState(env.state, split, gear, experience), split, gear,
                    note = if (Splits.manyDaysForABeginner(experience, days!!))
                        "3–4 days is plenty when you're starting. You can change days any time." else null)
                ErrorText(error)
            }
        }
    }
}

@Composable
private fun Welcome(onStart: () -> Unit, onRestore: () -> Unit) {
    SetupPage(null, "Rebuild", onBack = null, action = "Get started", enabled = true, onAction = onStart,
        secondary = { SecondaryButton(onClick = onRestore, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .testTag("setup-restore")) { Text("Restore a backup") } }) {
        Text("A workout notebook. It works offline, keeps everything on this phone and needs no account.",
            style = MaterialTheme.typography.titleMedium)
        Text("It won't coach you, keep streaks or tell you what weight to lift. You log what you did; it keeps " +
            "your plan, rests and history.", style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("A few questions set up a weekly plan. It takes about a minute.",
            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * One setup page: back, "Step N of 4", a heading, the choices, and the action pinned at the bottom (inline at large
 * text, so the choices keep the room). Screen readers hear the page's step and title as it opens.
 */
@Composable
private fun SetupPage(
    number: Int?,
    title: String,
    onBack: (() -> Unit)?,
    action: String,
    enabled: Boolean,
    onAction: () -> Unit,
    secondary: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val pinned = LocalDensity.current.fontScale <= 1.3f
    val step = number?.let { "Step $it of 4" }
    val actions: @Composable ColumnScope.() -> Unit = {
        Button(onClick = onAction, enabled = enabled,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("setup-action")) { Text(action) }
        secondary?.invoke()
    }
    Column(Modifier.fillMaxSize().semantics { paneTitle = listOfNotNull(step, title).joinToString(": ") }) {
        Row(Modifier.readableWidth().padding(horizontal = 8.dp).heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) IconButton(onClick = onBack, modifier = Modifier.testTag("setup-back")) {
                RebuildIcon(R.drawable.ic_arrow_left, contentDescription = "Back")
            }
            if (step != null) Text(step, style = overlineStyle(), color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = if (onBack == null) 12.dp else 4.dp).testTag("setup-step"))
        }
        ScrollBody {
            Text(title, style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.semantics {
                    heading()
                    if (step != null) contentDescription = "$step, $title"
                })
            content()
            if (!pinned) Column(verticalArrangement = Arrangement.spacedBy(8.dp), content = actions)
        }
        if (pinned) Column(Modifier.readableWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp), content = actions)
    }
}

/** Full-row radio choices in a group: nothing chosen until the user chooses, and nothing advances by itself. */
@Composable
internal fun <T> Options(
    options: List<T>,
    selected: T?,
    tagPrefix: String,
    title: (T) -> String,
    detail: (T) -> String?,
    onSelect: (T) -> Unit
) {
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        options.forEachIndexed { i, option ->
            Row(
                Modifier
                    .groupedRow(i, options.lastIndex)
                    .selectable(selected = option == selected, role = Role.RadioButton) { onSelect(option) }
                    .heightIn(min = 56.dp)
                    .padding(vertical = 10.dp)
                    .testTag("$tagPrefix-${option.toString().lowercase()}"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                RadioButton(selected = option == selected, onClick = null)
                Column(Modifier.weight(1f)) {
                    Text(title(option), style = MaterialTheme.typography.bodyLarge)
                    detail(option)?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/** The answers behind the plan, each one changeable where it's shown. */
@Composable
private fun Answers(answers: List<Triple<String, String, () -> Unit>>) {
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        answers.forEach { (label, description, onClick) ->
            TonalChip(selected = false, onClick = onClick, label = { Text(label) },
                modifier = Modifier.semantics { contentDescription = "$label. $description" })
        }
    }
}

/**
 * A split as it will be saved for [state]'s phase: its name and why, what the phase asks, what the gear changed, and
 * the week, one row per day that opens to its exercises.
 */
@Composable
internal fun SplitPreview(state: AppState, split: Split, gear: Gear, note: String? = null) {
    val phase = requireNotNull(state.phase)
    TonalCard { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(split.name, style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() }.testTag("split-name"))
        Text(split.reason, style = MaterialTheme.typography.bodyLarge)
        note?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = accentText()) }
    } }
    TonalCard { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Starts with ${phase.title}", style = MaterialTheme.typography.titleMedium)
        Text(state.phasePrescription(phase), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Reps in reserve are the reps you could still have done at the end of a set. You choose when to move " +
            "to the next phase, in Plan.", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    } }
    val swapped = split.swappedFor(gear)
    val dropped = split.droppedFor(gear)
    if (swapped > 0 || dropped > 0) Text(
        listOfNotNull(
            swapped.takeIf { it > 0 }?.let { "${countLabel(it, "exercise")} swapped for ${gear.short().lowercase()}" },
            dropped.takeIf { it > 0 }?.let { "$it left out with no match" }
        ).joinToString(", ") + ".",
        style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("split-gear-note"))
    Text("Your week", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        modifier = Modifier.semantics { heading() })
    val week = state.availableTemplates(phase).sortedBy { it.weekday }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        week.forEachIndexed { i, template ->
            var open by rememberSaveable(template.id) { mutableStateOf(false) }
            val planned = state.plannedExercises(template, phase)
            val day = DayOfWeek.of(template.weekday).getDisplayName(TextStyle.SHORT, Locale.getDefault())
            val dayLong = DayOfWeek.of(template.weekday).getDisplayName(TextStyle.FULL, Locale.getDefault())
            Column(Modifier.groupedRow(i, week.lastIndex)) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp)
                        .clickable(onClickLabel = if (open) "Hide exercises" else "Show exercises") { open = !open }
                        .semantics {
                            contentDescription = "$dayLong, ${template.name}, ${countLabel(planned.size, "exercise")}"
                            stateDescription = if (open) "Expanded" else "Collapsed"
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .testTag("split-day-${template.id}"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(day, style = MaterialTheme.typography.labelLarge, color = accentText(),
                        modifier = Modifier.padding(end = 4.dp))
                    Column(Modifier.weight(1f)) {
                        Text(template.name, style = MaterialTheme.typography.bodyLarge)
                        Text(countLabel(planned.size, "exercise"), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    RebuildIcon(if (open) R.drawable.ic_chevron_up else R.drawable.ic_chevron_down,
                        contentDescription = null, modifier = Modifier.size(20.dp))
                }
                AnimatedVisibility(open, enter = expandVertically(tween(200)) + fadeIn(tween(200)),
                    exit = shrinkVertically(tween(150)) + fadeOut(tween(150))) {
                    Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        planned.forEach { exercise ->
                            val name = state.exercises.find { it.id == exercise.exerciseId }?.name ?: exercise.exerciseId
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                Text("${exercise.sets} × ${exercise.minReps}–${exercise.maxReps}" +
                                    if (exercise.perLeg) " per leg" else "",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
    Text("You can change your split, days or equipment any time in Plan.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/**
 * The last, optional step, shown once the plan is saved: how a rest end is announced, and only then the request for
 * notifications, so Android's prompt has a reason beside it. Leaving at any point loses nothing.
 */
@Composable
internal fun RestAlertSetup(env: AppEnv, onDone: () -> Unit) {
    val alert = env.restAlert
    var asked by rememberSaveable { mutableStateOf(false) }
    val allowed = env.status.notificationsAllowed
    BackHandler(onBack = onDone)
    Column(Modifier.fillMaxSize().semantics { paneTitle = "Last step: Rest alerts" }) {
        Row(Modifier.readableWidth().padding(horizontal = 20.dp).heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text("Last step", style = overlineStyle(), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        ScrollBody {
            Text("Rest alerts", style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.semantics { heading(); contentDescription = "Last step, Rest alerts" })
            Text("Your plan is saved. When a rest between sets ends, Rebuild can tell you, even with the phone in " +
                "your pocket.", style = MaterialTheme.typography.bodyLarge)
            ChoiceGroup(RestSound.entries, alert.sound, "rest-sound", { it.title }, { it.detail }) {
                env.platform.setRestAlert(alert.withSound(it))
            }
            SecondaryButton(onClick = { env.previewRestEnd() }, icon = R.drawable.ic_play,
                modifier = Modifier.testTag("setup-rest-try")) { Text("Try it") }
            if (asked && !allowed) Text(
                "Notifications are off, so rest alerts only show while Rebuild is open. You can turn them on later " +
                    "in More › Rest alert.", style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("setup-rest-denied"))
            if (allowed) PlatformLimitations(env.status, env.platform)
        }
        Column(Modifier.readableWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!allowed && !asked) {
                Button(onClick = { asked = true; env.platform.requestNotifications() },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("setup-rest-allow")) {
                    Text("Allow notifications")
                }
                SecondaryButton(onClick = onDone, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .testTag("setup-rest-skip")) { Text("Not now") }
            } else {
                Button(onClick = onDone, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                    .testTag("setup-rest-done")) { Text("Go to Today") }
            }
        }
    }
}

/**
 * Plan › Change split: the setup questions again with the current answers filled in, the split they lead to (or one
 * chosen from the whole list), and a confirmation that says what happens to this week. Blocked mid-workout.
 */
@Composable
internal fun ChangeSplitScreen(env: AppEnv, onBack: () -> Unit) {
    val state = env.state
    val current = Splits.all.find { it.id == state.activeSplitId }
    var experienceName by rememberSaveable { mutableStateOf(state.split?.experience?.name) }
    var days by rememberSaveable { mutableStateOf(current?.days) }
    var gearName by rememberSaveable { mutableStateOf(state.gear.name) }
    // A split picked from the list replaces the recommendation until an answer changes.
    var pickedId by rememberSaveable { mutableStateOf<String?>(null) }
    var confirming by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val experience = experienceName?.let(Experience::valueOf)
    val gear = Gear.valueOf(gearName)
    val recommended = if (experience != null && days != null) Splits.recommend(experience, days!!, gear) else null
    val chosen = pickedId?.let(Splits::byId)?.takeIf { gear in it.gear } ?: recommended
    val active = state.activeWorkout
    ScreenScaffold(title = "Change split", onBack = onBack) {
        ScrollBody {
            if (active != null) {
                Text("${active.name} is in progress. Finish or discard it before changing your split.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("split-locked"))
            }
            Text("Your workouts so far, and the changes you made to this plan, are kept.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SectionHeader("How much have you trained?")
            Options(Experience.entries, experience, "split-experience", { it.title() }, { it.detail() }) {
                experienceName = it.name; pickedId = null
            }
            SectionHeader("How many days a week?")
            Options((2..6).toList(), days, "split-days", { "$it days a week" }, { null }) { days = it; pickedId = null }
            SectionHeader("What will you train with?")
            Options(Gear.entries, gear, "split-gear", { it.title() }, { it.detail() }) { gearName = it.name }
            SectionHeader("Or choose a split yourself")
            Options(Splits.forGear(gear), chosen, "split-pick", { it.name },
                { "${it.days} days a week" + if (it.id == state.activeSplitId) " · your split now" else "" }) {
                pickedId = it.id
            }
            if (chosen != null && experience != null) {
                SectionHeader(if (pickedId != null) "Your choice" else "Recommended for you")
                SplitPreview(previewState(state, chosen, gear, experience), chosen, gear)
            }
            ErrorText(error)
            Button(
                onClick = { confirming = true },
                enabled = active == null && chosen != null && experience != null && !env.runner.busy &&
                    !(chosen.id == state.activeSplitId && gear == state.gear),
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("split-switch")
            ) { Text(chosen?.let { if (it.id == state.activeSplitId) "Use ${gear.short().lowercase()}" else "Switch to ${it.name}" } ?: "Answer the questions") }
        }
    }
    if (confirming && chosen != null && experience != null) ConfirmDialog(
        title = "Switch to ${chosen.name}?",
        text = "From today, your week follows ${chosen.name}. Workouts you've already done stay done and keep " +
            "this week's credit. Changes you made to ${current?.name ?: "your plan"} are kept and come back if you " +
            "switch back. Your phase stays ${state.phase?.title}.",
        confirmLabel = "Switch",
        busy = env.runner.busy,
        onDismiss = { confirming = false },
        onConfirm = {
            env.runner.run(Action.ChangeSplit(chosen.id, gear, experience, localDateOf(env.clock()).toString()),
                onError = { error = it; confirming = false }) { confirming = false; onBack() }
        }
    )
}
