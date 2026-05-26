package ai.koog.prompt.executor.builder

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.Prompt
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.executor.model.PromptExecutorBuilder
import ai.koog.prompt.executor.model.PromptExecutorOperation
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
 * Builder for a [PromptExecutor] that routes prompts across one [LLMClient] per provider, with an
 * optional fallback model when the requested provider has no registered client.
 *
 * Resolution behavior:
 * - For [PromptExecutorOperation.Execute] and [PromptExecutorOperation.MultipleChoices], if the
 *   requested model's provider is not registered and a [fallback] is configured, [resolveModel]
 *   returns [FallbackPromptExecutorSettings.fallbackModel].
 * - For [PromptExecutorOperation.Streaming] and [PromptExecutorOperation.Moderate], the requested
 *   model is returned unchanged (no fallback applied).
 *
 * @param llmClients Map of LLM providers to clients.
 * @param fallback Optional fallback configuration.
 */
public open class MultiLLMPromptExecutorBuilder @JvmOverloads constructor(
    private val llmClients: Map<LLMProvider, LLMClient>,
    private val fallback: FallbackPromptExecutorSettings? = null,
) : PromptExecutorBuilder() {

    /**
     * Configuration for fallback model substitution.
     *
     * @property fallbackProvider Provider for fallback requests. Must already be registered in [llmClients].
     * @property fallbackModel Model to use when the requested provider has no client. Its provider must equal [fallbackProvider].
     */
    public data class FallbackPromptExecutorSettings(
        val fallbackProvider: LLMProvider,
        val fallbackModel: LLModel,
    ) {
        init {
            check(fallbackModel.provider == fallbackProvider) {
                "LLM model provider must match the fallback provider"
            }
        }
    }

    @JvmOverloads
    public constructor(
        vararg llmClients: Pair<LLMProvider, LLMClient>,
        fallback: FallbackPromptExecutorSettings? = null,
    ) : this(llmClients = mapOf(*llmClients), fallback = fallback)

    @JvmOverloads
    public constructor(
        llmClients: List<LLMClient>,
        fallback: FallbackPromptExecutorSettings? = null,
    ) : this(
        llmClients = llmClients.associateBy { it.llmProvider() },
        fallback = fallback,
    )

    @JvmOverloads
    public constructor(vararg llmClients: LLMClient) : this(llmClients.toList())

    private companion object {
        private val logger = KotlinLogging.logger("ai.koog.prompt.executor.llms.LLMPromptExecutor")
    }

    init {
        if (fallback != null) {
            check(fallback.fallbackProvider in llmClients.keys) {
                "Fallback client not found for provider: ${fallback.fallbackProvider}"
            }
        }
    }

    override fun resolveModel(model: LLModel, operation: PromptExecutorOperation): LLModel {
        if (model.provider in llmClients) {
            return model
        }

        return when (operation) {
            PromptExecutorOperation.Execute,
            PromptExecutorOperation.MultipleChoices -> {
                fallback?.fallbackModel ?: model
            }
            PromptExecutorOperation.Streaming,
            PromptExecutorOperation.Moderate -> {
                model
            }
        }
    }

    override suspend fun onExecute(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
    ): Message.Assistant {
        logger.debug { "Executing prompt: $prompt with tools: $tools and model: $model" }

        val client = llmClients[model.provider]
            ?: throw IllegalArgumentException("No client found for provider: ${model.provider}")

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
            val client = requireNotNull(llmClients[model.provider]) {
                "No client found for provider: ${model.provider}"
            }
            emitAll(client.executeStreaming(prompt, model, tools))
        }
    }

    override suspend fun onMultipleChoices(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
    ): LLMChoice {
        logger.debug { "Executing prompt: $prompt with tools: $tools and model: $model" }

        val client = llmClients[model.provider]
            ?: throw IllegalArgumentException("No client found for provider: ${model.provider}")
        val choices = client.executeMultipleChoices(prompt, model, tools)

        logger.debug { "Choices: $choices" }
        return choices
    }

    override suspend fun onModerate(prompt: Prompt, model: LLModel): ModerationResult {
        logger.debug { "Moderating multi-modal content with model: ${model.id}" }

        val client = llmClients[model.provider]
            ?: throw IllegalArgumentException("No client found for provider: ${model.provider}")
        return client.moderate(prompt, model)
    }

    override suspend fun onModels(): List<LLModel> {
        logger.debug { "Fetching available models from all clients" }
        return llmClients.values.flatMap { it.models() }
    }

    override fun getStandardJsonSchemaGenerator(model: LLModel): StandardJsonSchemaGenerator {
        val client = llmClients[model.provider]
            ?: throw IllegalArgumentException("No client found for provider: ${model.provider}")
        return client.getStandardJsonSchemaGenerator()
    }

    override fun getBasicJsonSchemaGenerator(model: LLModel): BasicJsonSchemaGenerator {
        val client = llmClients[model.provider]
            ?: throw IllegalArgumentException("No client found for provider: ${model.provider}")
        return client.getBasicJsonSchemaGenerator()
    }

    override fun onClose() {
        llmClients.forEach { (_, client) -> client.close() }
    }
}
