package ai.koog.agents.core.agent

import ai.koog.agents.core.agent.entity.AIAgentStrategy

/**
 * A strategy for implementing AI agent behavior that operates in a loop-based manner.
 *
 * The [AIAgentLoopStrategy] class allows for the definition of a custom looping logic
 * that processes input and produces output by utilizing an [AIAgentLoopContext]. This strategy
 * can be used to define iterative decision-making or execution processes for AI agents.
 *
 * @param Input The type of input data processed by the strategy.
 * @param Output The type of output data produced by the strategy.
 * @property name The name of the strategy, providing a way to identify and describe the strategy.
 * @property loop A suspending function representing the loop logic for the strategy. It accepts
 * input data of type [Input] and an [AIAgentLoopContext] to execute the loop and produce the output.
 */
public class AIAgentLoopStrategy<Input, Output>(
    override val name: String,
    public val loop: suspend AIAgentLoopContext.(Input) -> Output
) : AIAgentStrategy<Input, Output, AIAgentLoopContext> {
    override suspend fun execute(
        context: AIAgentLoopContext,
        input: Input
    ): Output = context.loop(input)
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
public fun <Input, Output> loopStrategy(name: String = "loopStrategy", loop: suspend AIAgentLoopContext.(input: Input) -> Output): AIAgentLoopStrategy<Input, Output> =
    AIAgentLoopStrategy(name, loop)
