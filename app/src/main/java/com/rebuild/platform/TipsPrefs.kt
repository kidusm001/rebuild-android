package com.rebuild.platform

import android.content.Context
import com.rebuild.ui.Tip
import com.rebuild.ui.TipsState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * This phone's in-place tip preference and dismissed tips. Kept in its own preferences file so UI hints never touch
 * the workout snapshot or backups; unknown tip names from another version are ignored safely.
 */
class TipsPrefs(context: Context) {
    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val _tips = MutableStateFlow(read())
    val tips: StateFlow<TipsState> = _tips.asStateFlow()

    private fun read(): TipsState {
        val enabled = prefs.getBoolean(KEY_ENABLED, true)
        val dismissed = readTips(KEY_DISMISSED)
        val forceShow = readTips(KEY_FORCE_SHOW)
        return TipsState(enabled = enabled, dismissed = dismissed, forceShow = forceShow)
    }

    private fun readTips(key: String): Set<Tip> =
        prefs.getStringSet(key, emptySet()).orEmpty()
            .mapNotNull { name -> Tip.entries.find { it.name == name } }
            .toSet()

    fun dismiss(tip: Tip) = write(_tips.value.dismiss(tip))

    fun setEnabled(enabled: Boolean) = write(_tips.value.setEnabled(enabled))

    fun reset() = write(_tips.value.reset())

    private fun write(next: TipsState) {
        prefs.edit()
            .putBoolean(KEY_ENABLED, next.enabled)
            .putStringSet(KEY_DISMISSED, next.dismissed.map { it.name }.toSet())
            .putStringSet(KEY_FORCE_SHOW, next.forceShow.map { it.name }.toSet())
            .apply()
        _tips.value = next
    }

    private companion object {
        const val FILE = "tips"
        const val KEY_ENABLED = "enabled"
        const val KEY_DISMISSED = "dismissed"
        const val KEY_FORCE_SHOW = "force_show"
    }
}
