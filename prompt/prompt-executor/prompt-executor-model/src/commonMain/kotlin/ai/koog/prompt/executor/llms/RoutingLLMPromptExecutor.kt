package ai.koog.prompt.executor.llms

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.model.ExecuteHook
import ai.koog.prompt.executor.model.HookablePromptExecutor
import ai.koog.prompt.executor.model.InitialExecutionIntent
import ai.koog.prompt.executor.model.ModerateHook
import ai.koog.prompt.executor.model.MultipleChoicesHook
import ai.koog.prompt.executor.llms.PromptExecutorHelper.executeWithHook
import ai.koog.prompt.executor.llms.PromptExecutorHelper.streamWithHook
import ai.koog.prompt.executor.model.StreamingHook
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.LLMChoice
import ai.koog.prompt.message.Message
import ai.koog.prompt.streaming.StreamFrame
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.Flow
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
) : HookablePromptExecutor() {

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

    /**
     * Resolved fallback client and model, derived from [fallback] at construction time.
     *
     * If [fallback] is provided, the corresponding client is looked up in [clientRouter] eagerly.
     * If no client is found for the fallback provider, construction fails with an error.
     * If multiple clients are registered for the fallback provider, the first one is used.
     *
     * `null` when no fallback is configured.
     */
    private val effectiveFallback: EffectiveExecutionSubject? = when {
        fallback != null -> {
            val fallbackProvider = fallback.fallbackModel.provider
            val fallbackClient = clientRouter.clients
                .firstOrNull { it.llmProvider() == fallbackProvider }
                ?: error("Client for provider $fallbackProvider not found in router")
            fallbackClient to fallback.fallbackModel
        }

        else -> null
    }

    override suspend fun execute(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
        hook: ExecuteHook?
    ): List<Message.Response> {
        return executeWithHook(
            prompt = prompt,
            model = model,
            tools = tools,
            chooseExecutionSubject = this::chooseExecutionSubject,
            hook = hook
        ) { finalIntent, (effectiveClient, effectiveModel) ->
            effectiveClient.execute(finalIntent.prompt, effectiveModel, finalIntent.tools)
        }
    }

    override fun executeStreaming(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
        hook: StreamingHook?
    ): Flow<StreamFrame> {
        logger.debug { "Executing streaming prompt: $prompt with model: $model" }
        return streamWithHook(
            prompt = prompt,
            model = model,
            tools = tools,
            chooseExecutionSubject = this::chooseExecutionSubject,
            hook = hook
        ) { finalIntent, (effectiveClient, effectiveModel) ->
            logger.debug { "Executing streaming prompt: $prompt with model: $model" }
            effectiveClient.executeStreaming(finalIntent.prompt, effectiveModel, finalIntent.tools)
        }
    }

    override suspend fun executeMultipleChoices(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
        hook: MultipleChoicesHook?
    ): List<LLMChoice> {
        logger.debug { "Executing multiple choices: $prompt with tools: $tools and model: $model" }
        return executeWithHook(
            prompt = prompt,
            model = model,
            tools = tools,
            chooseExecutionSubject = this::chooseExecutionSubject,
            hook = hook
        ) { finalIntent, (effectiveClient, effectiveModel) ->
            effectiveClient.executeMultipleChoices(finalIntent.prompt, effectiveModel, finalIntent.tools)
        }
    }

    override suspend fun moderate(
        prompt: Prompt,
        model: LLModel,
        hook: ModerateHook?
    ): ModerationResult {
        logger.debug { "Moderating content with model: ${model.id}" }
        return executeWithHook(
            prompt = prompt,
            model = model,
            chooseExecutionSubject = this::chooseExecutionSubject,
            hook = hook
        ) { finalIntent, (effectiveClient, effectiveModel) ->
            effectiveClient.moderate(finalIntent.prompt, effectiveModel)
        }
    }

    override suspend fun models(): List<LLModel> {
        logger.debug { "Fetching available models from all clients" }

        return clientRouter.clients
            .flatMap { it.models() }
            .distinct()
    }

    private fun chooseExecutionSubject(executionIntent: InitialExecutionIntent): EffectiveExecutionSubject {
        val lbClient = clientRouter.clientFor(executionIntent.model)
        return when {
            lbClient != null -> lbClient to executionIntent.model
            effectiveFallback != null -> effectiveFallback
            else -> {
                val error = IllegalArgumentException("No client found for provider: ${executionIntent.model.provider}")
                throw error
            }
        }
    }

    override fun close() {
        clientRouter.clients.forEach { it.close() }
    }
}
