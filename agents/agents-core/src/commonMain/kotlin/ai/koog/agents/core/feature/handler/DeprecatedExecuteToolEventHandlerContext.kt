package ai.koog.agents.core.feature.handler

/**
 * Represents the context for handling tool-specific events within the framework.
 */
@Deprecated(
    message = "Use ToolCallEventContext instead",
    replaceWith = ReplaceWith(
        expression = "ToolCallEventContext",
        imports = arrayOf("ai.koog.agents.core.feature.handler.tool.ToolCallEventContext")
    )
)
public typealias ToolEventHandlerContext = ai.koog.agents.core.feature.handler.tool.ToolCallEventContext

/**
 * Represents the context for handling a tool call event.
 */
@Deprecated(
    message = "Use ToolCallStartingContext instead",
    replaceWith = ReplaceWith(
        expression = "ToolCallStartingContext",
        imports = arrayOf("ai.koog.agents.core.feature.handler.tool.ToolCallStartingContext")
    )
)
public typealias ToolCallContext = ai.koog.agents.core.feature.handler.tool.ToolCallStartingContext

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
    message = "Use ToolCallFailedContext instead",
    replaceWith = ReplaceWith(
        expression = "ToolCallFailedContext",
        imports = arrayOf("ai.koog.agents.core.feature.handler.tool.ToolCallFailedContext")
    )
)
public typealias ToolCallFailureContext = ai.koog.agents.core.feature.handler.tool.ToolCallFailedContext

/**
 * Represents the context used when handling the result of a tool call.
 */
@Deprecated(
    message = "Use ToolCallCompletedContext instead",
    replaceWith = ReplaceWith(
        expression = "ToolCallCompletedContext",
        imports = arrayOf("ai.koog.agents.core.feature.handler.tool.ToolCallCompletedContext")
    )
)
public typealias ToolCallResultContext = ai.koog.agents.core.feature.handler.tool.ToolCallCompletedContext
