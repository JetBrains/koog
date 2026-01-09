@file:Suppress("MissingKDocForPublicAPI", "EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package ai.koog.agents.core.agent.config

import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.processor.ResponseProcessor

public actual class AIAgentConfig actual constructor(
    public actual override val prompt: Prompt,
    public actual override val model: LLModel,
    public actual val maxAgentIterations: Int,
    public actual val missingToolsConversionStrategy: MissingToolsConversionStrategy,
    public actual val responseProcessor: ResponseProcessor?
) : AIAgentConfigBase {

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
