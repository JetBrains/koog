package ai.koog.prompt.executor.llms

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.model.ExecutionCompleted
import ai.koog.prompt.executor.model.ExecutionDispatched
import ai.koog.prompt.executor.model.ExecutionFailed
import ai.koog.prompt.executor.model.ExecutionRequested
import ai.koog.prompt.executor.model.HookablePromptExecutor
import ai.koog.prompt.executor.model.ModerationCompleted
import ai.koog.prompt.executor.model.ModerationDispatched
import ai.koog.prompt.executor.model.ModerationFailed
import ai.koog.prompt.executor.model.ModerationRequested
import ai.koog.prompt.executor.model.MultipleChoicesCompleted
import ai.koog.prompt.executor.model.MultipleChoicesDispatched
import ai.koog.prompt.executor.model.MultipleChoicesFailed
import ai.koog.prompt.executor.model.MultipleChoicesRequested
import ai.koog.prompt.executor.model.PromptExecutionContext
import ai.koog.prompt.executor.model.StreamingCompleted
import ai.koog.prompt.executor.model.StreamingDispatched
import ai.koog.prompt.executor.model.StreamingFailed
import ai.koog.prompt.executor.model.StreamingFrameReceived
import ai.koog.prompt.executor.model.StreamingRequested
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.LLMChoice
import ai.koog.prompt.message.Message
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.prompt.structure.json.generator.BasicJsonSchemaGenerator
import ai.koog.prompt.structure.json.generator.StandardJsonSchemaGenerator
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.Flow
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
) : HookablePromptExecutor() {
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

    /**
     * Executes a given prompt using the specified tools and model, and returns a list of response messages.
     *
     * @param prompt The `Prompt` to be executed, containing the input messages and parameters.
     * @param tools A list of `ToolDescriptor` objects representing external tools available for use during execution.
     * @param model The LLM model to use for execution.
     * @return A list of `Message.Response` objects containing the responses generated based on the prompt.
     * @throws IllegalArgumentException If no client is found for the model's provider and no fallback settings are configured.
     */
    override suspend fun execute(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
        context: PromptExecutionContext
    ): List<Message.Response> {
        context.handle(ExecutionRequested(context.promptExecutionId, prompt, model, tools))
        logger.debug { "Executing prompt: $prompt with tools: $tools and model: $model" }

        val provider = model.provider
        val (client, effectiveModel) = try {
            when {
                provider in llmClients -> llmClients[provider]!! to model
                fallback != null -> fallbackClient!! to fallback.fallbackModel
                else -> throw IllegalArgumentException("No client found for provider: $provider")
            }
        } catch (error: Throwable) {
            context.handle(ExecutionFailed(context.promptExecutionId, prompt, model, tools, error))
            throw error
        }

        context.handle(ExecutionDispatched(context.promptExecutionId, prompt, effectiveModel, tools))
        val response = try {
            client.execute(prompt, effectiveModel, tools)
        } catch (error: Throwable) {
            context.handle(ExecutionFailed(context.promptExecutionId, prompt, effectiveModel, tools, error))
            throw error
        }

        context.handle(ExecutionCompleted(context.promptExecutionId, prompt, effectiveModel, tools, response))
        logger.debug { "Response: $response" }
        return response
    }

    /**
     * Executes the given prompt with the specified model and streams the response in chunks as a flow.
     *
     * @param prompt The prompt to execute, containing the messages and parameters.
     * @param model The LLM model to use for execution.
     * @param tools A list of `ToolDescriptor` objects representing external tools available for use during execution.
     **/
    override fun executeStreaming(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
        context: PromptExecutionContext
    ): Flow<StreamFrame> {
        return flow {
            context.handle(StreamingRequested(context.promptExecutionId, prompt, model, tools))
            logger.debug { "Executing streaming prompt: $prompt with model: $model" }

            val provider = model.provider
            val client = try {
                requireNotNull(llmClients[model.provider]) { "No client found for provider: $provider" }
            } catch (error: Throwable) {
                context.handle(StreamingFailed(context.promptExecutionId, prompt, model, tools, error))
                throw error
            }

            context.handle(StreamingDispatched(context.promptExecutionId, prompt, model, tools))
            try {
                client.executeStreaming(prompt, model, tools).collect { frame ->
                    context.handle(StreamingFrameReceived(context.promptExecutionId, prompt, model, tools, frame))
                    emit(frame)
                }
            } catch (error: Throwable) {
                context.handle(StreamingFailed(context.promptExecutionId, prompt, model, tools, error))
                throw error
            }

            context.handle(StreamingCompleted(context.promptExecutionId, prompt, model, tools))
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
    override suspend fun executeMultipleChoices(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
        context: PromptExecutionContext
    ): List<LLMChoice> {
        context.handle(MultipleChoicesRequested(context.promptExecutionId, prompt, model, tools))
        logger.debug { "Executing prompt: $prompt with tools: $tools and model: $model" }

        val provider = model.provider
        val (client, effectiveModel) = try {
            when {
                provider in llmClients -> llmClients[provider]!! to model
                fallback != null -> fallbackClient!! to fallback.fallbackModel
                else -> throw IllegalArgumentException("No client found for provider: $provider")
            }
        } catch (error: Throwable) {
            context.handle(MultipleChoicesFailed(context.promptExecutionId, prompt, model, tools, error))
            throw error
        }

        context.handle(MultipleChoicesDispatched(context.promptExecutionId, prompt, effectiveModel, tools))
        val choices = try {
            client.executeMultipleChoices(prompt, effectiveModel, tools)
        } catch (error: Throwable) {
            context.handle(MultipleChoicesFailed(context.promptExecutionId, prompt, effectiveModel, tools, error))
            throw error
        }

        context.handle(MultipleChoicesCompleted(context.promptExecutionId, prompt, effectiveModel, tools, choices))
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
    override suspend fun moderate(prompt: Prompt, model: LLModel, context: PromptExecutionContext): ModerationResult {
        context.handle(ModerationRequested(context.promptExecutionId, prompt, model))
        logger.debug { "Moderating multi-modal content with model: ${model.id}" }

        val provider = model.provider
        val client = try {
            llmClients[provider] ?: throw IllegalArgumentException("No client found for provider: $provider")
        } catch (error: Throwable) {
            context.handle(ModerationFailed(context.promptExecutionId, prompt, model, error))
            throw error
        }

        context.handle(ModerationDispatched(context.promptExecutionId, prompt, model))
        val result = try {
            client.moderate(prompt, model)
        } catch (error: Throwable) {
            context.handle(ModerationFailed(context.promptExecutionId, prompt, model, error))
            throw error
        }

        context.handle(ModerationCompleted(context.promptExecutionId, prompt, model, result))
        return result
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
