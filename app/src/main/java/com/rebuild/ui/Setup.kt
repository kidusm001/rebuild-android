package com.rebuild.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.togetherWith
import com.rebuild.ui.theme.Motion
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rebuild.R
import com.rebuild.domain.Action
import com.rebuild.domain.AppState
import com.rebuild.domain.Experience
import com.rebuild.domain.Gear
import com.rebuild.domain.MassUnit
import com.rebuild.domain.Phase
import com.rebuild.domain.ScheduledRoutine
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
    Gear.DUMBBELLS -> "Dumbbells you can change, and a bench. At home or a small gym."
    Gear.BODYWEIGHT -> "No weights. A pull-up bar or a sturdy table helps for pulling."
}

@DrawableRes
private fun Gear.icon(): Int = when (this) {
    Gear.GYM -> R.drawable.ic_building
    Gear.DUMBBELLS -> R.drawable.ic_dumbbell
    Gear.BODYWEIGHT -> R.drawable.ic_person_standing
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

private fun dayShort(weekday: Int): String = DayOfWeek.of(weekday).getDisplayName(TextStyle.SHORT, Locale.getDefault())
private fun dayLong(weekday: Int): String = DayOfWeek.of(weekday).getDisplayName(TextStyle.FULL, Locale.getDefault())
private fun dayLetter(weekday: Int): String = DayOfWeek.of(weekday).getDisplayName(TextStyle.NARROW, Locale.getDefault())

/** "Mon, Wed and Fri". */
private fun daysText(weekdays: List<Int>): String {
    val names = weekdays.map(::dayShort)
    return if (names.size <= 1) names.joinToString() else names.dropLast(1).joinToString(", ") + " and " + names.last()
}

/**
 * First-run setup: a welcome, three questions and the plan they lead to. Nothing is saved until "Start with this
 * plan"; then [onPlanSaved] shows the last, optional step (rest alerts). Steps slide in the direction you're going.
 */
@Composable
internal fun SetupFlow(env: AppEnv, onPlanSaving: (Boolean) -> Unit) {
    var stepName by rememberSaveable { mutableStateOf(SetupStep.WELCOME.name) }
    var experienceName by rememberSaveable { mutableStateOf<String?>(null) }
    var days by rememberSaveable { mutableStateOf<Int?>(null) }
    var gearName by rememberSaveable { mutableStateOf<String?>(null) }
    var unitName by rememberSaveable { mutableStateOf(defaultMassUnit().name) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    // The plan's "Change" links come back to the plan once the answer is changed.
    var returnToPlan by rememberSaveable { mutableStateOf(false) }
    // Which way the last move went, so the next page slides in from the side you're heading to.
    var forward by rememberSaveable { mutableStateOf(true) }
    val step = SetupStep.valueOf(stepName)
    val experience = experienceName?.let(Experience::valueOf)
    val gear = gearName?.let(Gear::valueOf)
    val unit = MassUnit.valueOf(unitName)

    fun go(to: SetupStep) { forward = to.ordinal > SetupStep.valueOf(stepName).ordinal; stepName = to.name; error = null }
    fun next() = go(if (returnToPlan) SetupStep.PLAN else SetupStep.entries[step.ordinal + 1])
    fun change(to: SetupStep) { returnToPlan = true; go(to) }
    fun back() {
        // Presses can arrive faster than the handler is switched off, so Welcome simply ignores one.
        val now = SetupStep.valueOf(stepName)
        if (now == SetupStep.WELCOME) return
        if (returnToPlan && now != SetupStep.PLAN) go(SetupStep.PLAN) else go(SetupStep.entries[now.ordinal - 1])
    }
    BackHandler(enabled = step != SetupStep.WELCOME, onBack = ::back)

    Column(Modifier.fillMaxSize()) {
    // Back and the progress stay put above the pages: only the pages move, and the progress fills by the Colour rule.
    SetupHeader(stepNumber(step), onBack = if (step == SetupStep.WELCOME) null else ::back)
    val shift = with(LocalDensity.current) { Motion.Shift.roundToPx() }
    AnimatedContent(
        targetState = step,
        modifier = Modifier.weight(1f),
        transitionSpec = {
            // Steps are a sequence: the next comes in from the side you're heading to, the last leaves the other way.
            val sign = if (forward) 1 else -1
            Motion.lateralEnter(sign * shift) togetherWith Motion.lateralExit(-sign * shift) using null
        },
        label = "setup-step"
    ) { shown ->
        // Only the settled page takes taps: while two pages overlap mid-slide, a quick second tap would otherwise land
        // on the next page's action (and could save a plan nobody has looked at). The leaving page is hidden from
        // screen readers so they never see two pages.
        val settled = shown == step && transition.currentState == androidx.compose.animation.EnterExitState.Visible
        Box(if (shown == step) Modifier else Modifier.clearAndSetSemantics {}) { CompositionLocalProvider(LocalSettled provides settled) {
        when (shown) {
            SetupStep.WELCOME -> Welcome(onStart = { go(SetupStep.EXPERIENCE) }, onRestore = { env.platform.chooseRestore() })
            SetupStep.EXPERIENCE -> SetupPage(1, "How much have you trained?",
                "This picks how your plan starts. You can change everything later.",
                action = "Continue", enabled = experience != null, onAction = ::next) {
                Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Experience.entries.forEach { option ->
                        ChoiceCard(option == experience, { experienceName = option.name }, option.title(), option.detail(),
                            tag = "setup-experience-${option.name.lowercase()}") { LevelGlyph(option.ordinal + 1, option == experience) }
                    }
                }
            }
            SetupStep.DAYS -> SetupPage(2, "How many days a week?",
                "Pick what fits a normal week. Rebuild spreads the days out so you rest between them.",
                action = "Continue", enabled = days != null, onAction = ::next) {
                DaySelector(days) { days = it }
                days?.let { count ->
                    WeekStrip(Splits.weekdays(count).associateWith { null }, Modifier.testTag("setup-week-strip"))
                    Text("Training on ${daysText(Splits.weekdays(count))}. Rest on " +
                        "${daysText((1..7).filterNot { it in Splits.weekdays(count) })}.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (experience != null && Splits.manyDaysForABeginner(experience, count)) Note(
                        "3–4 days is plenty when you're starting. You can change this later in Plan.",
                        Modifier.testTag("setup-days-note"))
                }
            }
            SetupStep.EQUIPMENT -> SetupPage(3, "What will you train with?",
                "Every exercise in your plan will fit it.",
                action = "See your plan", enabled = gear != null, onAction = ::next) {
                Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Gear.entries.forEach { option ->
                        ChoiceCard(option == gear, { gearName = option.name }, option.title(), option.detail(),
                            tag = "setup-gear-${option.name.lowercase()}") { IconBadge(option.icon(), option == gear) }
                    }
                }
                Text("Weights in", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(top = 4.dp).semantics { heading() })
                PillChoice(MassUnit.entries, unit, { unitName = it.name },
                    { if (it == MassUnit.KG) "Kilograms (kg)" else "Pounds (lb)" },
                    tag = { "setup-unit-${it.name.lowercase()}" })
            }
            SetupStep.PLAN -> {
                val split = Splits.recommend(experience!!, days!!, gear!!)
                SetupPage(4, null, null, action = "Start with this plan",
                    // The save greys nothing unless it is slow; a second tap meanwhile does nothing.
                    enabled = !env.runner.looksBusy(), onAction = {
                        if (!env.runner.busy) {
                            // The last step is marked before the save, so it still shows if the screen turns mid-save.
                            onPlanSaving(true)
                            env.runner.run(Action.CompleteSetup(experience, split.id, gear, unit,
                                localDateOf(env.clock()).toString()), onError = { error = it; onPlanSaving(false) })
                        }
                    }) {
                    PlanReveal(previewState(env.state, split, gear, experience), split, gear,
                        note = if (Splits.manyDaysForABeginner(experience, days!!))
                            "3–4 days is plenty when you're starting. You can change days any time." else null)
                    AnswersCard(experience, days!!, gear, unit, split, onChange = ::change)
                    ErrorText(error)
                }
            }
        }
        } }
    }
    }
}

