package ai.koog.agents.core.feature.handler.node

/**
 * Container for node execution handlers.
 * Holds both before and after node execution handlers.
 */
public class NodeExecutionEventHandler {

    /** Handler called before node execution */
    public var nodeExecutionStartingHandler: NodeExecutionStartingHandler = NodeExecutionStartingHandler { _ -> }

    /** Handler called after node execution */
    public var nodeExecutionCompletedHandler: NodeExecutionCompletedHandler = NodeExecutionCompletedHandler { _ -> }

    /** Handler called when an error occurs during node execution */
    public var nodeExecutionFailedHandler: NodeExecutionFailedHandler = NodeExecutionFailedHandler { _ -> }
}

/**
 * Handler for intercepting node execution before it starts.
 */
public fun interface NodeExecutionStartingHandler {
    /**
     * Called before a node is executed.
     */
    public suspend fun handle(eventContext: NodeExecutionStartingContext)
}

/**
 * Handler for intercepting node execution after it completes.
 */
public fun interface NodeExecutionCompletedHandler {
    /**
     * Called after a node has been executed.
     */
    public suspend fun handle(eventContext: NodeExecutionCompletedContext)
}

/**
 * Handler for intercepting node execution errors.
 */
public fun interface NodeExecutionFailedHandler {
    /**
     * Called when an error occurs during node execution.
     */
    public suspend fun handle(eventContext: NodeExecutionFailedContext)
}
