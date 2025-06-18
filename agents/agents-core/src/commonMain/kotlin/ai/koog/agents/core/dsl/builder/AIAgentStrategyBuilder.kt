package ai.koog.agents.core.dsl.builder

import ai.koog.agents.core.agent.entity.*

/**
 * A builder class responsible for constructing an instance of `AIAgentStrategy`.
 * The `AIAgentStrategyBuilder` serves as a specific configuration for creating AI agent strategies
 * with a defined start and finish node, along with a designated tool selection strategy.
 *
 * @param name The name of the strategy being built, serving as a unique identifier.
 * @param toolSelectionStrategy The strategy used to determine the subset of tools available during subgraph execution.
 */
public class AIAgentStrategyBuilder(
    private val name: String,
    private val toolSelectionStrategy: ToolSelectionStrategy,
) : AIAgentSubgraphBuilderBase<String, String>(), BaseBuilder<AIAgentStrategy> {
    public override val nodeStart: StartAIAgentNodeBase<String> = StartNode()
    public override val nodeFinish: FinishAIAgentNodeBase<String> = FinishNode()

    override fun build(): AIAgentStrategy {
        return AIAgentStrategy(
            name = name, nodeStart, nodeFinish, toolSelectionStrategy
        )
    }
}


/**
 * Builds a local AI agent that processes user input through a sequence of stages.
 *
 * The agent executes a series of stages in sequence, with each stage receiving the output
 * of the previous stage as its input.
 *
 * @property name The unique identifier for this agent.
 * @param init Lambda that defines stages and nodes of this agent
 */
public fun strategy(
    name: String,
    toolSelectionStrategy: ToolSelectionStrategy = ToolSelectionStrategy.ALL,
    init: AIAgentStrategyBuilder.() -> Unit,
): AIAgentStrategy {
    return AIAgentStrategyBuilder(
        name,
        toolSelectionStrategy,
    ).apply(init).build()
}