/** Which of the four numbered steps [step] is, or null for the welcome. */
private fun stepNumber(step: SetupStep): Int? = if (step == SetupStep.WELCOME) null else step.ordinal

/**
 * Back and the four-step progress, above the pages and never moving with them. On the welcome both are absent: they
 * fade in with the first question.
 */
@Composable
private fun SetupHeader(number: Int?, onBack: (() -> Unit)?) {
    val lastNumber = androidx.compose.runtime.remember { intArrayOf(1) }
    if (number != null) lastNumber[0] = number
    Row(Modifier.readableWidth().padding(start = 4.dp, end = 20.dp).heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(48.dp)) {
            androidx.compose.animation.AnimatedVisibility(onBack != null, enter = Motion.fadeIn(), exit = Motion.fadeOut()) {
                IconButton(onClick = { onBack?.invoke() }, modifier = Modifier.testTag("setup-back")) {
                    RebuildIcon(R.drawable.ic_arrow_left, contentDescription = "Back")
                }
            }
        }
        AnimatedVisibility(number != null, Modifier.weight(1f).padding(start = 4.dp),
            enter = Motion.fadeIn(), exit = Motion.fadeOut()) {
            SetupProgress(number ?: lastNumber[0])
        }
    }
}

/**
 * Actions sit pinned at the bottom on roomy screens; at large text or on a short (landscape) window they come inline
 * at the end, so the choices keep the room.
 */
