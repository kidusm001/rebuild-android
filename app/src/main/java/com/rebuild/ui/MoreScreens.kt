package com.rebuild.ui

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import com.rebuild.domain.Action
import com.rebuild.domain.MassUnit
import com.rebuild.domain.LoadUnit
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.rebuild.R

@Composable
internal fun MoreScreen(env: AppEnv, onNavigate: (String) -> Unit) {
    val state = env.state
    val context = LocalContext.current
    val version = remember(context) { appVersion(context) }
    ScreenScaffold(title = "More") {
        ScrollBody {
            SectionHeader("Training")
            Column {
                val custom = state.exercises.count { it.custom }
                MoreRow("Exercises", countLabel(state.exercises.size, "exercise") +
                    if (custom > 0) " · $custom custom" else "", R.drawable.ic_dumbbell) { onNavigate(Routes.EXERCISES) }
                HorizontalDivider()
                MoreRow(
                    "Routines",
                    if (state.routines.isEmpty()) "None saved yet"
                    else countLabel(state.routines.size, "routine"),
                    R.drawable.ic_library,
                    Modifier.testTag("more-routines")
                ) { onNavigate(Routes.ROUTINES) }
                HorizontalDivider()
                MoreRow("Training phase", state.phase?.title ?: "Not chosen", R.drawable.ic_sliders_horizontal) {
                    onNavigate(Routes.PHASE)
                }
                HorizontalDivider()
                MoreRow("Training guidance", "Warm-ups, rest, phases and progression", R.drawable.ic_book_open) {
                    onNavigate(Routes.HELP)
                }
            }
            SectionHeader("Units")
            LiftingUnitRow(env)
            SectionHeader("Your data")
            val unsaved = state.revision > (state.lastBackupRevision ?: -1)
            MoreRow(
                "Backup and restore",
                (state.lastBackupAt?.let { "Last backup ${formatDate(it)}" + if (unsaved) " · newer changes not included" else "" }
                    ?: "No backup yet") + "\nOffline, no account. Data stays on this phone.",
                R.drawable.ic_archive_restore
            ) { onNavigate(Routes.BACKUP) }
            SectionHeader("Rest alerts")
            PlatformLimitations(env.status, env.platform, showAllClear = true)
            if (version != null) Text(
                "Rebuild $version",
                style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 24.dp)
            )
        }
    }
}

/**
 * The unit every weighted exercise's next sets start in. Switching never converts a logged set, and plate-count
 * machines keep plates; one exercise can still be switched on its own from the workout.
 */
@Composable
private fun LiftingUnitRow(env: AppEnv) {
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.testTag("more-lifting-unit")) {
        Text("Lifting weights", style = MaterialTheme.typography.bodyLarge)
        Text("Your next sets on every exercise. Logged sets keep their unit; plate machines keep plates.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LoadUnitSelector(env.state.liftingUnit.loadUnit, listOf(LoadUnit.KG, LoadUnit.LB), enabled = !env.runner.busy) {
            error = null
            val unit = if (it == LoadUnit.LB) MassUnit.LB else MassUnit.KG
            if (unit != env.state.liftingUnit) env.runner.run(Action.SetLiftingUnit(unit), onError = { error = it })
        }
        ErrorText(error)
    }
}

