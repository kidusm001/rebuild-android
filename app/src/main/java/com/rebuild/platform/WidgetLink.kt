package com.rebuild.platform

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.rebuild.MainActivity
import com.rebuild.domain.WidgetDestination

const val ACTION_WIDGET_OPEN = "com.rebuild.WIDGET_OPEN"
const val EXTRA_WIDGET_DESTINATION = "widget_destination"

private const val REQUEST_CODE_TODAY = 101
private const val REQUEST_CODE_WORKOUT = 102

/**
 * Pure parser for widget launch intents. Ignores intents replayed from Android's Recents history so a previous
 * widget tap never overrides navigation after returning from Recents.
 */
fun parseWidgetDestination(action: String?, extra: String?, flags: Int): WidgetDestination? {
    if (flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return null
    if (action != ACTION_WIDGET_OPEN) return null
    return when (extra) {
        WidgetDestination.WORKOUT.name -> WidgetDestination.WORKOUT
        WidgetDestination.TODAY.name -> WidgetDestination.TODAY
        else -> null
    }
}

fun parseWidgetDestination(intent: Intent?): WidgetDestination? =
    intent?.let { parseWidgetDestination(it.action, it.getStringExtra(EXTRA_WIDGET_DESTINATION), it.flags) }

fun widgetOpenIntent(context: Context, destination: WidgetDestination): Intent =
    Intent(context, MainActivity::class.java).apply {
        action = ACTION_WIDGET_OPEN
        putExtra(EXTRA_WIDGET_DESTINATION, destination.name)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }

fun widgetPendingIntent(context: Context, destination: WidgetDestination): PendingIntent {
    val requestCode = when (destination) {
        WidgetDestination.TODAY -> REQUEST_CODE_TODAY
        WidgetDestination.WORKOUT -> REQUEST_CODE_WORKOUT
    }
    return PendingIntent.getActivity(
        context,
        requestCode,
        widgetOpenIntent(context, destination),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}
