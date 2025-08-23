package ai.koog.agents.core.feature.handler.tool

import ai.koog.agents.core.feature.handler.AgentLifecycleEventContext
import ai.koog.agents.core.feature.handler.AgentLifecycleEventType
import ai.koog.agents.core.tools.Tool

/**
 * Represents the context for handling tool-specific events within the framework.
 */
public interface ToolExecutionEventContext : AgentLifecycleEventContext

/**
 * Represents the context for handling a tool call event.
 *
 * @property tool The tool instance that is being executed. It encapsulates the logic and metadata for the operation.
 * @property toolArgs The arguments provided for the tool execution, adhering to the tool's expected input structure.
 */
public data class ToolExecutionStartingContext(
    val runId: String,
    val toolCallId: String?,
    val tool: Tool<*, *>,
    val toolArgs: Any?
) : ToolExecutionEventContext {
    override val eventType: AgentLifecycleEventType = AgentLifecycleEventType.ToolExecutionStarting
}

/**
 * Represents the context for handling validation errors that occur during the execution of a tool.
 *
 * @param tool The tool instance associated with the validation error.
 * @param toolArgs The arguments passed to the tool when the error occurred.
 * @param error The error message describing the validation issue.
 */
public data class ToolValidationFailedContext(
    val runId: String,
    val toolCallId: String?,
    val tool: Tool<*, *>,
    val toolArgs: Any?,
    val error: String
) : ToolExecutionEventContext {
    override val eventType: AgentLifecycleEventType = AgentLifecycleEventType.ToolValidationFailed
}

/**
 * Represents the context provided to handle a failure during the execution of a tool.
 *
 * @param tool The tool that was being executed when the failure occurred.
 * @param toolArgs The arguments that were passed to the tool during execution.
 * @param throwable The exception or error that caused the failure.
 */
public data class ToolExecutionFailedContext(
    val runId: String,
    val toolCallId: String?,
    val tool: Tool<*, *>,
    val toolArgs: Any?,
    val throwable: Throwable
) : ToolExecutionEventContext {
    override val eventType: AgentLifecycleEventType = AgentLifecycleEventType.ToolExecutionFailed
}

/**
 * Represents the context used when handling the result of a tool call.
 *
 * @param tool The tool being executed, which defines the operation to be performed.
 * @param toolArgs The arguments required by the tool for execution.
 * @param result An optional result produced by the tool after execution can be null if not applicable.
 */
public data class ToolExecutionCompletedContext(
    val runId: String,
    val toolCallId: String?,
    val tool: Tool<*, *>,
    val toolArgs: Any?,
    val result: Any?
) : ToolExecutionEventContext {
    override val eventType: AgentLifecycleEventType = AgentLifecycleEventType.ToolExecutionCompleted
}
