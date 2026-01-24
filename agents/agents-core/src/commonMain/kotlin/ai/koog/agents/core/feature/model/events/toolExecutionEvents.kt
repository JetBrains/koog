package ai.koog.agents.core.feature.model.events

import ai.koog.agents.core.agent.execution.AgentExecutionInfo
import ai.koog.agents.core.feature.model.AIAgentError
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Represents an event triggered when a tool is called within the system.
 *
 * This event is used to capture and describe the invocation of a tool
 * along with its associated arguments. It helps in tracking, logging,
 * or processing tool calls as part of a larger feature pipeline or system
 * workflow.
 *
 * @property eventId A unique identifier for the event or a group of events;
 * @property executionInfo Provides contextual information about the execution associated with this event.
 * @property runId A unique identifier representing the specific run or instance of the tool call;
 * @property toolCallId A unique identifier for the tool call;
 * @property toolName The unique name of the tool being called;
 * @property toolArgs The arguments provided for the tool execution;
 * @property timestamp The timestamp of the event, in milliseconds since the Unix epoch.
 */
@Serializable
public data class ToolCallStartingEvent(
    override val eventId: String,
    override val executionInfo: AgentExecutionInfo,
    val runId: String,
    val toolCallId: String?,
    val toolName: String,
    val toolArgs: JsonObject,
    override val timestamp: Long = Clock.System.now().toEpochMilliseconds(),
) : DefinedFeatureEvent() {

    /**
     * @deprecated Use constructor with [executionInfo] parameter
     */
    @Deprecated(
        message = "Use constructor with executionInfo parameter",
        replaceWith = ReplaceWith("ToolCallStartingEvent(executionInfo, runId, toolCallId, toolName, toolArgs, timestamp)")
    )
    public constructor(
        runId: String,
        toolCallId: String?,
        toolName: String,
        toolArgs: JsonObject,
        timestamp: Long = Clock.System.now().toEpochMilliseconds()
    ) : this(
        eventId = ToolCallStartingEvent::class.simpleName.toString(),
        executionInfo = AgentExecutionInfo(
            parent = null,
            partName = ToolCallStartingEvent::class.simpleName.toString(),
        ),
        runId = runId,
        toolCallId = toolCallId,
        toolName = toolName,
        toolArgs = toolArgs,
        timestamp = timestamp
    )
}

/**
 * Represents an event indicating that a tool encountered a validation error during its execution.
 *
 * This event captures details regarding the tool that failed validation, the arguments
 * provided to the tool, and the specific error message explaining why the validation failed.
 *
 * @property eventId A unique identifier for the event or a group of events;
 * @property executionInfo Provides contextual information about the execution associated with this event.
 * @property runId A unique identifier representing the specific run or instance of the tool call;
 * @property toolCallId A unique identifier for the tool call that encountered the validation error;
 * @property toolName The name of the tool that encountered the validation error;
 * @property toolArgs The arguments associated with the tool at the time of validation failure;
 * @property toolDescription A description of the tool that encountered the validation error;
 * @property error A message describing the validation error encountered;
 * @property timestamp The timestamp of the event, in milliseconds since the Unix epoch.
 */
@Serializable
public data class ToolValidationFailedEvent(
    override val eventId: String,
    override val executionInfo: AgentExecutionInfo,
    val runId: String,
    val toolCallId: String?,
    val toolName: String,
    val toolArgs: JsonObject,
    val toolDescription: String?,
    val message: String?,
    val error: AIAgentError,
    override val timestamp: Long = Clock.System.now().toEpochMilliseconds(),
) : DefinedFeatureEvent() {

    /**
     * @deprecated Use constructor with [executionInfo] parameter
     */
    @Deprecated(
        message = "Use constructor with executionInfo parameter",
        replaceWith = ReplaceWith("ToolValidationFailedEvent(executionInfo, runId, toolCallId, toolName, toolArgs, message, error, timestamp)")
    )
    public constructor(
        runId: String,
        toolCallId: String?,
        toolName: String,
        toolArgs: JsonObject,
        error: String,
        timestamp: Long = Clock.System.now().toEpochMilliseconds()
    ) : this(
        eventId = ToolValidationFailedEvent::class.simpleName.toString(),
        executionInfo = AgentExecutionInfo(
            parent = null,
            partName = ToolValidationFailedEvent::class.simpleName.toString(),
        ),
        runId = runId,
        toolCallId = toolCallId,
        toolName = toolName,
        toolArgs = toolArgs,
        toolDescription = null,
        message = error,
        error = AIAgentError(error, "", null)
    )
}

