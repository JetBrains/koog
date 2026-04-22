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
     * The running iteration counter of the agent's execution.
     *
     * The value is seeded from the [AIAgentState] constructor parameter and is incremented by the
     * framework as nodes are executed. Each call to [AIAgentStateManager.withStateLock] closes the
     * current [AIAgentState] snapshot and creates a new one carrying the latest counter value;
     * accessing [iterations] on a closed snapshot results in an [IllegalStateException].
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
 * Note: on every [withStateLock] invocation, a fresh [AIAgentState] snapshot is created that carries
 * over the current iteration counter, and the previous snapshot is closed. Consumers must therefore
 * not retain references to the [AIAgentState] instance received inside the lock across calls.
 *
 * @constructor Creates a new instance of [AIAgentStateManager] with the initial state,
 * defaulting to a new [AIAgentState] if not provided.
 */
public class AIAgentStateManager(
    private var state: AIAgentState = AIAgentState()
) {
    private val mutex = Mutex()

    /**
     * Executes the provided suspending [block] of code with exclusive access to the current state.
     *
     * After [block] completes, the current [AIAgentState] snapshot is closed and replaced with a new
     * snapshot carrying the updated iteration counter. The [AIAgentState] passed to [block] must not
     * be retained outside of the [block].
     *
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
