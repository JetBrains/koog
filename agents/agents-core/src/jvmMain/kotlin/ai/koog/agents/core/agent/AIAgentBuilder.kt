@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING", "MissingKDocForPublicAPI")

package ai.koog.agents.core.agent

import ai.koog.agents.annotations.JavaAPI
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.config.MissingToolsConversionStrategy
import ai.koog.agents.core.agent.context.AIAgentFunctionalContext
import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.agent.invoke
import ai.koog.agents.core.feature.AIAgentGraphFeature
import ai.koog.agents.core.feature.config.FeatureConfig
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.utils.ConfigureAction
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import kotlinx.datetime.Clock
import java.util.function.BiFunction

public actual class AIAgentBuilder internal actual constructor() : AIAgentBuilderAPI {
    private val delegate: AIAgentBuilderImpl = AIAgentBuilderImpl()

    @property:PublishedApi
    internal actual var promptExecutor: PromptExecutor?
        get() = delegate.promptExecutor
        set(value) {
            delegate.promptExecutor = value
        }

    @property:PublishedApi
    internal actual var toolRegistry: ToolRegistry
        get() = delegate.toolRegistry
        set(value) {
            delegate.toolRegistry = value
        }

    @property:PublishedApi
    internal actual var id: String?
        get() = delegate.id
        set(value) {
            delegate.id = value
        }

    @property:PublishedApi
    internal actual var prompt: Prompt
        get() = delegate.prompt
        set(value) {
            delegate.prompt = value
        }

    @property:PublishedApi
    internal actual var llmModel: LLModel?
        get() = delegate.llmModel
        set(value) {
            delegate.llmModel = value
        }

    @property:PublishedApi
    internal actual var temperature: Double
        get() = delegate.temperature
        set(value) {
            delegate.temperature = value
        }

    @property:PublishedApi
    internal actual var numberOfChoices: Int
        get() = delegate.numberOfChoices
        set(value) {
            delegate.numberOfChoices = value
        }

    @property:PublishedApi
    internal actual var missingToolsConversionStrategy: MissingToolsConversionStrategy
        get() = delegate.missingToolsConversionStrategy
        set(value) {
            delegate.missingToolsConversionStrategy = value
        }

    @property:PublishedApi
    internal actual var maxIterations: Int
        get() = delegate.maxIterations
        set(value) {
            delegate.maxIterations = value
        }

    @property:PublishedApi
    internal actual var clock: Clock
        get() = delegate.clock
        set(value) {
            delegate.clock = value
        }

    public actual override fun promptExecutor(promptExecutor: PromptExecutor): AIAgentBuilder =
        apply { delegate.promptExecutor(promptExecutor) }

    public actual override fun llmModel(model: LLModel): AIAgentBuilder = apply { delegate.llmModel(model) }

    public actual override fun toolRegistry(toolRegistry: ToolRegistry): AIAgentBuilder =
        apply { delegate.toolRegistry(toolRegistry) }

    public actual override fun <Input, Output> graphStrategy(
        strategy: AIAgentGraphStrategy<Input, Output>
    ): GraphAgentBuilder<Input, Output> = delegate.graphStrategy(strategy)

    public actual override fun <Input, Output> functionalStrategy(
        strategy: AIAgentFunctionalStrategy<Input, Output>
    ): FunctionalAgentBuilder<Input, Output> = delegate.functionalStrategy(strategy)

    /**
     * Creates a functional agent builder using the provided strategy.
     *
     * This method allows defining a custom functional strategy for the AI agent.
     *
     * @param Input The type of the input parameter for the strategy's execution logic.
     * @param Output The type of the output returned by the strategy's execution logic.
     * @param name The name identifying the functional strategy. Defaults to "funStrategy".
     * @param strategy The implementation of the functional strategy's execution logic.
     * @return A `FunctionalAgentBuilder` configured with the specified functional strategy.
     */
    @JavaAPI
    @JvmOverloads
    public fun <Input, Output> functionalStrategy(
        name: String = "funStrategy",
        strategy: BiFunction<AIAgentFunctionalContext, Input, Output>
    ): FunctionalAgentBuilder<Input, Output> = functionalStrategy(
        object : NonSuspendAIAgentFunctionalStrategy<Input, Output>(name) {
            override fun executeStrategy(context: AIAgentFunctionalContext, input: Input): Output =
                strategy.apply(context, input)
        }
    )

    public actual override fun id(id: String?): AIAgentBuilder = apply { delegate.id(id) }

    public actual override fun systemPrompt(systemPrompt: String): AIAgentBuilder = apply { delegate.systemPrompt(systemPrompt) }

    public actual override fun prompt(prompt: Prompt): AIAgentBuilder = apply { delegate.prompt(prompt) }

    public actual override fun temperature(temperature: Double): AIAgentBuilder = apply { delegate.temperature(temperature) }

    public actual override fun numberOfChoices(numberOfChoices: Int): AIAgentBuilder =
        apply { delegate.numberOfChoices(numberOfChoices) }

    public actual override fun maxIterations(maxIterations: Int): AIAgentBuilder = apply { delegate.maxIterations(maxIterations) }

    public actual override fun agentConfig(config: AIAgentConfig): AIAgentBuilder = apply { delegate.agentConfig(config) }

    public actual override fun <TConfig : FeatureConfig> install(
        feature: AIAgentGraphFeature<TConfig, *>,
        configure: ConfigureAction<TConfig>
    ): GraphAgentBuilder<String, String> = delegate.install(feature, configure)

    public actual override fun build(): AIAgent<String, String> = delegate.build()
}
