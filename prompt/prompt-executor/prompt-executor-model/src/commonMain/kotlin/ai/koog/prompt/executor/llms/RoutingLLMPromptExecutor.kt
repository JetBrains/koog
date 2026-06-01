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
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmSynthetic

/**
 * Executes prompts with load balancing across multiple LLM clients.
 *
 * Delegates client selection to [LLMClientRouter], which determines which client should
 * handle each request based on the requested model. This enables load distribution strategies
 * like round-robin, weighted routing, or health-based selection.
 *
 * @param clientRouter Router responsible for selecting appropriate clients for each request
 * @param fallback Optional fallback configuration when no client is available for the requested model
 *
 * This class remains open for source and binary compatibility with existing subclasses. For new
 * custom prompt executors, prefer extending [ai.koog.prompt.executor.model.DynamicPromptExecutor].
 * If you subclass this class, prefer overriding the [ResolvedModel]-based overloads. Override the
 * [LLModel]-based overloads only when you intentionally take over the full model-resolution and
 * execution flow.
 */
@OptIn(ExperimentalRoutingApi::class)
public open class RoutingLLMPromptExecutor @JvmOverloads constructor(
    private val clientRouter: LLMClientRouter,
    private val fallback: FallbackPromptExecutorSettings? = null,
) : PromptExecutor() {

    /**
     * Represents configuration for a fallback large language model (LLM) execution strategy.
     *
     * This class is used to specify a fallback LLM model that can be utilized when the primary LLM execution fails.
     * It ensures that the fallback model is associated with the specified fallback provider.
     *
     * @property fallbackModel The LLModel instance to be used for fallback execution.
     */
    public data class FallbackPromptExecutorSettings(val fallbackModel: LLModel)

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
     * Creates executor with a list of clients.
     * Clients are grouped by provider and routed using [RoundRobinRouter].
     *
     * @param llmClients Vararg clients to use
     * @param fallback Optional fallback configuration
     */
    @JvmOverloads
    public constructor(
        llmClients: List<LLMClient>,
        fallback: FallbackPromptExecutorSettings? = null
    ) : this(llmClients.groupBy { it.llmProvider() }, fallback)

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
    ) : this(llmClients.toList(), fallback)

    private companion object {
        /**
         * Logger instance used for logging messages within the RoutingLLMPromptExecutor class.
         *
         * This logger is used to provide debug logs during the execution of prompts and handling of streaming responses.
         * It primarily tracks operations such as prompt execution initiation, tool usage, and responses received from the
         * respective LLM clients.
         *
         * The logger can aid in debugging by capturing detailed information about the state and flow of operations within
         * the class.
         */
        private val logger = KotlinLogging.logger {}
    }

    private val supportedProviders: Set<LLMProvider> =
        clientRouter.clients.mapTo(mutableSetOf()) { it.llmProvider() }

    init {
        if (fallback != null) {
            check(fallback.fallbackModel.provider in supportedProviders) {
                "Fallback client not found for provider: ${fallback.fallbackModel.provider}"
            }
        }
    }

    /**
     * Resolves the [LLModel] to use for the given [promptExecutorOperation] by checking whether the
     * router has a client for the model's provider, falling back to [FallbackPromptExecutorSettings.fallbackModel]
     * when no direct client matches.
     *
     * @throws ModelResolutionException If no client is found for the model's provider and no fallback is configured.
     */
    override suspend fun resolveModel(
        model: LLModel,
        promptExecutorOperation: PromptExecutorOperation
    ): ResolvedModel = when {
        model.provider in supportedProviders -> ResolvedModel(effectiveModel = model)

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
     * Preferred extension point for subclasses that want to preserve this executor's model-resolution flow.
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
     * Executes the given prompt with the specified resolved model and streams the response in chunks as a flow.
     * Preferred extension point for subclasses that want to preserve this executor's model-resolution flow.
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
            emitAll(clientFor(effectiveModel).executeStreaming(prompt, effectiveModel, tools))
        }
    }

    /**
     * Executes a given prompt using the specified tools and model and returns the model choices.
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
     * Executes a given prompt using the specified tools and resolved model and returns the model choices.
     * Preferred extension point for subclasses that want to preserve this executor's model-resolution flow.
     *
     * @param prompt The `Prompt` to be executed, containing the input messages and parameters.
     * @param resolvedModel The resolved LLM model to use for execution.
     * @param tools A list of `ToolDescriptor` objects representing external tools available for use during execution.
     * @return An `LLMChoice` containing the choices generated based on the prompt.
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
     * Preferred extension point for subclasses that want to preserve this executor's model-resolution flow.
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
        requireNotNull(clientRouter.clientFor(effectiveModel)) {
            "No client found for provider: ${effectiveModel.provider}"
        }

    override suspend fun models(): List<LLModel> {
        logger.debug { "Fetching available models from all clients" }

        return clientRouter.clients
            .flatMap { it.models() }
            .distinct()
    }

    override fun close() {
        clientRouter.clients.forEach { it.close() }
    }
}
