package ai.koog.agents.core.dsl.builder

import ai.koog.agents.core.agent.entity.AIAgentNodeBase

/**
 * Represents an intermediate configuration of a linear strategy composed of interconnected AI agent nodes.
 *
 * This class is useful for defining a sequential processing pipeline where data flows
 * linearly from the first node to the last node through a predefined set of intermediate nodes.
 *
 * @param InputT The type of input handled by the first node in the strategy.
 * @param OutputT The type of output produced by the last node in the strategy.
 * @property firstNode The initial node in the linear strategy, responsible for processing the input.
 * @property lastNode The final node in the linear strategy, responsible for producing the output.
 * @property allNodes The complete list of nodes that make up the strategy, maintaining their order of execution.
 */
public class LinearStepsIntermediate<InputT, OutputT>(
    internal val firstNode: AIAgentNodeBase<InputT, *>,
    internal val lastNode: AIAgentNodeBase<*, OutputT>,
    internal val allNodes: List<AIAgentNodeBase<*, *>>
)
