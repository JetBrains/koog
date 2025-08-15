package ai.koog.agents.core.feature.handler

/**
 * Container for node execution handlers.
 * Holds both before and after node execution handlers.
 */
public class ExecuteNodeHandler {

    /** Handler called before node execution */
    public var beforeNodeHandler: BeforeNodeHandler = BeforeNodeHandler { _ -> }

    /** Handler called after node execution */
    public var afterNodeHandler: AfterNodeHandler = AfterNodeHandler { _ -> }

    /** Handler called when an error occurs during node execution */
    public var nodeExecutionErrorHandler: NodeExecutionErrorHandler = NodeExecutionErrorHandler { _ -> }
}

/**
 * Handler for intercepting node execution before it starts.
 */
public fun interface BeforeNodeHandler {
    /**
     * Called before a node is executed.
     */
    public suspend fun handle(eventContext: NodeBeforeExecuteContext)
}

/**
 * Handler for intercepting node execution after it completes.
 */
public fun interface AfterNodeHandler {
    /**
     * Called after a node has been executed.
     */
    public suspend fun handle(eventContext: NodeAfterExecuteContext)
}

/**
 * Handler for intercepting node execution errors.
 */
public fun interface NodeExecutionErrorHandler {
    /**
     * Called when an error occurs during node execution.
     */
    public suspend fun handle(eventContext: NodeExecutionErrorContext)
}
