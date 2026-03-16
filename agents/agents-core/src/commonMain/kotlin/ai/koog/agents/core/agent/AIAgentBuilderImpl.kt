package ai.koog.agents.core.agent

import ai.koog.agents.core.agent.AIAgentBuilderImpl.Companion.ModelNotSet
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.config.copy
import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.feature.AIAgentGraphFeature
import ai.koog.agents.core.feature.config.FeatureConfig
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.utils.BuilderChainAction
import ai.koog.agents.core.utils.ConfigureAction
import ai.koog.agents.planner.AIAgentPlannerStrategy
import ai.koog.agents.planner.AIAgentPlannerStrategyBuilder
import ai.koog.agents.planner.TypedAgentPlannerStrategyBuilder
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.serialization.JSONSerializer
import ai.koog.serialization.kotlinx.KotlinxSerializer
import ai.koog.serialization.typeToken
import kotlin.time.Clock

internal class AIAgentBuilderImpl internal constructor(
    serializer: JSONSerializer = KotlinxSerializer()
) : AIAgentBuilderAPI {
    companion object {
        internal val NoLLMProvider = object : LLMProvider("None", "Provider is not set") {}

        internal val ModelNotSet = LLModel(
            provider = NoLLMProvider,
            id = "model_not_set"
        )
    }

    internal var config: AIAgentConfig = AIAgentConfig(
        prompt = Prompt.Empty,
        model = ModelNotSet,
        maxAgentIterations = 50,
        serializer = serializer
    )

    @property:PublishedApi
    internal var promptExecutor: PromptExecutor? = null

    @property:PublishedApi
    internal var toolRegistry: ToolRegistry = ToolRegistry.EMPTY

    @property:PublishedApi
    internal var id: String? = null

    @property:PublishedApi
    internal var clock: Clock = Clock.System

    override fun promptExecutor(promptExecutor: PromptExecutor): AIAgentBuilderAPI = apply {
        this.promptExecutor = promptExecutor
    }

    override fun llmModel(model: LLModel): AIAgentBuilderAPI = apply {
        this.config = this.config.copy(model = model)
    }

    override fun toolRegistry(toolRegistry: ToolRegistry): AIAgentBuilderAPI = apply {
        this.toolRegistry = toolRegistry
    }

    override fun <Input, Output> graphStrategy(
        strategy: AIAgentGraphStrategy<Input, Output>
    ): GraphAgentBuilder<Input, Output> = GraphAgentBuilder(
        strategy = strategy,
        inputType = strategy.inputType,
        outputType = strategy.outputType,
        promptExecutor = this.promptExecutor,
        id = this.id,
        config = config,
        clock = this.clock,
        toolRegistry = this.toolRegistry
    )

    override fun <Input, Output> functionalStrategy(
        strategy: AIAgentFunctionalStrategy<Input, Output>
    ): FunctionalAgentBuilder<Input, Output> = FunctionalAgentBuilder(
        strategy = strategy,
        id = this.id,
        promptExecutor = this.promptExecutor,
        config = this.config,
        clock = this.clock,
        toolRegistry = this.toolRegistry
    )

    override fun <Input, Output> plannerStrategy(
        strategy: AIAgentPlannerStrategy<Input, Output, *>
    ): PlannerAgentBuilder<Input, Output> = PlannerAgentBuilder(
        strategy = strategy,
        id = this.id,
        promptExecutor = this.promptExecutor,
        config = this.config,
        clock = this.clock,
        toolRegistry = this.toolRegistry
    )

    override fun <Input : Any, Output : Any> plannerStrategy(
        name: String,
        buildStrategy: BuilderChainAction<AIAgentPlannerStrategyBuilder, TypedAgentPlannerStrategyBuilder<Input, Output>>
    ): PlannerAgentBuilder<Input, Output> = plannerStrategy(
        buildStrategy.configure(AIAgentPlannerStrategyBuilder(name)).build()
    )

    override fun id(id: String?): AIAgentBuilderAPI = apply {
        this.id = id
    }

    override fun systemPrompt(systemPrompt: String): AIAgentBuilderAPI =
        prompt(Prompt.build(id = "agent") { system(systemPrompt) })

    override fun prompt(prompt: Prompt): AIAgentBuilderAPI = apply {
        this.config = config.copy(prompt = prompt)
    }

    override fun temperature(temperature: Double): AIAgentBuilderAPI =
        prompt(config.prompt.withParams(config.prompt.params.copy(temperature = temperature)))

    override fun numberOfChoices(numberOfChoices: Int): AIAgentBuilderAPI =
        prompt(config.prompt.withParams(config.prompt.params.copy(numberOfChoices = numberOfChoices)))

    override fun maxIterations(maxIterations: Int): AIAgentBuilderAPI = apply {
        this.config = config.copy(maxAgentIterations = maxIterations)
    }

    override fun agentConfig(config: AIAgentConfig): AIAgentBuilderAPI = apply {
        this.config = config
    }

    override fun <TConfig : FeatureConfig> install(
        feature: AIAgentGraphFeature<TConfig, *>,
        configure: ConfigureAction<TConfig>
    ): GraphAgentBuilder<String, String> = GraphAgentBuilder(
        strategy = singleRunStrategy(),
        inputType = typeToken<String>(),
        outputType = typeToken<String>(),
        promptExecutor = this.promptExecutor,
        id = this.id,
        config = config,
        clock = this.clock,
        toolRegistry = this.toolRegistry,
        featureInstallers = mutableListOf({
            install(feature) {
                configure.configure(this)
            }
        })
    )

    override fun build(): AIAgent<String, String> {
        return AIAgent(
            promptExecutor = requireNotNull(promptExecutor) { "promptExecutor must be set" },
            strategy = singleRunStrategy(),
            toolRegistry = toolRegistry,
            id = id,
            agentConfig = validateConfig(config),
            clock = clock
        )
    }
}

internal fun validateConfig(config: AIAgentConfig): AIAgentConfig = when (config.model) {
    ModelNotSet -> throw IllegalArgumentException("model must be set, plase use .model() on AIAgentBuilder or set AIAgentConfig")
    else -> config
}
