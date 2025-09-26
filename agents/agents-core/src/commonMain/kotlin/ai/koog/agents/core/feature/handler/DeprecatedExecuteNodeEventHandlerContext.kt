package ai.koog.agents.core.feature.handler

/**
 * Represents the context for handling node-specific events within the framework.
 */
@Deprecated(
    message = "Use NodeExecutionEventContext instead",
    replaceWith = ReplaceWith(
        expression = "NodeExecutionEventContext",
        imports = arrayOf("ai.koog.agents.core.feature.handler.node.NodeExecutionEventContext")
    )
)
public typealias NodeEventHandlerContext = ai.koog.agents.core.feature.handler.node.NodeExecutionEventContext

/**
 * Represents the context for handling a before node execution event.
 */
@Deprecated(
    message = "Use NodeExecutionStartingContext instead",
    replaceWith = ReplaceWith(
        expression = "NodeExecutionStartingContext",
        imports = arrayOf("ai.koog.agents.core.feature.handler.node.NodeExecutionStartingContext")
    )
)
public typealias NodeBeforeExecuteContext = ai.koog.agents.core.feature.handler.node.NodeExecutionStartingContext

/**
 * Represents the context for handling an after node execution event.
 */
@Deprecated(
    message = "Use NodeExecutionCompletedContext instead",
    replaceWith = ReplaceWith(
        expression = "NodeExecutionCompletedContext",
        imports = arrayOf("ai.koog.agents.core.feature.handler.node.NodeExecutionCompletedContext")
    )
)
public typealias NodeAfterExecuteContext = ai.koog.agents.core.feature.handler.node.NodeExecutionCompletedContext

/**
 * Represents the context for handling errors during the execution of an AI agent node.
 */
@Deprecated(
    message = "Use NodeExecutionFailedContext instead",
    replaceWith = ReplaceWith(
        expression = "NodeExecutionFailedContext",
        imports = arrayOf("ai.koog.agents.core.feature.handler.node.NodeExecutionFailedContext")
    )
)
public typealias NodeExecutionErrorContext = ai.koog.agents.core.feature.handler.node.NodeExecutionFailedContext
