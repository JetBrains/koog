package ai.koog.agents.core.feature.model.events

import ai.koog.agents.core.agent.execution.AgentExecutionInfo
import ai.koog.agents.core.feature.model.AIAgentError
import ai.koog.utils.time.KoogClock
import kotlinx.serialization.Serializable

/**
 * Represents an event triggered when an AI agent starts executing a strategy.
 *
 * This event provides details about the agent's strategy, making it useful for
 * monitoring, debugging, and tracking the lifecycle of AI agents within the system.
 *
 * @property eventId A unique identifier for the event or a group of events;
 * @property executionInfo Provides contextual information about the execution associated with this event.
 * @property agentId The unique identifier of the AI agent;
 * @property runId The unique identifier of the AI agen run;
 * @property timestamp The timestamp of the event, in milliseconds since the Unix epoch.
 */
@Serializable
public data class AgentStartingEvent(
    override val eventId: String,
    override val executionInfo: AgentExecutionInfo,
    val agentId: String,
    val runId: String,
    override val timestamp: Long = KoogClock.System.now().toEpochMilliseconds(),
) : DefinedFeatureEvent()

/**
 * Event representing the completion of an AI Agent's execution.
 *
 * This event is emitted when an AI Agent finishes executing a strategy, providing
 * information about the strategy and its result. It can be used for logging, tracing,
 * or monitoring the outcomes of agent operations.
 *
 * @property eventId A unique identifier for the event or a group of events;
 * @property executionInfo Provides contextual information about the execution associated with this event.
 * @property agentId The unique identifier of the AI agent;
 * @property runId The unique identifier of the AI agen run;
 * @property result The result of the strategy execution, or null if unavailable;
 * @property timestamp The timestamp of the event, in milliseconds since the Unix epoch.
 */
@Serializable
public data class AgentCompletedEvent(
    override val eventId: String,
    override val executionInfo: AgentExecutionInfo,
    val agentId: String,
    val runId: String,
    val result: String?,
    override val timestamp: Long = KoogClock.System.now().toEpochMilliseconds(),
) : DefinedFeatureEvent()

/**
 * Represents an event triggered when an AI agent run encounters an error.
 *
 * This event is used to capture error information during the execution of an AI agent
 * strategy, including details of the strategy and the encountered error.
 *
 * @property eventId A unique identifier for the event or a group of events;
 * @property executionInfo Provides contextual information about the execution associated with this event.
 * @property agentId The unique identifier of the AI agent;
 * @property runId The unique identifier of the AI agen run;
 * @property error The [AIAgentError] instance encapsulating details about the encountered error,
 *                 such as its message, stack trace, and cause;
 * @property timestamp The timestamp of the event, in milliseconds since the Unix epoch.
 */
@Serializable
public data class AgentExecutionFailedEvent(
    override val eventId: String,
    override val executionInfo: AgentExecutionInfo,
    val agentId: String,
    val runId: String,
    val error: AIAgentError?,
    override val timestamp: Long = KoogClock.System.now().toEpochMilliseconds(),
) : DefinedFeatureEvent()

/**
 * Represents an event that signifies the closure or termination of an AI agent identified
 * by a unique `agentId`.
 *
 * @property eventId A unique identifier for the event or a group of events;
 * @property executionInfo Provides contextual information about the execution associated with this event.
 * @property agentId The unique identifier of the AI agent;
 * @property timestamp The timestamp of the event, in milliseconds since the Unix epoch.
 */
@Serializable
public data class AgentClosingEvent(
    override val eventId: String,
    override val executionInfo: AgentExecutionInfo,
    val agentId: String,
    override val timestamp: Long = KoogClock.System.now().toEpochMilliseconds(),
) : DefinedFeatureEvent()
