@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package ai.koog.agents.core.agent.entity

import ai.koog.agents.annotations.JavaAPI
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.params.LLMParams
import ai.koog.prompt.processor.ResponseProcessor

/**
 * Represents a subgraph within an AI agent execution strategy capable of processing input and producing output.
 *
 * A subgraph is a modular component of a larger execution graph, defined by a `StartNode` as the entry point
 * and a `FinishNode` as the exit point. The subgraph may implement tool selection strategies, incorporate language
 * model support, and apply*/
public actual class AIAgentSubgraph<TInput, TOutput> actual constructor(
    name: String,
    start: StartNode<TInput>,
    finish: FinishNode<TOutput>,
    toolSelectionStrategy: ToolSelectionStrategy,
    llmModel: LLModel?,
    llmParams: LLMParams?,
    responseProcessor: ResponseProcessor?
) : AIAgentSubgraphBase<TInput, TOutput>(
    name,
    start,
    finish,
    toolSelectionStrategy,
    llmModel,
    llmParams,
    responseProcessor
) {
    /**
     * Companion object for [AIAgentSubgraph].
     * */
    public companion object {
        /**
         * Creates and returns a new [AgentSubgraphBuilder] for constructing a subgraph
         * with the specified name in the context of the current graph strategy builder.
         *
         * @param name The name of the subgraph to create, or null if unspecified.
         * @return A new instance of [AgentSubgraphBuilder] for further configuration of the subgraph.
         */
        @JavaAPI
        @JvmOverloads
        @JvmStatic
        public fun builder(name: String? = null): AgentSubgraphBuilder<*> = AgentSubgraphBuilder(name)
    }
}
