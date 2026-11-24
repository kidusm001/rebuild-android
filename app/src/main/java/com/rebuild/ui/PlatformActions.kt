package com.rebuild.ui

import kotlinx.coroutines.flow.StateFlow

/** Snapshot of OS capabilities that affect rest alerts. Logging never depends on these. */
data class PlatformStatus(
    val notificationsAllowed: Boolean = true,
    val exactAlarmsAllowed: Boolean = true,
    val batteryRestricted: Boolean = false
)

/**
 * Implemented by the Activity layer. The UI only reads [status] and calls these functions;
 * the platform schedules rest alerts from repository state and owns the backup workflow
 * (SAF export/share, restore preview and confirmation before atomic replace, and its messages).
 */
interface PlatformActions {
    val status: StateFlow<PlatformStatus>
    fun requestNotifications()
    fun requestExactAlarms()
    fun openBatterySettings()
    fun exportBackup()
    fun shareBackup()
    fun chooseRestore()

    /** This phone's rest-end alert choice. */
    val restAlert: StateFlow<RestAlert>
    fun setRestAlert(alert: RestAlert)
    /** Sounds the chosen alert now, the way a real rest end would, so it can be tried from settings. */
    fun previewRestAlert()
    /** Silences a ringing rest alarm. */
    fun stopRestAlarm()
    /** When the rest alarm began ringing, or null while it is quiet. */
    val alarmRingingSince: StateFlow<Long?>
    /**
     * A rest reached its deadline with Rebuild in front: end it now and sound its alert, instead of waiting for
     * Android's alarm, which can arrive late and would then ring after the user had already moved on.
     */
    fun announceRestEnd(timerId: String, shortPause: Boolean)

    /** This phone's in-place tip preference and dismissed tips. */
    val tips: StateFlow<TipsState>
    fun dismissTip(tip: Tip)
    fun setTipsEnabled(enabled: Boolean)
    fun resetTips()
}
