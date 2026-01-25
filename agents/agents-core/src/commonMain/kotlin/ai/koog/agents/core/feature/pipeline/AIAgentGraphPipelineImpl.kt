@file:OptIn(InternalAgentsApi::class)

package ai.koog.agents.core.feature.pipeline

import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.context.AIAgentGraphContextBase
import ai.koog.agents.core.agent.entity.AIAgentNodeBase
import ai.koog.agents.core.agent.entity.AIAgentSubgraph
import ai.koog.agents.core.agent.execution.AgentExecutionInfo
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.feature.AIAgentGraphFeature
import ai.koog.agents.core.feature.handler.AgentLifecycleEventType
import ai.koog.agents.core.feature.handler.node.NodeExecutionCompletedContext
import ai.koog.agents.core.feature.handler.node.NodeExecutionFailedContext
import ai.koog.agents.core.feature.handler.node.NodeExecutionStartingContext
import ai.koog.agents.core.feature.handler.subgraph.SubgraphExecutionCompletedContext
import ai.koog.agents.core.feature.handler.subgraph.SubgraphExecutionFailedContext
import ai.koog.agents.core.feature.handler.subgraph.SubgraphExecutionStartingContext
import kotlinx.datetime.Clock
import kotlin.reflect.KType

internal class AIAgentGraphPipelineImpl(
    agentConfig: AIAgentConfig,
    clock: Clock = Clock.System,
    private val basePipelineDelegate: AIAgentPipelineImpl
) : AIAgentGraphPipelineAPI, AIAgentPipelineAPI by basePipelineDelegate {

    //region Trigger Node Handlers

    public override suspend fun onNodeExecutionStarting(
        eventId: String,
        executionInfo: AgentExecutionInfo,
        node: AIAgentNodeBase<*, *>,
        context: AIAgentGraphContextBase,
        input: Any?,
        inputType: KType
    ) {
        basePipelineDelegate.invokeRegisteredHandlersForEvent(
            eventType = AgentLifecycleEventType.NodeExecutionStarting,
            context = NodeExecutionStartingContext(eventId, executionInfo, node, context, input, inputType)
        )
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
        basePipelineDelegate.invokeRegisteredHandlersForEvent(
            eventType = AgentLifecycleEventType.NodeExecutionCompleted,
            context = NodeExecutionCompletedContext(eventId, executionInfo, node, context, input, inputType, output, outputType)
        )
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
        basePipelineDelegate.invokeRegisteredHandlersForEvent(
            eventType = AgentLifecycleEventType.NodeExecutionFailed,
            context = NodeExecutionFailedContext(eventId, executionInfo, node, context, input, inputType, throwable)
        )
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
        basePipelineDelegate.invokeRegisteredHandlersForEvent(
            eventType = AgentLifecycleEventType.SubgraphExecutionStarting,
            context = SubgraphExecutionStartingContext(eventId, executionInfo, subgraph, context, input, inputType)
        )
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
        basePipelineDelegate.invokeRegisteredHandlersForEvent(
            eventType = AgentLifecycleEventType.SubgraphExecutionCompleted,
            context = SubgraphExecutionCompletedContext(
                eventId,
                executionInfo,
                subgraph,
                context,
                input,
                output,
                inputType,
                outputType
            )
        )
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
        basePipelineDelegate.invokeRegisteredHandlersForEvent(
            eventType = AgentLifecycleEventType.SubgraphExecutionFailed,
            context = SubgraphExecutionFailedContext(eventId, executionInfo, subgraph, context, input, inputType, throwable)
        )
    }

    public override fun interceptNodeExecutionStarting(
        feature: AIAgentGraphFeature<*, *>,
        handle: suspend (eventContext: NodeExecutionStartingContext) -> Unit
    ) {
        basePipelineDelegate.addHandlerForFeature(
            featureKey = feature.key,
            eventType = AgentLifecycleEventType.NodeExecutionStarting,
            handler = basePipelineDelegate.createConditionalHandler(feature, handle)
        )
    }

    public override fun interceptNodeExecutionCompleted(
        feature: AIAgentGraphFeature<*, *>,
        handle: suspend (eventContext: NodeExecutionCompletedContext) -> Unit
    ) {
        basePipelineDelegate.addHandlerForFeature(
            featureKey = feature.key,
            eventType = AgentLifecycleEventType.NodeExecutionCompleted,
            handler = basePipelineDelegate.createConditionalHandler(feature, handle)
        )
    }

    public override fun interceptNodeExecutionFailed(
        feature: AIAgentGraphFeature<*, *>,
        handle: suspend (eventContext: NodeExecutionFailedContext) -> Unit
    ) {
        basePipelineDelegate.addHandlerForFeature(
            featureKey = feature.key,
            eventType = AgentLifecycleEventType.NodeExecutionFailed,
            handler = basePipelineDelegate.createConditionalHandler(feature, handle)
        )
    }

    public override fun interceptSubgraphExecutionStarting(
        feature: AIAgentGraphFeature<*, *>,
        handle: suspend (eventContext: SubgraphExecutionStartingContext) -> Unit
    ) {
        basePipelineDelegate.addHandlerForFeature(
            featureKey = feature.key,
            eventType = AgentLifecycleEventType.SubgraphExecutionStarting,
            handler = basePipelineDelegate.createConditionalHandler(feature, handle)
        )
    }

    public override fun interceptSubgraphExecutionCompleted(
        feature: AIAgentGraphFeature<*, *>,
        handle: suspend (eventContext: SubgraphExecutionCompletedContext) -> Unit
    ) {
        basePipelineDelegate.addHandlerForFeature(
            featureKey = feature.key,
            eventType = AgentLifecycleEventType.SubgraphExecutionCompleted,
            handler = basePipelineDelegate.createConditionalHandler(feature, handle)
        )
    }

    public override fun interceptSubgraphExecutionFailed(
        feature: AIAgentGraphFeature<*, *>,
        handle: suspend (eventContext: SubgraphExecutionFailedContext) -> Unit
    ) {
        basePipelineDelegate.addHandlerForFeature(
            featureKey = feature.key,
            eventType = AgentLifecycleEventType.SubgraphExecutionFailed,
            handler = basePipelineDelegate.createConditionalHandler(feature, handle)
        )
    }

    //endregion Interceptors
}
