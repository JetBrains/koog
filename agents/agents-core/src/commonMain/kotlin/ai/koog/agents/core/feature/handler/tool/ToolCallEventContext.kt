package ai.koog.agents.core.feature.handler.tool

import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.agent.execution.AgentExecutionInfo
import ai.koog.agents.core.feature.handler.AgentLifecycleEventContext
import ai.koog.agents.core.feature.handler.AgentLifecycleEventType
import ai.koog.agents.core.feature.model.AIAgentError
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Represents the context for handling tool call events.
 */
public interface ToolCallEventContext : AgentLifecycleEventContext {
    /**
     * [runId] The unique identifier for this tool call session;
     */
    public val runId: String

    /**
     * [toolCallId] The unique identifier for this tool call;
     */
    public val toolCallId: String?

    /**
     * [toolName] The tool name that is being executed;
     */
    public val toolName: String

    /**
     * [toolDescription] A description of the tool being executed;
     */
    public val toolDescription: String?

    /**
     * [toolArgs] The arguments provided for the tool execution, adhering to the tool's expected input structure.
     */
    public val toolArgs: JsonObject

    /**
     * [context] The agent context associated with the tool call;
     */
    public val context: AIAgentContext
}

/**
 * Represents the context for handling a tool call event.
 *
 * @property executionInfo The execution information containing parentId and current execution path;
 */
public data class ToolCallStartingContext(
    override val eventId: String,
    override val executionInfo: AgentExecutionInfo,
    override val runId: String,
    override val toolCallId: String?,
    override val toolName: String,
    override val toolDescription: String?,
    override val toolArgs: JsonObject,
    override val context: AIAgentContext
) : ToolCallEventContext {
    override val eventType: AgentLifecycleEventType = AgentLifecycleEventType.ToolCallStarting
}

/**
 * Represents the context for handling validation errors that occur during the execution of a tool.
 *
 * @property executionInfo The execution information containing parentId and current execution path;
 * @property message A message describing the validation error.
 * @property error The [AIAgentError] error describing the validation issue.
 */
public data class ToolValidationFailedContext(
    override val eventId: String,
    override val executionInfo: AgentExecutionInfo,
    override val runId: String,
    override val toolCallId: String?,
    override val toolName: String,
    override val toolDescription: String?,
    override val toolArgs: JsonObject,
    val message: String,
    val error: AIAgentError,
    override val context: AIAgentContext
) : ToolCallEventContext {
    override val eventType: AgentLifecycleEventType = AgentLifecycleEventType.ToolValidationFailed
}

/**
 * Represents the context provided to handle a failure during the execution of a tool.
 *
 * @property executionInfo The execution information containing parentId and current execution path;
 * @property message A message describing the failure that occurred.
 * @property error The [AIAgentError] instance describing the tool call failure.
 */
public data class ToolCallFailedContext(
    override val eventId: String,
    override val executionInfo: AgentExecutionInfo,
    override val runId: String,
    override val toolCallId: String?,
    override val toolName: String,
    override val toolDescription: String?,
    override val toolArgs: JsonObject,
    val message: String,
    val error: AIAgentError?,
    override val context: AIAgentContext
) : ToolCallEventContext {
    override val eventType: AgentLifecycleEventType = AgentLifecycleEventType.ToolCallFailed
}

/**
 * Represents the context used when handling the result of a tool call.
 *
 * @property executionInfo The execution information containing parentId and current execution path;
 * @property toolResult An optional result produced by the tool after execution can be null if not applicable.
 */
public data class ToolCallCompletedContext(
    override val eventId: String,
    override val executionInfo: AgentExecutionInfo,
    override val runId: String,
    override val toolCallId: String?,
    override val toolName: String,
    override val toolDescription: String?,
    override val toolArgs: JsonObject,
    val toolResult: JsonElement?,
    override val context: AIAgentContext
) : ToolCallEventContext {
    override val eventType: AgentLifecycleEventType = AgentLifecycleEventType.ToolCallCompleted
}