/**
 * Captures an event where a tool call has failed during its execution.
 *
 * This event is typically used to log or handle situations where a tool could not execute
 * successfully due to an error. It includes relevant details about the failed tool call,
 * such as the tool's name, the arguments provided, and the specific error encountered.
 *
 * @property eventId A unique identifier for the event or a group of events;
 * @property executionInfo Provides contextual information about the execution associated with this event.
 * @property runId A unique identifier representing the specific run or instance of the tool call;
 * @property toolCallId A unique identifier for the tool call that failed;
 * @property toolName The name of the tool that failed;
 * @property toolArgs The arguments passed to the tool during the failed execution;
 * @property toolDescription A description of the tool that failed;
 * @property error The error encountered during the tool's execution;
 * @property timestamp The timestamp of the event, in milliseconds since the Unix epoch.
 */
@Serializable
public data class ToolCallFailedEvent(
    override val eventId: String,
    override val executionInfo: AgentExecutionInfo,
    val runId: String,
    val toolCallId: String?,
    val toolName: String,
    val toolArgs: JsonObject,
    val toolDescription: String?,
    val error: AIAgentError?,
    override val timestamp: Long = Clock.System.now().toEpochMilliseconds(),
) : DefinedFeatureEvent() {

    /**
     * @deprecated Use constructor with [executionInfo] parameter
     */
    @Deprecated(
        message = "Use constructor with executionInfo parameter",
        replaceWith = ReplaceWith("ToolCallFailedEvent(executionInfo, runId, toolCallId, toolName, toolArgs, error, timestamp)")
    )
    public constructor(
        runId: String,
        toolCallId: String?,
        toolName: String,
        toolArgs: JsonObject,
        error: AIAgentError,
        timestamp: Long = Clock.System.now().toEpochMilliseconds()
    ) : this(
        eventId = ToolCallFailedEvent::class.simpleName.toString(),
        executionInfo = AgentExecutionInfo(
            parent = null,
            partName = ToolCallFailedEvent::class.simpleName.toString(),
        ),
        runId = runId,
        toolCallId = toolCallId,
        toolName = toolName,
        toolArgs = toolArgs,
        toolDescription = null,
        error = error,
        timestamp = timestamp
    )
}

/**
 * Represents an event that contains the results of a tool invocation.
 *
 * This event carries information about the tool that was executed, the arguments used for its execution,
 * and the resulting outcome. It is used to track and share the details of a tool's execution within
 * the system's event-handling framework.
 *
 * @property eventId A unique identifier for the event or a group of events;
 * @property executionInfo Provides contextual information about the execution associated with this event.
 * @property runId A unique identifier representing the specific run or instance of the tool call;
 * @property toolCallId A unique identifier for the tool call that was executed;
 * @property toolName The name of the tool that was executed;
 * @property toolArgs The arguments used for executing the tool;
 * @property toolDescription A description of the tool that was executed;
 * @property result The result of the tool execution, which may be null if no result was produced or an error occurred;
 * @property timestamp The timestamp of the event, in milliseconds since the Unix epoch.
 */
@Serializable
public data class ToolCallCompletedEvent(
    override val eventId: String,
    override val executionInfo: AgentExecutionInfo,
    val runId: String,
    val toolCallId: String?,
    val toolName: String,
    val toolArgs: JsonObject,
    val toolDescription: String?,
    val result: JsonElement?,
    override val timestamp: Long = Clock.System.now().toEpochMilliseconds(),
) : DefinedFeatureEvent() {

    /**
     * @deprecated Use constructor with [executionInfo] parameter
     */
    @Deprecated(
        message = "Use constructor with executionInfo parameter",
        replaceWith = ReplaceWith("ToolCallCompletedEvent(executionInfo, runId, toolCallId, toolName, toolArgs, result, timestamp)")
    )
    public constructor(
        runId: String,
        toolCallId: String?,
        toolName: String,
        toolArgs: JsonObject,
        result: String?,
        timestamp: Long = Clock.System.now().toEpochMilliseconds()
    ) : this(
        eventId = ToolCallCompletedEvent::class.simpleName.toString(),
        executionInfo = AgentExecutionInfo(
            parent = null,
            partName = ToolCallCompletedEvent::class.simpleName.toString(),
        ),
        runId = runId,
        toolCallId = toolCallId,
        toolName = toolName,
        toolArgs = toolArgs,
        toolDescription = null,
        result = JsonPrimitive(result),
        timestamp = timestamp
    )
}

//region Deprecated

@Deprecated(
    message = "Use ToolCallStartingEvent instead",
    replaceWith = ReplaceWith("ToolCallStartingEvent")
)
public typealias ToolCallEvent = ToolCallStartingEvent

@Deprecated(
    message = "Use ToolValidationFailedEvent instead",
    replaceWith = ReplaceWith("ToolValidationFailedEvent")
)
public typealias ToolValidationErrorEvent = ToolValidationFailedEvent

@Deprecated(
    message = "Use ToolCallFailedEvent instead",
    replaceWith = ReplaceWith("ToolCallFailedEvent")
)
public typealias ToolCallFailureEvent = ToolCallFailedEvent

@Deprecated(
    message = "Use ToolCallCompletedEvent instead",
    replaceWith = ReplaceWith("ToolCallCompletedEvent")
)
public typealias ToolCallResultEvent = ToolCallCompletedEvent

//endregion Deprecated
