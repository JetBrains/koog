package ai.koog.prompt.executor.llms

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.model.ModelSelection
import ai.koog.prompt.executor.model.ModelSelector
import ai.koog.prompt.executor.model.SelectingPromptExecutor
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.LLMChoice
import ai.koog.prompt.message.Message
import ai.koog.prompt.streaming.StreamFrame
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlin.jvm.JvmOverloads

/**
 * Executes prompts with load balancing across multiple LLM clients.
 *
 * Delegates client selection to [LLMClientRouter], which determines which client should
 * handle each request based on the requested model. This enables load distribution strategies
 * like round-robin, weighted routing, or health-based selection.
 *
 * @param clientRouter Router responsible for selecting appropriate clients for each request
 * @param fallback Optional fallback configuration when no client is available for the requested model
 */
@OptIn(ExperimentalRoutingApi::class)
public open class RoutingLLMPromptExecutor @JvmOverloads constructor(
    private val clientRouter: LLMClientRouter,
    private val fallback: FallbackPromptExecutorSettings? = null,
) : SelectingPromptExecutor() {

    /**
     * Fallback model used when no registered client can serve any model from the selector's ranked result.
     *
     * This is a routing fallback — it activates when the requested model's provider has no client
     * registered in the executor, not when an LLM call itself fails.
     *
     * @property fallbackModel Model to use as last resort.
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
        private val logger = KotlinLogging.logger {}
    }

    /**
     * Resolved fallback client and model, derived from [fallback] at construction time.
     *
     * If [fallback] is provided, the corresponding client is looked up in [clientRouter] eagerly.
     * If no client is found for the fallback provider, construction fails with an error.
     * If multiple clients are registered for the fallback provider, the first one is used.
     *
     * `null` when no fallback is configured.
     */
    private val effectiveFallback: ExecutionSubject? = when {
        fallback != null -> {
            val fallbackProvider = fallback.fallbackModel.provider
            val fallbackClient = clientRouter.clients
                .firstOrNull { it.llmProvider() == fallbackProvider }
                ?: error("Client for provider $fallbackProvider not found in router")
            fallbackClient to fallback.fallbackModel
        }

        else -> null
    }

    /**
     * Coroutine scope that owns [modelsDiscovery]. Canceled in [close] to tie the cache
     * lifetime to the executor lifetime.
     */
    private val modelsDiscoveryScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Lazily fetches and caches the union of all models across registered clients.
     *
     * The first [models] call triggers a single fan-out to every client; later calls return
     * the cached result immediately. A failed fetch poisons this [Deferred] — all future [models]
     * calls will rethrow the same exception, which is acceptable since model-list failures are
     * unrecoverable at runtime.
     */
    private val modelsDiscovery: Deferred<List<LLModel>> = modelsDiscoveryScope.async(start = CoroutineStart.LAZY) {
        logger.debug { "Fetching available models from all clients" }
        clientRouter.clients.flatMap { it.models() }.distinct()
    }

    /**
     * Executes [prompt] using the model and client chosen by [modelSelector].
     *
     * Passes all models available in this executor to [modelSelector], then routes to the
     * highest-ranked model that has a registered client. Falls back to [effectiveFallback] if
     * no ranked model has a client, or throws if no fallback is configured.
     *
     * @param prompt Prompt to execute.
     * @param modelSelector Selector that ranks models available to this executor.
     * @param tools Tools available during execution.
     * @return Responses generated by the selected model.
     * @throws IllegalArgumentException If no client is found for any ranked model and no fallback is configured.
     */
    override suspend fun execute(
        prompt: Prompt,
        modelSelector: ModelSelector,
        tools: List<ToolDescriptor>
    ): List<Message.Response> {
        logger.debug { "Executing prompt: $prompt with tools: $tools and modelSelector: $modelSelector" }

        val (effectiveClient, effectiveModel) = chooseClientAndModel(modelSelector)
        val response = effectiveClient.execute(prompt, effectiveModel, tools)

        logger.debug { "Response: $response" }

        return response
    }

    /**
     * Executes [prompt] using the model and client chosen by [modelSelector] and streams output frames.
     *
     * Passes all models available in this executor to [modelSelector], then routes to the
     * highest-ranked model that has a registered client. Falls back to [effectiveFallback] if
     * no ranked model has a client, or throws if no fallback is configured.
     *
     * @param prompt Prompt to execute.
     * @param modelSelector Selector that ranks models available to this executor.
     * @param tools Tools available during execution.
     * @return Stream of output frames from the selected model.
     * @throws IllegalArgumentException If no client is found for any ranked model and no fallback is configured.
     */
    override fun executeStreaming(
        prompt: Prompt,
        modelSelector: ModelSelector,
        tools: List<ToolDescriptor>
    ): Flow<StreamFrame> {
        logger.debug { "Executing streaming prompt: $prompt with modelSelector: $modelSelector" }
        return flow {
            val (client, effectiveModel) = chooseClientAndModel(modelSelector)
            emitAll(client.executeStreaming(prompt, effectiveModel, tools))
        }
    }

    /**
     * Receives multiple independent choices for [prompt] using the model and client chosen by [modelSelector].
     *
     * Passes all models available in this executor to [modelSelector], then routes to the
     * highest-ranked model that has a registered client. Falls back to [effectiveFallback] if
     * no ranked model has a client, or throws if no fallback is configured.
     *
     * @param prompt Prompt to execute.
     * @param modelSelector Selector that ranks models available to this executor.
     * @param tools Tools available during execution.
     * @return Generated model choices.
     * @throws IllegalArgumentException If no client is found for any ranked model and no fallback is configured.
     */
    override suspend fun executeMultipleChoices(
        prompt: Prompt,
        modelSelector: ModelSelector,
        tools: List<ToolDescriptor>
    ): List<LLMChoice> {
        logger.debug { "Executing prompt: $prompt with tools: $tools and modelSelector: $modelSelector" }

        val (client, effectiveModel) = chooseClientAndModel(modelSelector)
        val choices = client.executeMultipleChoices(prompt, effectiveModel, tools)

        logger.debug { "Choices: $choices" }

        return choices
    }

    /**
     * Moderates [prompt] using the model and client chosen by [modelSelector].
     *
     * Passes all models available in this executor to [modelSelector], then routes to the
     * highest-ranked model that has a registered client. Falls back to [effectiveFallback] if
     * no ranked model has a client, or throws if no fallback is configured.
     *
     * @param prompt Prompt containing content to moderate.
     * @param modelSelector Selector that ranks models available to this executor.
     * @return Moderation result.
     * @throws IllegalArgumentException If no client is found for any ranked model and no fallback is configured.
     */
    override suspend fun moderate(
        prompt: Prompt,
        modelSelector: ModelSelector
    ): ModerationResult {
        logger.debug { "Moderating multi-modal content with modelSelector: $modelSelector" }

        val (client, effectiveModel) = chooseClientAndModel(modelSelector)

        return client.moderate(prompt, effectiveModel)
    }

    override suspend fun models(): List<LLModel> = modelsDiscovery.await()

    override fun close() {
        modelsDiscoveryScope.cancel()
        clientRouter.clients.forEach { it.close() }
    }

    /**
     * Resolves the client and model to use for execution based on [modelSelector].
     *
     * Passes all executor models to [modelSelector], then walks the ranked result top-to-bottom,
     * returning the first model that has a registered client in [clientRouter].
     * Falls back to [effectiveFallback] if no ranked model has a client.
     *
     * @throws IllegalArgumentException If no client is found for any ranked model and no fallback is configured.
     */
    private suspend fun chooseClientAndModel(modelSelector: ModelSelector): ExecutionSubject {
        val selection = modelSelector.select(models())
        val selectedSubject = chooseClientAndModelFromSelection(selection)
        return when {
            selectedSubject != null -> selectedSubject
            effectiveFallback != null -> effectiveFallback
            else -> throw IllegalArgumentException("No client found for model selection")
        }
    }

    /**
     * Walks [selection] from best to worst, returning the first model with a registered client.
     *
     * Returns `null` if no model in the selection has a registered client.
     */
    private fun chooseClientAndModelFromSelection(selection: ModelSelection): ExecutionSubject? {
        for (model in selection.ranked) {
            val client = clientRouter.clientFor(model)
            if (client != null) {
                return client to model
            }
        }
        return null
    }
}

private typealias ExecutionSubject = Pair<LLMClient, LLModel>
