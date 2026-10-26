package com.rebuild.platform

import com.rebuild.domain.AppRepository
import com.rebuild.domain.RestTimer

internal suspend fun recoverRestAlarm(
    repository: AppRepository,
    now: Long,
    reconcile: (RestTimer?, String?) -> Unit
) {
    val state = repository.state.value
    val timer = state.restTimer
    if (timer != null && timer.deadline <= now) {
        repository.consumeRestTimer(timer.id, now)
    } else {
        reconcile(timer, state.activeWorkoutId)
    }
}
