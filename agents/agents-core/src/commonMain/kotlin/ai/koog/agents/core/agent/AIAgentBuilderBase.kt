package ai.koog.agents.core.agent

import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.config.copy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.processor.ResponseProcessor
import ai.koog.serialization.JSONSerializer
import ai.koog.serialization.kotlinx.KotlinxSerializer
import kotlin.time.Clock

/**
 * Shared fluent configuration for agent builders.
 */
public abstract class AIAgentBuilderBase<Self : AIAgentBuilderBase<Self>> internal constructor(
    protected var promptExecutor: PromptExecutor?,
    protected var toolRegistry: ToolRegistry,
    protected var id: String?,
    protected var agentConfig: AIAgentConfig,
    protected var clock: Clock,
) {
    internal constructor(
        serializer: JSONSerializer = KotlinxSerializer(),
    ) : this(
        null,
        ToolRegistry.EMPTY,
        null,
        AIAgentConfig(
            prompt = Prompt.Empty,
            model = ModelNotSet,
            maxAgentIterations = 50,
            serializer = serializer
        ),
        Clock.System,
    )

    private companion object {
        val NoLLMProvider = object : LLMProvider("None", "Provider is not set") {}

        val ModelNotSet = LLModel(
            provider = NoLLMProvider,
            id = "model_not_set"
        )
    }

    protected abstract fun self(): Self

    protected val validatedPromptExecutor: PromptExecutor
        get() = requireNotNull(promptExecutor) { "PromptExecutor must be set" }

    protected val validatedAgentConfig: AIAgentConfig
        get() = when (agentConfig.model) {
            ModelNotSet -> throw IllegalArgumentException("model must be set, plase use .model() on AIAgentBuilder or set AIAgentConfig")
            else -> agentConfig
        }

    /**
     * Sets the `PromptExecutor` to be used by the builder instance.
     *
     * This method configures the builder with the provided `PromptExecutor`, which is responsible
     * for executing prompts against language models, managing tool interactions, and handling output.
     *
     * @param promptExecutor An instance of `PromptExecutor` that will be utilized for processing prompts
     * and interacting with language models.
     * @return The current builder instance for chaining additional configurations.
     */
    public fun promptExecutor(promptExecutor: PromptExecutor): Self = self().apply {
        this.promptExecutor = promptExecutor
    }

    /**
     * Sets the `LLModel` instance to be used by the builder.
     *
     * This method configures the builder with a specified Large Language Model (LLM),
     * representing the model's provider, identifier, capabilities, and constraints such as
     * context length or maximum output tokens.
     *
     * @param model The [LLModel] instance representing the large language model to set.
     * @return The current builder instance for chaining additional configurations.
     */
    public fun llmModel(model: LLModel): Self = self().apply {
        this.agentConfig = agentConfig.copy(model = model)
    }

    /**
     * Sets the given `ToolRegistry` instance to the builder configuration.
     *
     * @param toolRegistry The instance of `ToolRegistry` to be used in the builder.
     * @return The current builder instance for chaining further configurations.
     */
    public fun toolRegistry(toolRegistry: ToolRegistry): Self = self().apply {
        this.toolRegistry = toolRegistry
    }

    /**
     * Sets the identifier for the builder configuration.
     *
     * @param id The identifier string to be set. Can be null.
     * @return The current builder instance for chaining method calls.
     */
    public fun id(id: String?): Self = self().apply {
        this.id = id
    }

    /**
     * Sets the system prompt to be used by the builder.
     *
     * This method configures the prompt with a system-level message that provides
     * instructions or context for a language model.
     *
     * @param systemPrompt The content of the system message to set as the prompt.
     * @return The current builder instance with the updated system prompt.
     */
    public fun systemPrompt(systemPrompt: String): Self = prompt(
        prompt(id = "agent") { system(systemPrompt) }
    )

    /**
     * Sets the prompt to be used by the builder.
     *
     * @param prompt The [Prompt] instance to set.
     * @return The current builder instance.
     */
    public fun prompt(prompt: Prompt): Self = self().apply {
        this.agentConfig = agentConfig.copy(prompt = prompt)
    }

    /**
     * Sets the temperature value for the builder.
     *
     * Temperature is typically used to control the randomness of outputs in language models. Higher values result in more
     * random outputs, while lower values make outputs more deterministic.
     *
     * @param temperature The temperature value to set. It should be a non-negative double, where common values are within
     * the range `[0.0, 1.0]`.
     * @return The current builder instance for method chaining.
     */
    public fun temperature(temperature: Double): Self = self().apply {
        this.agentConfig = agentConfig.copy(prompt = agentConfig.prompt.withParams(agentConfig.prompt.params.copy(temperature = temperature)))
    }

    /**
     * Sets the number of choices to be utilized by the builder instance.
     *
     * This method configures the builder with a specified number of discrete choices,
     * which could be utilized in the decision-making process or output generation.
     *
     * @param numberOfChoices The integer representing the number of choices to configure.
     * Must be a positive value.
     * @return The current builder instance for chaining additional configurations.
     */
    public fun numberOfChoices(numberOfChoices: Int): Self = self().apply {
        this.agentConfig = agentConfig.copy(prompt = agentConfig.prompt.withParams(agentConfig.prompt.params.copy(numberOfChoices = numberOfChoices)))
    }

    /**
     * Sets the response processor for the agent.
     */
    public fun responseProcessor(responseProcessor: ResponseProcessor): Self = self().apply {
        this.agentConfig = agentConfig.copy(responseProcessor = responseProcessor)
    }

    /**
     * Sets the maximum number of iterations for the builder.
     *
     * @param maxIterations The maximum number of iterations to be used. Must be a positive integer.
     * @return The current builder instance.
     */
    public fun maxIterations(maxIterations: Int): Self = self().apply {
        this.agentConfig = agentConfig.copy(maxAgentIterations = maxIterations)
    }

    /**
     * Sets the clock for the agent.
     */
    public fun clock(clock: Clock): Self = self().apply {
        this.clock = clock
    }

    /**
     * Configures the current builder instance using the provided `AIAgentConfig`.
     *
     * This method applies the settings from the given `AIAgentConfig`, such as the prompt, language model,
     * maximum agent iterations, and strategy to handle missing tools, to the builder instance.
     *
     * @param config An `AIAgentConfig` instance containing the configuration settings to be applied.
     * @return The current builder instance for chaining further methods.
     */
    public fun agentConfig(config: AIAgentConfig): Self = self().apply {
        this.agentConfig = config
    }
}
