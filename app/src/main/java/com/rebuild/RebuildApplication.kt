package com.rebuild

import android.app.Application
import android.content.res.Configuration
import android.util.Log
import com.rebuild.data.RebuildRepository
import com.rebuild.domain.AppRepository
import com.rebuild.domain.WidgetModel
import com.rebuild.platform.RestAlarmScheduler
import com.rebuild.platform.RestAlertPrefs
import com.rebuild.platform.TipsPrefs
import com.rebuild.platform.RebuildWidgetRenderer
import com.rebuild.platform.WidgetUpdater
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * What a widget render depends on besides the model: text size (text is drawn by the app) and whether the screen is
 * landscape (the widget ships the layout for the launcher's current cell; see RebuildWidgetRenderer.buildResponsive).
 */
internal data class WidgetRenderKey(
    val model: WidgetModel,
    val fontScale: Float,
    val landscape: Boolean = false
)

class RebuildApplication : Application() {
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val restAlerts by lazy { RestAlertPrefs(this) }
    val tips by lazy { TipsPrefs(this) }
    val alarms by lazy { RestAlarmScheduler(this) { restAlerts.alert.value.sound } }
    private val openMutex = Mutex()
    private var opened: AppRepository? = null
    @Volatile private var lastWidgetKey: WidgetRenderKey? = null

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (!WidgetUpdater.hasWidgets(this)) return
        val last = lastWidgetKey
        if (last?.fontScale != newConfig.fontScale || last.landscape != RebuildWidgetRenderer.displayIsLandscape(this)) {
            lastWidgetKey = null
            applicationScope.launch {
                try {
                    WidgetUpdater.refreshFromRepository(this@RebuildApplication)
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    Log.w("Rebuild", "Widget configuration refresh failed", error)
                }
            }
        }
    }

    suspend fun repository(): AppRepository = openMutex.withLock {
        opened ?: RebuildRepository.open(this).also { repository ->
            opened = repository
            applicationScope.launch {
                repository.state
                    .map { it.activeWorkoutId to it.restTimer }
                    .distinctUntilChanged()
                    .collect {
                        try {
                            alarms.reconcileCurrent(repository)
                        } catch (error: RuntimeException) {
                            Log.e("Rebuild", "Rest scheduling failed", error)
                            alarms.reportSchedulingFailure()
                        }
                    }
            }
            applicationScope.launch {
                repository.state.collectLatest { state ->
                    try {
                        if (!WidgetUpdater.hasWidgets(this@RebuildApplication)) {
                            lastWidgetKey = null
                            return@collectLatest
                        }
                        delay(100)
                        val now = System.currentTimeMillis()
                        val model = WidgetUpdater.modelFor(this@RebuildApplication, state, now)
                        val key = WidgetRenderKey(
                            model, resources.configuration.fontScale, RebuildWidgetRenderer.displayIsLandscape(this@RebuildApplication)
                        )
                        if (key != lastWidgetKey) {
                            WidgetUpdater.renderAll(this@RebuildApplication, model, nowMillis = now)
                            lastWidgetKey = key
                        }
                    } catch (error: Exception) {
                        if (error is CancellationException) throw error
                        Log.e("Rebuild", "Widget update failed", error)
                    }
                }
            }
        }
    }
}
