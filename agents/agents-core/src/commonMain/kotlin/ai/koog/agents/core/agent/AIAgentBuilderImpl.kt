package ai.koog.agents.core.agent

import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.config.MissingToolsConversionStrategy
import ai.koog.agents.core.agent.config.ToolCallDescriber
import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.feature.AIAgentGraphFeature
import ai.koog.agents.core.feature.config.FeatureConfig
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.utils.ConfigureAction
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import kotlinx.datetime.Clock
import kotlin.reflect.typeOf

internal class AIAgentBuilderImpl internal constructor() : AIAgentBuilderAPI {
    @property:PublishedApi
    internal var promptExecutor: PromptExecutor? = null

    @property:PublishedApi
    internal var toolRegistry: ToolRegistry = ToolRegistry.EMPTY

    @property:PublishedApi
    internal var id: String? = null

    @property:PublishedApi
    internal var prompt: Prompt = Prompt.Empty

    @property:PublishedApi
    internal var llmModel: LLModel? = null

    @property:PublishedApi
    internal var temperature: Double = 1.0

    @property:PublishedApi
    internal var numberOfChoices: Int = 1

    @property:PublishedApi
    internal var missingToolsConversionStrategy: MissingToolsConversionStrategy =
        MissingToolsConversionStrategy.Missing(ToolCallDescriber.JSON)

    @property:PublishedApi
    internal var maxIterations: Int = 50

    @property:PublishedApi
    internal var clock: Clock = Clock.System

    public override fun promptExecutor(promptExecutor: PromptExecutor): AIAgentBuilderAPI = apply {
        this.promptExecutor = promptExecutor
    }

    public override fun llmModel(model: LLModel): AIAgentBuilderAPI = apply {
        this.llmModel = model
    }

    public override fun toolRegistry(toolRegistry: ToolRegistry): AIAgentBuilderAPI = apply {
        this.toolRegistry = toolRegistry
    }

    public override fun <Input, Output> graphStrategy(
        strategy: AIAgentGraphStrategy<Input, Output>
    ): GraphAgentBuilder<Input, Output> = GraphAgentBuilder(
        strategy = strategy,
        inputType = strategy.inputType,
        outputType = strategy.outputType,
        id = this.id,
        prompt = this.prompt,
        llmModel = this.llmModel,
        temperature = this.temperature,
        promptExecutor = this.promptExecutor,
        numberOfChoices = this.numberOfChoices,
        maxIterations = this.maxIterations,
        missingToolsConversionStrategy = this.missingToolsConversionStrategy,
        clock = this.clock
    )

    public override fun <Input, Output> functionalStrategy(
        strategy: AIAgentFunctionalStrategy<Input, Output>
    ): FunctionalAgentBuilder<Input, Output> = FunctionalAgentBuilder(
        strategy = strategy,
        id = this.id,
        promptExecutor = this.promptExecutor,
        prompt = this.prompt,
        llmModel = this.llmModel,
        temperature = this.temperature,
        numberOfChoices = this.numberOfChoices,
        maxIterations = this.maxIterations,
        missingToolsConversionStrategy = this.missingToolsConversionStrategy,
        clock = this.clock
    )

    public override fun id(id: String?): AIAgentBuilderAPI = apply {
        this.id = id
    }

    public override fun systemPrompt(systemPrompt: String): AIAgentBuilderAPI = apply {
        this.prompt = ai.koog.prompt.dsl.prompt(id = "agent") { system(systemPrompt) }
    }

    public override fun prompt(prompt: Prompt): AIAgentBuilderAPI = apply {
        this.prompt = prompt
    }

    public override fun temperature(temperature: Double): AIAgentBuilderAPI = apply {
        this.temperature = temperature
    }

    public override fun numberOfChoices(numberOfChoices: Int): AIAgentBuilderAPI = apply {
        this.numberOfChoices = numberOfChoices
    }

    public override fun maxIterations(maxIterations: Int): AIAgentBuilderAPI = apply {
        this.maxIterations = maxIterations
    }

    public override fun agentConfig(config: AIAgentConfig): AIAgentBuilderAPI = apply {
        this.prompt = config.prompt
        this.llmModel = config.model
        this.maxIterations = config.maxAgentIterations
        this.missingToolsConversionStrategy = config.missingToolsConversionStrategy
    }

    public override fun <TConfig : FeatureConfig> install(
        feature: AIAgentGraphFeature<TConfig, *>,
        configure: ConfigureAction<TConfig>
    ): GraphAgentBuilder<String, String> = GraphAgentBuilder(
        strategy = singleRunStrategy(),
        inputType = typeOf<String>(),
        outputType = typeOf<String>(),
        id = this.id,
        prompt = this.prompt,
        llmModel = this.llmModel,
        temperature = this.temperature,
        numberOfChoices = this.numberOfChoices,
        promptExecutor = this.promptExecutor,
        maxIterations = this.maxIterations,
        missingToolsConversionStrategy = this.missingToolsConversionStrategy,
        clock = this.clock,
        featureInstallers = mutableListOf({
            install(feature) {
                configure.configure(this)
            }
        })
    )

    public override fun build(): AIAgent<String, String> {
        return AIAgent(
            promptExecutor = requireNotNull(promptExecutor) { "promptExecutor must be set" },
            strategy = singleRunStrategy(),
            toolRegistry = toolRegistry,
            id = id,
            agentConfig = AIAgentConfig(
                prompt = prompt ?: Prompt.Empty,
                model = requireNotNull(llmModel) { "llmModel must be set" },
                maxAgentIterations = maxIterations,
            ),
            clock = clock
        )
    }
}
