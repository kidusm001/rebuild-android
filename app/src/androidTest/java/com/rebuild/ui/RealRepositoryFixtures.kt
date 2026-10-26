package com.rebuild.ui

import com.rebuild.data.RebuildRepository
import com.rebuild.data.SnapshotCodec
import com.rebuild.data.SnapshotPersistence
import com.rebuild.domain.AppRepository
import com.rebuild.domain.AppState
import com.rebuild.domain.Phase
import com.rebuild.domain.Seed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/** The snapshot file of a real repository, kept in memory so a test can compare what was saved. */
internal class InMemorySnapshots(initial: AppState) : SnapshotPersistence {
    @Volatile var json: String = SnapshotCodec.encode(initial)
    override fun read() = json
    override fun write(snapshot: String) { json = snapshot }
}

/**
 * In the app, the repository's IO-thread save reaches Compose through the main-thread dispatcher. The test rule's
 * unconfined dispatcher would run that Compose frame on the IO thread instead, racing the main thread's layout
 * ("performMeasureAndLayout called during measure layout"), so here the UI collects on the main thread too. The
 * values, actions and saving are the repository's own.
 */
internal class MainThreadCollection(private val repository: AppRepository) : AppRepository by repository {
    @OptIn(ExperimentalForInheritanceCoroutinesApi::class)
    override val state: StateFlow<AppState> = object : StateFlow<AppState> {
        override val value: AppState get() = repository.state.value
        override val replayCache: List<AppState> get() = repository.state.replayCache
        override suspend fun collect(collector: FlowCollector<AppState>): Nothing =
            withContext(Dispatchers.Main.immediate) { repository.state.collect(collector) }
    }
}

/** The bundled plan and library in the second introductory phase (Upper on Tuesday 22 Sep), on the real reducer. */
internal fun introRepository(
    store: InMemorySnapshots = InMemorySnapshots(Seed.initialState().copy(phase = Phase.INTRO_TWO)),
    setup: suspend RebuildRepository.() -> Unit = {}
): RebuildRepository = runBlocking {
    RebuildRepository.fromPersistence(store, clock = { FIXED_NOW }).also { it.setup() }
}
