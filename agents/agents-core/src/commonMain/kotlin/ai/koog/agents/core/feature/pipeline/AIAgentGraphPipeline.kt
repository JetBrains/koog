package ai.koog.agents.core.feature.pipeline

import ai.koog.agents.core.agent.context.AIAgentGraphContextBase
import ai.koog.agents.core.agent.entity.AIAgentNodeBase
import ai.koog.agents.core.agent.entity.AIAgentStorageKey
import ai.koog.agents.core.agent.entity.AIAgentSubgraph
import ai.koog.agents.core.agent.execution.AgentExecutionInfo
import ai.koog.agents.core.feature.AIAgentGraphFeature
import ai.koog.agents.core.feature.config.FeatureConfig
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
import kotlinx.datetime.Clock
import kotlin.reflect.KType

/**
 * Represents a pipeline for AI agent graph execution, extending the functionality of `AIAgentPipeline`.
 * This class manages the execution of specific nodes in the pipeline using registered handlers.
 *
 * @property clock The clock used for time-based operations within the pipeline
 */
public class AIAgentGraphPipeline(clock: Clock = Clock.System) : AIAgentPipeline(clock) {

    /**
     * Map of node execution handlers registered for features.
     */
    private val executeNodeHandlers: MutableMap<AIAgentStorageKey<*>, NodeExecutionEventHandler> = mutableMapOf()

    /**
     * Map of subgraph execution handlers registered for features.
     */
    private val executeSubgraphHandlers: MutableMap<AIAgentStorageKey<*>, SubgraphExecutionEventHandler> = mutableMapOf()

    /**
     * Installs a feature into the pipeline with the provided configuration.
     *
     * This method initializes the feature with a custom configuration and registers it in the pipeline.
     * The feature's message processors are initialized during installation.
     *
     * @param TConfig The type of the feature configuration
     * @param TFeatureImpl The type of the feature being installed
     * @param feature The feature implementation to be installed
     * @param configure A lambda to customize the feature configuration
     */
    public fun <TConfig : FeatureConfig, TFeatureImpl : Any> install(
        feature: AIAgentGraphFeature<TConfig, TFeatureImpl>,
        configure: TConfig.() -> Unit,
    ) {
        val featureConfig = feature.createInitialConfig().apply { configure() }
        val featureImpl = feature.install(
            config = featureConfig,
            pipeline = this,
        )

        super.install(feature.key, featureConfig, featureImpl)
    }

    //region Trigger Node Handlers

    /**
     * Notifies all registered node handlers before a node is executed.
     *
     * @param executionInfo The execution information for the agent environment transformation event
     * @param node The node that is about to be executed
     * @param context The agent context in which the node is being executed
     * @param input The input data for the node execution
     * @param inputType The type of the input data provided to the node
     */
    public suspend fun onNodeExecutionStarting(
        executionInfo: AgentExecutionInfo,
        node: AIAgentNodeBase<*, *>,
        context: AIAgentGraphContextBase,
        input: Any?,
        inputType: KType
    ) {
        val eventContext = NodeExecutionStartingContext(executionInfo, node, context, input, inputType)
        executeNodeHandlers.values.forEach { handler -> handler.nodeExecutionStartingHandler.handle(eventContext) }
    }

    /**
     * Notifies all registered node handlers after a node has been executed.
     *
     * @param executionInfo The execution information for the agent environment transformation event
     * @param node The node that was executed
     * @param context The agent context in which the node was executed
     * @param input The input data that was provided to the node
     * @param inputType The type of the input data provided to the node
     * @param output The output data produced by the node execution
     * @param outputType The type of the output data produced by the node execution
     */
    public suspend fun onNodeExecutionCompleted(
        executionInfo: AgentExecutionInfo,
        node: AIAgentNodeBase<*, *>,
        context: AIAgentGraphContextBase,
        input: Any?,
        inputType: KType,
        output: Any?,
        outputType: KType,
    ) {
        val eventContext = NodeExecutionCompletedContext(executionInfo, node, context, input, inputType, output, outputType)
        executeNodeHandlers.values.forEach { handler -> handler.nodeExecutionCompletedHandler.handle(eventContext) }
    }

