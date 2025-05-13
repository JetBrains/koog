package ai.grazie.code.agents.core.agent.entity

import ai.grazie.code.agents.core.utils.ActiveProperty
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@OptIn(ExperimentalStdlibApi::class)
class AgentState internal constructor(
    iterations: Int = 0,
) : AutoCloseable {
    var iterations: Int by ActiveProperty(iterations) { isActive }

    private var isActive = true

    override fun close() {
        isActive = false
    }
}

class AgentStateManager internal constructor(
    private var state: AgentState = AgentState()
) {
    private val mutex = Mutex()

    suspend fun <T> withStateLock(block: suspend (AgentState) -> T): T = mutex.withLock {
        val result = block(state)
        val newState = AgentState(
            iterations = state.iterations
        )

        // close this snapshot and create a new one
        state.close()
        state = newState

        result
    }
}
