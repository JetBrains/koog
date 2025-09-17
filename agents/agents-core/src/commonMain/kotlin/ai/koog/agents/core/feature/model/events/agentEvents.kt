package ai.koog.agents.core.feature.model.events

import ai.koog.agents.core.feature.model.AIAgentError
import kotlinx.serialization.Serializable

/**
 * Represents an event triggered when an AI agent starts executing a strategy.
 *
 * This event provides details about the agent's strategy, making it useful for
 * monitoring, debugging, and tracking the lifecycle of AI agents within the system.
 *
 * @property agentId The unique identifier of the AI agent;
 * @property runId The unique identifier of the AI agen run;
 */
@Serializable
public data class AIAgentStartedEvent(
    val agentId: String,
    val runId: String,
    override val eventId: String = AIAgentStartedEvent::class.simpleName!!,
) : DefinedFeatureEvent()

/**
 * Event representing the completion of an AI Agent's execution.
 *
 * This event is emitted when an AI Agent finishes executing a strategy, providing
 * information about the strategy and its result. It can be used for logging, tracing,
 * or monitoring the outcomes of agent operations.
 *
 * @property agentId The unique identifier of the AI agent;
 * @property runId The unique identifier of the AI agen run;
 * @property result The result of the strategy execution, or null if unavailable.
 */
@Serializable
public data class AIAgentFinishedEvent(
    val agentId: String,
    val runId: String,
    val result: String?,
    override val eventId: String = AIAgentFinishedEvent::class.simpleName!!,
) : DefinedFeatureEvent()

/**
 * Represents an event triggered when an AI agent run encounters an error.
 *
 * This event is used to capture error information during the execution of an AI agent
 * strategy, including details of the strategy and the encountered error.
 *
 * @constructor Creates an instance of [AIAgentRunErrorEvent].
 * @property agentId The unique identifier of the AI agent;
 * @property runId The unique identifier of the AI agen run;
 * @property error The [AIAgentError] instance encapsulating details about the encountered error,
 *                 such as its message, stack trace, and cause.
 */
@Serializable
public data class AIAgentRunErrorEvent(
    val agentId: String,
    val runId: String,
    val error: AIAgentError,
    override val eventId: String = AIAgentRunErrorEvent::class.simpleName!!,
) : DefinedFeatureEvent()

/**
 * Represents an event that signifies the closure or termination of an AI agent identified
 * by a unique `agentId`.
 *
 * @property agentId The unique identifier of the AI agent.
 * @property eventId A string representing the event type.
 */
@Serializable
public data class AIAgentBeforeCloseEvent(
    val agentId: String,
    override val eventId: String = AIAgentBeforeCloseEvent::class.simpleName!!,
) : DefinedFeatureEvent()
