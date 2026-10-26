package com.rebuild.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rebuild.R
import com.rebuild.domain.Action
import com.rebuild.domain.Measure
import com.rebuild.domain.Phase
import com.rebuild.domain.RoutineStatus
import com.rebuild.domain.RoutineWeek
import com.rebuild.domain.TodayKind
import com.rebuild.domain.WeekChecklist
import com.rebuild.domain.Workout
import com.rebuild.domain.WorkoutExercise
import com.rebuild.domain.ScheduledRoutine
import com.rebuild.domain.backupDue
import com.rebuild.domain.localDateOf
import com.rebuild.domain.measureOf
import com.rebuild.domain.openGroup
import com.rebuild.domain.plannedExercises
import com.rebuild.domain.scheduledRoutine
import com.rebuild.domain.startLocalDate
import com.rebuild.domain.todayPlan
import com.rebuild.domain.weekChecklist
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Session cards list this many exercises; the rest wait behind "+N more". */
private const val SESSION_PREVIEW = 4

private val dayMonthFormat: DateTimeFormatter get() = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())

@Composable
internal fun HomeScreen(env: AppEnv, onOpenWorkout: () -> Unit, onNavigate: (String) -> Unit) {
    val state = env.state
    val phase = state.phase ?: return
    val now = env.clock()
    val today = weekdayOf(now)
    val date = localDateOf(now)
    val checklist = state.weekChecklist(date) ?: return
    val active = state.activeWorkout
    var startError by rememberSaveable { mutableStateOf<String?>(null) }

    fun start(template: ScheduledRoutine) {
        startError = null
        env.runner.run(Action.StartWorkout(template.id, env.clock()), onError = { startError = it }) {
            onOpenWorkout()
        }
    }

    fun startExtra() {
        startError = null
        env.runner.run(Action.StartExtraWorkout(env.clock()), onError = { startError = it }) { onOpenWorkout() }
    }

    ScreenScaffold(title = weekdayName(today), subtitle = "${dayMonthFormat.format(date)} · ${phaseShort(phase)}") {
        ScrollBody {
            // Sections sit 24dp apart: 16 here plus the section header's own 8 above its title.
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (active != null) {
                    ActiveSessionCard(env, active, onResume = onOpenWorkout)
                    TodayNotices(env, now, onNavigate)
                    BodySection(env, onNavigate)
                    WeekChecklistSection(env, checklist, date, featured = null, onStart = null)
                } else {
                    val plan = state.todayPlan(date, checklist)
                    val scheduled = plan.scheduled
                    val suggested = plan.suggested
                    // A day can hold several routines; each keeps its own Start in the week below.
                    val todays = checklist.routines.filter { it.template.weekday == today }
                    val featured = when {
                        plan.kind == TodayKind.SCHEDULED && scheduled != null -> {
                            // The header already names the day, so the card needs no "Scheduled for" line.
                            ScheduledSessionCard(
                                env, scheduled, phase, reason = null, "start-scheduled",
                                status = checklist.statusOf(scheduled.id), onStart = { start(scheduled) }
                            )
                            scheduled.id
                        }
                        plan.kind == TodayKind.NEXT_UNFINISHED && scheduled != null && suggested != null -> {
                            ScheduledSessionCard(
                                env, suggested, phase,
                                (if (todays.size > 1) "${weekdayName(today)}'s routines are" else "${scheduled.name} is") +
                                    " done this week · next unfinished",
                                "start-suggested", status = checklist.statusOf(suggested.id), onStart = { start(suggested) }
                            )
                            suggested.id
                        }
                        plan.kind == TodayKind.WEEK_COMPLETE -> {
                            NoteCard(
                                "Week complete",
                                "All ${checklist.routines.size} workouts done this week.",
                                Modifier.testTag("week-complete")
                            )
                            null
                        }
                        plan.kind == TodayKind.NEXT_UNFINISHED && scheduled != null -> {
                            NoteCard("Done this week", (if (todays.size > 1) "${weekdayName(today)}'s routines are"
                                else "${scheduled.name} is") + " already done this week.")
                            null
                        }
                        else -> {
                            NoteCard(
                                "Rest day",
                                "Nothing scheduled for ${weekdayName(today)}."
                            )
                            null
                        }
                    }
                    val alsoToday = todays.filter { it.template.id != featured }
                    if (plan.kind == TodayKind.SCHEDULED && alsoToday.isNotEmpty()) Text(
                        "Also on ${weekdayName(today)}: " + alsoToday.joinToString { "${it.template.name} (${statusWord(it.status)})" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("also-today")
                    )
                    ErrorText(startError)
                    TodayNotices(env, now, onNavigate)
                    WeekChecklistSection(env, checklist, date, featured = featured, onStart = ::start)
                    BodySection(env, onNavigate)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionHeader("Outside the plan")
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            OutlinedButton(
                                onClick = ::startExtra,
                                enabled = !env.runner.busy,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("start-empty")
                            ) {
                                RebuildIcon(R.drawable.ic_plus, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Start empty workout")
                            }
                            SecondaryTextButton(onClick = { onNavigate(Routes.ROUTINES) }, icon = R.drawable.ic_library,
                                modifier = Modifier.testTag("open-routines")) {
                                Text(if (state.routines.isEmpty()) "Your routines" else "Start one of your routines")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionSurface(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth(),
        content = content
    )
}

@Composable
internal fun PlayLabel(text: String) {
    RebuildIcon(R.drawable.ic_play, contentDescription = null, modifier = Modifier.size(18.dp))
    Spacer(Modifier.width(8.dp))
    Text(text)
}

@Composable
private fun NoteCard(title: String, text: String, modifier: Modifier = Modifier) {
    SessionSurface(modifier) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A session card's title block: an optional state or reason line, the routine's name, then one summary line. */
@Composable
private fun SessionTitle(eyebrow: String?, name: String, summary: String, eyebrowState: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (eyebrow != null) Text(
            eyebrow,
            style = MaterialTheme.typography.labelLarge,
            color = if (eyebrowState) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(name, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * The first [SESSION_PREVIEW] exercise rows, then a "+N more" action that reveals the rest in place. Hidden rows are
 * absent for screen readers too; the action names how many exercises it shows.
 */
@Composable
private fun <T> PreviewRows(items: List<T>, key: String, row: @Composable (index: Int, item: T) -> Unit) {
    var expanded by rememberSaveable(key) { mutableStateOf(false) }
    val hidden = items.size - SESSION_PREVIEW
    Column {
        items.take(SESSION_PREVIEW).forEachIndexed { i, item -> row(i, item) }
        if (hidden > 0) {
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(tween(200)) + expandVertically(tween(200)),
                exit = fadeOut(tween(200)) + shrinkVertically(tween(200))
            ) {
                Column { items.drop(SESSION_PREVIEW).forEachIndexed { i, item -> row(SESSION_PREVIEW + i, item) } }
            }
            MoreRowsToggle(expanded, hidden) { expanded = !expanded }
        }
    }
}

/** Sits in the rows' own columns: a chevron under the order badges, the label under the names. */
@Composable
private fun MoreRowsToggle(expanded: Boolean, hidden: Int, onToggle: () -> Unit) {
    val spoken = if (expanded) "Show fewer exercises" else "Show ${countLabel(hidden, "more exercise")}"
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .testTag("session-more")
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onToggle)
            .clearAndSetSemantics {
                contentDescription = spoken
                role = Role.Button
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(Modifier.widthIn(min = 24.dp), contentAlignment = Alignment.Center) {
            RebuildIcon(
                R.drawable.ic_chevron_down,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp).rotate(if (expanded) 180f else 0f)
            )
        }
        Text(
            if (expanded) "Show fewer" else "+$hidden more",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** The workout Today offers: why it is offered when that isn't today's schedule, one Start, and its exercises. */
@Composable
private fun ScheduledSessionCard(
    env: AppEnv,
    template: ScheduledRoutine,
    phase: Phase,
    reason: String?,
    tag: String,
    status: RoutineStatus?,
    onStart: () -> Unit
) {
    val targets = env.state.plannedExercises(template, phase)
    val hasReps = targets.any { env.state.measureOf(it.exerciseId) == Measure.REPS }
    SessionSurface {
        // The "+N more" row carries its own 48dp of air, so the card closes tighter below it.
        val bottom = if (targets.size > SESSION_PREVIEW) 4.dp else 16.dp
        Column(Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = bottom),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SessionTitle(
                reason, template.name,
                (plannedSummary(env.state, targets) + if (hasReps) " · about ${phase.targetRir} RIR" else "").keepSeparators()
            )
            if (status == RoutineStatus.PARTIAL) Text(
                "Partial ${template.name} this week · not counted",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = onStart,
                enabled = !env.runner.busy,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag(tag)
            ) { PlayLabel("Start ${template.name}") }
            if (targets.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                PreviewRows(targets, key = "planned-${template.id}") { i, target ->
                    val name = env.exercise(target.exerciseId)?.name ?: target.exerciseId
                    val measure = env.state.measureOf(target.exerciseId)
                    val short = shortTarget(measure, target.sets, target.minReps, target.maxReps, target.perLeg, target.goal)
                    val spoken = spokenTarget(measure, target.sets, target.minReps, target.maxReps, target.perLeg, target.goal)
                    SessionRow(
                        badge = { OrderBadge(i + 1) },
                        name = { Text(name, style = MaterialTheme.typography.bodyLarge) },
                        value = short,
                        spoken = "Exercise ${i + 1}: $name" + (spoken?.let { ", $it" } ?: "")
                    )
                }
            }
        }
    }
}

/**
 * Backup and rest-alert notices, one compact row each and only when they apply. Each opens the same remedy the full
 * sections did: the backup screen, or the platform setting behind the first rest-alert limitation.
 */
@Composable
private fun TodayNotices(env: AppEnv, now: Long, onNavigate: (String) -> Unit) {
    val state = env.state
    val status = env.status
    val backupDue = state.backupDue(now)
    val alert: Triple<String, String, () -> Unit>? = when {
        !status.notificationsAllowed ->
            Triple("Notifications off · rest alerts can't appear", "Allow notifications") { env.platform.requestNotifications() }
        !status.exactAlarmsAllowed ->
            Triple("Exact alarms off · rest alerts may be late", "Allow exact alarms") { env.platform.requestExactAlarms() }
        status.batteryRestricted ->
            Triple("Battery restricted · rest alerts may be late", "Battery settings") { env.platform.openBatterySettings() }
        else -> null
    }
    if (!backupDue && alert == null) return
    Column(Modifier.testTag("today-notices")) {
        if (backupDue) NoticeRow(
            R.drawable.ic_archive_restore,
            state.lastBackupAt?.let { "Last backup ${shortDate(localDateOf(it))} · changes since" }
                ?: "Not backed up · only on this phone",
            action = "Back up now",
            modifier = Modifier.testTag("notice-backup")
        ) { onNavigate(Routes.BACKUP) }
        if (backupDue && alert != null) HorizontalDivider()
        if (alert != null) NoticeRow(
            R.drawable.ic_rest, alert.first, action = alert.second,
            modifier = Modifier.testTag("notice-rest-alerts"), onClick = alert.third
        )
    }
}

@Composable
private fun NoticeRow(
    @DrawableRes icon: Int,
    text: String,
    action: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = "$text. $action"
                role = Role.Button
            }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        RebuildIcon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f),
            maxLines = 2, overflow = TextOverflow.Ellipsis)
        RebuildIcon(R.drawable.ic_chevron_right, contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
    }
}

/** A wrapped summary line never starts with a separator dot: each dot stays with the word before it. */
private fun String.keepSeparators(): String = replace(" · ", "\u00A0· ")

private fun statusWord(status: RoutineStatus): String = when (status) {
    RoutineStatus.DONE -> "done"
    RoutineStatus.IN_PROGRESS -> "in progress"
    RoutineStatus.PARTIAL -> "partial"
    RoutineStatus.NOT_DONE -> "not done"
}

private fun routineStatusText(routine: RoutineWeek): String = when (routine.status) {
    RoutineStatus.DONE -> "Done · " + (routine.doneBy?.let { shortDate(it.startLocalDate()) } ?: "this week")
    RoutineStatus.IN_PROGRESS -> "In progress"
    RoutineStatus.PARTIAL -> "Partial · not counted"
    RoutineStatus.NOT_DONE -> "Not done"
}

/**
 * This week's checklist for the current phase, Monday to Sunday, derived from finished workouts. A workout done on
 * any day ticks that workout; the schedule doesn't shift. [featured] already has its Start on the card above, and
 * without [onStart] (a workout in progress) the list is read-only.
 */
@Composable
private fun WeekChecklistSection(
    env: AppEnv,
    checklist: WeekChecklist,
    today: LocalDate,
    featured: String?,
    onStart: ((ScheduledRoutine) -> Unit)?
) {
    val done = checklist.done
    val total = checklist.routines.size
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Column {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                SectionHeader("This week", Modifier.weight(1f).alignByBaseline())
                Text(
                    "$done of $total",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .alignByBaseline()
                        .semantics { contentDescription = "Done $done of $total" }
                        .testTag("week-progress")
                )
            }
            Text(
                "${shortDate(checklist.start)} – ${shortDate(checklist.end)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Column(Modifier.testTag("week-checklist")) {
            checklist.routines.forEachIndexed { i, routine ->
                if (i > 0) HorizontalDivider()
                val template = routine.template
                val isDone = routine.status == RoutineStatus.DONE
                val week = checklist.routines.map { it.template }
                val day = occurrenceDay(template, week)
                ListRow(
                    headline = template.name,
                    supporting = routineStatusText(routine) + occurrenceOrdinal(template, week).let { if (it == null) "" else " · $it" },
                    leading = {
                        DayBlock(weekdayName(template.weekday, TextStyle.SHORT), null, weekdayName(template.weekday),
                            emphasized = template.weekday == today.dayOfWeek.value)
                    },
                    trailing = if (onStart == null || template.id == featured) null else {
                        {
                            OutlinedButton(
                                onClick = { onStart(template) },
                                enabled = !env.runner.busy,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                                modifier = Modifier.heightIn(min = 48.dp).semantics {
                                    contentDescription = "${if (isDone) "Repeat" else "Start"} ${template.name} ($day)"
                                }
                            ) { Text(if (isDone) "Repeat" else "Start") }
                        }
                    },
                    modifier = Modifier.testTag("week-row-${template.id}")
                )
            }
        }
        if (checklist.other.isNotEmpty()) {
            Text(
                "Also this week, not on this phase's checklist: " +
                    checklist.other.joinToString { "${it.name} (${shortDate(it.startLocalDate())})" } + ".",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (checklist.extras.isNotEmpty()) {
            Text(
                "Outside the plan: ${countLabel(checklist.extras.size, "extra workout")} (" +
                    checklist.extras.joinToString { shortDate(it.startLocalDate()) } + ") · not counted",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("week-extras")
            )
        }
    }
}

/**
 * The workout in progress on one surface: status and Resume first, then every exercise's working sets against its
 * target (warm-ups never count) with the one Resume opens marked, then Finish and Discard.
 */
@Composable
private fun ActiveSessionCard(env: AppEnv, workout: Workout, onResume: () -> Unit) {
    var finishing by rememberSaveable(workout.id) { mutableStateOf(false) }
    var discarding by rememberSaveable(workout.id) { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val resumeAt = opensAt(env.state, workout)
    val watch = env.state.stopwatch?.takeIf { it.workoutId == workout.id }
    SessionSurface(Modifier.testTag("active-banner")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SessionTitle(
                "In progress · started ${formatTime(workout.startedAt)}",
                workout.name,
                ((if (workout.extra) "Outside the plan" else phaseShort(workout.phase)) + " · ${workoutLoggedText(workout)}").keepSeparators(),
                eyebrowState = true
            )
            if (!workout.extra && env.state.scheduledRoutine(workout.phase, workout.templateId) == null) Text(
                OFF_WEEK_NOTE,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.testTag("off-week-note")
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onResume,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                ) { PlayLabel("Resume workout") }
                val openSet = resumeAt?.openGroup()
                val watchHere = watch?.takeIf { it.exerciseId == resumeAt?.id }
                if (resumeAt != null && (workout.exercises.size > 1 || openSet != null || watchHere != null)) {
                    Text(
                        "Opens ${resumeAt.exercise.name}" +
                            (openSet?.let { " · ${it.method.title.lowercase()} in progress" } ?: "") +
                            (watchHere?.let { " · stopwatch ${stopwatchStatus(it)}" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterHorizontally).testTag("opens-line")
                    )
                }
            }
            if (workout.exercises.isNotEmpty()) {
                HorizontalDivider(color = colors.outlineVariant)
                PreviewRows(workout.exercises, key = "active-${workout.id}") { i, we ->
                    SessionExerciseRow(i, workout.exercises.size, we, opensHere = we.id == resumeAt?.id)
                }
            }
            HorizontalDivider(color = colors.outlineVariant)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(onClick = { finishing = true }, enabled = !env.runner.busy,
                    modifier = Modifier.heightIn(min = 48.dp)) { Text("Finish") }
                TextButton(
                    onClick = { discarding = true },
                    enabled = !env.runner.busy,
                    colors = ButtonDefaults.textButtonColors(contentColor = colors.error),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) { Text("Discard") }
            }
        }
    }
    if (finishing) FinishWorkoutDialog(env, workout, onDismiss = { finishing = false }, onFinished = { finishing = false },
        onDiscard = { finishing = false; discarding = true })
    if (discarding) DiscardWorkoutDialog(env, workout, onDismiss = { discarding = false }, onDiscarded = { discarding = false })
}

/** Read-only progress line matching the workout's exercise rail. */
@Composable
private fun SessionExerciseRow(index: Int, count: Int, we: WorkoutExercise, opensHere: Boolean) {
    val met = we.goalMet()
    SessionRow(
        badge = { OrderBadge(index + 1, met = met, emphasized = opensHere) },
        name = {
            Text(
                we.exercise.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (opensHere) FontWeight.SemiBold else FontWeight.Normal,
                color = if (met && !opensHere) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
            )
        },
        value = if (we.showsSkipped()) "Skipped" else we.progressText(),
        spoken = "Exercise ${index + 1} of $count: ${we.exercise.name}, " +
            we.progressDescription() + if (opensHere) ", Resume opens this exercise" else ""
    )
}

/**
 * One exercise line in a session card, spoken whole: badge, name, and its target or progress at the end. Above 130%
 * font scale the value moves under the name, so long names keep the width instead of wrapping word by word.
 */
@Composable
private fun SessionRow(badge: @Composable () -> Unit, name: @Composable () -> Unit, value: String?, spoken: String) {
    val stacked = LocalDensity.current.fontScale > 1.3f
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .clearAndSetSemantics { contentDescription = spoken }
            .padding(vertical = if (stacked) 4.dp else 0.dp),
        verticalAlignment = if (stacked) Alignment.Top else Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        badge()
        Column(Modifier.weight(1f)) {
            name()
            if (stacked && value != null) Text(value, style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!stacked && value != null) Text(value, style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun BodySection(env: AppEnv, onNavigate: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionHeader("Body")
        TodayBodyRows(env, onWeight = { onNavigate(Routes.BODYWEIGHT) }, onMeasure = { onNavigate(Routes.MEASURE) })
    }
}
