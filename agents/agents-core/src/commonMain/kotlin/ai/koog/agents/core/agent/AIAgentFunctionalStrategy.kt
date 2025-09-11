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
 * @property func A suspending function representing the loop logic for the strategy. It accepts
 * input data of type [TInput] and an [AIAgentFunctionalContext] to execute the loop and produce the output.
 */
public class AIAgentFunctionalStrategy<TInput, TOutput>(
    override val name: String,
    public val func: suspend AIAgentFunctionalContext.(TInput) -> TOutput
) : AIAgentStrategy<TInput, TOutput, AIAgentFunctionalContext> {
    override suspend fun execute(
        context: AIAgentFunctionalContext,
        input: TInput
    ): TOutput = context.func(input)
}

/**
 * Creates an [AIAgentFunctionalStrategy] with the specified loop logic and name.
 *
 * This function allows the definition of custom looping strategies for AI agents, where
 * the provided logic defines how the agent processes input and produces output within
 * its execution context.
 *
 * @param name The name of the strategy, used to identify and describe the strategy. Defaults to "funStrategy".
 * @param func A suspending function representing the loop logic of the strategy. It accepts an input of type [Input]
 * and is executed within an [AIAgentFunctionalContext], producing an output of type [Output].
 * @return An instance of [AIAgentFunctionalStrategy] configured with the given loop logic and name.
 */
public fun <Input, Output> functionalStrategy(
    name: String = "funStrategy",
    func: suspend AIAgentFunctionalContext.(input: Input) -> Output
): AIAgentFunctionalStrategy<Input, Output> =
    AIAgentFunctionalStrategy(name, func)
