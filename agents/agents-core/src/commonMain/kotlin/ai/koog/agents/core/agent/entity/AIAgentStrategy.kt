@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package ai.koog.agents.core.agent.entity

import ai.koog.agents.core.agent.context.AIAgentContext

/**
 * An interface representing the execution strategy of an AI agent.
 *
 * This strategy defines how the AI agent processes input to produce
 * an output within the context of its operation. It serves as the primary
 * mechanism to encapsulate various decision-making, learning, or processing
 * approaches used by the agent in a flexible manner.
 *
 * @param TInput The type of input data that the strategy will process.
 * @param TOutput The type of output data that the strategy will generate.
 * @param TContext The type of context in which the strategy is executed, extending [AIAgentContext].
 */
public expect interface AIAgentStrategy<TInput, TOutput, TContext : AIAgentContext> {
    /**
     * The name of the AI agent strategy.
     *
     * This property provides a human-readable identifier for the strategy,
     * which can be used for logging, debugging, or distinguishing between
     * multiple strategies within the system.
     */
    public val name: String

    /**
     * Executes the AI agent's strategy using the provided context and input.
     *
     * This method processes the given input data within the specified context, leveraging
     * the AI agent's internal logic and strategy to produce an output. The result of this
     * execution may depend on the graph-based execution pipeline, decision-making processes,
     * and other stateful operations defined in the context.
     *
     * @param context The execution context in which the AI agent operates. It provides access
     * to the agent's configuration, pipeline, environment, and other components required for
     * execution in a graph-based structure.
     * @param input The input data to be processed by the AI agent's strategy. The type of input
     * is defined by the strategy's implementation and is used to derive the resulting output.
     * @return The output produced by the AI agent's strategy, or null if no output is generated.
     * The output type is defined by the strategy's implementation.
     */
    public suspend fun execute(context: TContext, input: TInput): TOutput?
}