    /**
     * Handles errors occurring during the execution of a node by invoking all registered node execution error handlers.
     *
     * @param executionInfo The execution information for the agent environment transformation event
     * @param node The instance of the node where the error occurred.
     * @param context The context associated with the AI agent executing the node.
     * @param input The input data provided to the node.
     * @param inputType The type of the input data provided to the node.
     * @param throwable The exception or error that occurred during node execution.
     */
    public suspend fun onNodeExecutionFailed(
        executionInfo: AgentExecutionInfo,
        node: AIAgentNodeBase<*, *>,
        context: AIAgentGraphContextBase,
        input: Any?,
        inputType: KType,
        throwable: Throwable
    ) {
        val eventContext = NodeExecutionFailedContext(executionInfo, node, context, input, inputType, throwable)
        executeNodeHandlers.values.forEach { handler -> handler.nodeExecutionFailedHandler.handle(eventContext) }
    }

    //endregion Trigger Node Handlers

    //region Trigger Subgraph Handlers

    /**
     * Notifies all registered subgraph handlers before a subgraph is executed.
     *
     * @param executionInfo The execution information for the agent environment transformation event
     * @param subgraph The subgraph that is about to be executed.
     * @param context The agent context in which the subgraph is being executed.
     * @param input The input data for the subgraph execution.
     * @param inputType The type of the input data provided to the subgraph.
     */
    public suspend fun onSubgraphExecutionStarting(
        executionInfo: AgentExecutionInfo,
        subgraph: AIAgentSubgraph<*, *>,
        context: AIAgentGraphContextBase,
        input: Any?,
        inputType: KType
    ) {
        val eventContext = SubgraphExecutionStartingContext(executionInfo, subgraph, context, input, inputType)
        executeSubgraphHandlers.values.forEach { handler -> handler.subgraphExecutionStartingHandler.handle(eventContext) }
    }

    /**
     * Notifies all registered subgraph handlers after a subgraph has been executed.
     *
     * @param executionInfo The execution information for the agent environment transformation event
     * @param subgraph The subgraph that was executed.
     * @param context The agent context in which the subgraph was executed.
     * @param input The input data provided to the subgraph.
     * @param inputType The type of the input data provided to the subgraph.
     * @param output The output data produced by the subgraph execution.
     * @param outputType The type of the output data produced by the subgraph execution.
     */
    public suspend fun onSubgraphExecutionCompleted(
        executionInfo: AgentExecutionInfo,
        subgraph: AIAgentSubgraph<*, *>,
        context: AIAgentGraphContextBase,
        input: Any?,
        inputType: KType,
        output: Any?,
        outputType: KType,
    ) {
        val eventContext = SubgraphExecutionCompletedContext(executionInfo, subgraph, context, input, output, inputType, outputType)
        executeSubgraphHandlers.values.forEach { handler -> handler.subgraphExecutionCompletedHandler.handle(eventContext) }
    }

    /**
     * Notifies all registered subgraph handlers when a subgraph execution fails.
     *
     * @param executionInfo The execution information for the agent environment transformation event
     * @param subgraph The subgraph for which the execution failed.
     * @param context The agent context in which the subgraph execution occurred.
     * @param input The input data that was provided to the subgraph when it failed.
     * @param inputType The type of the input data provided to the subgraph.
     * @param throwable The exception or error that caused the subgraph execution to fail.
     */
    public suspend fun onSubgraphExecutionFailed(
        executionInfo: AgentExecutionInfo,
        subgraph: AIAgentSubgraph<*, *>,
        context: AIAgentGraphContextBase,
        input: Any?,
        inputType: KType,
        throwable: Throwable
    ) {
        val eventContext = SubgraphExecutionFailedContext(executionInfo, subgraph, context, input, inputType, throwable)
        executeSubgraphHandlers.values.forEach { handler -> handler.subgraphExecutionFailedHandler.handle(eventContext) }
    }

    //endregion Trigger Subgraph Handlers

    //region Interceptors

    /**
     * Intercepts node execution before it starts.
     *
     * @param feature The feature associated with this handler.
     * @param handle The handler that processes before-node events
     *
     * Example:
     * ```
     * pipeline.interceptNodeExecutionStarting(feature) { eventContext ->
     *     logger.info("Node ${eventContext.node.name} is about to execute with input: ${eventContext.input}")
     * }
     * ```
     */
    public fun interceptNodeExecutionStarting(
        feature: AIAgentGraphFeature<*, *>,
        handle: suspend (eventContext: NodeExecutionStartingContext) -> Unit
    ) {
        val handler = executeNodeHandlers.getOrPut(feature.key) { NodeExecutionEventHandler() }

        handler.nodeExecutionStartingHandler = NodeExecutionStartingHandler(
            function = createConditionalHandler(feature, handle)
        )
    }

