@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package ai.koog.agents.core.agent.entity

import ai.koog.agents.annotations.JavaAPI
import kotlinx.serialization.json.Json

/**
 * Represents a strategy for managing and executing AI agent workflows built as subgraphs of interconnected nodes.
 *
 * @property name The unique identifier for the strategy.
 * @property nodeStart The starting node of the strategy, initiating the subgraph execution.
 * By default, the start node gets the agent input and returns
 * @property nodeFinish The finishing node of the strategy, marking the subgraph's endpoint.
 * @property toolSelectionStrategy The strategy responsible for determining the toolset available during subgraph execution.
 */
public actual class AIAgentGraphStrategy<TInput, TOutput> actual constructor(
    name: String,
    nodeStart: StartNode<TInput>,
    nodeFinish: FinishNode<TOutput>,
    toolSelectionStrategy: ToolSelectionStrategy,
    serializer: Json
) : AIAgentGraphStrategyBase<TInput, TOutput>(name, nodeStart, nodeFinish, toolSelectionStrategy, serializer) {
    /**
     * Companion object for the [AIAgentGraphStrategy] class.
     */
    public companion object {
        /**
         * Creates a new instance of the `GraphStrategyBuilder` for constructing graph processing strategies.
         *
         * This builder serves as the entry point for configuring a graph strategy with an optional strategy name.
         * It allows for detailed customization of the graph processing logic, including specifying the input type
         * of the graph when chaining additional configurations.
         *
         * @param strategyName The name of the graph strategy being constructed. Defaults to "graphStrategy".
         * @return A new instance of `GraphStrategyBuilder` initialized with the specified or default strategy name.
         */
        @JavaAPI
        @JvmOverloads
        @JvmStatic
        public fun builder(strategyName: String = "graphStrategy"): GraphStrategyBuilder = GraphStrategyBuilder(strategyName)
    }
}

private fun f() {
    val graph = AIAgentGraphStrategy.builder()
        .withInput(String::class.java)
        .withOutput(String::class.java)

    graph.edge(
        AIAgentEdge.builder()
            .from(graph.nodeStart)
            .to(graph.nodeFinish)
            .onCondition { it.isNotEmpty() }
            .transformed<Int> { x -> 1000 }
            .transformed { it.toString() }
            .build()
    )

    val subg = AIAgentSubgraph.builder()
        .withInput(String::class.java)
        .withVerification { "tell me" }
        .build()

    graph.edge(graph.nodeStart, graph.nodeFinish)
    graph.edge(graph.nodeStart, subg)
}
