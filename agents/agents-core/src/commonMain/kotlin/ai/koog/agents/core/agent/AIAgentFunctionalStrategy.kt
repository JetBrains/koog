@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package ai.koog.agents.core.agent

import ai.koog.agents.core.agent.context.AIAgentFunctionalContext
import ai.koog.agents.core.agent.entity.AIAgentStrategy
import io.github.oshai.kotlinlogging.KotlinLogging

/**
 * A strategy for implementing AI agent behavior that operates in a loop-based manner.
 *
 * The [AIAgentFunctionalStrategy] class allows for the definition of a custom looping logic
 * that processes input and produces output by utilizing an [ai.koog.agents.core.agent.context.AIAgentFunctionalContext]. This strategy
 * can be used to define iterative decision-making or execution processes for AI agents.
 *
 * @param TInput The type of input data processed by the strategy.
 * @param TOutput The type of output data produced by the strategy.
 * @property name The name of the strategy, providing a way to identify and describe the strategy.
 * @property func A suspending function representing the loop logic for the strategy. It accepts
 * input data of type [TInput] and an [ai.koog.agents.core.agent.context.AIAgentFunctionalContext] to execute the loop and produce the output.
 */
public interface AIAgentFunctionalStrategy<TInput, TOutput> :
    AIAgentStrategy<TInput, TOutput, AIAgentFunctionalContext> {

    private companion object {
        private val logger = KotlinLogging.logger { }
    }
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
): AIAgentFunctionalStrategy<Input, Output> = object : AIAgentFunctionalStrategy<Input, Output> {
    override val name: String = name
    override suspend fun execute(context: AIAgentFunctionalContext, input: Input): Output {
        return context.func(input)
    }
}
