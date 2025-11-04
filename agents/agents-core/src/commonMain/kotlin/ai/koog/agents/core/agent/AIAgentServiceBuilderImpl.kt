@file:OptIn(InternalAgentsApi::class)

package ai.koog.agents.core.agent

import ai.koog.agents.annotations.JavaAPI
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.config.MissingToolsConversionStrategy
import ai.koog.agents.core.agent.config.ToolCallDescriber
import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.params.LLMParams
import kotlinx.datetime.Clock

internal class AIAgentServiceBuilderImpl : AIAgentServiceBuilderAPI {
    internal var promptExecutor: PromptExecutor? = null

    internal var toolRegistry: ToolRegistry = ToolRegistry.EMPTY

    internal var prompt: Prompt = Prompt.Empty

    internal var llmModel: LLModel? = null

    internal var temperature: Double? = null

    internal var numberOfChoices: Int = 1

    internal var maxIterations: Int = 50

    internal var clock: Clock = Clock.System

    internal var missingToolsConversionStrategy: MissingToolsConversionStrategy =
        MissingToolsConversionStrategy.Missing(ToolCallDescriber.JSON)

    public override fun promptExecutor(promptExecutor: PromptExecutor): AIAgentServiceBuilderAPI = apply {
        this.promptExecutor = promptExecutor
    }

    public override fun llmModel(model: LLModel): AIAgentServiceBuilderAPI = apply {
        this.llmModel = model
    }

    public override fun toolRegistry(toolRegistry: ToolRegistry): AIAgentServiceBuilderAPI = apply {
        this.toolRegistry = toolRegistry
    }

    public override fun systemPrompt(systemPrompt: String): AIAgentServiceBuilderAPI = apply {
        this.prompt = prompt(id = "agent") { system(systemPrompt) }
    }

    public override fun prompt(prompt: Prompt): AIAgentServiceBuilderAPI = apply {
        this.prompt = prompt
    }

    public override fun temperature(temperature: Double): AIAgentServiceBuilderAPI = apply {
        this.temperature = temperature
    }

    public override fun numberOfChoices(numberOfChoices: Int): AIAgentServiceBuilderAPI = apply {
        this.numberOfChoices = numberOfChoices
    }

    public override fun maxIterations(maxIterations: Int): AIAgentServiceBuilderAPI = apply {
        this.maxIterations = maxIterations
    }

    @JavaAPI
    public override fun agentConfig(config: AIAgentConfig): AIAgentServiceBuilderAPI = apply {
        this.prompt = config.prompt
        this.llmModel = config.model
        this.maxIterations = config.maxAgentIterations
        this.missingToolsConversionStrategy = config.missingToolsConversionStrategy
    }

    public override fun <Input, Output> graphStrategy(
        strategy: AIAgentGraphStrategy<Input, Output>
    ): GraphAgentServiceBuilder<Input, Output> = GraphAgentServiceBuilder(
        strategy = strategy,
        inputType = strategy.inputType,
        outputType = strategy.outputType,
        prompt = this.prompt,
        llmModel = this.llmModel,
        temperature = this.temperature,
        numberOfChoices = this.numberOfChoices,
        maxIterations = this.maxIterations,
        clock = this.clock,
    ).also {
        // carry promptExecutor/toolRegistry lazily
        it.promptExecutor = this.promptExecutor
        it.toolRegistry = this.toolRegistry
    }

    public override fun <Input, Output> functionalStrategy(
        strategy: AIAgentFunctionalStrategy<Input, Output>
    ): FunctionalAgentServiceBuilder<Input, Output> = FunctionalAgentServiceBuilder(
        strategy = strategy,
        prompt = this.prompt,
        llmModel = this.llmModel,
        temperature = this.temperature,
        numberOfChoices = this.numberOfChoices,
        maxIterations = this.maxIterations,
        clock = this.clock,
    ).also {
        it.promptExecutor = this.promptExecutor
        it.toolRegistry = this.toolRegistry
    }

    public override fun build(): GraphAIAgentService<String, String> {
        val executor = requireNotNull(promptExecutor) { "PromptExecutor must be provided" }
        val model = requireNotNull(llmModel) { "LLModel must be provided" }
        val config = AIAgentConfig(
            prompt = if (prompt === Prompt.Empty) {
                prompt(
                    id = "chat", params = LLMParams(
                        temperature = temperature,
                        numberOfChoices = numberOfChoices
                    )
                ) {}
            } else prompt,
            model = model,
            maxAgentIterations = maxIterations
        )
        return AIAgentServiceHelper.invoke(
            promptExecutor = executor,
            agentConfig = config,
            strategy = singleRunStrategy(),
            toolRegistry = toolRegistry,
            installFeatures = {}
        )
    }
}
