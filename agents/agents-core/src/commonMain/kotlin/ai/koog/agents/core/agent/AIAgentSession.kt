package ai.koog.agents.core.agent

import ai.koog.agents.core.agent.context.AIAgentContext
import io.ktor.utils.io.core.Input
import io.ktor.utils.io.core.Output
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlin.coroutines.CoroutineContext

/**
 * AIAgentSession is a generic class that manages the execution context and result of an AI agent session.
 *
 * @param Output The type of the result produced by the AI agent session.
 */
public interface AIAgentSession<Input, Output> {
    /**
     * Launches the AI agent session with the provided input and runs it within the specified coroutine scope.
     *
     * @param agentInput The input data required to start the AI agent session.
     * @param scope The coroutine scope used to launch and manage the session execution.
     */
    public suspend fun launch(agentInput: Input, scope: CoroutineScope)

    /**
     * Awaits and retrieves the result produced by the AI agent session.
     *
     * @return The result of type Output produced by the AI agent session.
     */
    public suspend fun result(): Output

    /**
     * Stops the execution of the AI agent session.
     *
     * This method cancels any ongoing operations and releases any allocated resources
     * associated with the session. It ensures that the AI agent session terminates gracefully.
     */
    public suspend fun stop()

    /**
     * Schedules and executes an action within the context of an AI agent session.
     *
     * @param action The action to execute, represented as a lambda with the receiver of type [AIAgentContext].
     *               This action can utilize the features and functionalities of the [AIAgentContext]
     *               to perform operations specific to the AI agent's lifecycle.
     */
    public suspend fun withContext(action: suspend AIAgentContext.() -> Unit)
}