@Composable
private fun actionsPinned(): Boolean = LocalDensity.current.fontScale <= 1.3f &&
    with(LocalDensity.current) { androidx.compose.ui.platform.LocalWindowInfo.current.containerSize.height.toDp() } >= 480.dp

/** Whether this setup page has finished sliding in; until then its actions wait. */
private val LocalSettled = androidx.compose.runtime.staticCompositionLocalOf { true }

@Composable
private fun Welcome(onStart: () -> Unit, onRestore: () -> Unit) {
    SetupPage(null, null, null, action = "Get started", enabled = true, onAction = onStart,
        secondary = {
            SecondaryButton(onClick = onRestore, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .testTag("setup-restore")) { Text("I have a backup") }
        }) {
        Spacer(Modifier.height(8.dp))
        // The launcher icon itself: its 108 dp foreground canvas behind a 72 dp tile, cropped the way a launcher crops it.
        // The hairline keeps the ink tile's edge visible on the dark theme's near-black surface.
        val tile = RoundedCornerShape(22.dp)
        Box(
            Modifier.size(72.dp).clip(tile).background(colorResource(R.color.launcher_background))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, tile).testTag("setup-app-icon"),
            contentAlignment = Alignment.Center
        ) {
            Image(painterResource(R.drawable.ic_rebuild_foreground), contentDescription = null,
                modifier = Modifier.requiredSize(108.dp))
        }
        Text("Rebuild", style = overlineStyle(), color = accentText(), modifier = Modifier.padding(top = 8.dp))
        Text("Your training, written down.", style = MaterialTheme.typography.displaySmall,
            modifier = Modifier.semantics { heading() })
        Text("A notebook for the gym floor: log each set beside last time, rest on a timer, and keep it all.",
            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(top = 8.dp)) {
            Promise(R.drawable.ic_smartphone, "Stays on this phone", "Works offline. No account, and nothing is uploaded.")
            Promise(R.drawable.ic_notebook_pen, "You log, it remembers", "Every set beside the last one, so progress is plain to see.")
            Promise(R.drawable.ic_lock, "No pressure", "No streaks or scores, and it never tells you what weight to lift.")
        }
    }
}

@Composable
private fun Promise(@DrawableRes icon: Int, title: String, detail: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top,
        modifier = Modifier.semantics(mergeDescendants = true) {}) {
        IconBadge(icon, false)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Four segments, filled up to [step]; read as "Step 2 of 4". */
@Composable
private fun SetupProgress(step: Int, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    // The step is spoken with the page's heading; the bar itself is decoration to a screen reader.
    Row(modifier.testTag("setup-step").clearAndSetSemantics {
        contentDescription = "Step $step of 4"; hideFromAccessibility() },
        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        (1..4).forEach { segment ->
            val fill by animateColorAsState(if (segment <= step) colors.primaryContainer else colors.surfaceContainerHigh,
                Motion.colourSpec(), label = "setup-progress")
            Box(Modifier.weight(1f).height(6.dp).clip(CircleShape).background(fill))
        }
    }
}

/**
 * One setup page: back and progress at the top, a heading and its line, the choices, and the action pinned at the
 * bottom (inline at large text, so the choices keep the room). Screen readers hear the page's step and title.
 */
@Composable
private fun SetupPage(
    number: Int?,
    title: String?,
    subtitle: String?,
    action: String,
    enabled: Boolean,
    onAction: () -> Unit,
    secondary: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val pinned = actionsPinned()
    val step = number?.let { "Step $it of 4" }
    val actions: @Composable ColumnScope.() -> Unit = {
        Button(onClick = onAction, enabled = enabled && LocalSettled.current,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("setup-action")) { Text(action) }
        secondary?.invoke()
    }
    // Back and the progress are drawn above the pages by SetupHeader.
    Column(Modifier.fillMaxSize().semantics { paneTitle = listOfNotNull(step, title ?: "Rebuild").joinToString(": ") }) {
        ScrollBody {
            if (title != null) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.semantics {
                        heading()
                        if (step != null) contentDescription = "$step, $title"
                    })
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            content()
            if (!pinned) Column(verticalArrangement = Arrangement.spacedBy(8.dp), content = actions)
        }
        if (pinned) Column(Modifier.readableWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp), content = actions)
    }
}

