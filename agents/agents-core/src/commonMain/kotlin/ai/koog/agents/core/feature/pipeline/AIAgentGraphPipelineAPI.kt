@file:Suppress("MissingKDocForPublicAPI")

package ai.koog.agents.core.feature.pipeline

import ai.koog.agents.core.agent.context.AIAgentGraphContextBase
import ai.koog.agents.core.agent.entity.AIAgentNodeBase
import ai.koog.agents.core.agent.entity.AIAgentSubgraph
import ai.koog.agents.core.agent.execution.AgentExecutionInfo
import ai.koog.agents.core.feature.AIAgentGraphFeature
import ai.koog.agents.core.feature.handler.node.NodeExecutionCompletedContext
import ai.koog.agents.core.feature.handler.node.NodeExecutionFailedContext
import ai.koog.agents.core.feature.handler.node.NodeExecutionStartingContext
import ai.koog.agents.core.feature.handler.subgraph.SubgraphExecutionCompletedContext
import ai.koog.agents.core.feature.handler.subgraph.SubgraphExecutionFailedContext
import ai.koog.agents.core.feature.handler.subgraph.SubgraphExecutionStartingContext
import kotlin.reflect.KType

/**
 * Public API surface for graph-specific pipeline operations (nodes and subgraphs).
 *
 * Implemented by both the common expect AIAgentGraphPipeline and the concrete implementation
 * AIAgentGraphPipelineImpl, and used by all platform actual classes via delegation.
 */
public interface AIAgentGraphPipelineAPI : AIAgentPipelineAPI {

    //region Trigger Node Handlers
    public suspend fun onNodeExecutionStarting(
        eventId: String,
        executionInfo: AgentExecutionInfo,
        node: AIAgentNodeBase<*, *>,
        context: AIAgentGraphContextBase,
        input: Any?,
        inputType: KType
    )

    public suspend fun onNodeExecutionCompleted(
        eventId: String,
        executionInfo: AgentExecutionInfo,
        node: AIAgentNodeBase<*, *>,
        context: AIAgentGraphContextBase,
        input: Any?,
        inputType: KType,
        output: Any?,
        outputType: KType,
    )

    public suspend fun onNodeExecutionFailed(
        eventId: String,
        executionInfo: AgentExecutionInfo,
        node: AIAgentNodeBase<*, *>,
        context: AIAgentGraphContextBase,
        input: Any?,
        inputType: KType,
        throwable: Throwable
    )
    //endregion

    //region Trigger Subgraph Handlers
    public suspend fun onSubgraphExecutionStarting(
        eventId: String,
        executionInfo: AgentExecutionInfo,
        subgraph: AIAgentSubgraph<*, *>,
        context: AIAgentGraphContextBase,
        input: Any?,
        inputType: KType
    )

    public suspend fun onSubgraphExecutionCompleted(
        eventId: String,
        executionInfo: AgentExecutionInfo,
        subgraph: AIAgentSubgraph<*, *>,
        context: AIAgentGraphContextBase,
        input: Any?,
        inputType: KType,
        output: Any?,
        outputType: KType,
    )

    public suspend fun onSubgraphExecutionFailed(
        eventId: String,
        executionInfo: AgentExecutionInfo,
        subgraph: AIAgentSubgraph<*, *>,
        context: AIAgentGraphContextBase,
        input: Any?,
        inputType: KType,
        throwable: Throwable
    )
    //endregion

    //region Interceptors
    public fun interceptNodeExecutionStarting(
        feature: AIAgentGraphFeature<*, *>,
        handle: suspend (eventContext: NodeExecutionStartingContext) -> Unit
    )

    public fun interceptNodeExecutionCompleted(
        feature: AIAgentGraphFeature<*, *>,
        handle: suspend (eventContext: NodeExecutionCompletedContext) -> Unit
    )

    public fun interceptNodeExecutionFailed(
        feature: AIAgentGraphFeature<*, *>,
        handle: suspend (eventContext: NodeExecutionFailedContext) -> Unit
    )

    public fun interceptSubgraphExecutionStarting(
        feature: AIAgentGraphFeature<*, *>,
        handle: suspend (eventContext: SubgraphExecutionStartingContext) -> Unit
    )

    public fun interceptSubgraphExecutionCompleted(
        feature: AIAgentGraphFeature<*, *>,
        handle: suspend (eventContext: SubgraphExecutionCompletedContext) -> Unit
    )

    public fun interceptSubgraphExecutionFailed(
        feature: AIAgentGraphFeature<*, *>,
        handle: suspend (eventContext: SubgraphExecutionFailedContext) -> Unit
    )
    //endregion
}
