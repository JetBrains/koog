package ai.koog.agents.core.feature.model.events

import ai.koog.agents.core.agent.execution.AgentExecutionInfo
import ai.koog.agents.core.feature.model.AIAgentError
import ai.koog.utils.time.KoogClock
import kotlin.time.Duration
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
) : DefinedFeatureEvent() {

    /**
     * @deprecated. Creates an instance of [AgentStartingEvent].
     * Note! Do not relay on [executionInfo] parameter in this constructor.
     */
    @Deprecated(
        message = "Please use constructor with executionInfo parameter",
        replaceWith = ReplaceWith("AgentStartingEvent(executionInfo, agentId, runId)")
    )
    public constructor(
        agentId: String,
        runId: String
    ) : this(
        eventId = AgentStartingEvent::class.simpleName.toString(),
        executionInfo = AgentExecutionInfo(
            parent = null,
            partName = AgentStartingEvent::class.simpleName.toString(),
        ),
        agentId = agentId,
        runId = runId
    )
}

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
 * @property duration Elapsed time from immediately after `onAgentStarting` returned until just before this event fires.
 * See `AgentCompletedContext.duration` for full semantics.
 */
@Serializable
public data class AgentCompletedEvent(
    override val eventId: String,
    override val executionInfo: AgentExecutionInfo,
    val agentId: String,
    val runId: String,
    val result: String?,
    val duration: Duration,
    override val timestamp: Long = KoogClock.System.now().toEpochMilliseconds(),
) : DefinedFeatureEvent() {

    /**
     * @deprecated. Creates an instance of [AgentCompletedEvent].
     * Note! Do not relay on [executionInfo] parameter in this constructor.
     */
    @Deprecated(
        message = "Please use constructor with executionInfo parameter",
        replaceWith = ReplaceWith("AgentCompletedEvent(executionInfo, agentId, runId, result, duration)")
    )
    public constructor(
        agentId: String,
        runId: String,
        result: String?,
        duration: Duration,
    ) : this(
        eventId = AgentCompletedEvent::class.simpleName.toString(),
        executionInfo = AgentExecutionInfo(
            parent = null,
            partName = AgentCompletedEvent::class.simpleName.toString(),
        ),
        agentId = agentId,
        runId = runId,
        result = result,
        duration = duration,
    )
}

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
 * @property duration Elapsed time from immediately after `onAgentStarting` returned until the failure was observed,
 * or `null` if the failure originated before measurement could start (for example, when an `onAgentStarting`
 * handler threw). See `AgentExecutionFailedContext.duration` for full semantics.
 */
@Serializable
public data class AgentExecutionFailedEvent(
    override val eventId: String,
    override val executionInfo: AgentExecutionInfo,
    val agentId: String,
    val runId: String,
    val error: AIAgentError?,
    val duration: Duration?,
    override val timestamp: Long = KoogClock.System.now().toEpochMilliseconds(),
) : DefinedFeatureEvent() {

    /**
     * @deprecated. Creates an instance of [AgentExecutionFailedEvent].
     * Note! Do not relay on [executionInfo] parameter in this constructor.
     */
    @Deprecated(
        message = "Please use constructor with executionInfo parameter",
        replaceWith = ReplaceWith("AgentExecutionFailedEvent(executionInfo, agentId, runId, error, duration)")
    )
    public constructor(
        agentId: String,
        runId: String,
        error: AIAgentError,
        duration: Duration?,
    ) : this(
        eventId = AgentExecutionFailedEvent::class.simpleName.toString(),
        executionInfo = AgentExecutionInfo(
            parent = null,
            partName = AgentExecutionFailedEvent::class.simpleName.toString(),
        ),
        agentId = agentId,
        runId = runId,
        error = error,
        duration = duration,
    )
}

/**
 * Represents an event that signifies the closure or termination of an AI agent identified
 * by a unique `agentId`.
 *
 * @property eventId A unique identifier for the event or a group of events;
 * @property executionInfo Provides contextual information about the execution associated with this event.
 * @property agentId The unique identifier of the AI agent;
 * @property timestamp The timestamp of the event, in milliseconds since the Unix epoch.
 * @property duration Elapsed time of the entire agent session, from feature preparation through closing.
 * See `AgentClosingContext.duration` for full semantics.
 */
@Serializable
public data class AgentClosingEvent(
    override val eventId: String,
    override val executionInfo: AgentExecutionInfo,
    val agentId: String,
    val duration: Duration,
    override val timestamp: Long = KoogClock.System.now().toEpochMilliseconds(),
) : DefinedFeatureEvent() {

    /**
     * @deprecated. Creates an instance of [AgentClosingEvent].
     * Note! Do not relay on [executionInfo] parameter in this constructor.
     */
    @Deprecated(
        message = "Please use constructor with executionInfo parameter",
        replaceWith = ReplaceWith("AgentClosingEvent(executionInfo, agentId, duration)")
    )
    public constructor(
        agentId: String,
        duration: Duration,
    ) : this(
        eventId = AgentClosingEvent::class.simpleName.toString(),
        executionInfo = AgentExecutionInfo(
            parent = null,
            partName = AgentClosingEvent::class.simpleName.toString(),
        ),
        agentId = agentId,
        duration = duration,
    )
}

//region Deprecated

@Deprecated(
    message = "Use AgentStartingEvent instead",
    replaceWith = ReplaceWith("AgentStartingEvent")
)
public typealias AIAgentStartedEvent = AgentStartingEvent

@Deprecated(
    message = "Use AgentCompletedEvent instead",
    replaceWith = ReplaceWith("AgentCompletedEvent")
)
public typealias AIAgentFinishedEvent = AgentCompletedEvent

@Deprecated(
    message = "Use AgentExecutionFailedEvent instead",
    replaceWith = ReplaceWith("AgentExecutionFailedEvent")
)
public typealias AIAgentRunErrorEvent = AgentExecutionFailedEvent

@Deprecated(
    message = "Use AgentClosingEvent instead",
    replaceWith = ReplaceWith("AgentClosingEvent")
)
public typealias AIAgentBeforeCloseEvent = AgentClosingEvent

//endregion Deprecated
