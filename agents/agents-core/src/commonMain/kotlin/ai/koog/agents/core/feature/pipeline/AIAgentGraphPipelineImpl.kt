@file:OptIn(InternalAgentsApi::class)

package ai.koog.agents.core.feature.pipeline

import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.context.AIAgentGraphContextBase
import ai.koog.agents.core.agent.entity.AIAgentNodeBase
import ai.koog.agents.core.agent.entity.AIAgentStorageKey
import ai.koog.agents.core.agent.entity.AIAgentSubgraph
import ai.koog.agents.core.agent.execution.AgentExecutionInfo
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.feature.AIAgentGraphFeature
import ai.koog.agents.core.feature.handler.node.NodeExecutionCompletedContext
import ai.koog.agents.core.feature.handler.node.NodeExecutionCompletedHandler
import ai.koog.agents.core.feature.handler.node.NodeExecutionEventHandler
import ai.koog.agents.core.feature.handler.node.NodeExecutionFailedContext
import ai.koog.agents.core.feature.handler.node.NodeExecutionFailedHandler
import ai.koog.agents.core.feature.handler.node.NodeExecutionStartingContext
import ai.koog.agents.core.feature.handler.node.NodeExecutionStartingHandler
import ai.koog.agents.core.feature.handler.subgraph.SubgraphExecutionCompletedContext
import ai.koog.agents.core.feature.handler.subgraph.SubgraphExecutionCompletedHandler
import ai.koog.agents.core.feature.handler.subgraph.SubgraphExecutionEventHandler
import ai.koog.agents.core.feature.handler.subgraph.SubgraphExecutionFailedContext
import ai.koog.agents.core.feature.handler.subgraph.SubgraphExecutionFailedHandler
import ai.koog.agents.core.feature.handler.subgraph.SubgraphExecutionStartingContext
import ai.koog.agents.core.feature.handler.subgraph.SubgraphExecutionStartingHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.datetime.Clock
import kotlin.reflect.KType

