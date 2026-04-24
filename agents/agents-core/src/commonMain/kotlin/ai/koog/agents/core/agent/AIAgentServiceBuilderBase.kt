package ai.koog.agents.core.agent

import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.processor.ResponseProcessor
import ai.koog.serialization.JSONSerializer
import ai.koog.serialization.kotlinx.KotlinxSerializer

/**
 * Shared fluent configuration for agent service builders.
 */
public abstract class AIAgentServiceBuilderBase<Self : AIAgentServiceBuilderBase<Self>> internal constructor(
    protected var promptExecutor: PromptExecutor?,
    protected var toolRegistry: ToolRegistry,
    protected var config: AIAgentConfig,
) {
    internal constructor(
        serializer: JSONSerializer = KotlinxSerializer(),
    ) : this(
        null,
        ToolRegistry.EMPTY,
        AIAgentConfig(
            prompt = Prompt.Empty,
            model = ModelNotSet,
            maxAgentIterations = 50,
            serializer = serializer
        ),
    )

    protected companion object {
        protected val NoLLMProvider: LLMProvider = object : LLMProvider("None", "Provider is not set") {}

        protected val ModelNotSet: LLModel = LLModel(
            provider = NoLLMProvider,
            id = "model_not_set"
        )
    }

    protected abstract fun self(): Self

    protected val validatedPromptExecutor: PromptExecutor
        get() = requireNotNull(promptExecutor) { "PromptExecutor must be set" }

    protected val validatedConfig: AIAgentConfig
        get() = when (config.model) {
            ModelNotSet -> throw IllegalArgumentException("model must be set, please use .llmModel() or set AIAgentConfig")
            else -> config
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
        this.config = config.copy(model = model)
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
     * Sets the system prompt to be used by the builder.
     *
     * This method configures the prompt with a system-level message that provides
     * instructions or context for a language model.
     *
     * @param systemPrompt The content of the system message to set as the prompt.
     * @return The current builder instance with the updated system prompt.
     */
    public fun systemPrompt(systemPrompt: String): Self =
        prompt(prompt(config.prompt) { system(systemPrompt) })

    /**
     * Sets the prompt to be used by the builder.
     *
     * @param prompt The [Prompt] instance to set.
     * @return The current builder instance.
     */
    public fun prompt(prompt: Prompt): Self = self().apply {
        this.config = config.copy(prompt = prompt)
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
        this.config = config.copy(prompt = config.prompt.withParams(config.prompt.params.copy(temperature = temperature)))
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
        this.config = config.copy(prompt = config.prompt.withParams(config.prompt.params.copy(numberOfChoices = numberOfChoices)))
    }

    /**
     * Sets the response processor for the agent.
     */
    public fun responseProcessor(responseProcessor: ResponseProcessor): Self = self().apply {
        this.config = config.copy(responseProcessor = responseProcessor)
    }

    /**
     * Sets the maximum number of iterations for the builder.
     *
     * @param maxIterations The maximum number of iterations to be used. Must be a positive integer.
     * @return The current builder instance.
     */
    public fun maxIterations(maxIterations: Int): Self = self().apply {
        this.config = config.copy(maxAgentIterations = maxIterations)
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
        this.config = config
    }
}
