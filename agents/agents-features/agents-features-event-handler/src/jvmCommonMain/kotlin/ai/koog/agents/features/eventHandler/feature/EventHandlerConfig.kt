@file:Suppress("MissingKDocForPublicAPI")

package ai.koog.agents.features.eventHandler.feature

import ai.koog.agents.annotations.JavaAPI
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.feature.handler.agent.AgentClosingContext
import ai.koog.agents.core.feature.handler.agent.AgentCompletedContext
import ai.koog.agents.core.feature.handler.agent.AgentExecutionFailedContext
import ai.koog.agents.core.feature.handler.agent.AgentStartingContext
import ai.koog.agents.core.feature.handler.llm.LLMCallCompletedContext
import ai.koog.agents.core.feature.handler.llm.LLMCallStartingContext
import ai.koog.agents.core.feature.handler.node.NodeExecutionCompletedContext
import ai.koog.agents.core.feature.handler.node.NodeExecutionFailedContext
import ai.koog.agents.core.feature.handler.node.NodeExecutionStartingContext
import ai.koog.agents.core.feature.handler.strategy.StrategyCompletedContext
import ai.koog.agents.core.feature.handler.strategy.StrategyStartingContext
import ai.koog.agents.core.feature.handler.streaming.LLMStreamingCompletedContext
import ai.koog.agents.core.feature.handler.streaming.LLMStreamingFailedContext
import ai.koog.agents.core.feature.handler.streaming.LLMStreamingFrameReceivedContext
import ai.koog.agents.core.feature.handler.streaming.LLMStreamingStartingContext
import ai.koog.agents.core.feature.handler.subgraph.SubgraphExecutionCompletedContext
import ai.koog.agents.core.feature.handler.subgraph.SubgraphExecutionFailedContext
import ai.koog.agents.core.feature.handler.subgraph.SubgraphExecutionStartingContext
import ai.koog.agents.core.feature.handler.tool.ToolCallCompletedContext
import ai.koog.agents.core.feature.handler.tool.ToolCallFailedContext
import ai.koog.agents.core.feature.handler.tool.ToolCallStartingContext
import ai.koog.agents.core.feature.handler.tool.ToolValidationFailedContext
import ai.koog.agents.core.feature.pipeline.Interceptor
import ai.koog.utils.annotations.InternalKoogUtils
import ai.koog.utils.concurrency.withContextReentrant

/**
 * JVM implementation of event-handler configuration with Java-friendly handler registration methods.
 */
