package com.rebuild

import android.Manifest
import android.app.AlarmManager
import android.app.PictureInPictureParams
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.rebuild.platform.BackupViewModel
import com.rebuild.ui.PlatformActions
import com.rebuild.ui.PlatformStatus
import com.rebuild.ui.RebuildApp
import com.rebuild.ui.RestPip
import com.rebuild.ui.theme.RebuildTheme
import com.rebuild.ui.AdaptiveDialog
import com.rebuild.ui.countLabel
import com.rebuild.ui.formatDuration
import androidx.compose.ui.platform.testTag
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity(), PlatformActions {
    private val model: BackupViewModel by viewModels()
    private val app get() = application as RebuildApplication
    private val _status = MutableStateFlow(PlatformStatus(false, false, false))
    private var notificationRequestAttempted = false
    private val inPictureInPicture = MutableStateFlow(false)
    /** A rest is counting down, so leaving the app shrinks it to a picture-in-picture countdown. */
    private var restCounting = false
    override val status: StateFlow<PlatformStatus> = _status
    private val saveBackup = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) {
        model.completeExport(it)
    }
    private val openBackup = registerForActivityResult(ActivityResultContracts.OpenDocument()) { model.readRestore(it) }
    private val notifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        refreshStatus()
        if (!granted) {
            model.showPlatformError("Notifications remain off. Logging and the on-screen timer still work. Tap notification settings again to enable rest alerts in Android Settings.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        notificationRequestAttempted = savedInstanceState?.getBoolean("notificationRequestAttempted") ?: false
        enableEdgeToEdge()
        setContent {
            val repository by model.repository.collectAsStateWithLifecycle()
            val loadError by model.loadError.collectAsStateWithLifecycle()
            val backup by model.ui.collectAsStateWithLifecycle()
            val alarmError by app.alarms.failure.collectAsStateWithLifecycle()
            RebuildTheme {
                val loaded = repository
                if (loaded == null) {
                    Surface(Modifier.fillMaxSize()) {
                        Column(
                            Modifier.safeDrawingPadding().padding(24.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (loadError != null) {
                                Text(requireNotNull(loadError))
                                Button(onClick = model::load) { Text("Try again") }
                            } else {
                                CircularProgressIndicator()
                                Text("Opening your saved workouts", Modifier.padding(top = 16.dp))
                            }
                        }
                    }
                } else {
                    val appState by loaded.state.collectAsStateWithLifecycle()
                    val timer = appState.restTimer?.takeIf { it.workoutId == appState.activeWorkoutId }
                    val pip by inPictureInPicture.collectAsStateWithLifecycle()
                    LaunchedEffect(timer?.id) { updateRestPip(timer != null) }
                    // The app stays composed underneath, so returning from the small window keeps every screen's state.
                    Box(Modifier.fillMaxSize()) {
                        RebuildApp(loaded, this@MainActivity)
                        if (pip) RestPip(timer, System::currentTimeMillis)
                    }
                }
                val preview = backup.preview
                if (preview != null && backup.message == null) {
                    AdaptiveDialog(
                        onDismissRequest = model::cancelRestore,
                        title = { Text(if (backup.safetyRevision == null) "Preview replacement" else "Replace all current data?") },
                        text = {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text("${countLabel(preview.state.workouts.size, "workout")}, ${countLabel(preview.state.exercises.count { it.custom }, "custom exercise")}.")
                                Text("Phase: ${preview.state.phase?.title ?: "Not selected"}. Active workout: ${if (preview.state.activeWorkoutId == null) "none" else "will resume"}.")
                                val routines = preview.state.routines.size
                                val copies = preview.state.placements.count { !it.removedFromPlan }
                                val takenOff = preview.state.scheduleRemovals.size
                                if (routines + copies + takenOff > 0) Text(
                                    listOfNotNull(
                                        countLabel(routines, "saved routine").takeIf { routines > 0 },
                                        (if (copies == 1) "1 routine copy on weekly plans"
                                        else "$copies routine copies on weekly plans").takeIf { copies > 0 },
                                        (if (takenOff == 1) "1 original routine taken off its week"
                                        else "$takenOff original routines taken off their weeks").takeIf { takenOff > 0 }
                                    ).joinToString(", ") + ".",
                                    modifier = Modifier.testTag("restore-preview-routines")
                                )
                                if (preview.dataUpdated && preview.contentUpdated) {
                                    Text("This file is from an older version of Rebuild and is updated as it is restored: " +
                                        listOfNotNull("missing library exercises are added where needed",
                                            "each workout gets the start date the weekly checklist uses".takeIf { preview.startDatesAdded }
                                        ).joinToString(", and ") +
                                        ". Saved sets keep their values and their own units; " +
                                        "nothing is converted between kg and plates.")
                                } else if (preview.dataUpdated) {
                                    Text("This file is from an older version of Rebuild and is updated to the current format " +
                                        "as it is restored. Workouts, sets, stages, dates and weekly credit keep their values.")
                                }
                                preview.state.stopwatch?.let { watch ->
                                    Text(
                                        when {
                                            watch.needsConfirmation ->
                                                "Its running stopwatch comes back stopped. Confirm the time before logging it."
                                            watch.stopped -> "Its stopped stopwatch comes back, ready to log or discard."
                                            else -> "Its paused stopwatch comes back at ${formatDuration(watch.elapsedMs / 1000)}."
                                        },
                                        modifier = Modifier.testTag("restore-preview-stopwatch")
                                    )
                                }
                                if (preview.futurePlanUpdated) {
                                    Text("The future Wednesday plan will use Lat pulldown instead of pull-ups. " +
                                        "Started workouts and recorded sets stay unchanged.")
                                }
                                Text(
                                    if (backup.safetyRevision == null)
                                        "Restore replaces all data, never merges. Save a backup of the current data first. Canceling or a failed restore leaves it intact."
                                    else
                                        "Your safety backup was saved. This replaces the workouts, routines, plan edits, exercise catalog and phase on this phone. Rest alerts from the file will not restart."
                                )
                                if (backup.busy) CircularProgressIndicator()
                            }
                        },
                        confirmButton = {
                            Button(
                                enabled = !backup.busy,
                                modifier = Modifier.testTag("restore-confirm"),
                                onClick = {
                                    if (backup.safetyRevision == null) model.beginExport(true) { saveBackup.launch(it) }
                                    else model.confirmRestore()
                                }
                            ) { Text(if (backup.safetyRevision == null) "Save current backup" else "Replace all data") }
                        },
                        dismissButton = { TextButton(onClick = model::cancelRestore, enabled = !backup.busy, modifier = Modifier.testTag("restore-cancel")) { Text("Cancel") } }
                    )
                }
                backup.message?.let { message ->
                    AdaptiveDialog(
                        onDismissRequest = model::dismissMessage,
                        title = { Text(backup.messageTitle) },
                        text = { Text(message) },
                        confirmButton = { TextButton(onClick = model::dismissMessage) { Text("OK") } }
                    )
                }
                if (alarmError != null && backup.message == null && preview == null) {
                    Surface(Modifier.safeDrawingPadding().padding(16.dp), color = MaterialTheme.colorScheme.errorContainer) {
                        Text(requireNotNull(alarmError), Modifier.padding(12.dp))
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
        lifecycleScope.launch {
            try {
                model.repository.value?.let { app.alarms.reconcileCurrent(it) }
            } catch (error: RuntimeException) {
                Log.e("Rebuild", "Could not refresh background rest alert", error)
                app.alarms.reportSchedulingFailure()
            }
        }
    }

    private val supportsPip: Boolean
        get() = packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    private fun pipParams(autoEnter: Boolean): PictureInPictureParams =
        PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).apply {
            if (Build.VERSION.SDK_INT >= 31) {
                setAutoEnterEnabled(autoEnter)
                setSeamlessResizeEnabled(false)
            }
        }.build()

    private fun updateRestPip(counting: Boolean) {
        restCounting = counting
        if (!supportsPip) return
        try {
            setPictureInPictureParams(pipParams(counting))
        } catch (error: IllegalStateException) {
            Log.w("Rebuild", "Picture-in-picture is unavailable", error)
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Android 12 and later enter on their own from the params; earlier versions need asking here.
        if (Build.VERSION.SDK_INT < 31 && restCounting && supportsPip) {
            try {
                enterPictureInPictureMode(pipParams(false))
            } catch (error: IllegalStateException) {
                Log.w("Rebuild", "Could not enter picture-in-picture", error)
            }
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPictureInPicture.value = isInPictureInPictureMode
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("notificationRequestAttempted", notificationRequestAttempted)
        super.onSaveInstanceState(outState)
    }

    private fun refreshStatus() {
        val power = getSystemService(PowerManager::class.java)
        _status.value = PlatformStatus(
            notificationsAllowed = app.alarms.notificationsAllowed(),
            exactAlarmsAllowed = Build.VERSION.SDK_INT < 31 || getSystemService(AlarmManager::class.java).canScheduleExactAlarms(),
            batteryRestricted = !power.isIgnoringBatteryOptimizations(packageName)
        )
    }

    override fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && !app.alarms.notificationsAllowed() &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED &&
            !notificationRequestAttempted && !shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
        ) {
            notificationRequestAttempted = true
            notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            openSettings(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
        }
    }

    override fun requestExactAlarms() {
        if (Build.VERSION.SDK_INT >= 31) {
            openSettings(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
        }
    }

    override fun openBatterySettings() {
        openSettings(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
    }

    override fun exportBackup() { model.beginExport(false) { saveBackup.launch(it) } }
    override fun chooseRestore() { openBackup.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
    override fun shareBackup() {
        model.prepareShare { file ->
            val uri = FileProvider.getUriForFile(this, "$packageName.backups", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = android.content.ClipData.newRawUri("Rebuild backup", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Share private backup"))
        }
    }

    private fun openSettings(intent: Intent) {
        try {
            startActivity(intent)
        } catch (error: ActivityNotFoundException) {
            model.showPlatformError("This device does not expose that settings page. Open Android Settings, Apps, Rebuild manually.")
        }
    }
}
