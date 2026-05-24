@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING", "MissingKDocForPublicAPI")

package ai.koog.agents.core.agent

import ai.koog.agents.annotations.JavaAPI
import ai.koog.agents.core.agent.context.AIAgentFunctionalContext
import ai.koog.agents.core.agent.entity.AIAgentStrategyBlocking
import java.util.concurrent.ExecutorService

/**
 * [AIAgentFunctionalStrategy] that operates in non-suspend context and is run on [ExecutorService] configured in [ai.koog.agents.core.agent.config.AIAgentConfig].
 *
 * See [ai.koog.agents.core.agent.AIAgentFunctionalStrategyBlocking.executeBlocking]
 * */
@JavaAPI
public abstract class AIAgentFunctionalStrategyBlocking<TInput, TOutput> public constructor(
    override val name: String
) : AIAgentStrategyBlocking<TInput, TOutput, AIAgentFunctionalContext>(), AIAgentFunctionalStrategy<TInput, TOutput>
