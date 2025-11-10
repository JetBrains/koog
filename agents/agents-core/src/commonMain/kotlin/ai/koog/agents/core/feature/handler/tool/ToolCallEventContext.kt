package ai.koog.agents.core.feature.handler.tool

import ai.koog.agents.core.feature.handler.AgentLifecycleEventContext
import ai.koog.agents.core.feature.handler.AgentLifecycleEventType
import ai.koog.agents.core.tools.Tool

/**
 * Represents the context for handling tool-specific events within the framework.
 */
public interface ToolCallEventContext : AgentLifecycleEventContext

/**
 * Represents the context for handling a tool call event.
 *
 * @property runId The unique identifier for this tool call session.
 * @property toolCallId The unique identifier for this tool call.
 * @property tool The tool instance that is being executed. It encapsulates the logic and metadata for the operation.
 * @property toolArgs The arguments provided for the tool execution, adhering to the tool's expected input structure.
 */
public data class ToolCallStartingContext(
    val runId: String,
    val toolCallId: String?,
    val tool: Tool<*, *>,
    val toolArgs: Any?
) : ToolCallEventContext {
    override val eventType: AgentLifecycleEventType = AgentLifecycleEventType.ToolCallStarting
}

/**
 * Represents the context for handling validation errors that occur during the execution of a tool.
 *
 * @property runId The unique identifier for this tool call session.
 * @property toolCallId The unique identifier for this tool call.
 * @property tool The tool instance associated with the validation error.
 * @property toolArgs The arguments passed to the tool when the error occurred.
 * @property error The error message describing the validation issue.
 */
public data class ToolValidationFailedContext(
    val runId: String,
    val toolCallId: String?,
    val tool: Tool<*, *>,
    val toolArgs: Any?,
    val error: String
) : ToolCallEventContext {
    override val eventType: AgentLifecycleEventType = AgentLifecycleEventType.ToolValidationFailed
}

/**
 * Represents the context provided to handle a failure during the execution of a tool.
 *
 * @property runId The unique identifier for this tool call session.
 * @property toolCallId The unique identifier for this tool call.
 * @property tool The tool that was being executed when the failure occurred.
 * @property toolArgs The arguments that were passed to the tool during execution.
 * @property throwable The exception or error that caused the failure.
 */
public data class ToolCallFailedContext(
    val runId: String,
    val toolCallId: String?,
    val tool: Tool<*, *>,
    val toolArgs: Any?,
    val throwable: Throwable
) : ToolCallEventContext {
    override val eventType: AgentLifecycleEventType = AgentLifecycleEventType.ToolCallFailed
}

/**
 * Represents the context used when handling the result of a tool call.
 *
 * @property runId The unique identifier for this tool call session.
 * @property toolCallId The unique identifier for this tool call.
 * @property tool The tool being executed, which defines the operation to be performed.
 * @property toolArgs The arguments required by the tool for execution.
 * @property result An optional result produced by the tool after execution can be null if not applicable.
 */
public data class ToolCallCompletedContext(
    val runId: String,
    val toolCallId: String?,
    val tool: Tool<*, *>,
    val toolArgs: Any?,
    val result: Any?
) : ToolCallEventContext {
    override val eventType: AgentLifecycleEventType = AgentLifecycleEventType.ToolCallCompleted
}
