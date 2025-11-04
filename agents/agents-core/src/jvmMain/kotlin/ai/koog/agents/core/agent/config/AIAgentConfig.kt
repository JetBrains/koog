@file:Suppress("MissingKDocForPublicAPI", "EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package ai.koog.agents.core.agent.config

import ai.koog.agents.annotations.JavaAPI
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.llm.LLModel
import java.util.concurrent.ExecutorService

public actual class AIAgentConfig @JvmOverloads actual constructor(
    public actual val prompt: Prompt,
    public actual val model: LLModel,
    public actual val maxAgentIterations: Int,
    public actual val missingToolsConversionStrategy: MissingToolsConversionStrategy
) {

    /**
     * [ExecutorService] for running agent strategy logic
     *
     * By default, all agent operations will be performed on [kotlinx.coroutines.Dispatchers.Default]
     *  */
    @JavaAPI
    @property:PublishedApi
    internal var strategyExecutorService: ExecutorService? = null

    /**
     * IO-bounded [ExecutorService] for performing LLM communications
     *
     * By default, all IO/LLM operations will be performed on [kotlinx.coroutines.Dispatchers.IO]
     * */
    @JavaAPI
    @property:PublishedApi
    internal var llmRequestExecutorService: ExecutorService? = null

    @JvmOverloads
    public constructor(
        prompt: Prompt,
        model: LLModel,
        maxAgentIterations: Int,
        agentStrategyExecutorService: ExecutorService?,
        llmRequestExecutorService: ExecutorService? = null,
        missingToolsConversionStrategy: MissingToolsConversionStrategy =
            MissingToolsConversionStrategy.Missing(ToolCallDescriber.JSON)
    ) : this(prompt, model, maxAgentIterations, missingToolsConversionStrategy) {
        this.strategyExecutorService = agentStrategyExecutorService
        this.llmRequestExecutorService = llmRequestExecutorService
    }

    init {
        require(maxAgentIterations > 0) { "maxAgentIterations must be greater than 0" }
    }

    public actual companion object {
        public actual fun withSystemPrompt(
            prompt: String,
            llm: LLModel,
            id: String,
            maxAgentIterations: Int
        ): AIAgentConfig =
            AIAgentConfig(
                prompt = prompt(id) {
                    system(prompt)
                },
                model = llm,
                maxAgentIterations = maxAgentIterations
            )
    }
}
