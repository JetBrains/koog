package ai.koog.prompt.executor.llms

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.executor.selection.ModelSelection
import ai.koog.prompt.executor.selection.ModelSelector
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
public open class MultiLLMPromptExecutor @JvmOverloads constructor(
    private val llmClients: Map<LLMProvider, LLMClient>,
    private val fallback: FallbackPromptExecutorSettings? = null
) : PromptExecutor() {
    /**
     * Represents configuration for a fallback large language model (LLM) execution strategy.
     *
     * This class specifies a fallback provider/model pair used when the requested model's
     * provider has no registered client in this executor.
     *
     * This is a routing fallback. It does not handle failures from an already selected client call.
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
         * Logger instance used for logging messages within the [MultiLLMPromptExecutor] class.
         *
         * This logger is utilized to provide debug logs during the execution of prompts and handling of streaming responses.
         * It primarily tracks operations such as prompt execution initiation, tool usage, and responses received from the
         * respective LLM clients.
         *
         * The logger can aid in debugging by capturing detailed information about the state and flow of operations within
         * the class.
         */
        private val logger = KotlinLogging.logger {}
    }

    /**
     * Lazily initialized fallback client for interacting with a fallback LLM provider.
     *
     * Utilizes the fallback provider specified in the `fallback` to retrieve a corresponding
     * `LLMClient` from the `llmClients` collection, if available. This client is intended to
     * handle cases where no specific provider is matched during prompt execution.
     *
     * Returns `null` if `fallback` or its `fallbackProvider` is not specified.
     */
    private val fallbackClient: LLMClient? by lazy { fallback?.fallbackProvider?.let(llmClients::get) }

    init {
        if (fallback != null) {
            check(fallback.fallbackProvider in llmClients.keys) {
                "Fallback client not found for provider: ${fallback.fallbackProvider}"
            }
        }
    }

    override suspend fun execute(
        prompt: Prompt,
        modelSelector: ModelSelector,
        tools: List<ToolDescriptor>,
    ): List<Message.Response> {
        logger.debug { "Executing prompt: $prompt with tools: $tools and modelSelector: $modelSelector" }

        val (client, model) = chooseClientAndModel(modelSelector)
        val response = client.execute(prompt, model, tools)

        logger.debug { "Response: $response" }
        return response
    }

    override fun executeStreaming(
        prompt: Prompt,
        modelSelector: ModelSelector,
        tools: List<ToolDescriptor>
    ): Flow<StreamFrame> {
        logger.debug { "Executing streaming prompt: $prompt with modelSelector: $modelSelector" }
        return flow {
            val (client, model) = chooseClientAndModel(modelSelector)
            emitAll(client.executeStreaming(prompt, model, tools))
        }
    }

    override suspend fun executeMultipleChoices(
        prompt: Prompt,
        modelSelector: ModelSelector,
        tools: List<ToolDescriptor>,
    ): List<LLMChoice> {
        logger.debug { "Executing prompt: $prompt with tools: $tools and modelSelector: $modelSelector" }

        val (client, model) = chooseClientAndModel(modelSelector)
        val choices = client.executeMultipleChoices(prompt, model, tools)

        logger.debug { "Choices: $choices" }
        return choices
    }

    override suspend fun moderate(prompt: Prompt, modelSelector: ModelSelector): ModerationResult {
        logger.debug { "Moderating multi-modal content with modelSelector: $modelSelector" }
        val (client, model) = chooseClientAndModel(modelSelector)
        return client.moderate(prompt, model)
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

    private suspend fun chooseClientAndModel(modelSelector: ModelSelector): Pair<LLMClient, LLModel> {
        val selection = modelSelector.select(models())
        val selected = chooseClientAndModelFromSelection(selection)
        return when {
            selected != null -> selected

            fallback != null -> fallbackClient!! to fallback.fallbackModel

            else -> throw IllegalArgumentException(
                "No model with an available client selected by modelSelector and no fallback model configured"
            )
        }
    }

    private fun chooseClientAndModelFromSelection(selection: ModelSelection): Pair<LLMClient, LLModel>? {
        for (model in selection.ranked) {
            val client = llmClients[model.provider]
            if (client != null) {
                return client to model
            }
        }
        return null
    }
}
