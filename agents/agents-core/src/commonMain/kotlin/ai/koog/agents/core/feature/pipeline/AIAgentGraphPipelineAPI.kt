package ai.koog.agents.core.feature.pipeline

import ai.koog.agents.core.feature.AIAgentGraphFeature
import ai.koog.agents.core.feature.handler.node.NodeExecutionCompletedContext
import ai.koog.agents.core.feature.handler.node.NodeExecutionFailedContext
import ai.koog.agents.core.feature.handler.node.NodeExecutionStartingContext
import ai.koog.agents.core.feature.handler.subgraph.SubgraphExecutionCompletedContext
import ai.koog.agents.core.feature.handler.subgraph.SubgraphExecutionFailedContext
import ai.koog.agents.core.feature.handler.subgraph.SubgraphExecutionStartingContext

/**
 * Public API surface for graph-specific pipeline operations (nodes and subgraphs).
 *
 * Implemented by both the common expect AIAgentGraphPipeline and the concrete implementation
 * AIAgentGraphPipelineImpl, and used by all platform actual classes via delegation.
 */
public interface AIAgentGraphPipelineAPI : AIAgentPipelineAPI {

    //region Interceptors

    /**
     * Registers an interceptor to handle events triggered before the execution of a node in the AI agent graph pipeline.
     *
     * @param feature The graph-specific feature that defines the context in which the interceptor applies.
     * @param handle A suspending function that will be invoked when a node execution is about to start.
     *               The function receives a [NodeExecutionStartingContext] containing detailed information about the event.
     */
    public fun interceptNodeExecutionStarting(
        feature: AIAgentGraphFeature<*, *>,
        handle: suspend (eventContext: NodeExecutionStartingContext) -> Unit
    )

    /**
     * Registers an interceptor to handle events triggered after the execution of a node in the AI agent graph pipeline.
     *
     * @param feature The graph-specific feature that defines the context in which the interceptor applies.
     * @param handle A suspending function that will be invoked when a node execution is completed.
     *               The function receives a [NodeExecutionCompletedContext] containing detailed information
     *               about the completed node execution.
     */
    public fun interceptNodeExecutionCompleted(
        feature: AIAgentGraphFeature<*, *>,
        handle: suspend (eventContext: NodeExecutionCompletedContext) -> Unit
    )

    /**
     * Registers an interceptor to handle events triggered when a node execution fails in the AI agent graph pipeline.
     *
     * @param feature The graph-specific feature that defines the context in which the interceptor applies.
     * @param handle A suspending function that will be invoked when a node execution fails.
     *               The function receives a [NodeExecutionFailedContext], which contains detailed information
     *               about the failed node execution.
     */
    public fun interceptNodeExecutionFailed(
        feature: AIAgentGraphFeature<*, *>,
        handle: suspend (eventContext: NodeExecutionFailedContext) -> Unit
    )

    /**
     * Registers an interceptor to handle events triggered before the execution of a subgraph
     * in the AI agent graph pipeline.
     *
     * @param feature The graph-specific feature that defines the context in which the interceptor applies.
     * @param handle A suspending function that will be invoked when a subgraph execution is about to start.
     *               The function receives a [SubgraphExecutionStartingContext] containing detailed
     *               information about the starting subgraph execution.
     */
    public fun interceptSubgraphExecutionStarting(
        feature: AIAgentGraphFeature<*, *>,
        handle: suspend (eventContext: SubgraphExecutionStartingContext) -> Unit
    )

    /**
     * Registers an interceptor to handle events triggered after the execution of a subgraph
     * in the AI agent graph pipeline.
     *
     * @param feature The graph-specific feature that defines the context in which the interceptor applies.
     * @param handle A suspending function that will be invoked when a subgraph execution is completed.
     *               The function receives a [SubgraphExecutionCompletedContext] containing detailed
     *               information about the completed subgraph execution.
     */
    public fun interceptSubgraphExecutionCompleted(
        feature: AIAgentGraphFeature<*, *>,
        handle: suspend (eventContext: SubgraphExecutionCompletedContext) -> Unit
    )

    /**
     * Registers an interceptor to handle events triggered when a subgraph execution fails
     * in the AI agent graph pipeline.
     *
     * @param feature The graph-specific feature that defines the context in which the interceptor applies.
     * @param handle A suspending function that will be invoked when a subgraph execution fails.
     *               The function receives a [SubgraphExecutionFailedContext], which contains detailed
     *               information about the failed subgraph execution.
     */
    public fun interceptSubgraphExecutionFailed(
        feature: AIAgentGraphFeature<*, *>,
        handle: suspend (eventContext: SubgraphExecutionFailedContext) -> Unit
    )

    //endregion Interceptors
}
