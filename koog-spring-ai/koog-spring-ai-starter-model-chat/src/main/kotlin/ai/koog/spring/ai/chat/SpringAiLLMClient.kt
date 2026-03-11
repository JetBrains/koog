package ai.koog.spring.ai.chat

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.LLMClientException
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.params.LLMParams
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.prompt.streaming.buildStreamFrameFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
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
public class SpringAiLLMClient(
    private val chatModel: ChatModel,
    private val provider: LLMProvider,
    private val clock: kotlin.time.Clock,
    private val dispatcher: CoroutineDispatcher,
    private val chatOptionsCustomizer: ChatOptionsCustomizer,
    private val moderationModel: ModerationModel?,
) : LLMClient() {

    /**
     * Java-friendly builder access.
     */
    public companion object {
        /**
         * Returns a new [Builder] for constructing a [SpringAiLLMClient].
         * Intended for Java callers who want to avoid dealing with Kotlin default parameters
         * and [kotlin.time.Clock].
         *
         * Usage:
         * ```java
         * SpringAiLLMClient.builder()
         *     .chatModel(chatModel)
         *     .moderationModel(moderationModel)
         *     .clock(clock)
         *     .dispatcher(dispatcher)
         *     .chatOptionsCustomizer(customizer)
         *     .build();
         * ```
         */
        @JvmStatic
        public fun builder(): Builder = Builder()
    }

    /**
     * A Java-friendly builder for [SpringAiLLMClient].
     *
     * The only required property is [chatModel]; all others have sensible defaults.
     */
    public class Builder {
        private var chatModel: ChatModel? = null
        private var provider: LLMProvider = SpringAiLLMProvider
        private var clock: kotlin.time.Clock = kotlin.time.Clock.System
        private var dispatcher: CoroutineDispatcher = Dispatchers.IO
        private var chatOptionsCustomizer: ChatOptionsCustomizer = ChatOptionsCustomizer.NOOP
        private var moderationModel: ModerationModel? = null

        /** Sets the Spring AI [ChatModel] to delegate to. Required. */
        public fun chatModel(chatModel: ChatModel): Builder = apply { this.chatModel = chatModel }

        /** Sets the [LLMProvider] to report for this client. Default is [SpringAiLLMProvider]. */
        public fun provider(provider: LLMProvider): Builder = apply { this.provider = provider }

        /** Sets the clock used for creating response metadata timestamps. Default is [kotlin.time.Clock.System]. */
        public fun clock(clock: kotlin.time.Clock): Builder = apply { this.clock = clock }

        /** Sets the [CoroutineDispatcher] used for blocking model calls. Default is [Dispatchers.IO]. */
        public fun dispatcher(dispatcher: CoroutineDispatcher): Builder = apply { this.dispatcher = dispatcher }

        /** Sets the customizer for provider-specific [ChatOptions] tuning. Default is [ChatOptionsCustomizer.NOOP]. */
        public fun chatOptionsCustomizer(chatOptionsCustomizer: ChatOptionsCustomizer): Builder =
            apply { this.chatOptionsCustomizer = chatOptionsCustomizer }

        /** Sets the optional Spring AI [ModerationModel] for content moderation. Default is `null`. */
        public fun moderationModel(moderationModel: ModerationModel?): Builder =
            apply { this.moderationModel = moderationModel }

        /**
         * Builds a new [SpringAiLLMClient] instance.
         *
         * @throws IllegalStateException if [chatModel] has not been set
         */
        public fun build(): SpringAiLLMClient {
            val chatModel = requireNotNull(this.chatModel) { "chatModel must be set" }
            return SpringAiLLMClient(
                chatModel = chatModel,
                provider = provider,
                clock = clock,
                dispatcher = dispatcher,
                chatOptionsCustomizer = chatOptionsCustomizer,
                moderationModel = moderationModel,
            )
        }
    }

    private val logger = LoggerFactory.getLogger(SpringAiLLMClient::class.java)

    override fun llmProvider(): LLMProvider = provider

    /**
     * Returns the list with one model based on the configured [LLMProvider] and [ChatModel] without capabilities or parameters.
     *
     * The model id is extracted from [ChatModel.getDefaultOptions] at runtime,
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

    /**
     * Streams LLM responses by subscribing to [ChatModel.stream] and converting each chunk
     * into Koog [StreamFrame] events.
     *
     * Text content is emitted as [StreamFrame.TextDelta] frames immediately. Tool calls are
     * handled by a [SpringAiToolCallAssembler] whose mode depends on the detected [LLMProvider]:
     * - **Anthropic / Google** ([SpringAiToolStreamingMode.EMIT_IMMEDIATELY]): tool calls arrive
     *   fully formed in each chunk and are emitted immediately.
     * - **OpenAI and unknown providers** ([SpringAiToolStreamingMode.BUFFER_UNTIL_END]): tool call
     *   fragments are buffered across chunks and emitted as complete tool calls after the stream ends.
     *
     * The resulting flow uses [ai.koog.prompt.streaming.StreamFrameFlowBuilder] which automatically pairs each
     * [StreamFrame.ToolCallDelta] with a corresponding [StreamFrame.ToolCallComplete] and
     * emits [StreamFrame.TextComplete] / [StreamFrame.ReasoningComplete] boundaries.
     *
     * All blocking I/O runs on the configured [dispatcher] (default [Dispatchers.IO]).
     */
    override fun executeStreaming(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ): Flow<StreamFrame> = buildStreamFrameFlow {
        val springPrompt = toSpringPrompt(prompt, model, tools)
        val toolCallAssembler = SpringAiToolCallAssembler.forProvider(provider)
        val flux = try {
            chatModel.stream(springPrompt)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw LLMClientException(clientName, "ChatModel.stream() failed: ${e.message}", e)
        }
        var lastChatResponse: ChatResponse? = null
        try {
            flux.asFlow().collect { chatResponse ->
                lastChatResponse = chatResponse
                for ((generationIndex, generation) in chatResponse.results.withIndex()) {
                    val assistantMessage = generation.output
                    val text = assistantMessage.text
                    if (!text.isNullOrEmpty()) {
                        emitTextDelta(text, generationIndex)
                    }
                    // Emit reasoning content if present
                    val reasoningContent = assistantMessage.metadata["reasoningContent"]
                        ?.toString()
                        ?.takeIf { it.isNotEmpty() }
                    if (reasoningContent != null) {
                        emitReasoningDelta(text = reasoningContent, index = generationIndex)
                    }
                    if (assistantMessage.hasToolCalls()) {
                        toolCallAssembler.accept(assistantMessage.toolCalls, generationIndex, this)
                    }
                }
            }
            toolCallAssembler.flush(this)
        } catch (e: CancellationException) {
            throw e
        } catch (e: LLMClientException) {
            throw e
        } catch (e: Exception) {
            throw LLMClientException(clientName, "ChatModel.stream() failed during collection: ${e.message}", e)
        } finally {
            // Always emit End frame so downstream consumers are not left hanging
            val finishReason = lastChatResponse?.results?.firstOrNull()?.metadata?.finishReason
            val usage = lastChatResponse?.metadata?.usage
            val metaInfo = if (usage != null) {
                ResponseMetaInfo.create(
                    clock = clock,
                    totalTokensCount = usage.totalTokens,
                    inputTokensCount = usage.promptTokens,
                    outputTokensCount = usage.completionTokens
                )
            } else {
                null
            }
            emitEnd(finishReason = finishReason, metaInfo = metaInfo)
        }
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

        if (prompt.messages.any { it.hasAttachments() }) {
            throw UnsupportedOperationException(
                "Moderation of non-text content (images, audio, video, files) is not supported by Spring AI. " +
                    "Only text-only prompts can be moderated. Remove attachments before calling moderate()."
            )
        }

        val response = try {
            val instructions = prompt.messages.joinToString(separator = "\n\n") { it.content.trim() }.trim()
            springModerationModel.call(ModerationPrompt(instructions))
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
