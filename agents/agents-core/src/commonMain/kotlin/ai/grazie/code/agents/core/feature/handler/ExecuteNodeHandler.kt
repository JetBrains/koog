package ai.grazie.code.agents.core.feature.handler

import ai.grazie.code.agents.core.agent.stage.LocalAgentStageContext
import ai.grazie.code.agents.core.agent.graph.LocalAgentNode

/**
 * Container for node execution handlers.
 * Holds both before and after node execution handlers.
 */
class ExecuteNodeHandler {

    /** Handler called before node execution */
    var beforeNodeHandler: BeforeNodeHandler = BeforeNodeHandler { _, _, _ -> }

    /** Handler called after node execution */
    var afterNodeHandler: AfterNodeHandler = AfterNodeHandler { _, _, _, _ -> }
}

/**
 * Handler for intercepting node execution before it starts.
 */
fun interface BeforeNodeHandler {
    /**
     * Called before a node is executed.
     *
     * @param node The node that will be executed
     * @param context The stage context in which the node is executing
     * @param input The input data for the node
     */
    suspend fun handle(
        node: LocalAgentNode<*, *>,
        context: LocalAgentStageContext,
        input: Any?
    )
}

/**
 * Handler for intercepting node execution after it completes.
 */
fun interface AfterNodeHandler {
    /**
     * Called after a node has been executed.
     *
     * @param node The node that was executed
     * @param context The stage context in which the node executed
     * @param input The input data that was provided to the node
     * @param output The output data produced by the node
     */
    suspend fun handle(
        node: LocalAgentNode<*, *>,
        context: LocalAgentStageContext,
        input: Any?,
        output: Any?
    )
}