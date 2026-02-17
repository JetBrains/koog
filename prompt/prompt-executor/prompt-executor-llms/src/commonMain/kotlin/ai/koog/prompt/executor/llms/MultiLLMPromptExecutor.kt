package ai.koog.prompt.executor.llms

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.executor.router.LLMClientRouter
import ai.koog.prompt.executor.router.RoundRobinRouter
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.LLMChoice
import ai.koog.prompt.message.Message
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.prompt.structure.json.generator.BasicJsonSchemaGenerator
import ai.koog.prompt.structure.json.generator.StandardJsonSchemaGenerator
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.Flow
import kotlin.jvm.JvmOverloads

/**
 * Executes prompts across multiple Large Language Models (LLMs).
 *
 * Delegates client selection to [LLMClientRouter], which determines which client should
 * handle each request based on the requested model.
 *
 * @param clientRouter Router responsible for selecting appropriate clients for each request
 * @param fallback Optional fallback configuration when no client is available for the requested model
 */
public open class MultiLLMPromptExecutor @JvmOverloads constructor(
    private val clientRouter: LLMClientRouter,
    private val fallback: FallbackPromptExecutorSettings? = null,
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
     * Creates executor with a map of providers to their client lists.
     * Uses [RoundRobinRouter] for load distribution.
     *
     * @param llmClients Map of providers to lists of clients for each provider
     * @param fallback Optional fallback configuration
     */
    @JvmOverloads
    public constructor(
        llmClients: Map<LLMProvider, List<LLMClient>>,
        fallback: FallbackPromptExecutorSettings? = null
    ) : this(RoundRobinRouter(llmClients), fallback)

    /**
     * Creates executor with provider-client pairs.
     * Clients are grouped by provider and routed using [RoundRobinRouter].
     *
     * @param llmClients Provider-client pairs
     * @param fallback Optional fallback configuration
     */
    @JvmOverloads
    public constructor (
        vararg llmClients: Pair<LLMProvider, LLMClient>,
        fallback: FallbackPromptExecutorSettings? = null
    ) : this(RoundRobinRouter(*llmClients), fallback = fallback)

    /**
     * Creates executor with a list of clients.
     * Clients are grouped by provider and routed using [RoundRobinRouter].
     *
     * @param llmClients Vararg clients to use
     * @param fallback Optional fallback configuration
     */
    @JvmOverloads
    public constructor(
        vararg llmClients: LLMClient,
        fallback: FallbackPromptExecutorSettings? = null
    ) : this(llmClients.groupBy { it.llmProvider() }, fallback)

    public companion object {
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
     * Fallback LLM client for interacting with a fallback LLM provider.
     *
     * Retrieves client specified in `fallback` settings from `clientRouter` if available.
     * This client is intended to handle cases where no specific client is matched during prompt execution.
     *
     * Returns `null` if `fallbackSettings` is not specified or corresponding client is not found in `clientRouter`.
     */
    private val fallbackClient: LLMClient? by lazy {
        when {
            fallback != null -> {
                clientRouter.clients
                    .firstOrNull { it.llmProvider() == fallback.fallbackProvider }
                    ?: error("Client for provider ${fallback.fallbackProvider} not found in router")
            }
            else -> null
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

        val (effectiveClient, effectiveModel) = chooseClientAndModel(model)
        val response = effectiveClient.execute(prompt, effectiveModel, tools)

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
        tools: List<ToolDescriptor>
    ): Flow<StreamFrame> {
        logger.debug { "Executing streaming prompt: $prompt with model: $model" }

        val (client, effectiveModel) = chooseClientAndModel(model)

        return client.executeStreaming(prompt, effectiveModel, tools)
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
        tools: List<ToolDescriptor>
    ): List<LLMChoice> {
        logger.debug { "Executing prompt: $prompt with tools: $tools and model: $model" }

        val (client, effectiveModel) = chooseClientAndModel(model)
        val choices = client.executeMultipleChoices(prompt, effectiveModel, tools)

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

        val (client, effectiveModel) = chooseClientAndModel(model)

        return client.moderate(prompt, effectiveModel)
    }

    override suspend fun models(): List<LLModel> {
        logger.debug { "Fetching available models from all clients" }

        return clientRouter.clients
            .flatMap { it.models() }
            .distinct()
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
        clientRouter.clients.forEach { it.close() }
    }

    private fun chooseClientAndModel(requestedModel: LLModel): EffectiveExecutionSubject {
        val lbClient = clientRouter.chooseRouteFor(requestedModel)
        return when {
            lbClient != null -> lbClient to requestedModel
            fallback != null -> fallbackClient!! to fallback.fallbackModel
            else -> throw IllegalArgumentException("No client found for provider: ${requestedModel.provider}")
        }
    }
}

private typealias EffectiveExecutionSubject = Pair<LLMClient, LLModel>
