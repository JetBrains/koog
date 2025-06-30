package ai.koog.agents.core.agent.context.element

import ai.koog.agents.core.agent.config.AIAgentConfigBase
import kotlinx.coroutines.currentCoroutineContext
import kotlin.coroutines.CoroutineContext

/**
 * Represents a coroutine context element that holds execution metadata for an agent's run.
 * This metadata includes the agent's identifier, session details, and the strategy used.
 *
 * This class implements `CoroutineContext.Element`, allowing it to be used as a part of
 * a coroutine's context and enabling retrieval of the run-related information within
 * coroutine scopes.
 *
 * @property agentId The unique identifier for the agent running in the current context.
 * @property runId The identifier for the session associated with the current agent run.
 * @property agentConfig The agent configuration property.
 * @property strategyName The name of the strategy being executed by the agent in the current context.
 */
public data class AgentRunInfoContextElement(
    val agentId: String,
    val runId: String,
    val agentConfig: AIAgentConfigBase,
    val strategyName: String
) : CoroutineContext.Element {

    /**
     * A companion object that serves as the key for the `AgentRunInfoContextElement` in a `CoroutineContext`.
     * This key allows retrieval of the `AgentRunInfoContextElement` instance stored in a coroutine's context.
     *
     * Implements `CoroutineContext.Key` to provide a type-safe mechanism for accessing the associated context element.
     */
    public companion object Key : CoroutineContext.Key<AgentRunInfoContextElement>

    override val key: CoroutineContext.Key<*> = Key
}

/**
 * Retrieves the `AgentRunInfoContextElement` from the current coroutine context, if present.
 *
 * @return The `AgentRunInfoContextElement` if it exists in the current coroutine context, or `null` if not found.
 */
public suspend fun CoroutineContext.getAgentRunInfoElementOrThrow(): AgentRunInfoContextElement =
    currentCoroutineContext()[AgentRunInfoContextElement.Key]
        ?: error("Unable to retrieve AgentRunInfoContextElement from CoroutineContext. " +
            "Please make sure the AgentRunInfoContextElement is added to the current CoroutineContext.")