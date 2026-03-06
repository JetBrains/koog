package ai.koog.spring.ai.chat

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.LLMClientException
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.params.LLMParams
import ai.koog.prompt.streaming.StreamFrame
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.prompt.ChatOptions
import org.springframework.ai.model.tool.ToolCallingChatOptions
import org.springframework.ai.moderation.ModerationModel
import org.springframework.ai.moderation.ModerationPrompt
import org.springframework.ai.chat.prompt.Prompt as SpringPrompt

/**
 * An [LLMProvider] representing a Spring AI-backed provider.
 */
public class SpringAILLMProvider @JvmOverloads constructor(id: String = "spring-ai", display: String = "Spring AI") :
    LLMProvider(id, display)

/**
 * An [LLMClient] implementation that delegates to a Spring AI [ChatModel].
 *
 * This adapter allows Koog agents to use any Spring AI chat model provider
 * (Anthropic, OpenAI, Google, Ollama, etc.) as their underlying LLM backend.
 *
 * Tool execution is always owned by the Koog agent framework. Spring AI receives only
 * tool definitions (via [org.springframework.ai.tool.ToolCallback] with a throwing `call()`)
 * and `internalToolExecutionEnabled=false`, so Spring never attempts to execute tools.
 *
 * @param chatModel the Spring AI chat model to delegate to
 * @param provider the [LLMProvider] to report for this client
 * @param clock the clock used for creating response metadata timestamps
 * @param dispatcher the [CoroutineDispatcher] used for blocking model calls
 * @param chatOptionsCustomizer optional customizer for provider-specific [ChatOptions] tuning
 * @param moderationModel optional Spring AI [ModerationModel] for content moderation; if null, [moderate] throws [UnsupportedOperationException]
 */
