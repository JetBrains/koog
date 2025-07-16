package ai.koog.prompt.executor.llms

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.model.LLMChoice
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * MultiLLMPromptExecutor is a class responsible for executing prompts
 * across multiple Large Language Models (LLMs). This implementation supports direct execution
 * with specific LLM clients or utilizes a fallback strategy if no primary LLM client is available
 * for the requested provider.
 *
 * @constructor Constructs an executor instance with a map of LLM providers associated with their respective clients.
 * @param llmClients A map containing LLM providers associated with their respective [LLMClient]s.
 * @param fallback Optional settings to configure the fallback mechanism in case a specific provider is not directly available.
 */
public open class MultiLLMPromptExecutor(
    private val llmClients: Map<LLMProvider, LLMClient>,
    private val fallback: FallbackPromptExecutorSettings? = null
) : PromptExecutor {
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
    public constructor(vararg llmClients: Pair<LLMProvider, LLMClient>) : this(mapOf(*llmClients))

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

    /**
     * Lazily initialized fallback client for interacting with a fallback LLM provider.
     *
     * Utilizes the fallback provider specified in the `fallbackSettings` to retrieve a corresponding
     * `LLMClient` from the `llmClients` collection, if available. This client is intended to
     * handle cases where no specific provider is matched during prompt execution.
     *
     * Returns `null` if `fallbackSettings` or its `fallbackProvider` is not specified.
     */
    private val fallbackClient: LLMClient? by lazy { fallback?.fallbackProvider?.let(llmClients::get) }

    init {
        if (fallback != null) {
            check(fallback.fallbackProvider in llmClients.keys) {
                "Fallback client not found for provider: ${fallback.fallbackProvider}"
            }
        }
    }

    /**
     * Executes a given prompt using the specified tools and model, and returns a list of response messages.
     *
     * @param prompt The `Prompt` to be executed, containing the input messages and parameters.
     * @param tools A list of `ToolDescriptor` objects representing external tools available for use during execution.
     * @param model The LLM model to use for execution.
     * @return A list of `Message.Response` objects containing the responses generated based on the prompt.
     * @throws IllegalArgumentException If no client is found for the model's provider and no fallback settings are configured.
     */
    override suspend fun execute(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): List<Message.Response> {
        logger.debug { "Executing prompt: $prompt with tools: $tools and model: $model" }

        val provider = model.provider

        val response = when {
            provider in llmClients -> llmClients[provider]!!.execute(prompt, model, tools)
            fallback != null -> fallbackClient!!.execute(
                prompt,
                fallback.fallbackModel,
                tools
            )
            else -> throw IllegalArgumentException("No client found for provider: $provider")
        }

        logger.debug { "Response: $response" }

        return response
    }

    /**
     * Executes the given prompt with the specified model and streams the response in chunks as a flow.
     *
     * @param prompt The prompt to execute, containing the messages and parameters.
     * @param model The LLM model to use for execution.
     **/
    override suspend fun executeStreaming(prompt: Prompt, model: LLModel): Flow<String> = flow {
        logger.debug { "Executing streaming prompt: $prompt with model: $model" }

        val provider = model.provider
        val client = llmClients[provider] ?: throw IllegalArgumentException("No client found for provider: $provider")

        val responseFlow = client.executeStreaming(prompt, model)

        responseFlow.collect { chunk ->
            emit(chunk)
        }
    }

    /**
     * Executes a given prompt using the specified tools and model and returns a list of model choices.
     *
     * @param prompt The `Prompt` to be executed, containing the input messages and parameters.
     * @param tools A list of `ToolDescriptor` objects representing external tools available for use during execution.
     * @param model The LLM model to use for execution.
     * @return A list of `LLMChoice` objects containing the choices generated based on the prompt.
     * @throws IllegalArgumentException If no client is found for the model's provider and no fallback settings are configured.
     */
    override suspend fun executeMultipleChoices(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): List<LLMChoice> {
        logger.debug { "Executing prompt: $prompt with tools: $tools and model: $model" }

        val provider = model.provider

        val choices = when {
            provider in llmClients -> llmClients[provider]!!.executeMultipleChoices(prompt, model, tools)
            fallback != null -> fallbackClient!!.executeMultipleChoices(
                prompt,
                fallback.fallbackModel,
                tools
            )
            else -> throw IllegalArgumentException("No client found for provider: $provider")
        }

        logger.debug { "Choices: $choices" }

        return choices
    }

    /**
     * Moderates the provided multi-modal content using the specified model.
     *
     * @param prompt The `Prompt` containing the content to be moderated.
     * @param model The `LLModel` to use for moderation, including its ID and provider information.
     * @return A `ModerationResult` representing the result of the moderation process.
     * @throws IllegalArgumentException If no client is found for the model's provider.
     */
    override suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult {
        logger.debug { "Moderating multi-modal content with model: ${model.id}" }

        val provider = model.provider
        val client = llmClients[provider] ?: throw IllegalArgumentException("No client found for provider: $provider")

        return client.moderate(prompt, model)
    }
}
