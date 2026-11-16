package com.rebuild.platform

import android.content.Context
import com.rebuild.ui.RestAlert
import com.rebuild.ui.RestSound
import com.rebuild.ui.RestVisual
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * This phone's rest-end alert, kept in its own preferences file: it describes this phone's speaker and screen, not
 * training, so it stays out of the workout snapshot and backups. An unknown stored name (from a later version) reads
 * as the default rather than failing.
 */
class RestAlertPrefs(context: Context) {
    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val _alert = MutableStateFlow(read())
    val alert: StateFlow<RestAlert> = _alert.asStateFlow()

    private fun read(): RestAlert {
        val sound = RestSound.entries.find { it.name == prefs.getString(KEY_SOUND, null) } ?: RestSound.BUZZ
        val visual = RestVisual.entries.find { it.name == prefs.getString(KEY_VISUAL, null) } ?: RestVisual.NONE
        // A stored pair that the rules forbid is repaired the same way choosing it would be.
        return RestAlert(sound).withSound(sound).withVisual(visual)
    }

    fun set(alert: RestAlert) {
        val valid = RestAlert(alert.sound).withVisual(alert.visual).withSound(alert.sound)
        prefs.edit().putString(KEY_SOUND, valid.sound.name).putString(KEY_VISUAL, valid.visual.name).apply()
        _alert.value = valid
    }

    private companion object {
        const val FILE = "rest_alert"
        const val KEY_SOUND = "sound"
        const val KEY_VISUAL = "visual"
    }
}
