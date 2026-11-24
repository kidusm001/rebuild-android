package com.rebuild.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rebuild.domain.Action
import com.rebuild.domain.AppRepository
import com.rebuild.ui.theme.Motion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * Test-visible state of the app's input guard, on its root node: true while a tap would be ignored (a save running, or
 * its double-tap cooldown). Controls no longer grey out for a normal save, so tests wait on this instead of on
 * `isEnabled`. Nothing reads it aloud.
 */
internal val ActionGuard = SemanticsPropertyKey<Boolean>("ActionGuard")

/** Holds a [SavedStateHandle] under a stable key, for a ViewModel whose own key changes between process lives. */
class SavedHandle(val handle: SavedStateHandle) : ViewModel()

/**
 * Runs repository actions one at a time. Lives in a ViewModel so an in-flight save survives rotation and the single
 * guard blocks duplicate taps.
 *
 * Two things are kept apart: [busy] is the input guard (a tap while it is closed does nothing, cooldown included), and
 * [showsBusy] is the busy look, shown only once a save has run for [Motion.BusyAfterMs] of real time. A normal save is
 * done long before that, so the controls never flash grey on every logged set; a slow one still says it is saving.
 */
class ActionRunner(
    private val repository: AppRepository,
    private val saved: SavedStateHandle = SavedStateHandle()
) : ViewModel() {
    /** The input guard: true from a tap until the save (and its cooldown) is over. Controls check it on click. */
    var busy by mutableStateOf(false)
        private set
    /** True while the repository write itself runs (not during the cooldown). */
    var saving by mutableStateOf(false)
        private set
    /** The busy look: true only after a save has run for [Motion.BusyAfterMs] (wall clock), never in the cooldown. */
    var showsBusy by mutableStateOf(false)
        private set
    /** The tag the running action was started with, so only the control that started it says "Saving…". */
    var busyTag by mutableStateOf<String?>(null)
        private set

    /**
     * A workout that was just finished here and has not shown its receipt yet. Set by the finish's `onSaved`; the
     * app shows the receipt once the state it composes shows that workout finished. Kept across recreation.
     */
    var receiptPending by mutableStateOf(saved.get<String>(RECEIPT_KEY))
        private set

    fun receiptFor(workoutId: String) {
        receiptPending = workoutId
        saved[RECEIPT_KEY] = workoutId
    }

    /**
     * The workout whose receipt has been drawn (still below the screen). The finish dialog stays up until then, so its
     * dim covers the wait for the finished state and the receipt's first frame.
     */
    var receiptOnScreen by mutableStateOf<String?>(null)
        private set

    fun receiptDrawn(workoutId: String) {
        receiptOnScreen = workoutId
        // The receipt's own scrim has taken the dialog's dim over.
        coverDim = 0f
    }

    /**
     * The in-app scrim standing in for the finish dialog's own dim (0 when none): the dialog's window dims nothing, so
     * the app draws this beneath it, and the receipt's scrim takes it over in the very frame the receipt arrives.
     */
    var coverDim by mutableStateOf(0f)
        private set

    fun cover(dim: Float) {
        coverDim = dim
    }

    fun receiptShown() {
        receiptPending = null
        receiptOnScreen = null
        saved.remove<String>(RECEIPT_KEY)
    }

    /**
     * A set being logged whose confirming tick is due: it fires on the frame after the first one that draws the set
     * (see SetLoggedTick), so the tick and the change on screen arrive together however long the write or that frame
     * takes. A failed save clears it.
     */
    var tickFor by mutableStateOf<String?>(null)
        private set

    fun tickWhenShown(setId: String) {
        tickFor = setId
    }

    fun ticked(setId: String) {
        if (tickFor == setId) tickFor = null
    }

    private val errorChannel = Channel<String>(Channel.BUFFERED)
    val errors = errorChannel.receiveAsFlow()

    /** Whether the control tagged [tag] should show its busy look ("Saving…") now. */
    fun showsBusy(tag: String): Boolean = showsBusy && busyTag == tag

    /**
     * Returns false (and does nothing) when another action is still saving.
     * [cooldownMs] keeps the guard closed briefly after a success, so a physical double tap
     * landing after the first commit can't repeat the action on the refreshed screen.
     * [onSaved] runs the moment the write lands, before the cooldown (a finish marks its receipt); [onSuccess] after it.
     */
    fun run(
        action: Action,
        onError: (String) -> Unit = {},
        cooldownMs: Long = 0,
        tag: String? = null,
        onSaved: () -> Unit = {},
        onSuccess: () -> Unit = {}
    ): Boolean {
        if (busy) return false
        busy = true
        saving = true
        busyTag = tag
        // A real-time threshold: viewModelScope runs on the main dispatcher, whose delay is wall clock and never
        // follows the animator duration scale.
        val look = viewModelScope.launch {
            delay(Motion.BusyAfterMs)
            if (saving) showsBusy = true
        }
        viewModelScope.launch {
            val failure = try {
                repository.execute(action)
                saving = false
                showsBusy = false
                look.cancel()
                onSaved()
                if (cooldownMs > 0) delay(cooldownMs)
                null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.message?.takeIf { it.isNotBlank() } ?: "Couldn't save (${e::class.simpleName})."
            } finally {
                look.cancel()
                busy = false
                saving = false
                showsBusy = false
                busyTag = null
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
        private const val RECEIPT_KEY = "receipt-pending"
    }
}

/**
 * The busy look ([ActionRunner.showsBusy], or for [tag] only) for a control on the layer being composed. A hidden kept
 * tab or a layer leaving never reads it, so a slow save doesn't recompose screens nobody sees in the frame it lands.
 */
@Composable
internal fun ActionRunner.looksBusy(tag: String? = null): Boolean =
    LocalBusyHeld.current || (LocalLayerActive.current && (if (tag == null) showsBusy else showsBusy(tag)))

/**
 * A screen leaving because of its own save (Today under a workout just started) keeps the busy look it had, frozen,
 * instead of dropping it the moment it stops being the target.
 */
internal val LocalBusyHeld = androidx.compose.runtime.compositionLocalOf { false }
