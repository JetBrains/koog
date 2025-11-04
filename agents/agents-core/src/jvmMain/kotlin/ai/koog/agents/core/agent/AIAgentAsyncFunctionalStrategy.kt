@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING", "MissingKDocForPublicAPI")

package ai.koog.agents.core.agent

import ai.koog.agents.annotations.JavaAPI
import ai.koog.agents.core.agent.context.AIAgentFunctionalContext
import ai.koog.agents.core.agent.entity.NonSuspendAIAgentStrategy
import ai.koog.agents.core.utils.asCoroutineContext
import ai.koog.prompt.message.Message
import io.ktor.util.reflect.instanceOf
import kotlinx.coroutines.future.await
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CompletableFuture
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