@Composable
private fun MoreRow(
    title: String,
    supporting: String,
    @DrawableRes icon: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    ListRow(
        title,
        modifier = modifier,
        supporting = supporting,
        onClick = onClick,
        leading = { RebuildIcon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        trailing = {
            RebuildIcon(R.drawable.ic_chevron_right, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    )
}

private fun appVersion(context: Context): String? = runCatching {
    val manager = context.packageManager
    val info = if (Build.VERSION.SDK_INT >= 33) {
        manager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION") manager.getPackageInfo(context.packageName, 0)
    }
    info.versionName
}.getOrNull()

@Composable
internal fun BackupScreen(env: AppEnv, onBack: () -> Unit) {
    val state = env.state
    val unsaved = state.revision > (state.lastBackupRevision ?: -1)
    ScreenScaffold(title = "Backup and restore", onBack = onBack) {
        ScrollBody {
            Text(
                state.lastBackupAt?.let { "Last backup: ${formatDate(it)} at ${formatTime(it)}" } ?: "No backup yet.",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                if (unsaved) "There are changes since the last backup." else "Everything is included in the last backup.",
                style = MaterialTheme.typography.bodyMedium
            )
            SectionHeader("Export")
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("One file with your phase, plan, routines, exercises, every workout and your body records.",
                    style = MaterialTheme.typography.bodyMedium)
                Text("You choose where it goes, such as Downloads.", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Restoring it needs Rebuild 0.1.12 or newer.", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(
                onClick = env.platform::exportBackup,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
            ) { Text("Export backup file") }
            OutlinedButton(
                onClick = env.platform::shareBackup,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
            ) { Text("Share backup file") }
            Text(
                "Only share it with yourself.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider()
            SectionHeader("Restore")
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Replaces everything on this phone. It doesn't merge.", style = MaterialTheme.typography.bodyMedium)
                Text("You'll see a preview and confirm first.", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Before replacing, you save a backup of this phone's data.", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedButton(
                onClick = env.platform::chooseRestore,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
            ) { Text("Choose backup to restore") }
        }
    }
}

private val helpSections = listOf(
    "Cable plates" to "$PLATES_HELP Enter whole counts from 1 to 1000. Changing units never converts saved sets. " +
        "Kilograms and plates have separate previous results and charts.",
    "Warming up" to "Spend about 5 minutes moving easily, then do a few lighter ramp-up sets for the first exercise " +
        "of a muscle. Mark them as warm-ups: they are optional and never count toward working sets.",
    "Rest" to "Rest about 3 minutes (180 s) after compound lifts and about 90 s after isolation exercises. " +
        "Adjust today's rest if you need more.",
    "Drops and rest-pause" to "These are logging options, not a recommendation. A drop is part of the set before " +
        "it: after a set, tap Add drop to set N (or choose it from the set type) and log the lighter load. The set " +
        "keeps its own row, the drop is listed beneath it, and it never counts as another working set. A rest-pause " +
        "set is one set with short pauses between mini-sets: choose Rest-pause before its first stage, log each " +
        "mini-set, then tap Finish set. It counts as one working set. Charts and records use each set's own values, " +
        "never a drop's. A drop logged as a set of its own in an older version can be made part of the set before it " +
        "from that set's details.",
    "Adding and skipping exercises" to "Add any exercise to a workout from its exercise list, or start an empty " +
        "workout from Today and add exercises as you go; you can log sets right away without a target. Exercises " +
        "added to a planned workout are optional: they never block finishing or complete the routine by themselves, " +
        "but a partial workout with sets on them can still be counted if you choose. Reorder " +
        "a workout's exercises for that session only. To keep an added exercise in future workouts, choose Save to " +
        "plan and enter its sets and reps, or its goal if it is timed, distance or cardio. Skipping a planned " +
        "exercise keeps its target and leaves the workout partial " +
        "unless you choose to count it. Remove planned exercises from future workouts, or restore them, in Plan. In " +
        "History, add an exercise you forgot to log; that never changes your plan or makes a workout done by itself.",
    "Your routines" to "Build your own routines in Routines (Today, Plan or More), or open a finished workout in History " +
        "and choose Save as routine. Every exercise needs rest and its target: working sets and a rep range, or the " +
        "goal of a timed, distance or cardio exercise. The loads, reps, times and distances you logged never become " +
        "targets. A routine is optional until you add it to a phase's week: start it any time as " +
        "an extra session, which never counts on the weekly checklist. Adding it to a week puts a copy on the day you " +
        "choose, with its own settings. Editing the routine later changes a copy only when you choose Update. If the " +
        "day already has a workout, choose Add alongside or Replace; nothing is replaced unless you choose it. Each " +
        "copy counts on its own, so the same routine on two days is two checklist rows. Change a day, take a routine " +
        "off the week or put an original one back in Plan. Workouts already done keep everything.",
    "Time, distance and cardio" to "Some exercises are measured rather than counted in reps: holds such as a plank " +
        "by time, carries such as a farmer's carry by distance, and cardio by time, distance or both. Find them with " +
        "the library's type filter, or create a custom exercise and choose how it is measured. A distance keeps the " +
        "unit you entered (m, km or mi) and is never converted; each unit has its own chart. There is no pace, " +
        "calorie or score. A timed or distance goal is a number of sets that each reach a minimum; a shorter effort " +
        "is still logged but doesn't count toward it. A cardio goal is a total time, distance or both. The next " +
        "effort offers your last load and distance unit, never a time or distance. Rest starts when you log a timed " +
        "or distance set; after cardio, tap Start rest if you want one. An effort added later in History starts no " +
        "rest or stopwatch.",
    "Activity stopwatch" to "Tap Stopwatch on a timed, distance or cardio exercise to time an effort; starting it " +
        "ends any rest. Pause and resume as needed; paused time never counts. Stop fills in the time for you to " +
        "check: nothing is saved until you tap Log, and Discard asks first. One effort is timed at a time. Another " +
        "exercise shows a way back to it, and other screens show a bar with its time. While it is timing, pause or " +
        "stop it before logging anything else. It keeps time from the phone's clock while Rebuild is closed or the " +
        "screen is locked, but it has no notification or alarm. If it was running when the phone restarted or a " +
        "backup was restored, it stops without a time and you enter the time yourself, as you do after more than 24 " +
        "hours. Finishing a workout asks you to log an unlogged effort first or discard it.",
    "Weekly checklist" to "Each week runs Monday to Sunday and lists the current phase's workouts. A workout belongs " +
        "to the week it started in. Doing a workout on any day ticks that workout, not the one scheduled that day. " +
        "A finished workout that met every target counts. " +
        "A partial one counts only if you choose to count it, when finishing or later in History; an empty or " +
        "discarded workout never counts, and neither does an extra workout. Repeating a workout " +
        "doesn't count twice. Nothing is carried over: a new week " +
        "starts fresh, and your history stays. Changing phase changes the checklist without changing past workouts. " +
        "Schedule changes apply at once: adding a routine to a complete week makes it incomplete again.",
    "Phases" to "Start with the four-day gentle return: 1 working set per exercise at about 4 reps in reserve (RIR). " +
        "Then four days with 2 sets at 3 RIR, then five days with 2 sets at 3 RIR. From there, approach the baseline " +
        "gradually by adding 2–4 weekly sets at a time, moving toward about 2 RIR. You change phases yourself.",
    "Baseline, target and advisory upper" to "The baseline plan is 66 weekly working sets; five days at 2 sets each is 54. " +
        "The advisory upper of 86 weekly sets is a ceiling, not a target. Your target is whatever the current phase asks for.",
    "Effort" to "Reps in reserve is how many more good reps you could have done. Don't train to failure; " +
        "stop with the intended reps in reserve.",
    "Adding volume" to "Only consider more sets after several consistent weeks. Add 1–2 weekly sets for one muscle, " +
        "reassess after 2–3 weeks, and keep them only if you are still progressing and recovering.",
    "Progression" to "Use double progression: when every working set reaches the top of the rep range with good form " +
        "at the intended RIR, increase to the smallest available load next time. Rebuild never changes loads for you.",
    "Wednesday vertical pull" to "Lat pulldown replaces pull-ups in the prescribed Pull workout. Choose a controllable load " +
        "for 8–12 clean reps. At the established baseline, use 3 sets with about 2 reps left; earlier phases keep their " +
        "normal set and RIR targets. Build reps before adding weight. No pull-up ability is required.",
    "Fatigue and pain" to "If you feel run down, take an easier week with about half the sets and more reps in reserve. " +
        "Sharp or persistent joint pain means stop that exercise, substitute something comfortable, and get it assessed. " +
        "This app gives general guidance only and makes no health guarantees."
)

@Composable
internal fun HelpScreen(onBack: () -> Unit) {
    ScreenScaffold(title = "Training guidance", onBack = onBack) {
        ScrollBody {
            helpSections.forEachIndexed { i, (title, body) ->
                if (i > 0) HorizontalDivider()
                SectionHeader(title)
                Text(body, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
