package ai.koog.agents.core.agent

import ai.koog.agents.core.agent.entity.AIAgentStrategy

/**
 * A strategy for implementing AI agent behavior that operates in a loop-based manner.
 *
 * The [AIAgentFunctionalStrategy] class allows for the definition of a custom looping logic
 * that processes input and produces output by utilizing an [AIAgentFunctionalContext]. This strategy
 * can be used to define iterative decision-making or execution processes for AI agents.
 *
 * @param TInput The type of input data processed by the strategy.
 * @param TOutput The type of output data produced by the strategy.
 * @property name The name of the strategy, providing a way to identify and describe the strategy.
 * @property loop A suspending function representing the loop logic for the strategy. It accepts
 * input data of type [TInput] and an [AIAgentFunctionalContext] to execute the loop and produce the output.
 */
public class AIAgentFunctionalStrategy<TInput, TOutput>(
    override val name: String,
    public val loop: suspend AIAgentFunctionalContext.(TInput) -> TOutput
) : AIAgentStrategy<TInput, TOutput, AIAgentFunctionalContext> {
    override suspend fun execute(
        context: AIAgentFunctionalContext,
        input: TInput
    ): TOutput = context.loop(input)
}

/**
 * Creates an instance of a loop strategy for an AI agent.
 *
 * This function constructs and returns a specific implementation of `AIAgentLoopStrategy`
 * using the provided `name` and `loop` parameters. The `loop` function specifies the behavior
 * of the agent within its execution loop. It is executed with the given input and the
 * `AIAgentLoopContext` to produce an output.
 *
 * @param name The name of the strategy, describing its purpose or behavior.
 * @param loop A suspending function representing the execution behavior of the agent in the loop.
 *             It takes an input of type `Any?` and an `AIAgentLoopContext`, and produces an output of type `Any?`.
 * @return An `AIAgentLoopStrategy` configured with the provided name and loop function.
 */
public fun <Input, Output> functionalStrategy(name: String = "funStrategy", loop: suspend AIAgentFunctionalContext.(input: Input) -> Output): AIAgentFunctionalStrategy<Input, Output> =
    AIAgentFunctionalStrategy(name, loop)