/**
 * A large choice card: a leading glyph, a title and one line. Chosen, it fills amber-tinted with a ring and a check,
 * so the choice reads at a glance and never by colour alone.
 */
@Composable
private fun ChoiceCard(
    selected: Boolean,
    onClick: () -> Unit,
    title: String,
    detail: String?,
    tag: String,
    leading: @Composable () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val fill by animateColorAsState(if (selected) colors.secondaryContainer else colors.surfaceContainerLowest,
        Motion.colourSpec(), label = "choice-fill")
    val ring by animateColorAsState(if (selected) colors.primary else colors.primary.copy(alpha = 0f), Motion.colourSpec(),
        label = "choice-ring")
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(fill)
            .border(2.dp, ring, MaterialTheme.shapes.medium)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .heightIn(min = 72.dp)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        leading()
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium,
                color = if (selected) colors.onSecondaryContainer else colors.onSurface)
            detail?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium,
                    color = if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant)
            }
        }
        Box(Modifier.size(24.dp).clip(CircleShape)
            .background(if (selected) colors.primary else Color.Transparent)
            .border(2.dp, if (selected) colors.primary else colors.outline, CircleShape),
            contentAlignment = Alignment.Center) {
            if (selected) RebuildIcon(R.drawable.ic_check, contentDescription = null, tint = colors.onPrimary,
                modifier = Modifier.size(16.dp))
        }
    }
}

/** An icon in a round tonal badge; amber when its card is chosen. */
@Composable
private fun IconBadge(@DrawableRes icon: Int, active: Boolean) {
    val colors = MaterialTheme.colorScheme
    Box(Modifier.size(44.dp).clip(CircleShape)
        .background(if (active) colors.primaryContainer else colors.surfaceContainerHigh),
        contentAlignment = Alignment.Center) {
        RebuildIcon(icon, contentDescription = null, modifier = Modifier.size(22.dp),
            tint = if (active) colors.onPrimaryContainer else colors.onSurface)
    }
}

/** Rising bars, [level] of four filled: how much training an experience answer means, without a word of jargon. */
@Composable
private fun LevelGlyph(level: Int, active: Boolean) {
    val colors = MaterialTheme.colorScheme
    Box(Modifier.size(44.dp).clip(CircleShape)
        .background(if (active) colors.primaryContainer else colors.surfaceContainerHigh),
        contentAlignment = Alignment.Center) {
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
            (1..4).forEach { bar ->
                val on = bar <= level
                Box(Modifier.width(4.dp).height((5 + bar * 4).dp).clip(RoundedCornerShape(2.dp)).background(
                    when {
                        on && active -> colors.onPrimaryContainer
                        on -> colors.onSurface
                        else -> colors.onSurface.copy(alpha = 0.2f)
                    }))
            }
        }
    }
}

