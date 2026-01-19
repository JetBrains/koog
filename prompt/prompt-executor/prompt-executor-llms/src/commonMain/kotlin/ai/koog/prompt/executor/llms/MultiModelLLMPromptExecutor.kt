package ai.koog.prompt.executor.llms

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.LLMChoice
import ai.koog.prompt.message.Message
import ai.koog.prompt.streaming.StreamFrame
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.Flow


/**
 * Tips: Especially for Azure OpenAI services provider . Designed to handle multi-model scenarios run on Azure OpenAI
 * services where base-uri contains deployment name and model in request is ignored.
 *
 * MultiModelLLMPromptExecutor is a class responsible for executing prompts
 * across multiple Large Language Models (LLMs). This implementation maps specific LLM models
 * to their respective clients and supports a fallback strategy when a requested model is not available.
 *
 * @constructor Constructs an executor instance with a map of LLM models associated with their respective clients.
 * @param llmClients A map containing LLM models associated with their respective [LLMClient]s.
 * @param fallback Optional settings to configure the fallback mechanism in case a specific model is not directly available.
 */
public open class MultiModelLLMPromptExecutor(
    private val llmClients: Map<LLModel, LLMClient>,
    private val fallback: FallbackPromptMultiModelExecutorSettings? = null,
) : PromptExecutor {
    /**
     * Represents configuration for a fallback large language model (LLM) execution strategy.
     *
     * This class is used to specify a fallback LLM model and client that can be utilized
     * when the requested model is not available in the primary clients map. It ensures that
     * a default execution path exists for prompt processing.
     *
     * @property fallbackModel The LLModel to be used for fallback execution when the requested model is not found.
     * @property fallbackClient The LLMClient instance responsible for handling fallback requests.
     */
    public data class FallbackPromptMultiModelExecutorSettings(
        val fallbackModel: LLModel,
        val fallbackClient: LLMClient,
    )

    private companion object {
        private val logger = KotlinLogging.logger("ai.koog.prompt.executor.llms.LLMPromptExecutor")
    }

    /**
     * Lazily initialized fallback client for interacting with a fallback LLM model.
     *
     * Returns the fallback client from the `fallback` settings if available.
     * This client is intended to handle cases where the requested model is not found.
     *
     * Returns `null` if `fallback` is not specified.
     */
    private val fallbackClient: LLMClient? by lazy { fallback?.fallbackClient }

    init {
        if (fallback != null) {
            check(fallback.fallbackModel in llmClients.keys) {
                "Fallback model not found in clients: ${fallback.fallbackModel}"
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
     * @throws IllegalArgumentException If no client is found for the model and no fallback settings are configured.
     */
    override suspend fun execute(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
    ): List<Message.Response> {
        logger.debug { "Executing prompt: $prompt with tools: $tools and model: $model" }

        val response =
            when {
                model in llmClients -> llmClients[model]!!.execute(prompt, model, tools)
                fallback != null ->
                    fallbackClient!!.execute(
                        prompt,
                        fallback.fallbackModel,
                        tools,
                    )

                else -> throw IllegalArgumentException("No client found for model: $model")
            }

        logger.debug { "Response: $response" }

        return response
    }

    /**
     * Executes the given prompt with the specified model and streams the response in chunks as a flow.
     *
     * @param prompt The prompt to execute, containing the messages and parameters.
     * @param model The LLM model to use for execution.
     * @param tools A list of `ToolDescriptor` objects representing external tools available for use during execution.
     * @return A Flow of StreamFrame objects representing the streaming response.
     * @throws IllegalArgumentException If no client is found for the model.
     */
    override fun executeStreaming(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
    ): Flow<StreamFrame> {
        logger.debug { "Executing streaming prompt: $prompt with model: $model" }

        val client =
            when {
                model in llmClients -> llmClients[model]
                fallback != null -> fallbackClient
                else -> null
            }

        requireNotNull(client) { "No client found for model: $model" }

        return client.executeStreaming(prompt, model, tools)
    }

    /**
     * Executes a given prompt using the specified tools and model and returns a list of model choices.
     *
     * @param prompt The `Prompt` to be executed, containing the input messages and parameters.
     * @param tools A list of `ToolDescriptor` objects representing external tools available for use during execution.
     * @param model The LLM model to use for execution.
     * @return A list of `LLMChoice` objects containing the choices generated based on the prompt.
     * @throws IllegalArgumentException If no client is found for the model and no fallback settings are configured.
     */
    override suspend fun executeMultipleChoices(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
    ): List<LLMChoice> {
        logger.debug { "Executing prompt: $prompt with tools: $tools and model: $model" }

        val choices =
            when {
                model in llmClients -> llmClients[model]!!.executeMultipleChoices(prompt, model, tools)
                fallback != null ->
                    fallbackClient!!.executeMultipleChoices(
                        prompt,
                        fallback.fallbackModel,
                        tools,
                    )

                else -> throw IllegalArgumentException("No client found for model: $model")
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
     * @throws IllegalArgumentException If no client is found for the model.
     */
    override suspend fun moderate(
        prompt: Prompt,
        model: LLModel,
    ): ModerationResult {
        logger.debug { "Moderating multi-modal content with model: ${model.id}" }

        val client =
            when {
                model in llmClients -> llmClients[model]
                fallback != null -> fallbackClient
                else -> null
            }

        requireNotNull(client) { "No client found for model: $model" }

        return client.moderate(prompt, model)
    }

    /**
     * Retrieves a list of all available model IDs from the registered clients.
     *
     * @return A list of model IDs (Strings) representing all available models.
     */
    override suspend fun models(): List<String> {
        logger.debug { "Fetching available models from all clients" }

        return llmClients.keys.map { it.id }
    }

    /**
     * Closes all registered LLM clients, releasing any resources they may hold.
     */
    override fun close() {
        llmClients.forEach { (_, client) -> client.close() }
    }
}
