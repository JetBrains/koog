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
) : ToolEventHandlerContext

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
) : ToolEventHandlerContext

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
) : ToolEventHandlerContext

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
) : ToolEventHandlerContext

/**
 * Represents the context for when tool execution is denied due to insufficient permissions.
 *
 * @property tool The tool that was denied.
 * @property toolArgs The arguments that were provided for the tool.
 * @property requiredRole The minimum role required to use this tool.
 * @property effectiveRoles The roles of the agent attempting the call.
 * @property reason A descriptive message explaining the denial.
 */
public data class ToolPermissionDeniedContext(
    val runId: String,
    val toolCallId: String?,
    val tool: Tool<*, *>,
    val toolArgs: ToolArgs?,
    val requiredRole: String?,
    val effectiveRoles: List<String>,
    val reason: String
) : ToolEventHandlerContext

/**
 * Represents the context for when tool execution is denied due to rate limiting.
 *
 * @property tool The tool that was rate limited.
 * @property toolArgs The arguments that were provided for the tool.
 * @property limit The rate limit that was exceeded.
 * @property resetIn The duration until the rate limit resets.
 */
public data class ToolRateLimitExceededContext(
    val runId: String,
    val toolCallId: String?,
    val tool: Tool<*, *>,
    val toolArgs: ToolArgs?,
    val limit: String,
    val resetIn: String?
) : ToolEventHandlerContext

/**
 * Represents the context for when a tool result is retrieved from cache.
 *
 * @property tool The tool whose result was cached.
 * @property toolArgs The arguments that were used for the cached call.
 * @property cacheKey The key used to retrieve the cached result.
 * @property cacheAge The age of the cached result in milliseconds.
 */
public data class ToolCacheHitContext(
    val runId: String,
    val toolCallId: String?,
    val tool: Tool<*, *>,
    val toolArgs: ToolArgs?,
    val cacheKey: String,
    val cacheAge: Long?
) : ToolEventHandlerContext

/**
 * Represents the context for when a tool cache lookup misses.
 *
 * @property tool The tool being executed.
 * @property toolArgs The arguments being used for the tool call.
 * @property cacheKey The key that was checked in the cache.
 */
public data class ToolCacheMissContext(
    val runId: String,
    val toolCallId: String?,
    val tool: Tool<*, *>,
    val toolArgs: ToolArgs?,
    val cacheKey: String
) : ToolEventHandlerContext

/**
 * Context for tool result cached events.
 *
 * @property runId The unique identifier for the current run.
 * @property toolCallId The unique identifier for the tool call.
 * @property tool The tool whose result was cached.
 * @property toolArgs The arguments used for the tool call.
 * @property cacheKey The key used to store the result in cache.
 * @property ttlSeconds The time-to-live in seconds for the cached result.
 */
public data class ToolResultCachedContext(
    val runId: String,
    val toolCallId: String?,
    val tool: Tool<*, *>,
    val toolArgs: ToolArgs?,
    val cacheKey: String,
    val ttlSeconds: Long
) : ToolEventHandlerContext
