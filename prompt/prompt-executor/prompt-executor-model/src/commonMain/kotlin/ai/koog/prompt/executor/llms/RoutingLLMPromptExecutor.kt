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
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.Flow
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
     * Executes a given prompt using the specified tools and model, and returns a list of response messages.
     *
     * @param prompt The `Prompt` to be executed, containing the input messages and parameters.
     * @param tools A list of `ToolDescriptor` objects representing external tools available for use during execution.
     * @param model The LLM model to use for execution.
     * @return A list of `Message.Response` objects containing the responses generated based on the prompt.
     * @throws IllegalArgumentException If no client is found for the model's provider and no fallback is configured.
     */
    override suspend fun execute(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
        context: PromptExecutionContext
    ): List<Message.Response> {
        context.handle(ExecutionRequested(context.promptExecutionId, prompt, model, tools))
        logger.debug {
            "Executing prompt: $prompt with tools: $tools and model: $model. Prompt execution id: ${context.promptExecutionId}"
        }

        val (effectiveClient, effectiveModel) = try {
            chooseClientAndModel(model)
        } catch (error: Throwable) {
            context.handle(ExecutionFailed(context.promptExecutionId, prompt, model, tools, error))
            throw error
        }

        context.handle(ExecutionDispatched(context.promptExecutionId, prompt, effectiveModel, tools))
        val response = try {
            effectiveClient.execute(prompt, effectiveModel, tools)
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
            logger.debug {
                "Executing streaming prompt: $prompt with model: $model. Prompt execution id: ${context.promptExecutionId}"
            }

            val (client, effectiveModel) = try {
                chooseClientAndModel(model)
            } catch (error: Throwable) {
                context.handle(StreamingFailed(context.promptExecutionId, prompt, model, tools, error))
                throw error
            }

            context.handle(StreamingDispatched(context.promptExecutionId, prompt, effectiveModel, tools))
            try {
                client.executeStreaming(prompt, effectiveModel, tools).collect { frame ->
                    context.handle(StreamingFrameReceived(context.promptExecutionId, prompt, effectiveModel, tools, frame))
                    emit(frame)
                }
            } catch (error: Throwable) {
                context.handle(StreamingFailed(context.promptExecutionId, prompt, effectiveModel, tools, error))
                throw error
            }
            context.handle(StreamingCompleted(context.promptExecutionId, prompt, effectiveModel, tools))
        }
    }

    /**
     * Executes a given prompt using the specified tools and model and returns a list of model choices.
     *
     * @param prompt The `Prompt` to be executed, containing the input messages and parameters.
     * @param tools A list of `ToolDescriptor` objects representing external tools available for use during execution.
     * @param model The LLM model to use for execution.
     * @return A list of `LLMChoice` objects containing the choices generated based on the prompt.
     * @throws IllegalArgumentException If no client is found for the model's provider and no fallback is configured.
     */
    override suspend fun executeMultipleChoices(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
        context: PromptExecutionContext
    ): List<LLMChoice> {
        context.handle(MultipleChoicesRequested(context.promptExecutionId, prompt, model, tools))
        logger.debug {
            "Executing prompt: $prompt with tools: $tools and model: $model. Prompt execution id: ${context.promptExecutionId}"
        }

        val (client, effectiveModel) = try {
            chooseClientAndModel(model)
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
        logger.debug { "Moderating multi-modal content with model: ${model.id}. Prompt execution id: ${context.promptExecutionId}" }

        val (client, effectiveModel) = try {
            chooseClientAndModel(model)
        } catch (error: Throwable) {
            context.handle(ModerationFailed(context.promptExecutionId, prompt, model, error))
            throw error
        }

        context.handle(ModerationDispatched(context.promptExecutionId, prompt, effectiveModel))
        val result = try {
            client.moderate(prompt, effectiveModel)
        } catch (error: Throwable) {
            context.handle(ModerationFailed(context.promptExecutionId, prompt, effectiveModel, error))
            throw error
        }

        context.handle(ModerationCompleted(context.promptExecutionId, prompt, effectiveModel, result))
        return result
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

    private fun chooseClientAndModel(requestedModel: LLModel): ExecutionSubject {
        val lbClient = clientRouter.clientFor(requestedModel)
        return when {
            lbClient != null -> lbClient to requestedModel
            effectiveFallback != null -> effectiveFallback
            else -> {
                throw IllegalArgumentException("No client found for provider: ${requestedModel.provider}")
            }
        }
    }
}

private typealias ExecutionSubject = Pair<LLMClient, LLModel>
