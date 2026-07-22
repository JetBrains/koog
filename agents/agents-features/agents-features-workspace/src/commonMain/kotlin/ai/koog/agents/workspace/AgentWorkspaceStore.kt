package ai.koog.agents.workspace

import ai.koog.agents.workspace.model.AgentWorkspaceEvent
import ai.koog.agents.workspace.model.AgentWorkspaceRunSnapshot
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Storage contract for durable workspace state and replayable events. */
public interface AgentWorkspaceStore {
    /** Loads a run snapshot, or `null` when the run is unknown. */
    public suspend fun loadRun(runId: String): AgentWorkspaceRunSnapshot?

    /** Creates or replaces a run snapshot. */
    public suspend fun saveRun(snapshot: AgentWorkspaceRunSnapshot)

    /** Appends [event] and returns the persisted event with its assigned sequence. */
    public suspend fun appendEvent(event: AgentWorkspaceEvent): AgentWorkspaceEvent

    /** Returns events with a sequence greater than [afterSequence]. */
    public suspend fun listEvents(runId: String, afterSequence: Long = 0): List<AgentWorkspaceEvent>
}

/** In-memory workspace store intended for tests and short-lived applications. */
public class InMemoryAgentWorkspaceStore : AgentWorkspaceStore {
    private val mutex: Mutex = Mutex()
    private val runs: MutableMap<String, AgentWorkspaceRunSnapshot> = mutableMapOf()
    private val events: MutableMap<String, MutableList<AgentWorkspaceEvent>> = mutableMapOf()

    override suspend fun loadRun(runId: String): AgentWorkspaceRunSnapshot? = mutex.withLock { runs[runId] }

    override suspend fun saveRun(snapshot: AgentWorkspaceRunSnapshot): Unit = mutex.withLock {
        runs[snapshot.runId] = snapshot
    }

    override suspend fun appendEvent(event: AgentWorkspaceEvent): AgentWorkspaceEvent = mutex.withLock {
        val target = events.getOrPut(event.runId) { mutableListOf() }
        val stored = event.copy(sequence = (target.lastOrNull()?.sequence ?: 0L) + 1L)
        target += stored
        stored
    }

    override suspend fun listEvents(runId: String, afterSequence: Long): List<AgentWorkspaceEvent> = mutex.withLock {
        events[runId].orEmpty().filter { it.sequence > afterSequence }
    }
}
