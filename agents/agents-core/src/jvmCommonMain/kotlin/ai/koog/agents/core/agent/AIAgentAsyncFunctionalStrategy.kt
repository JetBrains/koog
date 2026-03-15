@file:Suppress("MissingKDocForPublicAPI")

package ai.koog.agents.core.agent

import ai.koog.agents.annotations.JavaAPI
import ai.koog.agents.core.agent.context.AIAgentFunctionalContext
import ai.koog.agents.core.agent.entity.NonSuspendAIAgentStrategy
import java.util.concurrent.ExecutorService

/**
 * [AIAgentFunctionalStrategy] that operates in non-suspend context and is run on [ExecutorService] configured in [ai.koog.agents.core.agent.config.AIAgentConfig].
 *
 * See [ai.koog.agents.core.agent.NonSuspendAIAgentFunctionalStrategy.executeStrategy]
 * */
@JavaAPI
public abstract class NonSuspendAIAgentFunctionalStrategy<TInput, TOutput> public constructor(
    override val name: String
) : NonSuspendAIAgentStrategy<TInput, TOutput, AIAgentFunctionalContext>(), AIAgentFunctionalStrategy<TInput, TOutput> {

    abstract override fun executeStrategy(context: AIAgentFunctionalContext, input: TInput): TOutput
}
