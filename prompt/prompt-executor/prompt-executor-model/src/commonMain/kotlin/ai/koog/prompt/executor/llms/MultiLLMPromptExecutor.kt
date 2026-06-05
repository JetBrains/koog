package ai.koog.prompt.executor.llms

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.Prompt
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.model.ModelResolutionException
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.executor.model.PromptExecutorOperation
import ai.koog.prompt.executor.model.ResolvedModel
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.LLMChoice
import ai.koog.prompt.message.Message
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.prompt.structure.json.generator.BasicJsonSchemaGenerator
import ai.koog.prompt.structure.json.generator.StandardJsonSchemaGenerator
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmSynthetic

/**
 * MultiLLMPromptExecutor is a class responsible for executing prompts
 * across multiple Large Language Models (LLMs). This implementation supports direct execution
 * with specific LLM clients or utilizes a fallback strategy if no primary LLM client is available
 * for the requested provider.
 *
 * @constructor Constructs an executor instance with a map of LLM providers associated with their respective clients.
 * @param llmClients A map containing LLM providers associated with their respective [LLMClient]s.
 * @param fallback Optional settings to configure the fallback mechanism in case a specific provider is not directly available.
 *
 * This class remains open for source and binary compatibility with existing subclasses. For new
 * custom prompt executors, prefer extending [ai.koog.prompt.executor.model.DynamicPromptExecutor].
 * If you subclass this class, prefer overriding the [ResolvedModel]-based overloads. Override the
 * [LLModel]-based overloads only when you intentionally take over the full model-resolution and
 * execution flow.
 */