public class SpringAILLMClient @JvmOverloads constructor(
    private val chatModel: ChatModel,
    private val provider: LLMProvider = SpringAILLMProvider(),
    private val clock: kotlin.time.Clock = kotlin.time.Clock.System,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val chatOptionsCustomizer: ChatOptionsCustomizer = ChatOptionsCustomizer.NOOP,
    private val moderationModel: ModerationModel? = null,
) : LLMClient() {

    /**
     * Java-friendly static factory methods.
     */
    public companion object {
        /**
         * Creates a [SpringAILLMClient] with only a [ChatModel], using all defaults.
         * Intended for Java callers who want to avoid dealing with [kotlin.time.Clock].
         *
         * @param chatModel the Spring AI chat model to delegate to
         */
        @JvmStatic
        public fun create(chatModel: ChatModel): SpringAILLMClient =
            SpringAILLMClient(chatModel)

        /**
         * Creates a [SpringAILLMClient] with a [ChatModel] and [ModerationModel], using all other defaults.
         * Intended for Java callers who want to avoid dealing with [kotlin.time.Clock].
         *
         * @param chatModel the Spring AI chat model to delegate to
         * @param moderationModel the Spring AI moderation model to use
         */
        @JvmStatic
        public fun create(chatModel: ChatModel, moderationModel: ModerationModel): SpringAILLMClient =
            SpringAILLMClient(chatModel, moderationModel = moderationModel)
    }

    override val clientName: String = "spring-ai-chat"
    private val logger = LoggerFactory.getLogger(SpringAILLMClient::class.java)

    override fun llmProvider(): LLMProvider = provider

    /**
     * Returns the list of models available from the configured [ChatModel].
     *
     * The model name is extracted from [ChatModel.getDefaultOptions] at runtime,
     * reflecting whatever model the Spring AI provider has been configured with.
     * If the underlying [ChatModel] does not expose a model name via its default options,
     * an empty list is returned.
     *
     * @return a list containing the configured [LLModel], or an empty list if the model name is unavailable.
     */
    override suspend fun models(): List<LLModel> {
        val modelId = chatModel.defaultOptions.model ?: return emptyList()
        return listOf(LLModel(provider = provider, id = modelId))
    }

    override suspend fun execute(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ): List<Message.Response> = withContext(dispatcher) {
        val springPrompt = toSpringPrompt(prompt, model, tools)
        val chatResponse: ChatResponse = try {
            chatModel.call(springPrompt)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw LLMClientException(clientName, "ChatModel.call() failed: ${e.message}", e)
        }
        val usage = chatResponse.metadata?.usage
        chatResponse.results.flatMap { generation ->
            springGenerationToKoogResponses(generation, clock, usage)
        }
    }

    override fun executeStreaming(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ): Flow<StreamFrame> = flow {
        val springPrompt = toSpringPrompt(prompt, model, tools)
        val flux = try {
            chatModel.stream(springPrompt)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw LLMClientException(clientName, "ChatModel.stream() failed: ${e.message}", e)
        }
        try {
            flux.asFlow().collect { chatResponse ->
                for (generation in chatResponse.results) {
                    val assistantMessage = generation.output
                    val text = assistantMessage.text
                    if (!text.isNullOrEmpty()) {
                        emit(StreamFrame.TextDelta(text))
                    }
                    if (assistantMessage.hasToolCalls()) {
                        for (toolCall in assistantMessage.toolCalls) { // TODO: No documentation or per-provider verification that Spring AI accumulates partial argument chunks before exposing them.
                            emit(
                                StreamFrame.ToolCallDelta(
                                    id = toolCall.id(),
                                    name = toolCall.name(),
                                    content = toolCall.arguments()
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: LLMClientException) {
            throw e
        } catch (e: Exception) {
            throw LLMClientException(clientName, "ChatModel.stream() failed during collection: ${e.message}", e)
        }
        emit(StreamFrame.End())
    }.flowOn(dispatcher)

    override suspend fun moderate(
        prompt: Prompt,
        model: LLModel
    ): ModerationResult = withContext(dispatcher) {
        val springModerationModel = moderationModel
            ?: throw UnsupportedOperationException(
                "Moderation is not supported: no ModerationModel bean is available in the Spring context. " +
                    "Add a Spring AI moderation provider (e.g. spring-ai-openai) to your classpath and ensure " +
                    "a ModerationModel bean is registered."
            )

        require(prompt.messages.isNotEmpty()) { "Can't moderate an empty prompt" }

        val response = try {
            springModerationModel.call(ModerationPrompt(promptToPlainText(prompt)))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw LLMClientException(clientName, "ModerationModel.call() failed: ${e.message}", e)
        }
        springModerationResultToKoogModerationResult(response.result.output)
    }

    override fun close() {
        // ChatModel does not implement Closeable; nothing to close
    }

    private fun toSpringPrompt(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ): SpringPrompt {
        val springMessages = prompt.messages.map { koogMessageToSpringMessage(it) }
        val chatOptions: ChatOptions = buildChatOptions(prompt.params, model, tools)
        return SpringPrompt(springMessages, chatOptions)
    }

    private fun buildChatOptions(
        params: LLMParams,
        model: LLModel,
        tools: List<ToolDescriptor>
    ): ChatOptions {
        val options: ChatOptions = if (tools.isNotEmpty()) {
            val toolCallbacks = tools.map { koogToolDescriptorToToolCallback(it) }
            ToolCallingChatOptions.builder()
                .model(model.id)
                .temperature(params.temperature)
                .maxTokens(params.maxTokens)
                .toolCallbacks(toolCallbacks)
                .internalToolExecutionEnabled(false)
                .build()
        } else {
            ChatOptions.builder()
                .model(model.id)
                .temperature(params.temperature)
                .maxTokens(params.maxTokens)
                .build()
        }

        // Log unsupported params once at debug level
        params.numberOfChoices?.let {
            logger.debug(
                "Koog Spring AI: 'numberOfChoices={}' is not supported by Spring AI ChatOptions; ignored for provider '{}'",
                it,
                model.provider.id
            )
        }
        params.speculation?.let {
            logger.debug(
                "Koog Spring AI: 'speculation' is not supported by Spring AI ChatOptions; ignored for provider '{}'",
                model.provider.id
            )
        }

        return chatOptionsCustomizer.customize(options, params, model)
    }
}

/**
 * Extension point for provider-specific [ChatOptions] customization.
 *
 * Implement this interface and register it as a Spring bean to apply
 * provider-specific option tuning on top of the default mapping.
 */
public fun interface ChatOptionsCustomizer {
    /**
     * Customize the given [options] based on the original Koog [params] and [model].
     *
     * @return the customized (or original) [ChatOptions]
     */
    public fun customize(options: ChatOptions, params: LLMParams, model: LLModel): ChatOptions

    /**
     * A companion object for ChatOptionsCustomizer
     */
    public companion object {
        /** No-op customizer that returns options unchanged. */
        @JvmField
        public val NOOP: ChatOptionsCustomizer = ChatOptionsCustomizer { options, _, _ -> options }
    }
}
