package ai.koog.agents.core.feature.handler.subgraph

/**
 * Container for subgraph execution handlers.
 * Holds both before and after subgraph execution handlers.
 */
public class SubgraphExecutionEventHandler {

    /** Handler called before subgraph execution */
    public var subgraphExecutionStartingHandler: SubgraphExecutionStartingHandler = SubgraphExecutionStartingHandler { _ -> }

    /** Handler called after subgraph execution */
    public var subgraphExecutionCompletedHandler: SubgraphExecutionCompletedHandler = SubgraphExecutionCompletedHandler { _ -> }

    /** Handler called when an error occurs during subgraph execution */
    public var subgraphExecutionFailedHandler: SubgraphExecutionFailedHandler = SubgraphExecutionFailedHandler { _ -> }
}

/**
 * Handler for intercepting subgraph execution before it starts.
 */
public fun interface SubgraphExecutionStartingHandler {
    /**
     * Called before a subgraph is executed.
     */
    public suspend fun handle(eventContext: SubgraphExecutionStartingContext)
}

/**
 * Handler for intercepting subgraph execution after it completes.
 */
public fun interface SubgraphExecutionCompletedHandler {
    /**
     * Called after a subgraph has been executed.
     */
    public suspend fun handle(eventContext: SubgraphExecutionCompletedContext)
}

/**
 * Handler for intercepting subgraph execution errors.
 */
public fun interface SubgraphExecutionFailedHandler {
    /**
     * Called when an error occurs during subgraph execution.
     */
    public suspend fun handle(eventContext: SubgraphExecutionFailedContext)
}
