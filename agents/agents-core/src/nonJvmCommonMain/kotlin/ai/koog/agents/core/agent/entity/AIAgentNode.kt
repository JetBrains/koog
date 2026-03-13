package ai.koog.agents.core.agent.entity

import ai.koog.agents.core.agent.context.AIAgentGraphContextBase
import ai.koog.serialization.TypeToken

/**
 * Represents a simple implementation of an AI agent node, encapsulating a specific execution
 * logic that processes the input data and produces an output.
 *
 * @param TInput The type of input data this node processes.
 * @param TOutput The type of output data this node produces.
 * @property name The name of the node, used for identification and debugging.
 * @property execute A suspending function that defines the execution logic for the node. It
 * processes the provided input within the given execution context and produces an output.
 */
@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
public actual open class AIAgentNode<TInput, TOutput> internal actual constructor(
    name: String,
    inputType: TypeToken,
    outputType: TypeToken,
    execute: suspend AIAgentGraphContextBase.(input: TInput) -> TOutput,
) : SimpleAIAgentNodeImpl<TInput, TOutput>(
    name,
    inputType,
    outputType,
    execute
)
