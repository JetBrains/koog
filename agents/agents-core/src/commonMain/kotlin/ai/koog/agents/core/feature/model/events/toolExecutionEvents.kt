package ai.koog.agents.core.feature.model.events

import ai.koog.agents.core.feature.model.AIAgentError
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Represents an event triggered when a tool is called within the system.
 *
 * This event is used to capture and describe the invocation of a tool
 * along with its associated arguments. It helps in tracking, logging,
 * or processing tool calls as part of a larger feature pipeline or system
 * workflow.
 *
 * @property toolName The unique name of the tool being called;
 * @property toolArgs The arguments provided for the tool execution;
 * @property timestamp The timestamp of the event, in milliseconds since the Unix epoch.
 */
@Serializable
public data class ToolCallStartingEvent(
    val runId: String,
    val toolCallId: String?,
    val toolName: String,
    val toolArgs: JsonObject,
    override val timestamp: Long = Clock.System.now().toEpochMilliseconds(),
) : DefinedFeatureEvent()

/**
 * Represents an event indicating that a tool encountered a validation error during its execution.
 *
 * This event captures details regarding the tool that failed validation, the arguments
 * provided to the tool, and the specific error message explaining why the validation failed.
 *
 * @property toolName The name of the tool that encountered the validation error;
 * @property toolArgs The arguments associated with the tool at the time of validation failure;
 * @property error A message describing the validation error encountered;
 * @property timestamp The timestamp of the event, in milliseconds since the Unix epoch.
 */
@Serializable
public data class ToolValidationFailedEvent(
    val runId: String,
    val toolCallId: String?,
    val toolName: String,
    val toolArgs: JsonObject,
    val error: String,
    override val timestamp: Long = Clock.System.now().toEpochMilliseconds(),
) : DefinedFeatureEvent()

/**
 * Captures an event where a tool call has failed during its execution.
 *
 * This event is typically used to log or handle situations where a tool could not execute
 * successfully due to an error. It includes relevant details about the failed tool call,
 * such as the tool's name, the arguments provided, and the specific error encountered.
 *
 * @property toolName The name of the tool that failed;
 * @property toolArgs The arguments passed to the tool during the failed execution;
 * @property error The error encountered during the tool's execution;
 * @property timestamp The timestamp of the event, in milliseconds since the Unix epoch.
 */
@Serializable
public data class ToolCallFailedEvent(
    val runId: String,
    val toolCallId: String?,
    val toolName: String,
    val toolArgs: JsonObject,
    val error: AIAgentError,
    override val timestamp: Long = Clock.System.now().toEpochMilliseconds(),
) : DefinedFeatureEvent()

/**
 * Represents an event that contains the results of a tool invocation.
 *
 * This event carries information about the tool that was executed, the arguments used for its execution,
 * and the resulting outcome. It is used to track and share the details of a tool's execution within
 * the system's event-handling framework.
 *
 * @property toolName The name of the tool that was executed;
 * @property toolArgs The arguments used for executing the tool;
 * @property result The result of the tool execution, which may be null if no result was produced or an error occurred;
 * @property timestamp The timestamp of the event, in milliseconds since the Unix epoch.
 */
@Serializable
public data class ToolCallCompletedEvent(
    val runId: String,
    val toolCallId: String?,
    val toolName: String,
    val toolArgs: JsonObject,
    val result: String?,
    override val timestamp: Long = Clock.System.now().toEpochMilliseconds(),
) : DefinedFeatureEvent()

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
