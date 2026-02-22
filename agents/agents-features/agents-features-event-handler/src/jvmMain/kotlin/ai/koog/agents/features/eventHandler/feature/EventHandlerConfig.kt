@file:Suppress("MissingKDocForPublicAPI")
@file:OptIn(InternalAgentsApi::class)

package ai.koog.agents.features.eventHandler.feature

import ai.koog.agents.annotations.JavaAPI
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.feature.config.FeatureConfig
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
import ai.koog.agents.core.utils.submitToMainDispatcher

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
public actual open class EventHandlerConfig actual constructor() :
    FeatureConfig(),
    EventHandlerConfigAPI by EventHandlerConfigImpl() {

    // Java Specific Handlers:

    /**
     * Registers a handler for the subgraph execution starting event. This method allows asynchronous
     * interception of the event, enabling users to execute custom logic during the beginning of a
     * subgraph execution.
     *
     * @param handler The asynchronous interceptor that processes the SubgraphExecutionStartingContext.
     */
    @JavaAPI
    @InternalAgentsApi
    @JvmName("onSubgraphExecutionStarting")
    public fun javaApiOnSubgraphExecutionStarting(handler: Interceptor<SubgraphExecutionStartingContext>) {
        onSubgraphExecutionStarting { eventContext ->
            eventContext.context.config.submitToMainDispatcher {
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
    @InternalAgentsApi
    @JvmName("onSubgraphExecutionCompleted")
    public fun javaApiOnSubgraphExecutionCompleted(handler: Interceptor<SubgraphExecutionCompletedContext>) {
        onSubgraphExecutionCompleted { eventContext ->
            eventContext.context.config.submitToMainDispatcher {
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
    @InternalAgentsApi
    @JvmName("onSubgraphExecutionFailed")
    public fun javaApiOnSubgraphExecutionFailed(handler: Interceptor<SubgraphExecutionFailedContext>) {
        onSubgraphExecutionFailed { eventContext ->
            eventContext.context.config.submitToMainDispatcher {
                handler.intercept(eventContext)
            }
        }
    }

    /**
     * Append handler called when an agent is started.
     */
    @JavaAPI
    @InternalAgentsApi
    @JvmName("onAgentStarting")
    public fun javaApiOnAgentStarting(handler: Interceptor<AgentStartingContext>) {
        onAgentStarting { eventContext ->
            eventContext.context.config.submitToMainDispatcher {
                handler.intercept(eventContext)
            }
        }
    }

    /**
     * Append handler called when an agent finishes execution.
     */
    @JavaAPI
    @InternalAgentsApi
    @JvmName("onAgentCompleted")
    public fun javaApiOnAgentCompleted(handler: Interceptor<AgentCompletedContext>) {
        onAgentCompleted { eventContext ->
            eventContext.context.config.submitToMainDispatcher {
                handler.intercept(eventContext)
            }
        }
    }

    /**
     * Append handler called when an error occurs during agent execution.
     */
    @JavaAPI
    @InternalAgentsApi
    @JvmName("onAgentExecutionFailed")
    public fun javaApiOnAgentExecutionFailed(handler: Interceptor<AgentExecutionFailedContext>) {
        onAgentExecutionFailed { eventContext ->
            eventContext.context.config.submitToMainDispatcher {
                handler.intercept(eventContext)
            }
        }
    }

    /**
     * Appends a handler called before an agent is closed. This allows for additional behavior
     * to be executed prior to the agent being closed.
     */
    @JavaAPI
    @InternalAgentsApi
    @JvmName("onAgentClosing")
    public fun javaApiOnAgentClosing(handler: Interceptor<AgentClosingContext>) {
        onAgentClosing { eventContext ->
            eventContext.config.submitToMainDispatcher {
                handler.intercept(eventContext)
            }
        }
    }

    /**
     * Append handler called when a strategy starts execution.
     */
    @JavaAPI
    @InternalAgentsApi
    @JvmName("onStrategyStarting")
    public fun javaApiOnStrategyStarting(handler: Interceptor<StrategyStartingContext>) {
        onStrategyStarting { eventContext ->
            eventContext.context.config.submitToMainDispatcher {
                handler.intercept(eventContext)
            }
        }
    }

    /**
     * Append handler called when a strategy finishes execution.
     */
    @JavaAPI
    @InternalAgentsApi
    @JvmName("onStrategyCompleted")
    public fun javaApiOnStrategyCompleted(handler: Interceptor<StrategyCompletedContext>) {
        onStrategyCompleted { eventContext ->
            eventContext.context.config.submitToMainDispatcher {
                handler.intercept(eventContext)
            }
        }
    }

    /**
     * Append handler called before a node in the agent's execution graph is processed.
     */
    @JavaAPI
    @InternalAgentsApi
    @JvmName("onNodeExecutionStarting")
    public fun javaApiOnNodeExecutionStarting(handler: Interceptor<NodeExecutionStartingContext>) {
        onNodeExecutionStarting { eventContext ->
            eventContext.context.config.submitToMainDispatcher {
                handler.intercept(eventContext)
            }
        }
    }

    /**
     * Append handler called after a node in the agent's execution graph has been processed.
     */
    @JavaAPI
    @InternalAgentsApi
    @JvmName("onNodeExecutionCompleted")
    public fun javaApiOnNodeExecutionCompleted(handler: Interceptor<NodeExecutionCompletedContext>) {
        onNodeExecutionCompleted { eventContext ->
            eventContext.context.config.submitToMainDispatcher {
                handler.intercept(eventContext)
            }
        }
    }

    /**
     * Append handler called when an error occurs during the execution of a node.
     */
    @JavaAPI
    @InternalAgentsApi
    @JvmName("onNodeExecutionFailed")
    public fun javaApiOnNodeExecutionFailed(handler: Interceptor<NodeExecutionFailedContext>) {
        onNodeExecutionFailed { eventContext ->
            eventContext.context.config.submitToMainDispatcher {
                handler.intercept(eventContext)
            }
        }
    }

    /**
     * Append handler called before a call is made to the language model.
     */
    @JavaAPI
    @InternalAgentsApi
    @JvmName("onLLMCallStarting")
    public fun javaApiOnLLMCallStarting(handler: Interceptor<LLMCallStartingContext>) {
        onLLMCallStarting { eventContext ->
            eventContext.context.config.submitToMainDispatcher {
                handler.intercept(eventContext)
            }
        }
    }

    /**
     * Append handler called after a response is received from the language model.
     */
    @JavaAPI
    @InternalAgentsApi
    @JvmName("onLLMCallCompleted")
    public fun javaApiOnLLMCallCompleted(handler: Interceptor<LLMCallCompletedContext>) {
        onLLMCallCompleted { eventContext ->
            eventContext.context.config.submitToMainDispatcher {
                handler.intercept(eventContext)
            }
        }
    }

    /**
     * Append handler called when a tool is about to be called.
     */
    @JavaAPI
    @InternalAgentsApi
    @JvmName("onToolCallStarting")
    public fun javaApiOnToolCallStarting(handler: Interceptor<ToolCallStartingContext>) {
        onToolCallStarting { eventContext ->
            eventContext.context.config.submitToMainDispatcher {
                handler.intercept(eventContext)
            }
        }
    }

    /**
     * Append handler called when a validation error occurs during a tool call.
     */
    @JavaAPI
    @InternalAgentsApi
    @JvmName("onToolValidationFailed")
    public fun javaApiOnToolValidationFailed(handler: Interceptor<ToolValidationFailedContext>) {
        onToolValidationFailed { eventContext ->
            eventContext.context.config.submitToMainDispatcher {
                handler.intercept(eventContext)
            }
        }
    }

    /**
     * Append handler called when a tool call fails with an exception.
     */
    @JavaAPI
    @InternalAgentsApi
    @JvmName("onToolCallFailed")
    public fun javaApiOnToolCallFailed(handler: Interceptor<ToolCallFailedContext>) {
        onToolCallFailed { eventContext ->
            eventContext.context.config.submitToMainDispatcher {
                handler.intercept(eventContext)
            }
        }
    }

    /**
     * Append handler called when a tool call completes successfully.
     */
    @JavaAPI
    @InternalAgentsApi
    @JvmName("onToolCallCompleted")
    public fun javaApiOnToolCallCompleted(handler: Interceptor<ToolCallCompletedContext>) {
        onToolCallCompleted { eventContext ->
            eventContext.context.config.submitToMainDispatcher {
                handler.intercept(eventContext)
            }
        }
    }

    /**
     * Registers a handler to be invoked before streaming from a language model begins.
     */
    @JavaAPI
    @InternalAgentsApi
    @JvmName("onLLMStreamingStarting")
    public fun javaApiOnLLMStreamingStarting(handler: Interceptor<LLMStreamingStartingContext>) {
        onLLMStreamingStarting { eventContext ->
            eventContext.context.config.submitToMainDispatcher {
                handler.intercept(eventContext)
            }
        }
    }

    /**
     * Registers a handler to be invoked when stream frames are received during streaming.
     */
    @JavaAPI
    @InternalAgentsApi
    @JvmName("onLLMStreamingFrameReceived")
    public fun javaApiOnLLMStreamingFrameReceived(handler: Interceptor<LLMStreamingFrameReceivedContext>) {
        onLLMStreamingFrameReceived { eventContext ->
            eventContext.context.config.submitToMainDispatcher {
                handler.intercept(eventContext)
            }
        }
    }

    /**
     * Registers a handler to be invoked when an error occurs during streaming.
     */
    @JavaAPI
    @InternalAgentsApi
    @JvmName("onLLMStreamingFailed")
    public fun javaApiOnLLMStreamingFailed(handler: Interceptor<LLMStreamingFailedContext>) {
        onLLMStreamingFailed { eventContext ->
            eventContext.context.config.submitToMainDispatcher {
                handler.intercept(eventContext)
            }
        }
    }

    /**
     * Registers a handler to be invoked after streaming from a language model completes.
     */
    @JavaAPI
    @InternalAgentsApi
    @JvmName("onLLMStreamingCompleted")
    public fun javaApiOnLLMStreamingCompleted(handler: Interceptor<LLMStreamingCompletedContext>) {
        onLLMStreamingCompleted { eventContext ->
            eventContext.context.config.submitToMainDispatcher {
                handler.intercept(eventContext)
            }
        }
    }
}