/** Five big round tiles, 2 to 6, one chosen; each read as "3 days a week". */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DaySelector(selected: Int?, onSelect: (Int) -> Unit) {
    val colors = MaterialTheme.colorScheme
    FlowRow(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp), maxItemsInEachRow = 5) {
        (2..6).forEach { count ->
            val on = count == selected
            val fill by animateColorAsState(if (on) colors.primaryContainer else colors.surfaceContainerLowest,
                Motion.colourSpec(), label = "day-fill")
            Column(
                Modifier
                    .weight(1f)
                    .heightIn(min = 72.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(fill)
                    .selectable(on, role = Role.RadioButton) { onSelect(count) }
                    .semantics { contentDescription = "$count days a week" }
                    .padding(vertical = 10.dp)
                    .testTag("setup-days-$count"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("$count", style = MaterialTheme.typography.displaySmall,
                    color = if (on) colors.onPrimaryContainer else colors.onSurface)
                Text("days", style = MaterialTheme.typography.labelMedium,
                    color = if (on) colors.onPrimaryContainer else colors.onSurfaceVariant)
            }
        }
    }
}

/**
 * The week, Monday to Sunday: training days filled amber, rest days quiet, with each day's workout beneath when
 * known. One spoken line names the training days.
 */
@Composable
private fun WeekStrip(days: Map<Int, String?>, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val spoken = days.keys.sorted().joinToString(", ") { d -> dayLong(d) + (days[d]?.let { ": $it" } ?: "") }
    TonalCard(modifier.clearAndSetSemantics { contentDescription = "Training days: $spoken" }) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            (1..7).forEach { weekday ->
                val training = weekday in days
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(dayLetter(weekday), style = MaterialTheme.typography.labelMedium,
                        color = if (training) colors.onSurface else colors.onSurfaceVariant)
                    Box(Modifier.fillMaxWidth().aspectRatio(1f, matchHeightConstraintsFirst = false)
                        .padding(2.dp).clip(CircleShape)
                        .background(if (training) colors.primaryContainer else colors.surfaceContainerHigh),
                        contentAlignment = Alignment.Center) {
                        if (training) RebuildIcon(R.drawable.ic_dumbbell, contentDescription = null,
                            tint = colors.onPrimaryContainer, modifier = Modifier.size(14.dp))
                    }
                    if (days.values.any { it != null }) Text(days[weekday]?.let(::workoutInitials) ?: "",
                        style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant, maxLines = 1,
                        textAlign = TextAlign.Center)
                }
            }
        }
    }
}

/** "Full body A" → "A", "Upper B" → "Up B", "Upper power" → "Up P", "Chest and back A" → "C/B A": short for the strip. */
internal fun workoutInitials(name: String): String {
    val words = name.split(" ")
    return when {
        name.startsWith("Full body") -> words.last()
        name == "Shoulders" -> "Delts"
        name.startsWith("Chest and back") -> "C/B " + words.last()
        name.startsWith("Shoulders and arms") -> "S/A " + words.last()
        words.size == 2 && words.first() == "Push" -> "Psh " + words.last()
        words.size == 2 && words.first() == "Pull" -> "Pll " + words.last()
        words.size == 2 && words.last() == "power" -> words.first().take(2) + " P"
        words.size == 2 && words.last() == "hypertrophy" -> words.first().take(2) + " H"
        words.size == 2 && words.last().length == 1 -> words.first().take(2) + " " + words.last()
        else -> words.first().take(5)
    }
}

