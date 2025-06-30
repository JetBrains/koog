package ai.koog.agents.core.dsl.builder

import ai.koog.agents.core.agent.entity.AIAgentStrategy
import ai.koog.agents.core.agent.entity.FinishNode
import ai.koog.agents.core.agent.entity.StartNode
import ai.koog.agents.core.agent.entity.ToolSelectionStrategy

/**
 * A builder class responsible for constructing an instance of `AIAgentStrategy`.
 * The `AIAgentStrategyBuilder` serves as a specific configuration for creating AI agent strategies
 * with a defined start and finish node, along with a designated tool selection strategy.
 *
 * @param name The name of the strategy being built, serving as a unique identifier.
 * @param toolSelectionStrategy The strategy used to determine the subset of tools available during subgraph execution.
 */
public class AIAgentStrategyBuilder<Input, Output>(
    private val name: String,
    private val toolSelectionStrategy: ToolSelectionStrategy,
) : AIAgentSubgraphBuilderBase<Input, Output>(), BaseBuilder<AIAgentStrategy<Input, Output>> {
    public override val nodeStart: StartNode<Input> = StartNode()
    public override val nodeFinish: FinishNode<Output> = FinishNode()

    override fun build(): AIAgentStrategy<Input, Output> {
        return AIAgentStrategy(
            name = name,
            nodeStart = nodeStart,
            nodeFinish = nodeFinish,
            toolSelectionStrategy = toolSelectionStrategy
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
public fun <Input, Output> strategy(
    name: String,
    toolSelectionStrategy: ToolSelectionStrategy = ToolSelectionStrategy.ALL,
    init: AIAgentStrategyBuilder<Input, Output>.() -> Unit,
): AIAgentStrategy<Input, Output> {
    return AIAgentStrategyBuilder<Input, Output>(name, toolSelectionStrategy)
        .apply(init)
        .build()
}
