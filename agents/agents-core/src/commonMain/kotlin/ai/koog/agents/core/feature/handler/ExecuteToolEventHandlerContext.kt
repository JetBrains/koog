package ai.koog.agents.core.feature.handler

import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolArgs
import ai.koog.agents.core.tools.ToolResult

/**
 * Represents the context for handling tool-specific events within the framework.
 */
public interface ToolEventHandlerContext : EventHandlerContext

/**
 * Represents the context for handling a tool call event.
 *
 * @property tool The tool instance that is being executed. It encapsulates the logic and metadata for the operation.
 * @property toolArgs The arguments provided for the tool execution, adhering to the tool's expected input structure.
 */
public data class ToolCallContext(
    val runId: String,
    val toolCallId: String?,
    val tool: Tool<*, *>,
    val toolArgs: ToolArgs
) : ToolEventHandlerContext {
    override val eventType: AgentEventType = AgentEventType.ExecuteTool
}

/**
 * Represents the context for handling validation errors that occur during the execution of a tool.
 *
 * @param tool The tool instance associated with the validation error.
 * @param toolArgs The arguments passed to the tool when the error occurred.
 * @param error The error message describing the validation issue.
 */
public data class ToolValidationErrorContext(
    val runId: String,
    val toolCallId: String?,
    val tool: Tool<*, *>,
    val toolArgs: ToolArgs,
    val error: String
) : ToolEventHandlerContext {
    override val eventType: AgentEventType = AgentEventType.ExecuteToolValidationError
}

/**
 * Represents the context provided to handle a failure during the execution of a tool.
 *
 * @param tool The tool that was being executed when the failure occurred.
 * @param toolArgs The arguments that were passed to the tool during execution.
 * @param throwable The exception or error that caused the failure.
 */
public data class ToolCallFailureContext(
    val runId: String,
    val toolCallId: String?,
    val tool: Tool<*, *>,
    val toolArgs: ToolArgs,
    val throwable: Throwable
) : ToolEventHandlerContext {
    override val eventType: AgentEventType = AgentEventType.ExecuteToolFailure
}

/**
 * Represents the context used when handling the result of a tool call.
 *
 * @param tool The tool being executed, which defines the operation to be performed.
 * @param toolArgs The arguments required by the tool for execution.
 * @param result An optional result produced by the tool after execution can be null if not applicable.
 */
public data class ToolCallResultContext(
    val runId: String,
    val toolCallId: String?,
    val tool: Tool<*, *>,
    val toolArgs: ToolArgs,
    val result: ToolResult?
) : ToolEventHandlerContext {
    override val eventType: AgentEventType = AgentEventType.ExecuteToolResult
}
