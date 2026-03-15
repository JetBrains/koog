@file:OptIn(InternalAgentsApi::class)

package ai.koog.agents.core.agent

import ai.koog.agents.annotations.JavaAPI
import ai.koog.agents.core.agent.AIAgentBuilderImpl.Companion.ModelNotSet
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.config.copy
import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.serialization.JSONSerializer
import ai.koog.serialization.kotlinx.KotlinxSerializer
import kotlin.time.Clock

internal class AIAgentServiceBuilderImpl(
    serializer: JSONSerializer = KotlinxSerializer(),
) : AIAgentServiceBuilderAPI {
    internal var promptExecutor: PromptExecutor? = null

    internal var toolRegistry: ToolRegistry = ToolRegistry.EMPTY

    internal var config: AIAgentConfig = AIAgentConfig(
        prompt = Prompt.Empty,
        model = ModelNotSet,
        maxAgentIterations = 50,
        serializer = serializer
    )

    internal var clock: Clock = Clock.System

    public override fun promptExecutor(promptExecutor: PromptExecutor): AIAgentServiceBuilderAPI = apply {
        this.promptExecutor = promptExecutor
    }

    public override fun llmModel(model: LLModel): AIAgentServiceBuilderAPI = apply {
        this.config = this.config.copy(model = model)
    }

    public override fun toolRegistry(toolRegistry: ToolRegistry): AIAgentServiceBuilderAPI = apply {
        this.toolRegistry = toolRegistry
    }

    public override fun systemPrompt(systemPrompt: String): AIAgentServiceBuilderAPI =
        this.prompt(prompt(id = "agent") { system(systemPrompt) })

    public override fun prompt(prompt: Prompt): AIAgentServiceBuilderAPI = apply {
        this.config = config.copy(prompt = prompt)
    }

    public override fun temperature(temperature: Double): AIAgentServiceBuilderAPI =
        prompt(config.prompt.withParams(config.prompt.params.copy(temperature = temperature)))

    public override fun numberOfChoices(numberOfChoices: Int): AIAgentServiceBuilderAPI =
        prompt(config.prompt.withParams(config.prompt.params.copy(numberOfChoices = numberOfChoices)))

    public override fun maxIterations(maxIterations: Int): AIAgentServiceBuilderAPI = apply {
        this.config = config.copy(maxAgentIterations = maxIterations)
    }

    @JavaAPI
    public override fun agentConfig(config: AIAgentConfig): AIAgentServiceBuilderAPI = apply {
        this.config = config
    }

    public override fun <Input, Output> graphStrategy(
        strategy: AIAgentGraphStrategy<Input, Output>
    ): GraphAgentServiceBuilder<Input, Output> = GraphAgentServiceBuilder(
        strategy = strategy,
        inputType = strategy.inputType,
        outputType = strategy.outputType,
        config = this.config,
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
        config = this.config,
        clock = this.clock,
    ).also {
        it.promptExecutor = this.promptExecutor
        it.toolRegistry = this.toolRegistry
    }

    public override fun build(): GraphAIAgentService<String, String> {
        val executor = requireNotNull(promptExecutor) { "PromptExecutor must be provided" }
        return AIAgentServiceHelper.invoke(
            promptExecutor = executor,
            agentConfig = validateConfig(config),
            strategy = singleRunStrategy(),
            toolRegistry = toolRegistry,
            installFeatures = {}
        )
    }
}
