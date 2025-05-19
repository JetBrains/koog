package ai.koog.agents.core.dsl.builder

import ai.koog.agents.core.agent.entity.*

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