/** A quiet amber-edged note: a heads-up, never an error. */
@Composable
private fun Note(text: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
        .background(MaterialTheme.colorScheme.secondaryContainer).padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

/**
 * The plan, revealed: its name and why, the week as a strip of real workouts, what the phase asks in three tiles,
 * then each day as a card that opens to its exercises.
 */
@Composable
internal fun PlanReveal(state: AppState, split: Split, gear: Gear, note: String? = null) {
    val phase = requireNotNull(state.phase)
    val week = state.availableTemplates(phase).sortedBy { it.weekday }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Your plan", style = overlineStyle(), color = accentText())
        Text(split.name, style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.semantics { heading() }.testTag("split-name"))
        Text(split.reason, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    note?.let { Note(it) }
    WeekStrip(week.associate { it.weekday to it.name })
    val sets = week.flatMap { state.plannedExercises(it, phase) }.mapNotNull { it.sets }
    val setRange = range(sets)
    val perWorkout = range(week.map { state.plannedExercises(it, phase).size })
    val setWord = if (setRange == "1") "set" else "sets"
    // Three tiles side by side; at large text one line, so the reveal isn't a screen of grey slabs.
    if (LocalDensity.current.fontScale > 1.3f) {
        Text("$perWorkout exercises a workout · $setRange $setWord each · about ${phase.targetRir} reps in reserve",
            style = MaterialTheme.typography.titleMedium)
    } else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatTile(perWorkout, "exercises a day", Modifier.weight(1f), spoken = "$perWorkout exercises a workout")
        StatTile(setRange, "$setWord per exercise", Modifier.weight(1f), spoken = "$setRange working $setWord per exercise")
        StatTile("${phase.targetRir}", "reps in reserve", Modifier.weight(1f), spoken = "about ${phase.targetRir} reps in reserve")
    }
    Text("You start in ${phase.title}: ${phaseInWords(phase)} Stop each set with about ${phase.targetRir} reps still " +
        "in you (${phase.targetRir} RIR, reps in reserve). You choose when to move up, in Plan.",
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (split.rampsDays) Text("This plan is 4 days now and 5 once you move to Two-set start.",
        style = MaterialTheme.typography.bodyMedium)
    Text("Your week", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        modifier = Modifier.padding(top = 4.dp).semantics { heading() })
    val labels = week.map { dayShort(it.weekday) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { week.forEach { DayCard(state, it, phase, labels) } }
    val swapped = split.swappedFor(gear)
    val dropped = split.droppedFor(gear)
    if (swapped > 0 || dropped > 0) Text(
        "Built for ${gear.title().replaceFirstChar { it.lowercase() }}: " + listOfNotNull(
            swapped.takeIf { it > 0 }?.let { "${countLabel(it, "gym exercise")} swapped for " +
                if (gear == Gear.BODYWEIGHT) "bodyweight versions" else "dumbbell versions" },
            dropped.takeIf { it > 0 }?.let { "${countLabel(it, "machine exercise")} with no match left out" }
        ).joinToString(", ") + ".",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.testTag("split-gear-note"))
}

/** "6" or "6–7". */
private fun range(values: List<Int>): String =
    if (values.isEmpty()) "–" else if (values.min() == values.max()) "${values.min()}" else "${values.min()}–${values.max()}"

/** What a phase asks, in a beginner's words. */
private fun phaseInWords(phase: Phase): String = when (phase) {
    Phase.INTRO_ONE -> "one easy set of each exercise."
    Phase.INTRO_TWO, Phase.FIVE_TWO -> "two working sets of each exercise."
    Phase.BASELINE -> "each exercise's full number of sets."
}

/** One day of the plan: its weekday, workout and count, opening to each exercise's sets and reps. */
@Composable
private fun DayCard(state: AppState, template: ScheduledRoutine, phase: Phase, weekLabels: List<String>) {
    var open by rememberSaveable(template.id) { mutableStateOf(false) }
    val planned = state.plannedExercises(template, phase)
    TonalCard { Column {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 64.dp)
                .clickable(onClickLabel = if (open) "Hide exercises" else "Show exercises") { open = !open }
                .semantics {
                    contentDescription = "${dayLong(template.weekday)}, ${template.name}, ${countLabel(planned.size, "exercise")}"
                    stateDescription = if (open) "Expanded" else "Collapsed"
                }
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .testTag("split-day-${template.id}"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // One width for every day, growing only at large text, so the workout names line up.
            Box(Modifier.heightIn(min = 44.dp).widthIn(min = 56.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer).padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center) {
                // Every label of the week stacked unseen, so each badge is as wide as the widest day at any text size.
                weekLabels.forEach {
                    Text(it, style = MaterialTheme.typography.labelLarge, maxLines = 1, color = Color.Transparent,
                        modifier = Modifier.clearAndSetSemantics {})
                }
                Text(dayShort(template.weekday), style = MaterialTheme.typography.labelLarge, maxLines = 1,
                    color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Column(Modifier.weight(1f)) {
                Text(template.name, style = MaterialTheme.typography.titleMedium)
                // Closed, a taste of the day; open, the list below says the rest.
                val sets = range(planned.mapNotNull { it.sets })
                Text(if (open) "${countLabel(planned.size, "exercise")} · $sets ${if (sets == "1") "set" else "sets"} each"
                    else planned.take(2).joinToString(", ") { state.exercises.find { e -> e.id == it.exerciseId }?.name ?: "" } +
                        if (planned.size > 2) " and ${planned.size - 2} more" else "",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Chevron(open, Modifier.size(20.dp))
        }
        AnimatedVisibility(open, enter = Motion.expand(), exit = Motion.collapse()) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)) {
                planned.forEachIndexed { i, exercise ->
                    val name = state.exercises.find { it.id == exercise.exerciseId }?.name ?: exercise.exerciseId
                    val spoken = "$name, ${exercise.sets} ${if (exercise.sets == 1) "set" else "sets"} of " +
                        "${exercise.minReps} to ${exercise.maxReps} reps" + if (exercise.perLeg) ", each leg" else ""
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp).clearAndSetSemantics { contentDescription = spoken },
                        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        OrderBadge(i + 1)
                        Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text("${exercise.sets} × ${exercise.minReps}–${exercise.maxReps}" +
                            if (exercise.perLeg) " / leg" else "",
                            style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    } }
}

/** The answers behind the plan, each with its own Change, so adjusting one never means starting over. */
@Composable
private fun AnswersCard(experience: Experience, days: Int, gear: Gear, unit: MassUnit, split: Split,
    onChange: (SetupStep) -> Unit) {
    Text("Built from your answers", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        modifier = Modifier.padding(top = 4.dp).semantics { heading() })
    val rows = listOf(
        Triple("Experience", experience.title(), SetupStep.EXPERIENCE),
        Triple("Days", if (split.rampsDays) "$days a week (4 to start)" else "$days a week", SetupStep.DAYS),
        Triple("Equipment", "${gear.short()} · ${unit.symbol}", SetupStep.EQUIPMENT)
    )
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        rows.forEachIndexed { i, (label, value, step) ->
            Row(Modifier.groupedRow(i, rows.lastIndex).heightIn(min = 56.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(value, style = MaterialTheme.typography.bodyLarge)
                }
                TextButton(onClick = { onChange(step) },
                    modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Change ${label.lowercase()}" }) {
                    Text("Change")
                }
            }
        }
    }
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
    val colors = MaterialTheme.colorScheme
    BackHandler(onBack = onDone)
    Column(Modifier.fillMaxSize().semantics { paneTitle = "Last step: Rest alerts" }) {
        Row(Modifier.readableWidth().padding(horizontal = 20.dp).heightIn(min = 56.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text("Plan saved · last step", style = overlineStyle(), color = colors.onSurfaceVariant)
        }
        ScrollBody {
            Box(Modifier.size(72.dp).clip(RoundedCornerShape(22.dp)).background(colors.primaryContainer),
                contentAlignment = Alignment.Center) {
                RebuildIcon(R.drawable.ic_bell, contentDescription = null, tint = colors.onPrimaryContainer,
                    modifier = Modifier.size(34.dp))
            }
            Text("Know when to lift again", style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.semantics { heading(); contentDescription = "Last step, Rest alerts: know when to lift again" })
            Text("When a rest between sets ends, Rebuild can tell you, even with the phone in your pocket.",
                style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
            Text("How it tells you", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.semantics { heading() })
            val main = listOf(RestSound.BUZZ, RestSound.CHIME, RestSound.ALARM, RestSound.SILENT)
            // Four short pills when they fit; at large text a list, so no word is broken.
            if (LocalDensity.current.fontScale > 1.3f) {
                ChoiceGroup(main, if (alert.sound in main) alert.sound else RestSound.BUZZ, "rest-sound",
                    { it.title }, { it.detail }) { env.platform.setRestAlert(alert.withSound(it)) }
            } else {
                PillChoice(main, if (alert.sound in main) alert.sound else RestSound.BUZZ,
                    { env.platform.setRestAlert(alert.withSound(it)) }, { it.title },
                    tag = { "rest-sound-${it.name.lowercase()}" })
                Text(alert.sound.detail, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }
            SecondaryButton(onClick = { env.previewRestEnd() }, icon = R.drawable.ic_play,
                modifier = Modifier.testTag("setup-rest-try")) { Text("Try it") }
            when {
                allowed -> {
                    val late = env.status.batteryRestricted || !env.status.exactAlarmsAllowed
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag("setup-rest-on")) {
                        RebuildIcon(R.drawable.ic_check, contentDescription = null, tint = accentText())
                        Text(when {
                            !env.status.exactAlarmsAllowed -> "Rest alerts are on, but Android may let them arrive a little late."
                            late -> "Rest alerts are on, but battery saving may delay them."
                            else -> "Rest alerts are on."
                        },
                            style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    }
                    if (late) SecondaryButton(
                        onClick = { if (!env.status.exactAlarmsAllowed) env.platform.requestExactAlarms()
                            else env.platform.openBatterySettings() },
                        modifier = Modifier.testTag("setup-rest-battery")) { Text("Allow on-time alerts") }
                }
                asked -> Note("Notifications are off, so rest alerts only show while Rebuild is open. You can turn " +
                    "them on later in More › Rest alert.", Modifier.testTag("setup-rest-denied"))
                else -> Text("This needs notifications. Android will ask next.", style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant)
            }
            Text("More options, like a long buzz or a flash on screen, are in More › Rest alert.",
                style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
        Column(Modifier.readableWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!allowed && !asked) {
                Button(onClick = { asked = true; env.platform.requestNotifications() },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("setup-rest-allow")) {
                    Text("Turn on rest alerts")
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
    // A split picked from the list replaces the recommendation until an answer changes; the split in use starts picked,
    // so one chosen by hand isn't swapped for the quiz's suggestion just by opening this screen.
    var pickedId by rememberSaveable { mutableStateOf<String?>(state.activeSplitId) }
    var confirming by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val experience = experienceName?.let(Experience::valueOf)
    val gear = Gear.valueOf(gearName)
    val recommended = if (experience != null && days != null) Splits.recommend(experience, days!!, gear) else null
    val chosen = pickedId?.let(Splits::byId)?.takeIf { gear in it.gear } ?: recommended
    val active = state.activeWorkout
    val unchanged = chosen?.id == state.activeSplitId && gear == state.gear
    val pinned = actionsPinned()
    val switch: @Composable () -> Unit = {
        if (unchanged) Text("This is your plan now. Change an answer or pick another split to switch.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp).testTag("split-unchanged"))
        else Button(
            onClick = { confirming = true },
            enabled = active == null && chosen != null && experience != null && !env.runner.busy && !unchanged,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("split-switch")
        ) {
            Text(when {
                experience == null -> "Choose how much you've trained"
                chosen == null -> "Choose your days"
                unchanged -> "This is your plan now"
                chosen.id == state.activeSplitId -> "Switch to ${gear.title().replaceFirstChar { it.lowercase() }}"
                else -> "Switch to ${chosen.name}"
            })
        }
    }
    ScreenScaffold(title = "Change split", onBack = onBack) {
        ScrollBody {
            if (active != null) Note("${active.name} is in progress. Finish or discard it before changing your split.",
                Modifier.testTag("split-locked"))
            Text("Answer again, or pick a split yourself. Your workouts so far, and the changes you made to this plan, " +
                "are kept.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SectionHeader("How much have you trained?")
            Text("This helps pick a split. Your phase stays ${state.phase?.title}; change it with Change phase.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Experience.entries.forEach { option ->
                    ChoiceCard(option == experience, { experienceName = option.name; pickedId = null }, option.title(),
                        option.detail(), tag = "split-experience-${option.name.lowercase()}") {
                        LevelGlyph(option.ordinal + 1, option == experience)
                    }
                }
            }
            SectionHeader("How many days a week?")
            DaySelector(days) { days = it; pickedId = null }
            SectionHeader("What will you train with?")
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Gear.entries.forEach { option ->
                    ChoiceCard(option == gear, { gearName = option.name }, option.title(), option.detail(),
                        tag = "split-gear-${option.name.lowercase()}") { IconBadge(option.icon(), option == gear) }
                }
            }
            SectionHeader("Or pick a split yourself")
            Text("Picking one here replaces the split your answers suggest.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Splits.forGear(gear).sortedWith(compareBy({ it.days }, { it.name })).forEach { split ->
                    ChoiceCard(split == chosen, { pickedId = split.id }, split.name,
                        "${split.days} days a week" + (if (split.id == state.activeSplitId) " · your split now" else "") +
                            "\n${split.reason}",
                        tag = "split-pick-${split.id}") {
                        val on = split == chosen
                        Box(Modifier.size(44.dp).clip(CircleShape).background(
                            if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh),
                            contentAlignment = Alignment.Center) {
                            Text("${split.days}", style = MaterialTheme.typography.titleMedium,
                                color = if (on) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
            if (chosen != null && experience != null) {
                Spacer(Modifier.height(4.dp))
                PlanReveal(previewState(state, chosen, gear, experience), chosen, gear)
            }
            ErrorText(error)
            if (!pinned) switch()
        }
        // On roomy screens the switch stays in reach, as setup's Continue does, however long the preview gets.
        if (pinned) Column(Modifier.readableWidth().padding(horizontal = 20.dp, vertical = 12.dp)) { switch() }
    }
    if (confirming && chosen != null && experience != null) ConfirmDialog(
        title = if (chosen.id == state.activeSplitId) "Train ${chosen.name} with ${gear.title().replaceFirstChar { it.lowercase() }}?"
            else "Switch to ${chosen.name}?",
        text = (if (chosen.id == state.activeSplitId) "From today, each exercise uses its version for " +
            "${gear.title().replaceFirstChar { it.lowercase() }}. " else "From today, your week follows ${chosen.name}. ") +
            "Workouts you've already done stay done and keep this week's credit. Changes you made to " +
            "${current?.name ?: "your plan"} are kept and come back if you switch back. Your phase stays ${state.phase?.title}.",
        confirmLabel = "Switch",
        busy = env.runner.busy,
        onDismiss = { confirming = false },
        onConfirm = {
            env.runner.run(Action.ChangeSplit(chosen.id, gear, experience, localDateOf(env.clock()).toString()),
                onError = { error = it; confirming = false }) { confirming = false; onBack() }
        }
    )
}