@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
@OptIn(InternalAgentsApi::class, InternalKoogUtils::class)
public actual open class EventHandlerConfig actual constructor() : EventHandlerConfigCommon() {
    // Java Specific Handlers:
    /**
     * Registers a handler for the subgraph execution starting event. This method allows asynchronous
     * interception of the event, enabling users to execute custom logic during the beginning of a
     * subgraph execution.
     *
     * @param handler The asynchronous interceptor that processes the SubgraphExecutionStartingContext.
     */
    @JavaAPI
    @JvmName("onSubgraphExecutionStarting")
    public fun onSubgraphExecutionStartingBlocking(handler: Interceptor<SubgraphExecutionStartingContext>) {
        onSubgraphExecutionStarting { eventContext ->
            withContextReentrant(eventContext.context.config.strategyDispatcher) {
                handler.intercept(eventContext)
            }
        }
    }

    /**
     * Registers a handler to be invoked when the execution of a subgraph is completed.
     *
     * @param handler An asynchronous interceptor that processes the subgraph execution
     *                completion context. It provides a mechanism to inspect or modify
     *                the context as needed before completion.
     */
    @JavaAPI
    @JvmName("onSubgraphExecutionCompleted")
    public fun onSubgraphExecutionCompletedBlocking(handler: Interceptor<SubgraphExecutionCompletedContext>) {
        onSubgraphExecutionCompleted { eventContext ->
            withContextReentrant(eventContext.context.config.strategyDispatcher) {
                handler.intercept(eventContext)
            }
        }
    }

    /**
     * Handles an event where the execution of a subgraph has failed.
     *
     * @param handler An asynchronous interceptor that processes the subgraph execution failure context.
     */
    @JavaAPI
    @JvmName("onSubgraphExecutionFailed")
    public fun onSubgraphExecutionFailedBlocking(handler: Interceptor<SubgraphExecutionFailedContext>) {
        onSubgraphExecutionFailed { eventContext ->
            withContextReentrant(eventContext.context.config.strategyDispatcher) {
                handler.intercept(eventContext)
            }
        }
    }

    /**
     * Append handler called when an agent is started.
     */
    @JavaAPI
    @JvmName("onAgentStarting")
    public fun onAgentStartingBlocking(handler: Interceptor<AgentStartingContext>) {
        onAgentStarting { eventContext ->
            withContextReentrant(eventContext.context.config.strategyDispatcher) {
                handler.intercept(eventContext)
            }
        }
    }

    /**
     * Append handler called when an agent finishes execution.
     */
    @JavaAPI
    @JvmName("onAgentCompleted")
    public fun onAgentCompletedBlocking(handler: Interceptor<AgentCompletedContext>) {
        onAgentCompleted { eventContext ->
            withContextReentrant(eventContext.context.config.strategyDispatcher) {
                handler.intercept(eventContext)
            }
        }
    }

    /**
     * Append handler called when an error occurs during agent execution.
     */
    @JavaAPI
    @JvmName("onAgentExecutionFailed")
    public fun onAgentExecutionFailedBlocking(handler: Interceptor<AgentExecutionFailedContext>) {
        onAgentExecutionFailed { eventContext ->
            withContextReentrant(eventContext.context.config.strategyDispatcher) {
                handler.intercept(eventContext)
            }
        }
    }

    /**
     * Appends a handler called before an agent is closed. This allows for additional behavior
     * to be executed prior to the agent being closed.
     */
    @JavaAPI
    @JvmName("onAgentClosing")
    public fun onAgentClosingBlocking(handler: Interceptor<AgentClosingContext>) {
        onAgentClosing { eventContext ->
            withContextReentrant(eventContext.agent.agentConfig.strategyDispatcher) {
                handler.intercept(eventContext)
            }
        }
    }

    /**
     * Append handler called when a strategy starts execution.
     */
    @JavaAPI
    @JvmName("onStrategyStarting")
    public fun onStrategyStartingBlocking(handler: Interceptor<StrategyStartingContext>) {
        onStrategyStarting { eventContext ->
            withContextReentrant(eventContext.context.config.strategyDispatcher) {
                handler.intercept(eventContext)
            }
        }
    }

    /**
     * Append handler called when a strategy finishes execution.
     */
    @JavaAPI
    @JvmName("onStrategyCompleted")
    public fun onStrategyCompletedBlocking(handler: Interceptor<StrategyCompletedContext>) {
        onStrategyCompleted { eventContext ->
            withContextReentrant(eventContext.context.config.strategyDispatcher) {
                handler.intercept(eventContext)
            }
        }
    }

    /**
     * Append handler called before a node in the agent's execution graph is processed.
     */
    @JavaAPI
    @JvmName("onNodeExecutionStarting")
    public fun onNodeExecutionStartingBlocking(handler: Interceptor<NodeExecutionStartingContext>) {
        onNodeExecutionStarting { eventContext ->
            withContextReentrant(eventContext.context.config.strategyDispatcher) {
                handler.intercept(eventContext)
            }
        }
    }

    /**
     * Append handler called after a node in the agent's execution graph has been processed.
     */
    @JavaAPI
    @JvmName("onNodeExecutionCompleted")
    public fun onNodeExecutionCompletedBlocking(handler: Interceptor<NodeExecutionCompletedContext>) {
        onNodeExecutionCompleted { eventContext ->
            withContextReentrant(eventContext.context.config.strategyDispatcher) {
                handler.intercept(eventContext)
            }
        }
    }

    /**
     * Append handler called when an error occurs during the execution of a node.
     */
    @JavaAPI
    @JvmName("onNodeExecutionFailed")
    public fun onNodeExecutionFailedBlocking(handler: Interceptor<NodeExecutionFailedContext>) {
        onNodeExecutionFailed { eventContext ->
            withContextReentrant(eventContext.context.config.strategyDispatcher) {
                handler.intercept(eventContext)
            }
        }
    }

    /**
     * Append handler called before a call is made to the language model.
     */
    @JavaAPI
    @JvmName("onLLMCallStarting")
    public fun onLLMCallStartingBlocking(handler: Interceptor<LLMCallStartingContext>) {
        onLLMCallStarting { eventContext ->
            withContextReentrant(eventContext.context.config.strategyDispatcher) {
                handler.intercept(eventContext)
            }
        }
    }

    /**
     * Append handler called after a response is received from the language model.
     */
    @JavaAPI
    @JvmName("onLLMCallCompleted")
    public fun onLLMCallCompletedBlocking(handler: Interceptor<LLMCallCompletedContext>) {
        onLLMCallCompleted { eventContext ->
            withContextReentrant(eventContext.context.config.strategyDispatcher) {
                handler.intercept(eventContext)
            }
        }
    }

    /**
     * Append handler called when a tool is about to be called.
     */
    @JavaAPI
    @JvmName("onToolCallStarting")
    public fun onToolCallStartingBlocking(handler: Interceptor<ToolCallStartingContext>) {
        onToolCallStarting { eventContext ->
            withContextReentrant(eventContext.context.config.strategyDispatcher) {
                handler.intercept(eventContext)
            }
        }
    }

    /**
     * Append handler called when a validation error occurs during a tool call.
     */
    @JavaAPI
    @JvmName("onToolValidationFailed")
    public fun onToolValidationFailedBlocking(handler: Interceptor<ToolValidationFailedContext>) {
        onToolValidationFailed { eventContext ->
            withContextReentrant(eventContext.context.config.strategyDispatcher) {
                handler.intercept(eventContext)
            }
        }
    }

    /**
     * Append handler called when a tool call fails with an exception.
     */
    @JavaAPI
    @JvmName("onToolCallFailed")
    public fun onToolCallFailedBlocking(handler: Interceptor<ToolCallFailedContext>) {
        onToolCallFailed { eventContext ->
            withContextReentrant(eventContext.context.config.strategyDispatcher) {
                handler.intercept(eventContext)
            }
        }
    }

    /**
     * Append handler called when a tool call completes successfully.
     */
    @JavaAPI
    @JvmName("onToolCallCompleted")
    public fun onToolCallCompletedBlocking(handler: Interceptor<ToolCallCompletedContext>) {
        onToolCallCompleted { eventContext ->
            withContextReentrant(eventContext.context.config.strategyDispatcher) {
                handler.intercept(eventContext)
            }
        }
    }

    /**
     * Registers a handler to be invoked before streaming from a language model begins.
     */
    @JavaAPI
    @JvmName("onLLMStreamingStarting")
    public fun onLLMStreamingStartingBlocking(handler: Interceptor<LLMStreamingStartingContext>) {
        onLLMStreamingStarting { eventContext ->
            withContextReentrant(eventContext.context.config.strategyDispatcher) {
                handler.intercept(eventContext)
            }
        }
    }

    /**
     * Registers a handler to be invoked when stream frames are received during streaming.
     */
    @JavaAPI
    @JvmName("onLLMStreamingFrameReceived")
    public fun onLLMStreamingFrameReceivedBlocking(handler: Interceptor<LLMStreamingFrameReceivedContext>) {
        onLLMStreamingFrameReceived { eventContext ->
            withContextReentrant(eventContext.context.config.strategyDispatcher) {
                handler.intercept(eventContext)
            }
        }
    }

    /**
     * Registers a handler to be invoked when an error occurs during streaming.
     */
    @JavaAPI
    @JvmName("onLLMStreamingFailed")
    public fun onLLMStreamingFailedBlocking(handler: Interceptor<LLMStreamingFailedContext>) {
        onLLMStreamingFailed { eventContext ->
            withContextReentrant(eventContext.context.config.strategyDispatcher) {
                handler.intercept(eventContext)
            }
        }
    }

    /**
     * Registers a handler to be invoked after streaming from a language model completes.
     */
    @JavaAPI
    @JvmName("onLLMStreamingCompleted")
    public fun onLLMStreamingCompletedBlocking(handler: Interceptor<LLMStreamingCompletedContext>) {
        onLLMStreamingCompleted { eventContext ->
            withContextReentrant(eventContext.context.config.strategyDispatcher) {
                handler.intercept(eventContext)
            }
        }
    }
}
