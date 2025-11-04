@file:Suppress("MissingKDocForPublicAPI", "EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package ai.koog.agents.core.agent.config

import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.llm.LLModel

public actual class AIAgentConfig actual constructor(
    public actual val prompt: Prompt,
    public actual val model: LLModel,
    public actual val maxAgentIterations: Int,
    public actual val missingToolsConversionStrategy: MissingToolsConversionStrategy
) {

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
