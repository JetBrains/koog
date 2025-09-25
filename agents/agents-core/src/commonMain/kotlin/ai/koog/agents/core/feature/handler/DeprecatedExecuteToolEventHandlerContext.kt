package ai.koog.agents.core.feature.handler

/**
 * Represents the context for handling tool-specific events within the framework.
 */
@Deprecated(
    message = "Use ToolExecutionEventContext instead",
    replaceWith = ReplaceWith(
        expression = "ToolExecutionEventContext",
        imports = arrayOf("ai.koog.agents.core.feature.handler.tool.ToolExecutionEventContext")
    )
)
public typealias ToolEventHandlerContext = ai.koog.agents.core.feature.handler.tool.ToolExecutionEventContext

/**
 * Represents the context for handling a tool call event.
 */
@Deprecated(
    message = "Use ToolExecutionStartingContext instead",
    replaceWith = ReplaceWith(
        expression = "ToolExecutionStartingContext",
        imports = arrayOf("ai.koog.agents.core.feature.handler.tool.ToolExecutionStartingContext")
    )
)
public typealias ToolCallContext = ai.koog.agents.core.feature.handler.tool.ToolExecutionStartingContext

/**
 * Represents the context for handling validation errors that occur during the execution of a tool.
 */
@Deprecated(
    message = "Use ToolValidationFailedContext instead",
    replaceWith = ReplaceWith(
        expression = "ToolValidationFailedContext",
        imports = arrayOf("ai.koog.agents.core.feature.handler.tool.ToolValidationFailedContext")
    )
)
public typealias ToolValidationErrorContext = ai.koog.agents.core.feature.handler.tool.ToolValidationFailedContext

/**
 * Represents the context provided to handle a failure during the execution of a tool.
 */
@Deprecated(
    message = "Use ToolExecutionFailedContext instead",
    replaceWith = ReplaceWith(
        expression = "ToolExecutionFailedContext",
        imports = arrayOf("ai.koog.agents.core.feature.handler.tool.ToolExecutionFailedContext")
    )
)
public typealias ToolCallFailureContext = ai.koog.agents.core.feature.handler.tool.ToolExecutionFailedContext

/**
 * Represents the context used when handling the result of a tool call.
 */
@Deprecated(
    message = "Use ToolExecutionCompletedContext instead",
    replaceWith = ReplaceWith(
        expression = "ToolExecutionCompletedContext",
        imports = arrayOf("ai.koog.agents.core.feature.handler.tool.ToolExecutionCompletedContext")
    )
)
public typealias ToolCallResultContext = ai.koog.agents.core.feature.handler.tool.ToolExecutionCompletedContext
