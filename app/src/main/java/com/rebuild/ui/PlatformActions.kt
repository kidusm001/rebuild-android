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
}
