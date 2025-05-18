package ai.koog.agents.core.feature.handler

import ai.koog.agents.core.agent.context.AIAgentContextBase
import ai.koog.agents.core.agent.entity.AIAgentNodeBase

/**
 * Container for node execution handlers.
 * Holds both before and after node execution handlers.
 */
public class ExecuteNodeHandler {

    /** Handler called before node execution */
    public var beforeNodeHandler: BeforeNodeHandler = BeforeNodeHandler { _, _, _ -> }

    /** Handler called after node execution */
    public var afterNodeHandler: AfterNodeHandler = AfterNodeHandler { _, _, _, _ -> }
}

/**
 * Handler for intercepting node execution before it starts.
 */
public fun interface BeforeNodeHandler {
    /**
     * Called before a node is executed.
     *
     * @param node The node that will be executed
     * @param context The stage context in which the node is executing
     * @param input The input data for the node
     */
    public suspend fun handle(
        node: AIAgentNodeBase<*, *>,
        context: AIAgentContextBase,
        input: Any?
    )
}

/**
 * Handler for intercepting node execution after it completes.
 */
public fun interface AfterNodeHandler {
    /**
     * Called after a node has been executed.
     *
     * @param node The node that was executed
     * @param context The stage context in which the node executed
     * @param input The input data that was provided to the node
     * @param output The output data produced by the node
     */
    public suspend fun handle(
        node: AIAgentNodeBase<*, *>,
        context: AIAgentContextBase,
        input: Any?,
        output: Any?
    )
}
