@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package ai.koog.agents.core.agent.entity

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
) : AIAgentGraphStrategyBase<TInput, TOutput>(name, nodeStart, nodeFinish, toolSelectionStrategy, serializer)