internal class AIAgentGraphPipelineImpl(
    agentConfig: AIAgentConfig,
    clock: Clock = Clock.System,
    basePipelineDelegate: AIAgentPipelineImpl
) : AIAgentGraphPipelineAPI, AIAgentPipelineAPI by basePipelineDelegate {
    /**
     * Map of node execution handlers registered for features.
     */
    private val executeNodeHandlers: MutableMap<AIAgentStorageKey<*>, NodeExecutionEventHandler> = mutableMapOf()

    /**
     * Map of subgraph execution handlers registered for features.
     */
    private val executeSubgraphHandlers: MutableMap<AIAgentStorageKey<*>, SubgraphExecutionEventHandler> =
        mutableMapOf()

    private val registeredFeatures: MutableMap<AIAgentStorageKey<*>, RegisteredFeature> = mutableMapOf()
    private val featurePrepareDispatcher = Dispatchers.Default.limitedParallelism(5)

    //region Trigger Node Handlers

    public override suspend fun onNodeExecutionStarting(
        eventId: String,
        executionInfo: AgentExecutionInfo,
        node: AIAgentNodeBase<*, *>,
        context: AIAgentGraphContextBase,
        input: Any?,
        inputType: KType
    ) {
        val eventContext = NodeExecutionStartingContext(eventId, executionInfo, node, context, input, inputType)
        executeNodeHandlers.values.forEach { handler -> handler.nodeExecutionStartingHandler.handle(eventContext) }
    }

    public override suspend fun onNodeExecutionCompleted(
        eventId: String,
        executionInfo: AgentExecutionInfo,
        node: AIAgentNodeBase<*, *>,
        context: AIAgentGraphContextBase,
        input: Any?,
        inputType: KType,
        output: Any?,
        outputType: KType
    ) {
        val eventContext =
            NodeExecutionCompletedContext(eventId, executionInfo, node, context, input, inputType, output, outputType)
        executeNodeHandlers.values.forEach { handler -> handler.nodeExecutionCompletedHandler.handle(eventContext) }
    }

    public override suspend fun onNodeExecutionFailed(
        eventId: String,
        executionInfo: AgentExecutionInfo,
        node: AIAgentNodeBase<*, *>,
        context: AIAgentGraphContextBase,
        input: Any?,
        inputType: KType,
        throwable: Throwable
    ) {
        val eventContext =
            NodeExecutionFailedContext(eventId, executionInfo, node, context, input, inputType, throwable)
        executeNodeHandlers.values.forEach { handler -> handler.nodeExecutionFailedHandler.handle(eventContext) }
    }

    //endregion Trigger Node Handlers

    //region Interceptors

    public override suspend fun onSubgraphExecutionStarting(
        eventId: String,
        executionInfo: AgentExecutionInfo,
        subgraph: AIAgentSubgraph<*, *>,
        context: AIAgentGraphContextBase,
        input: Any?,
        inputType: KType
    ) {
        val eventContext = SubgraphExecutionStartingContext(eventId, executionInfo, subgraph, context, input, inputType)
        executeSubgraphHandlers.values.forEach { handler -> handler.subgraphExecutionStartingHandler.handle(eventContext) }
    }

    public override suspend fun onSubgraphExecutionCompleted(
        eventId: String,
        executionInfo: AgentExecutionInfo,
        subgraph: AIAgentSubgraph<*, *>,
        context: AIAgentGraphContextBase,
        input: Any?,
        inputType: KType,
        output: Any?,
        outputType: KType
    ) {
        val eventContext =
            SubgraphExecutionCompletedContext(
                eventId,
                executionInfo,
                subgraph,
                context,
                input,
                output,
                inputType,
                outputType
            )
        executeSubgraphHandlers.values.forEach { handler ->
            handler.subgraphExecutionCompletedHandler.handle(
                eventContext
            )
        }
    }

    public override suspend fun onSubgraphExecutionFailed(
        eventId: String,
        executionInfo: AgentExecutionInfo,
        subgraph: AIAgentSubgraph<*, *>,
        context: AIAgentGraphContextBase,
        input: Any?,
        inputType: KType,
        throwable: Throwable
    ) {
        val eventContext =
            SubgraphExecutionFailedContext(eventId, executionInfo, subgraph, context, input, inputType, throwable)
        executeSubgraphHandlers.values.forEach { handler -> handler.subgraphExecutionFailedHandler.handle(eventContext) }
    }

    public override fun interceptNodeExecutionStarting(
        feature: AIAgentGraphFeature<*, *>,
        handle: suspend (eventContext: NodeExecutionStartingContext) -> Unit
    ) {
        val handler = executeNodeHandlers.getOrPut(feature.key) { NodeExecutionEventHandler() }

        handler.nodeExecutionStartingHandler = NodeExecutionStartingHandler(
            function = createConditionalHandler(feature, handle)
        )
    }

    public override fun interceptNodeExecutionCompleted(
        feature: AIAgentGraphFeature<*, *>,
        handle: suspend (eventContext: NodeExecutionCompletedContext) -> Unit
    ) {
        val handler = executeNodeHandlers.getOrPut(feature.key) { NodeExecutionEventHandler() }

        handler.nodeExecutionCompletedHandler = NodeExecutionCompletedHandler(
            function = createConditionalHandler(feature, handle)
        )
    }

    public override fun interceptNodeExecutionFailed(
        feature: AIAgentGraphFeature<*, *>,
        handle: suspend (eventContext: NodeExecutionFailedContext) -> Unit
    ) {
        val handler = executeNodeHandlers.getOrPut(feature.key) { NodeExecutionEventHandler() }

        handler.nodeExecutionFailedHandler = NodeExecutionFailedHandler(
            function = createConditionalHandler(feature, handle)
        )
    }

    public override fun interceptSubgraphExecutionStarting(
        feature: AIAgentGraphFeature<*, *>,
        handle: suspend (eventContext: SubgraphExecutionStartingContext) -> Unit
    ) {
        val handler = executeSubgraphHandlers.getOrPut(feature.key) { SubgraphExecutionEventHandler() }

        handler.subgraphExecutionStartingHandler = SubgraphExecutionStartingHandler(
            function = createConditionalHandler(feature, handle)
        )
    }

    public override fun interceptSubgraphExecutionCompleted(
        feature: AIAgentGraphFeature<*, *>,
        handle: suspend (eventContext: SubgraphExecutionCompletedContext) -> Unit
    ) {
        val handler = executeSubgraphHandlers.getOrPut(feature.key) { SubgraphExecutionEventHandler() }

        handler.subgraphExecutionCompletedHandler = SubgraphExecutionCompletedHandler(
            function = createConditionalHandler(feature, handle)
        )
    }

    public override fun interceptSubgraphExecutionFailed(
        feature: AIAgentGraphFeature<*, *>,
        handle: suspend (eventContext: SubgraphExecutionFailedContext) -> Unit
    ) {
        val handler = executeSubgraphHandlers.getOrPut(feature.key) { SubgraphExecutionEventHandler() }

        handler.subgraphExecutionFailedHandler = SubgraphExecutionFailedHandler(
            function = createConditionalHandler(feature, handle)
        )
    }

    //endregion Interceptors
}
