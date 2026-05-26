package ai.koog.prompt.executor.builder

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.Prompt
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.llms.LLMClientRouter
import ai.koog.prompt.executor.llms.RoundRobinRouter
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.executor.model.PromptExecutorBuilder
import ai.koog.prompt.executor.model.PromptExecutorOperation
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.LLMChoice
import ai.koog.prompt.message.Message
import ai.koog.prompt.streaming.StreamFrame
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlin.jvm.JvmOverloads

/**
 * Builder for a [PromptExecutor] that load-balances across multiple [LLMClient]s via a
 * [LLMClientRouter], with optional fallback configuration.
 *
 * Resolution behavior: fallback applies to all operations if the requested model's provider
 * has no registered client.
 *
 * @param clientRouter Router responsible for selecting appropriate clients for each request.
 * @param fallback Optional fallback configuration when no client is available for the requested model.
 */
public open class RoutingLLMPromptExecutorBuilder @JvmOverloads constructor(
    private val clientRouter: LLMClientRouter,
    private val fallback: FallbackPromptExecutorSettings? = null,
) : PromptExecutorBuilder() {

    /**
     * Fallback model configuration. The provider of [fallbackModel] must have at least one
     * registered client in the router.
     */
    public data class FallbackPromptExecutorSettings(val fallbackModel: LLModel)

    @JvmOverloads
    public constructor(
        llmClients: Map<LLMProvider, List<LLMClient>>,
        fallback: FallbackPromptExecutorSettings? = null,
    ) : this(RoundRobinRouter(llmClients), fallback)

    @JvmOverloads
    public constructor(
        llmClients: List<LLMClient>,
        fallback: FallbackPromptExecutorSettings? = null,
    ) : this(llmClients.groupBy { it.llmProvider() }, fallback)

    @JvmOverloads
    public constructor(
        vararg llmClients: LLMClient,
        fallback: FallbackPromptExecutorSettings? = null,
    ) : this(llmClients.toList(), fallback)

    private companion object {
        private val logger = KotlinLogging.logger {}
    }

    init {
        if (fallback != null) {
            val fallbackProvider = fallback.fallbackModel.provider
            check(clientRouter.clients.any { it.llmProvider() == fallbackProvider }) {
                "Client for provider $fallbackProvider not found in router"
            }
        }
    }

    override fun resolveModel(model: LLModel, operation: PromptExecutorOperation): LLModel {
        // Non-consuming check: avoid advancing the round-robin counter from resolveModel.
        val providerHasClient = clientRouter.clients.any { it.llmProvider() == model.provider }
        if (providerHasClient) return model
        return fallback?.fallbackModel ?: model
    }

    override suspend fun onExecute(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
    ): Message.Assistant {
        logger.debug { "Executing prompt: $prompt with tools: $tools and model: $model" }
        val client = chooseClient(model)
        val response = client.execute(prompt, model, tools)
        logger.debug { "Response: $response" }
        return response
    }

    override fun onStreaming(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
    ): Flow<StreamFrame> {
        logger.debug { "Executing streaming prompt: $prompt with model: $model" }
        return flow {
            val client = chooseClient(model)
            emitAll(client.executeStreaming(prompt, model, tools))
        }
    }

    override suspend fun onMultipleChoices(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
    ): LLMChoice {
        logger.debug { "Executing prompt: $prompt with tools: $tools and model: $model" }
        val client = chooseClient(model)
        val choices = client.executeMultipleChoices(prompt, model, tools)
        logger.debug { "Choices: $choices" }
        return choices
    }

    override suspend fun onModerate(prompt: Prompt, model: LLModel): ModerationResult {
        logger.debug { "Moderating multi-modal content with model: ${model.id}" }
        val client = chooseClient(model)
        return client.moderate(prompt, model)
    }

    override suspend fun onModels(): List<LLModel> {
        logger.debug { "Fetching available models from all clients" }
        return clientRouter.clients.flatMap { it.models() }.distinct()
    }

    override fun onClose() {
        clientRouter.clients.forEach { it.close() }
    }

    private fun chooseClient(model: LLModel): LLMClient =
        clientRouter.clientFor(model)
            ?: throw IllegalArgumentException("No client found for provider: ${model.provider}")
}
