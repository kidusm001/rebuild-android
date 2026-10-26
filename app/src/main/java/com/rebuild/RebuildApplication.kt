package com.rebuild

import android.app.Application
import android.util.Log
import com.rebuild.data.RebuildRepository
import com.rebuild.domain.AppRepository
import com.rebuild.platform.RestAlarmScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class RebuildApplication : Application() {
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val alarms by lazy { RestAlarmScheduler(this) }
    private val openMutex = Mutex()
    private var opened: AppRepository? = null

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
        }
    }
}
