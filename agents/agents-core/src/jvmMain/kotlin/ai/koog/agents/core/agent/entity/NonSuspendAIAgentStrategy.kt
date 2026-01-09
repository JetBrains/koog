@file:Suppress("MissingKDocForPublicAPI", "EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
@file:OptIn(InternalAgentsApi::class)

package ai.koog.agents.core.agent.entity

import ai.koog.agents.annotations.JavaAPI
import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.utils.submitToMainDispatcher

/**
 * [AIAgentStrategy] that is executed in non-suspend context using [java.util.concurrent.ExecutorService] provided in [ai.koog.agents.core.agent.config.AIAgentConfig].
 *
 * See [executeStrategy]
 * */
@JavaAPI
public abstract class NonSuspendAIAgentStrategy<TInput, TOutput, TContext : AIAgentContext> :
    AIAgentStrategy<TInput, TOutput, TContext> {

    /**
     * Executes the AI agent's strategy asynchronously using the provided [context] and [input].
     *
     * The strategy is executed on the main dispatcher that is either:
     *  a) [ai.koog.agents.core.agent.config.AIAgentConfig.strategyExecutorService] from [AIAgentContext.config] of the provided [config]
     *  b) [kotlinx.coroutines.Dispatchers.Default]
     *
     * @param context The execution context in which the AI agent operates. It provides access to the agent's
     * environment, configuration, lifecycle state, and other components required for processing.
     * @param input The input data to be processed by the AI agent's strategy. The type of the input is determined
     * by the implementation of the strategy and is used to derive an appropriate output.
     * @return Result of the agent of [TOutput] type.
     */
    public abstract fun executeStrategy(context: TContext, input: TInput): TOutput

    final override suspend fun execute(context: TContext, input: TInput): TOutput? =
        context.config.submitToMainDispatcher {
            executeStrategy(context, input)
        }
}
