@file:Suppress("MissingKDocForPublicAPI", "EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package ai.koog.agents.core.agent

import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.config.MissingToolsConversionStrategy
import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import kotlinx.datetime.Clock

public actual class AIAgentServiceBuilder internal actual constructor() :
    AIAgentServiceBuilderAPI {
    private val delegate = AIAgentServiceBuilderImpl()

    actual override fun promptExecutor(promptExecutor: PromptExecutor): AIAgentServiceBuilder =
        apply { delegate.promptExecutor(promptExecutor) }

    actual override fun llmModel(model: LLModel): AIAgentServiceBuilder = apply { delegate.llmModel(model) }

    actual override fun toolRegistry(toolRegistry: ToolRegistry): AIAgentServiceBuilder =
        apply { delegate.toolRegistry(toolRegistry) }

    actual override fun systemPrompt(systemPrompt: String): AIAgentServiceBuilder =
        apply { delegate.systemPrompt(systemPrompt) }

    actual override fun prompt(prompt: Prompt): AIAgentServiceBuilder = apply { delegate.prompt(prompt) }

    actual override fun temperature(temperature: Double): AIAgentServiceBuilder =
        apply { delegate.temperature(temperature) }

    actual override fun numberOfChoices(numberOfChoices: Int): AIAgentServiceBuilder =
        apply { delegate.numberOfChoices(numberOfChoices) }

    actual override fun maxIterations(maxIterations: Int): AIAgentServiceBuilder =
        apply { delegate.maxIterations(maxIterations) }

    actual override fun agentConfig(config: AIAgentConfig): AIAgentServiceBuilder =
        apply { delegate.agentConfig(config) }

    actual override fun <Input, Output> graphStrategy(strategy: AIAgentGraphStrategy<Input, Output>): GraphAgentServiceBuilder<Input, Output> =
        graphStrategy(strategy)

    actual override fun <Input, Output> functionalStrategy(strategy: AIAgentFunctionalStrategy<Input, Output>): FunctionalAgentServiceBuilder<Input, Output> =
        delegate.functionalStrategy(strategy)

    actual override fun build(): GraphAIAgentService<String, String> = delegate.build()
}