public open class MultiLLMPromptExecutor @JvmOverloads constructor(
    private val llmClients: Map<LLMProvider, LLMClient>,
    private val fallback: FallbackPromptExecutorSettings? = null
) : PromptExecutor() {
    /**
     * Represents configuration for a fallback large language model (LLM) execution strategy.
     *
     * This class is used to specify a fallback LLM provider and model that can be utilized
     * when the primary LLM execution fails. It ensures that the fallback model is associated
     * with the specified fallback provider.
     *
     * @property fallbackProvider The LLMProvider responsible for handling fallback requests.
     * @property fallbackModel The LLModel instance to be used for fallback execution.
     *
     * @throws IllegalArgumentException If the provider of the fallback model does not match the
     * fallback provider.
     */
    public data class FallbackPromptExecutorSettings(
        val fallbackProvider: LLMProvider,
        val fallbackModel: LLModel
    ) {
        init {
            check(fallbackModel.provider == fallbackProvider) {
                "LLM model provider must match the fallback provider"
            }
        }
    }

    /**
     * Initializes a new instance of the `MultiLLMPromptExecutor` class with multiple LLM clients.
     *
     * Allows specifying a variable number of client-provider pairs, where each pair links a specific
     * `LLMProvider` with a corresponding implementation of `LLMClient`. All provided pairs are
     * internally converted into a map for efficient access and management of clients by their associated
     * providers.
     *
     * @param llmClients Variable number of pairs, where each pair consists of an `LLMProvider` representing
     *                   the provider and a `LLMClient` for communication with that provider.
     */
    @JvmOverloads
    public constructor (
        vararg llmClients: Pair<LLMProvider, LLMClient>,
        fallback: FallbackPromptExecutorSettings? = null
    ) : this(llmClients = mapOf(*llmClients), fallback = fallback)

    /**
     * Secondary constructor for `MultiLLMPromptExecutor` that accepts a list of `LLMClient` instances.
     * The provided clients are processed to create a mapping of `LLMProvider` to their respective `LLMClient`.
     *
     * @param llmClients Vararg parameter of `LLMClient` instances used to construct the executor.
     */
    @JvmOverloads
    public constructor (
        llmClients: List<LLMClient>,
        fallback: FallbackPromptExecutorSettings? = null
    ) : this(
        llmClients = llmClients.map {
            it.llmProvider() to it
        }.associateBy({ it.first }, { it.second }),
        fallback = fallback
    )

    /**
     * Secondary constructor for `MultiLLMPromptExecutor` that accepts a variable number of `LLMClient` instances.
     * The provided clients are processed to create a mapping of `LLMProvider` to their respective `LLMClient`.
     *
     * @param llmClients Vararg parameter of `LLMClient` instances used to construct the executor.
     */
    @JvmOverloads
    public constructor (vararg llmClients: LLMClient) : this(llmClients.toList())

    /**
     * Companion object for `MultiLLMPromptExecutor` class.
     *
     * Provides shared utilities and constants, including a logger instance for logging
     * events and debugging information related to the execution of prompts using
     * multiple LLM clients.
     */
    private companion object {
        /**
         * Logger instance used for logging messages within the LLMPromptExecutor and MultiLLMPromptExecutor classes.
         *
         * This logger is utilized to provide debug logs during the execution of prompts and handling of streaming responses.
         * It primarily tracks operations such as prompt execution initiation, tool usage, and responses received from the
         * respective LLM clients.
         *
         * The logger can aid in debugging by capturing detailed information about the state and flow of operations within
         * the respective classes.
         */
        private val logger = KotlinLogging.logger("ai.koog.prompt.executor.llms.LLMPromptExecutor")
    }

    init {
        if (fallback != null) {
            check(fallback.fallbackProvider in llmClients.keys) {
                "Fallback client not found for provider: ${fallback.fallbackProvider}"
            }
        }
    }

    /**
     * Resolves the [LLModel] to use for the given [promptExecutorOperation] by selecting a known
     * client for the model's provider, falling back to [FallbackPromptExecutorSettings.fallbackModel]
     * when no direct client matches.
     *
     * @throws ModelResolutionException If no client is found for the model's provider and no fallback settings are configured.
     */
    override suspend fun resolveModel(
        model: LLModel,
        promptExecutorOperation: PromptExecutorOperation
    ): ResolvedModel = when {
        model.provider in llmClients -> ResolvedModel(effectiveModel = model)

        fallback != null -> ResolvedModel(effectiveModel = fallback.fallbackModel)

        else -> throw ModelResolutionException(
            model,
            "No client found for provider: ${model.provider}"
        )
    }

    /**
     * Executes a given prompt using the specified tools and model, and returns the assistant response.
     *
     * This legacy extension point is kept open for existing subclasses. New subclasses should prefer
     * overriding the [ResolvedModel]-based overload. If this method is overridden without calling
     * `super`, [resolveModel] is bypassed.
     */
    override suspend fun execute(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ): Message.Assistant = execute(prompt, resolveModel(model, PromptExecutorOperation.Execute), tools)

    /**
     * Executes a given prompt using the specified tools and resolved model, and returns the assistant response.
     *
     * @param prompt The `Prompt` to be executed, containing the input messages and parameters.
     * @param resolvedModel The resolved LLM model to use for execution.
     * @param tools A list of `ToolDescriptor` objects representing external tools available for use during execution.
     * @return A `Message.Assistant` containing the response generated based on the prompt.
     */
    override suspend fun execute(
        prompt: Prompt,
        model: ResolvedModel,
        tools: List<ToolDescriptor>
    ): Message.Assistant {
        val effectiveModel = model.effectiveModel
        logger.debug { "Executing prompt: $prompt with tools: $tools and model: $effectiveModel" }

        val response = clientFor(effectiveModel).execute(prompt, effectiveModel, tools)

        logger.debug { "Response: $response" }

        return response
    }

    /**
     * Executes the given prompt with the specified model and streams the response in chunks as a flow.
     *
     * This legacy extension point is kept open for existing subclasses. New subclasses should prefer
     * overriding the [ResolvedModel]-based overload. If this method is overridden without calling
     * `super`, [resolveModel] is bypassed.
     */
    @JvmSynthetic
    override fun executeStreaming(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ): Flow<StreamFrame> = flow {
        emitAll(executeStreaming(prompt, resolveModel(model, PromptExecutorOperation.Streaming), tools))
    }

    /**
     * Executes the given prompt with the specified model and streams the response in chunks as a flow.
     *
     * @param prompt The prompt to execute, containing the messages and parameters.
     * @param model The resolved LLM model to use for execution.
     * @param tools A list of `ToolDescriptor` objects representing external tools available for use during execution.
     **/
    @JvmSynthetic
    override fun executeStreaming(
        prompt: Prompt,
        resolvedModel: ResolvedModel,
        tools: List<ToolDescriptor>
    ): Flow<StreamFrame> {
        val effectiveModel = resolvedModel.effectiveModel
        logger.debug { "Executing streaming prompt: $prompt with model: $effectiveModel" }
        return flow {
            emitAll(
                clientFor(effectiveModel).executeStreaming(prompt, effectiveModel, tools)
            )
        }
    }

    /**
     * Executes a given prompt using the specified tools and model and returns a list of model choices.
     *
     * This legacy extension point is kept open for existing subclasses. New subclasses should prefer
     * overriding the [ResolvedModel]-based overload. If this method is overridden without calling
     * `super`, [resolveModel] is bypassed.
     */
    override suspend fun executeMultipleChoices(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ): LLMChoice = executeMultipleChoices(prompt, resolveModel(model, PromptExecutorOperation.MultipleChoices), tools)

    /**
     * Executes a given prompt using the specified tools and model and returns a list of model choices.
     *
     * @param prompt The `Prompt` to be executed, containing the input messages and parameters.
     * @param tools A list of `ToolDescriptor` objects representing external tools available for use during execution.
     * @param resolvedModel The resolved LLM model to use for execution.
     * @return A list of `LLMChoice` objects containing the choices generated based on the prompt.
     */
    override suspend fun executeMultipleChoices(
        prompt: Prompt,
        resolvedModel: ResolvedModel,
        tools: List<ToolDescriptor>
    ): LLMChoice {
        val effectiveModel = resolvedModel.effectiveModel
        logger.debug { "Executing prompt: $prompt with tools: $tools and model: $effectiveModel" }
        val choices = clientFor(effectiveModel).executeMultipleChoices(prompt, effectiveModel, tools)
        logger.debug { "Choices: $choices" }
        return choices
    }

    /**
     * Moderates the provided multi-modal content using the specified model.
     *
     * This legacy extension point is kept open for existing subclasses. New subclasses should prefer
     * overriding the [ResolvedModel]-based overload. If this method is overridden without calling
     * `super`, [resolveModel] is bypassed.
     */
    override suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult =
        moderate(prompt, resolveModel(model, PromptExecutorOperation.Moderate))

    /**
     * Moderates the provided multi-modal content using the specified resolved model.
     *
     * @param prompt The `Prompt` containing the content to be moderated.
     * @param model The resolved `LLModel` to use for moderation.
     * @return A `ModerationResult` representing the result of the moderation process.
     */
    override suspend fun moderate(prompt: Prompt, model: ResolvedModel): ModerationResult {
        val effectiveModel = model.effectiveModel
        logger.debug { "Moderating multi-modal content with model: ${effectiveModel.id}" }
        return clientFor(effectiveModel).moderate(prompt, effectiveModel)
    }

    private fun clientFor(effectiveModel: LLModel): LLMClient =
        requireNotNull(llmClients[effectiveModel.provider]) {
            "No client found for provider: ${effectiveModel.provider}"
        }

    override suspend fun models(): List<LLModel> {
        logger.debug { "Fetching available models from all clients" }

        return llmClients.values.flatMap { client ->
            client.models()
        }
    }

    override fun getStandardJsonSchemaGenerator(model: LLModel): StandardJsonSchemaGenerator {
        val provider = model.provider
        val client = llmClients[provider] ?: throw IllegalArgumentException("No client found for provider: $provider")

        return client.getStandardJsonSchemaGenerator()
    }

    override fun getBasicJsonSchemaGenerator(model: LLModel): BasicJsonSchemaGenerator {
        val provider = model.provider
        val client = llmClients[provider] ?: throw IllegalArgumentException("No client found for provider: $provider")

        return client.getBasicJsonSchemaGenerator()
    }

    override fun close() {
        llmClients.forEach { (_, client) -> client.close() }
    }
}