    /**
     * Intercepts node execution after it completes.
     *
     * @param feature The feature associated with this handler.
     * @param handle The handler that processes after-node events
     *
     * Example:
     * ```
     * pipeline.interceptNodeExecutionCompleted(feature) { eventContext ->
     *     logger.info("Node ${eventContext.node.name} executed with input: ${eventContext.input} and produced output: ${eventContext.output}")
     * }
     * ```
     */
    public fun interceptNodeExecutionCompleted(
        feature: AIAgentGraphFeature<*, *>,
        handle: suspend (eventContext: NodeExecutionCompletedContext) -> Unit
    ) {
        val handler = executeNodeHandlers.getOrPut(feature.key) { NodeExecutionEventHandler() }

        handler.nodeExecutionCompletedHandler = NodeExecutionCompletedHandler(
            function = createConditionalHandler(feature, handle)
        )
    }

    /**
     * Intercepts and handles node execution errors for a given feature.
     *
     * @param feature The feature associated with this handler.
     * @param handle A suspend function that processes the node execution error.
     *
     * Example:
     * ```
     * pipeline.interceptNodeExecutionFailed(feature) { eventContext ->
     *     logger.error("Node ${eventContext.node.name} execution failed with error: ${eventContext.throwable}")
     * }
     * ```
     */
    public fun interceptNodeExecutionFailed(
        feature: AIAgentGraphFeature<*, *>,
        handle: suspend (eventContext: NodeExecutionFailedContext) -> Unit
    ) {
        val handler = executeNodeHandlers.getOrPut(feature.key) { NodeExecutionEventHandler() }

        handler.nodeExecutionFailedHandler = NodeExecutionFailedHandler(
            function = createConditionalHandler(feature, handle)
        )
    }

    /**
     * Intercepts the execution of a subgraph when it starts.
     *
     * @param feature The graph feature associated with the AI agent for which the subgraph execution is intercepted.
     * @param handle A suspendable lambda that handles the subgraph execution starting event context.
     *
     * Example:
     * ```
     * pipeline.interceptSubgraphExecutionStarting(feature) { eventContext ->
     *     logger.info("Subgraph ${eventContext.subgraph.name} is about to execute with input: ${eventContext.input}")
     * }
     * ```
     */
    public fun interceptSubgraphExecutionStarting(
        feature: AIAgentGraphFeature<*, *>,
        handle: suspend (eventContext: SubgraphExecutionStartingContext) -> Unit
    ) {
        val handler = executeSubgraphHandlers.getOrPut(feature.key) { SubgraphExecutionEventHandler() }

        handler.subgraphExecutionStartingHandler = SubgraphExecutionStartingHandler(
            function = createConditionalHandler(feature, handle)
        )
    }

    /**
     * Intercepts the completion of a subgraph execution and allows handling of the event.
     *
     * @param feature The AI agent graph feature that specifies the feature to intercept.
     * @param handle A suspendable function that handles the subgraph execution completion event,
     * taking the event context as a parameter.
     *
     * Example:
     * ```
     * pipeline.interceptSubgraphExecutionCompleted(feature) { eventContext ->
     *     logger.info("Subgraph ${eventContext.subgraph.name} executed with input: ${eventContext.input} and produced output: ${eventContext.output}")
     * }
     * ```
     */
    public fun interceptSubgraphExecutionCompleted(
        feature: AIAgentGraphFeature<*, *>,
        handle: suspend (eventContext: SubgraphExecutionCompletedContext) -> Unit
    ) {
        val handler = executeSubgraphHandlers.getOrPut(feature.key) { SubgraphExecutionEventHandler() }

        handler.subgraphExecutionCompletedHandler = SubgraphExecutionCompletedHandler(
            function = createConditionalHandler(feature, handle)
        )
    }

    /**
     * Intercepts and handles subgraph execution failures for a given feature.
     *
     * @param feature The feature associated with this handler.
     * @param handle A suspend function that processes the subgraph execution failure event.
     *
     * Example:
     * ```
     * pipeline.interceptSubgraphExecutionFailed(feature) { eventContext ->
     *     logger.error("Subgraph ${eventContext.subgraph.name} execution failed with error: ${eventContext.throwable}")
     * }
     * ```
     */
    public fun interceptSubgraphExecutionFailed(
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
