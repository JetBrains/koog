package ai.koog.agents.core.feature.pipeline

import ai.koog.agents.annotations.JavaAPI
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.environment.AIAgentEnvironment
import ai.koog.agents.core.feature.AIAgentFeature
import ai.koog.agents.core.feature.handler.agent.AgentClosingContext
import ai.koog.agents.core.feature.handler.agent.AgentCompletedContext
import ai.koog.agents.core.feature.handler.agent.AgentEnvironmentTransformingContext
import ai.koog.agents.core.feature.handler.agent.AgentExecutionFailedContext
import ai.koog.agents.core.feature.handler.agent.AgentStartingContext
import ai.koog.agents.core.feature.handler.llm.LLMCallCompletedContext
import ai.koog.agents.core.feature.handler.llm.LLMCallFailedContext
import ai.koog.agents.core.feature.handler.llm.LLMCallStartingContext
import ai.koog.agents.core.feature.handler.strategy.StrategyCompletedContext
import ai.koog.agents.core.feature.handler.strategy.StrategyStartingContext
import ai.koog.agents.core.feature.handler.streaming.LLMStreamingCompletedContext
import ai.koog.agents.core.feature.handler.streaming.LLMStreamingFailedContext
import ai.koog.agents.core.feature.handler.streaming.LLMStreamingFrameReceivedContext
import ai.koog.agents.core.feature.handler.streaming.LLMStreamingStartingContext
import ai.koog.agents.core.feature.handler.tool.ToolCallCompletedContext
import ai.koog.agents.core.feature.handler.tool.ToolCallFailedContext
import ai.koog.agents.core.feature.handler.tool.ToolCallStartingContext
import ai.koog.agents.core.feature.handler.tool.ToolValidationFailedContext
import ai.koog.utils.annotations.InternalKoogUtils
import ai.koog.utils.concurrency.withContextReentrant
import ai.koog.utils.time.KoogClock

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
public actual abstract class AIAgentPipeline actual constructor(
    agentConfig: AIAgentConfig,
    clock: KoogClock
) : AIAgentPipelineAPI by AIAgentPipelineImpl(agentConfig, clock) {
    // JVM Unique Interceptors

    /**
     * Intercepts environment creation to allow features to modify or enhance the agent environment.
     *
     * This overload is JVM-friendly and accepts an async transformer.
     *
     * @param feature The feature associated with this transformer;
     * @param transform An async transformer that takes the transforming context and the current environment
     *                  and returns a possibly modified environment.
     *
     * Example (Java):
     * pipeline.interceptEnvironmentCreated(feature, (ctx, environment) -> {
     *     // Modify the environment and return a CompletionStage
     *     return java.util.concurrent.CompletableFuture.completedFuture(environment);
     * });
     */
    @JavaAPI
    @JvmName("interceptEnvironmentCreated")
    public fun interceptEnvironmentCreatedBlocking(
        feature: AIAgentFeature<*, *>,
        transform: TransformInterceptor<AgentEnvironmentTransformingContext, AIAgentEnvironment>
    ) {
        interceptEnvironmentCreated(feature) { ctx, environment ->
            transform.transform(ctx, environment)
        }
    }

    /**
     * Intercepts on before an agent started to modify or enhance the agent.
     *
     * JVM-friendly overload that accepts an async interceptor.
     *
     * Example (Java):
     * pipeline.interceptAgentStarting(feature, eventContext -> {
     *     // Inspect agent stages
     *     return java.util.concurrent.CompletableFuture.completedFuture(null);
     * });
     */
    @JavaAPI
    @JvmName("interceptAgentStarting")
    public fun interceptAgentStartingBlocking(
        feature: AIAgentFeature<*, *>,
        handle: Interceptor<AgentStartingContext>
    ) {
        interceptAgentStarting(feature) { ctx ->
            @OptIn(InternalKoogUtils::class, InternalAgentsApi::class)
            withContextReentrant(config.strategyDispatcher) {
                handle.intercept(ctx)
            }
        }
    }

    /**
     * Intercepts the completion of an agent's operation and assigns a custom handler to process the result.
     *
     * JVM-friendly overload that accepts an async interceptor.
     *
     * Example (Java):
     * pipeline.interceptAgentCompleted(feature, eventContext -> {
     *     // Handle completion
     *     return java.util.concurrent.CompletableFuture.completedFuture(null);
     * });
     */
    @JavaAPI
    @JvmName("interceptAgentCompleted")
    public fun interceptAgentCompletedBlocking(
        feature: AIAgentFeature<*, *>,
        handle: Interceptor<AgentCompletedContext>
    ) {
        interceptAgentCompleted(feature) { ctx ->
            @OptIn(InternalKoogUtils::class, InternalAgentsApi::class)
            withContextReentrant(config.strategyDispatcher) {
                handle.intercept(ctx)
            }
        }
    }

    /**
     * Intercepts and handles errors occurring during the execution of an AI agent's strategy.
     *
     * JVM-friendly overload that accepts an async interceptor.
     *
     * Example (Java):
     * pipeline.interceptAgentExecutionFailed(feature, eventContext -> {
     *     // Handle the error
     *     return java.util.concurrent.CompletableFuture.completedFuture(null);
     * });
     */
    @JavaAPI
    @JvmName("interceptAgentExecutionFailed")
    public fun interceptAgentExecutionFailedBlocking(
        feature: AIAgentFeature<*, *>,
        handle: Interceptor<AgentExecutionFailedContext>
    ) {
        interceptAgentExecutionFailed(feature) { ctx ->
            @OptIn(InternalKoogUtils::class, InternalAgentsApi::class)
            withContextReentrant(config.strategyDispatcher) {
                handle.intercept(ctx)
            }
        }
    }

    /**
     * Intercepts and sets a handler to be invoked before an agent is closed.
     *
     * JVM-friendly overload that accepts an async interceptor.
     *
     * Example (Java):
     * pipeline.interceptAgentClosing(feature, eventContext -> {
     *     // Pre-close actions
     *     return java.util.concurrent.CompletableFuture.completedFuture(null);
     * });
     */
    @JavaAPI
    @JvmName("interceptAgentClosing")
    public fun interceptAgentClosingBlocking(
        feature: AIAgentFeature<*, *>,
        handle: Interceptor<AgentClosingContext>
    ) {
        interceptAgentClosing(feature) { ctx ->
            @OptIn(InternalKoogUtils::class, InternalAgentsApi::class)
            withContextReentrant(config.strategyDispatcher) {
                handle.intercept(ctx)
            }
        }
    }

    /**
     * Intercepts starting strategy event to perform actions when an agent strategy begins execution.
     *
     * JVM-friendly overload that accepts an async interceptor.
     *
     * Example (Java):
     * pipeline.interceptStrategyStarting(feature, event -> {
     *     // Strategy has been started
     *     return java.util.concurrent.CompletableFuture.completedFuture(null);
     * });
     */
    @JavaAPI
    @JvmName("interceptStrategyStarting")
    public fun interceptStrategyStartingBlocking(
        feature: AIAgentFeature<*, *>,
        handle: Interceptor<StrategyStartingContext>
    ) {
        interceptStrategyStarting(feature) { ctx ->
            @OptIn(InternalKoogUtils::class, InternalAgentsApi::class)
            withContextReentrant(config.strategyDispatcher) {
                handle.intercept(ctx)
            }
        }
    }

    /**
     * Sets up an interceptor to handle the completion of a strategy for the given feature.
     *
     * JVM-friendly overload that accepts an async interceptor.
     *
     * Example (Java):
     * pipeline.interceptStrategyCompleted(feature, event -> {
     *     // Strategy completed
     *     return java.util.concurrent.CompletableFuture.completedFuture(null);
     * });
     */
    @JavaAPI
    @JvmName("interceptStrategyCompleted")
    public fun interceptStrategyCompletedBlocking(
        feature: AIAgentFeature<*, *>,
        handle: Interceptor<StrategyCompletedContext>
    ) {
        interceptStrategyCompleted(feature) { ctx ->
            @OptIn(InternalKoogUtils::class, InternalAgentsApi::class)
            withContextReentrant(config.strategyDispatcher) {
                handle.intercept(ctx)
            }
        }
    }

    /**
     * Intercepts LLM calls before they are made to modify or log the prompt.
     *
     * JVM-friendly overload that accepts an async interceptor.
     *
     * Example (Java):
     * pipeline.interceptLLMCallStarting(feature, eventContext -> {
     *     // About to call LLM
     *     return java.util.concurrent.CompletableFuture.completedFuture(null);
     * });
     */
    @JavaAPI
    @JvmName("interceptLLMCallStarting")
    public fun interceptLLMCallStartingBlocking(
        feature: AIAgentFeature<*, *>,
        handle: Interceptor<LLMCallStartingContext>
    ) {
        interceptLLMCallStarting(feature) { ctx ->
            @OptIn(InternalKoogUtils::class, InternalAgentsApi::class)
            withContextReentrant(config.strategyDispatcher) {
                handle.intercept(ctx)
            }
        }
    }

    /**
     * Intercepts LLM calls after they are made to process or log the response.
     *
     * JVM-friendly overload that accepts an async interceptor.
     *
     * Example (Java):
     * pipeline.interceptLLMCallCompleted(feature, eventContext -> {
     *     // Process response
     *     return java.util.concurrent.CompletableFuture.completedFuture(null);
     * });
     */
    @JavaAPI
    @JvmName("interceptLLMCallCompleted")
    public fun interceptLLMCallCompletedBlocking(
        feature: AIAgentFeature<*, *>,
        handle: Interceptor<LLMCallCompletedContext>
    ) {
        interceptLLMCallCompleted(feature) { ctx ->
            @OptIn(InternalKoogUtils::class, InternalAgentsApi::class)
            withContextReentrant(config.strategyDispatcher) {
                handle.intercept(ctx)
            }
        }
    }

    /**
     * Intercepts a failed call to the Language Learning Model (LLM) within a specific AI agent feature
     * and delegates the handling of the failure context to the provided interceptor.
     *
     * JVM-friendly overload that accepts an async interceptor.
     *
     * Example (Java):
     * pipeline.interceptLLMCallFailed(feature, eventContext -> {
     *     // Process failure
     *     return java.util.concurrent.CompletableFuture.completedFuture(null);
     * });
     */
    @JavaAPI
    @JvmName("interceptLLMCallFailed")
    public fun interceptLLMCallFailedBlocking(
        feature: AIAgentFeature<*, *>,
        handle: Interceptor<LLMCallFailedContext>
    ) {
        interceptLLMCallFailed(feature) { ctx ->
            @OptIn(InternalKoogUtils::class, InternalAgentsApi::class)
            withContextReentrant(config.strategyDispatcher) {
                handle.intercept(ctx)
            }
        }
    }

    /**
     * Intercepts streaming operations before they begin to modify or log the streaming request.
     *
     * JVM-friendly overload that accepts an async interceptor.
     *
     * Example (Java):
     * pipeline.interceptLLMStreamingStarting(feature, eventContext -> {
     *     // About to start streaming
     *     return java.util.concurrent.CompletableFuture.completedFuture(null);
     * });
     */
    @JavaAPI
    @JvmName("interceptLLMStreamingStarting")
    public fun interceptLLMStreamingStartingBlocking(
        feature: AIAgentFeature<*, *>,
        handle: Interceptor<LLMStreamingStartingContext>
    ) {
        interceptLLMStreamingStarting(feature) { ctx ->
            @OptIn(InternalKoogUtils::class, InternalAgentsApi::class)
            withContextReentrant(config.strategyDispatcher) {
                handle.intercept(ctx)
            }
        }
    }

    /**
     * Intercepts stream frames as they are received during the streaming process.
     *
     * JVM-friendly overload that accepts an async interceptor.
     *
     * Example (Java):
     * pipeline.interceptLLMStreamingFrameReceived(feature, eventContext -> {
     *     // Handle stream frame
     *     return java.util.concurrent.CompletableFuture.completedFuture(null);
     * });
     */
    @JavaAPI
    @JvmName("interceptLLMStreamingFrameReceived")
    public fun interceptLLMStreamingFrameReceivedBlocking(
        feature: AIAgentFeature<*, *>,
        handle: Interceptor<LLMStreamingFrameReceivedContext>
    ) {
        interceptLLMStreamingFrameReceived(feature) { ctx ->
            @OptIn(InternalKoogUtils::class, InternalAgentsApi::class)
            withContextReentrant(config.strategyDispatcher) {
                handle.intercept(ctx)
            }
        }
    }

    /**
     * Intercepts errors during the streaming process.
     *
     * JVM-friendly overload that accepts an async interceptor.
     *
     * Example (Java):
     * pipeline.interceptLLMStreamingFailed(feature, eventContext -> {
     *     // Handle streaming error
     *     return java.util.concurrent.CompletableFuture.completedFuture(null);
     * });
     */
    @JavaAPI
    @JvmName("interceptLLMStreamingFailed")
    public fun interceptLLMStreamingFailedBlocking(
        feature: AIAgentFeature<*, *>,
        handle: Interceptor<LLMStreamingFailedContext>
    ) {
        interceptLLMStreamingFailed(feature) { ctx ->
            @OptIn(InternalKoogUtils::class, InternalAgentsApi::class)
            withContextReentrant(config.strategyDispatcher) {
                handle.intercept(ctx)
            }
        }
    }

    /**
     * Intercepts streaming operations after they complete to perform post-processing or cleanup.
     *
     * JVM-friendly overload that accepts an async interceptor.
     *
     * Example (Java):
     * pipeline.interceptLLMStreamingCompleted(feature, eventContext -> {
     *     // Streaming completed
     *     return java.util.concurrent.CompletableFuture.completedFuture(null);
     * });
     */
    @JavaAPI
    @JvmName("interceptLLMStreamingCompleted")
    public fun interceptLLMStreamingCompletedBlocking(
        feature: AIAgentFeature<*, *>,
        handle: Interceptor<LLMStreamingCompletedContext>
    ) {
        interceptLLMStreamingCompleted(feature) { ctx ->
            @OptIn(InternalKoogUtils::class, InternalAgentsApi::class)
            withContextReentrant(config.strategyDispatcher) {
                handle.intercept(ctx)
            }
        }
    }

    /**
     * Intercepts and handles tool calls for the specified feature.
     *
     * JVM-friendly overload that accepts an async interceptor.
     *
     * Example (Java):
     * pipeline.interceptToolCallStarting(feature, eventContext -> {
     *     // Process tool call
     *     return java.util.concurrent.CompletableFuture.completedFuture(null);
     * });
     */
    @JavaAPI
    @JvmName("interceptToolCallStarting")
    public fun interceptToolCallStartingBlocking(
        feature: AIAgentFeature<*, *>,
        handle: Interceptor<ToolCallStartingContext>
    ) {
        interceptToolCallStarting(feature) { ctx ->
            @OptIn(InternalKoogUtils::class, InternalAgentsApi::class)
            withContextReentrant(config.strategyDispatcher) {
                handle.intercept(ctx)
            }
        }
    }

    /**
     * Intercepts validation errors encountered during the execution of tools associated with the specified feature.
     *
     * JVM-friendly overload that accepts an async interceptor.
     *
     * Example (Java):
     * pipeline.interceptToolValidationFailed(feature, eventContext -> {
     *     // Handle validation failure
     *     return java.util.concurrent.CompletableFuture.completedFuture(null);
     * });
     */
    @JavaAPI
    @JvmName("interceptToolValidationFailed")
    public fun interceptToolValidationFailedBlocking(
        feature: AIAgentFeature<*, *>,
        handle: Interceptor<ToolValidationFailedContext>
    ) {
        interceptToolValidationFailed(feature) { ctx ->
            @OptIn(InternalKoogUtils::class, InternalAgentsApi::class)
            withContextReentrant(config.strategyDispatcher) {
                handle.intercept(ctx)
            }
        }
    }

    /**
     * Sets up an interception mechanism to handle tool call failures for a specific feature.
     *
     * JVM-friendly overload that accepts an async interceptor.
     *
     * Example (Java):
     * pipeline.interceptToolCallFailed(feature, eventContext -> {
     *     // Handle tool call failure
     *     return java.util.concurrent.CompletableFuture.completedFuture(null);
     * });
     */
    @JavaAPI
    @JvmName("interceptToolCallFailed")
    public fun interceptToolCallFailedBlocking(
        feature: AIAgentFeature<*, *>,
        handle: Interceptor<ToolCallFailedContext>
    ) {
        interceptToolCallFailed(feature) { ctx ->
            @OptIn(InternalKoogUtils::class, InternalAgentsApi::class)
            withContextReentrant(config.strategyDispatcher) {
                handle.intercept(ctx)
            }
        }
    }

    /**
     * Intercepts the result of a tool call with a custom handler for a specific feature.
     *
     * JVM-friendly overload that accepts an async interceptor.
     *
     * Example (Java):
     * pipeline.interceptToolCallCompleted(feature, eventContext -> {
     *     // Handle tool call result
     *     return java.util.concurrent.CompletableFuture.completedFuture(null);
     * });
     */
    @JavaAPI
    @JvmName("interceptToolCallCompleted")
    public fun interceptToolCallCompletedBlocking(
        feature: AIAgentFeature<*, *>,
        handle: Interceptor<ToolCallCompletedContext>
    ) {
        interceptToolCallCompleted(feature) { ctx ->
            @OptIn(InternalKoogUtils::class, InternalAgentsApi::class)
            withContextReentrant(config.strategyDispatcher) {
                handle.intercept(ctx)
            }
        }
    }
}
