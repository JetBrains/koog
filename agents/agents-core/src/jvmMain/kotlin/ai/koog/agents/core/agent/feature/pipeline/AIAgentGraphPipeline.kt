@file:Suppress("MissingKDocForPublicAPI", "EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
@file:OptIn(InternalAgentsApi::class)

package ai.koog.agents.core.feature.pipeline

import ai.koog.agents.annotations.JavaAPI
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.feature.AIAgentGraphFeature
import ai.koog.agents.core.feature.config.FeatureConfig
import ai.koog.agents.core.feature.handler.node.NodeExecutionCompletedContext
import ai.koog.agents.core.feature.handler.node.NodeExecutionFailedContext
import ai.koog.agents.core.feature.handler.node.NodeExecutionStartingContext
import ai.koog.agents.core.feature.handler.subgraph.SubgraphExecutionCompletedContext
import ai.koog.agents.core.feature.handler.subgraph.SubgraphExecutionFailedContext
import ai.koog.agents.core.feature.handler.subgraph.SubgraphExecutionStartingContext
import ai.koog.agents.core.utils.submitToMainDispatcher
import kotlinx.datetime.Clock

public actual open class AIAgentGraphPipeline @JvmOverloads actual constructor(
    agentConfig: AIAgentConfig,
    clock: Clock,
    private val basePipelineDelegate: AIAgentPipelineImpl
) : AIAgentPipeline(agentConfig, clock),
    AIAgentGraphPipelineAPI by AIAgentGraphPipelineImpl(agentConfig, clock, basePipelineDelegate) {

    public actual fun <TConfig : FeatureConfig, TFeatureImpl : Any> install(
        feature: AIAgentGraphFeature<TConfig, TFeatureImpl>,
        configure: TConfig.() -> Unit,
    ) {
        val featureConfig = feature.createInitialConfig().apply { configure() }
        val featureImpl = feature.install(
            config = featureConfig,
            pipeline = this,
        )

        basePipelineDelegate.install(feature.key, featureConfig, featureImpl)
    }

    /**
     * Intercepts node execution before it starts.
     *
     * @param feature The feature associated with this handler.
     * @param handle The handler that processes before-node events.
     *
     * Example:
     * ```
     * pipeline.interceptNodeExecutionStarting(feature, eventContext -> {
     *     logger.info("Node " + eventContext.getNode().getName()
     *         + " is about to execute with input: " + eventContext.getInput());
     * });
     * ```
     */
    @JavaAPI
    public fun interceptNodeExecutionStarting(
        feature: AIAgentGraphFeature<*, *>,
        handle: Interceptor<NodeExecutionStartingContext>
    ) {
        interceptNodeExecutionStarting(feature) {
            config.submitToMainDispatcher {
                handle.intercept(it)
            }
        }
    }

    /**
     * Intercepts node execution after it is successfully completed.
     *
     * @param feature The feature associated with this handler.
     * @param handle The handler that processes after-node completion events.
     *
     * Example:
     * ```
     * pipeline.interceptNodeExecutionCompleted(feature, eventContext -> {
     *     logger.info("Node " + eventContext.getNode().getName()
     *         + " completed with output: " + eventContext.getOutput());
     * });
     * ```
     */
    @JavaAPI
    public fun interceptNodeExecutionCompleted(
        feature: AIAgentGraphFeature<*, *>,
        handle: Interceptor<NodeExecutionCompletedContext>
    ) {
        interceptNodeExecutionCompleted(feature) {
            config.submitToMainDispatcher {
                handle.intercept(it)
            }
        }
    }

    /**
     * Intercepts node execution when it fails.
     *
     * @param feature The feature associated with this handler.
     * @param handle The handler that processes node-failure events.
     *
     * Example:
     * ```
     * pipeline.interceptNodeExecutionFailed(feature, eventContext -> {
     *     logger.warn("Node " + eventContext.getNode().getName()
     *         + " failed with error: " + eventContext.getThrowable().getMessage());
     * });
     * ```
     */
    @JavaAPI
    public fun interceptNodeExecutionFailed(
        feature: AIAgentGraphFeature<*, *>,
        handle: Interceptor<NodeExecutionFailedContext>
    ) {
        interceptNodeExecutionFailed(feature) {
            config.submitToMainDispatcher {
                handle.intercept(it)
            }
        }
    }

    /**
     * Intercepts subgraph execution before it starts.
     *
     * @param feature The feature associated with this handler.
     * @param handle The handler that processes before-subgraph events.
     *
     * Example:
     * ```
     * pipeline.interceptSubgraphExecutionStarting(feature, eventContext -> {
     *     logger.info("Subgraph " + eventContext.getSubgraph().getName()
     *         + " is about to execute with input: " + eventContext.getInput());
     * });
     * ```
     */
    @JavaAPI
    public fun interceptSubgraphExecutionStarting(
        feature: AIAgentGraphFeature<*, *>,
        handle: Interceptor<SubgraphExecutionStartingContext>
    ) {
        interceptSubgraphExecutionStarting(feature) {
            config.submitToMainDispatcher {
                handle.intercept(it)
            }
        }
    }

    /**
     * Intercepts subgraph execution after it is successfully completed.
     *
     * @param feature The feature associated with this handler.
     * @param handle The handler that processes subgraph-completion events.
     *
     * Example:
     * ```
     * pipeline.interceptSubgraphExecutionCompleted(feature, eventContext -> {
     *     logger.info("Subgraph " + eventContext.getSubgraph().getName()
     *         + " completed with output: " + eventContext.getOutput());
     * });
     * ```
     */
    @JavaAPI
    public fun interceptSubgraphExecutionCompleted(
        feature: AIAgentGraphFeature<*, *>,
        handle: Interceptor<SubgraphExecutionCompletedContext>
    ) {
        interceptSubgraphExecutionCompleted(feature) {
            config.submitToMainDispatcher {
                handle.intercept(it)
            }
        }
    }

    /**
     * Intercepts subgraph execution when it fails.
     *
     * @param feature The feature associated with this handler.
     * @param handle The handler that processes subgraph-failure events.
     *
     * Example:
     * ```
     * pipeline.interceptSubgraphExecutionFailed(feature, eventContext -> {
     *     logger.warn("Subgraph " + eventContext.getSubgraph().getName()
     *         + " failed with error: " + eventContext.getThrowable().getMessage());
     * });
     * ```
     */
    @JavaAPI
    public fun interceptSubgraphExecutionFailed(
        feature: AIAgentGraphFeature<*, *>,
        handle: Interceptor<SubgraphExecutionFailedContext>
    ) {
        interceptSubgraphExecutionFailed(feature) {
            config.submitToMainDispatcher {
                handle.intercept(it)
            }
        }
    }
}
