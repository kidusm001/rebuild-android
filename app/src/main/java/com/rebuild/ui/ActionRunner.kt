package com.rebuild.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rebuild.domain.Action
import com.rebuild.domain.AppRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * Runs repository actions one at a time. Lives in a ViewModel so an in-flight save
 * survives rotation and the single "busy" guard blocks duplicate taps.
 */
class ActionRunner(private val repository: AppRepository) : ViewModel() {
    var busy by mutableStateOf(false)
        private set
    var saving by mutableStateOf(false)
        private set

    private val errorChannel = Channel<String>(Channel.BUFFERED)
    val errors = errorChannel.receiveAsFlow()

    /**
     * Returns false (and does nothing) when another action is still saving.
     * [cooldownMs] keeps the guard closed briefly after a success, so a physical double tap
     * landing after the first commit can't repeat the action on the refreshed screen.
     */
    fun run(
        action: Action,
        onError: (String) -> Unit = {},
        cooldownMs: Long = 0,
        onSuccess: () -> Unit = {}
    ): Boolean {
        if (busy) return false
        busy = true
        saving = true
        viewModelScope.launch {
            val failure = try {
                repository.execute(action)
                saving = false
                if (cooldownMs > 0) delay(cooldownMs)
                null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.message?.takeIf { it.isNotBlank() } ?: "Couldn't save (${e::class.simpleName})."
            } finally {
                busy = false
                saving = false
            }
            if (failure == null) {
                onSuccess()
            } else {
                onError(failure)
                errorChannel.trySend(failure)
            }
        }
        return true
    }

    fun report(message: String) {
        errorChannel.trySend(message)
    }

    companion object {
        /** Guard kept after logging a new set; long enough to swallow a double tap, short enough to go unnoticed. */
        const val NEW_SET_COOLDOWN_MS = 500L
    }
}
