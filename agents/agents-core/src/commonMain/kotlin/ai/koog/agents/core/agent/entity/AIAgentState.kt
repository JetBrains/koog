package ai.koog.agents.core.agent.entity

import ai.koog.agents.core.utils.ActiveProperty
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Represents the state of an AI agent.
 */
@OptIn(ExperimentalStdlibApi::class)
public class AIAgentState(
    iterations: Int = 0,
) : AutoCloseable {
    /**
     * The number of iterations that have been completed since the agent was created.
     */
    public var iterations: Int by ActiveProperty(iterations) { isActive }

    private var isActive = true

    override fun close() {
        isActive = false
    }

    /**
     * Creates a copy of the current state.
     */
    public fun copy(): AIAgentState {
        return AIAgentState(
            iterations = iterations
        )
    }
}

/**
 * Manages the state of an AI agent by providing thread-safe access and mechanisms
 * to update the internal state using a locking mechanism.
 *
 * This class ensures consistency across state modifications by using a mutual exclusion
 * lock, allowing only one coroutine to access or modify the state at a time.
 *
 * @constructor Creates a new instance of AIAgentStateManager with the initial state,
 * defaulting to a new `AIAgentState` if not provided.
 */
public class AIAgentStateManager(
    private var state: AIAgentState = AIAgentState()
) {
    private val mutex = Mutex()

    /**
     * Executes the provided suspending [block] of code with exclusive access to the current state.
     * @return The result of [block].
     */
    public suspend fun <T> withStateLock(block: suspend (AIAgentState) -> T): T = mutex.withLock {
        val result = block(state)
        val newState = AIAgentState(
            iterations = state.iterations
        )

        // close this snapshot and create a new one
        state.close()
        state = newState

        result
    }

    internal suspend fun copy(): AIAgentStateManager {
        return withStateLock {
            AIAgentStateManager(state.copy())
        }
    }
}
