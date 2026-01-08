package ai.koog.prompt.executor.litertlm.client

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.LLMClientException
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.AttachmentContent
import ai.koog.prompt.message.ContentPart
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.prompt.streaming.emitAppend
import ai.koog.prompt.streaming.streamFrameFlow
import com.google.ai.edge.litertlm.BenchmarkInfo
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.LogSeverity
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.Backend as LiteRTBackend
import com.google.ai.edge.litertlm.Message as LiteRTMessage
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import java.util.concurrent.atomic.AtomicReference

/**
 * Client for interacting with LiteRT-LM for on-device LLM inference.
 *
 * LiteRT-LM is Google's on-device inference engine that enables running LLMs locally
 * on Android and JVM platforms without requiring network connectivity.
 *
 * ## Basic Usage (Stateless)
 * ```kotlin
 * val client = LiteRTLMClient.create(
 *     LiteRTLMClientConfig(modelPath = "/path/to/model.litertlm")
 * )
 * val response = client.execute(prompt, model, tools)
 * client.close()
 * ```
 *
 * ## Multi-turn Conversation (Recommended)
 * ```kotlin
 * val client = LiteRTLMClient.create(config)
 * client.conversation(systemPrompt = "You are helpful.").use { conv ->
 *     val r1 = conv.send("Hello")
 *     val r2 = conv.send("Tell me more")  // Maintains context
 * }
 * ```
 *
 * @param config The configuration for the client.
 */
public class LiteRTLMClient private constructor(
    private val config: LiteRTLMClientConfig,
    private val engine: Engine,
    private val toolExecutor: ToolExecutor? = null,
) : LLMClient {

    private companion object {
        private val logger = KotlinLogging.logger { }
    }

    // Atomic reference for lock-free reads of closed state
    private val closed = AtomicReference(false)

    /** Returns `true` if the client has been closed. */
    public fun isClosed(): Boolean = closed.get()

    /**
     * Provides the type of Language Learning Model (LLM) provider used by the client.
     */
    override fun llmProvider(): LLMProvider = LLMProvider.LiteRTLM

    /**
     * Creates a managed conversation for multi-turn interactions.
     *
     * This is the recommended API for chat-like interactions where context
     * should be preserved across multiple messages.
     *
     * @param systemPrompt Optional system prompt for the conversation.
     * @param samplerConfig Optional sampler configuration override.
     * @return A [ManagedConversation] that maintains state across turns.
     */
    @MustUseReturnValue("The returned ManagedConversation should be used for sending messages")
    public fun conversation(
        systemPrompt: String? = null,
        samplerConfig: LiteRTLMSamplerConfig? = null,
    ): ManagedConversation {
        checkNotClosed()

        val effectiveSampler = samplerConfig ?: config.samplerConfig
        val nativeSamplerConfig = effectiveSampler?.toNative()
            ?: SamplerConfig(
                topK = LiteRTLMSamplerConfig.DEFAULT_TOP_K,
                topP = LiteRTLMSamplerConfig.DEFAULT_TOP_P,
                temperature = LiteRTLMSamplerConfig.DEFAULT_TEMPERATURE,
                seed = 0,
            )

        val conversationConfig = ConversationConfig(
            systemMessage = systemPrompt?.let { LiteRTMessage.of(it) },
            samplerConfig = nativeSamplerConfig,
        )

        val nativeConversation = engine.createConversation(conversationConfig)
        return ManagedConversation(nativeConversation, config.clock)
    }

    /**
     * Executes a prompt and returns the response.
     *
     * Note: This creates a new conversation per call. For multi-turn interactions,
     * use [conversation] instead to maintain context.
     */
    override suspend fun execute(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ): List<Message.Response> = withContext(Dispatchers.IO) {
        require(model.provider == LLMProvider.LiteRTLM) { "Model not supported by LiteRT-LM" }
        checkNotClosed()

        try {
            val conversationConfig = createConversationConfig(prompt, tools)

            engine.createConversation(conversationConfig).use { conversation ->
                val conversationMessages = buildConversationMessages(prompt, model)

                var response: LiteRTMessage? = null
                for (message in conversationMessages) {
                    response = conversation.sendMessage(message)
                }

                val responseMetadata = ResponseMetaInfo.create(config.clock)
                val responseText = response?.toString() ?: ""

                listOf(
                    Message.Assistant(
                        content = responseText,
                        metaInfo = responseMetadata
                    )
                )
            }
        } catch (e: Exception) {
            throw LLMClientException(
                clientName = clientName,
                message = "Failed to execute prompt: ${e.message}",
                cause = e
            )
        }
    }

    override fun executeStreaming(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ): Flow<StreamFrame> = streamFrameFlow {
        require(model.provider == LLMProvider.LiteRTLM) { "Model not supported by LiteRT-LM" }
        checkNotClosed()

        val conversationConfig = createConversationConfig(prompt, tools)

        engine.createConversation(conversationConfig).use { conversation ->
            val conversationMessages = buildConversationMessages(prompt, model)

            // Send all messages except the last one synchronously to build history
            for (i in 0 until conversationMessages.size - 1) {
                conversation.sendMessage(conversationMessages[i])
            }

            // Stream the last message
            val lastMessage = conversationMessages.lastOrNull()
            if (lastMessage != null) {
                conversation.sendMessageAsync(lastMessage)
                    .collect { chunk ->
                        emitAppend(chunk.toString())
                    }
            }
        }
    }

    override suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult {
        throw LLMClientException(
            clientName = clientName,
            message = "Moderation is not supported by LiteRT-LM"
        )
    }

    /**
     * Closes the client and releases all native resources.
     *
     * After calling this method, the client cannot be used.
     */
    override fun close() {
        if (closed.compareAndSet(false, true)) {
            engine.close()
            logger.info { "LiteRT-LM client closed" }
        }
    }

    private fun createConversationConfig(prompt: Prompt, tools: List<ToolDescriptor>): ConversationConfig {
        val baseSystemMessage = prompt.messages
            .filterIsInstance<Message.System>()
            .firstOrNull()
            ?.content

        val samplerConfig = config.samplerConfig
        val temperature = prompt.params.temperature
            ?: samplerConfig?.temperature
            ?: LiteRTLMSamplerConfig.DEFAULT_TEMPERATURE

        val finalSamplerConfig = if (samplerConfig != null) {
            SamplerConfig(
                topK = samplerConfig.topK,
                topP = samplerConfig.topP,
                temperature = temperature,
                seed = samplerConfig.seed,
            )
        } else {
            SamplerConfig(
                topK = LiteRTLMSamplerConfig.DEFAULT_TOP_K,
                topP = LiteRTLMSamplerConfig.DEFAULT_TOP_P,
                temperature = temperature,
                seed = 0,
            )
        }

        // Create tool bridge if tools are provided and executor is available
        val (toolBridge, systemMessage) = if (tools.isNotEmpty() && toolExecutor != null) {
            val bridge = LiteRTLMToolBridge(tools, toolExecutor)
            val toolsPrompt = bridge.generateToolsPrompt()
            val enhancedSystemMessage = buildString {
                if (baseSystemMessage != null) {
                    appendLine(baseSystemMessage)
                    appendLine()
                }
                append(toolsPrompt)
            }
            bridge to enhancedSystemMessage
        } else {
            null to baseSystemMessage
        }

        return ConversationConfig(
            systemMessage = systemMessage?.let { LiteRTMessage.of(it) },
            tools = listOfNotNull(toolBridge),
            samplerConfig = finalSamplerConfig,
        )
    }

    private fun buildConversationMessages(prompt: Prompt, model: LLModel): List<LiteRTMessage> {
        return buildList(prompt.messages.size) {
            for (message in prompt.messages) {
                when (message) {
                    is Message.System -> {
                        // Handled in ConversationConfig
                    }
                    is Message.User -> {
                        add(buildUserMessage(message, model))
                    }
                    is Message.Assistant -> {
                        add(LiteRTMessage.of(message.content))
                    }
                    is Message.Tool.Call -> {
                        add(LiteRTMessage.of("[Tool Call: ${message.tool}] ${message.content}"))
                    }
                    is Message.Tool.Result -> {
                        add(LiteRTMessage.of("[Tool Result: ${message.tool}] ${message.content}"))
                    }
                    is Message.Reasoning -> {
                        add(LiteRTMessage.of("[Reasoning] ${message.content}"))
                    }
                }
            }
        }.also { messages ->
            if (messages.isEmpty()) {
                throw LLMClientException(
                    clientName = clientName,
                    message = "Prompt must contain at least one non-system message"
                )
            }
        }
    }

    private fun buildUserMessage(userMessage: Message.User, model: LLModel): LiteRTMessage {
        val contentParts = buildList(userMessage.parts.size) {
            for (part in userMessage.parts) {
                when (part) {
                    is ContentPart.Text -> add(Content.Text(part.text))
                    is ContentPart.Image -> {
                        if (LLMCapability.Vision.Image !in model.capabilities) {
                            throw LLMClientException(
                                clientName = clientName,
                                message = "Model ${model.id} does not support image inputs"
                            )
                        }
                        add(convertImageContent(part))
                    }
                    is ContentPart.Audio -> {
                        if (LLMCapability.Audio !in model.capabilities) {
                            throw LLMClientException(
                                clientName = clientName,
                                message = "Model ${model.id} does not support audio inputs"
                            )
                        }
                        add(convertAudioContent(part))
                    }
                    is ContentPart.Video -> {
                        throw LLMClientException(
                            clientName = clientName,
                            message = "Video content is not yet supported by LiteRT-LM"
                        )
                    }
                    is ContentPart.File -> {
                        val fileText = when (val content = part.content) {
                            is AttachmentContent.PlainText -> content.text
                            is AttachmentContent.Binary -> content.asBase64()
                            is AttachmentContent.URL -> "[File URL: ${content.url}]"
                        }
                        add(Content.Text("[File: ${part.fileName ?: "unnamed"}]\n$fileText"))
                    }
                }
            }
        }

        if (contentParts.isEmpty()) {
            throw LLMClientException(
                clientName = clientName,
                message = "User message must contain at least one content part"
            )
        }

        return if (contentParts.size == 1 && contentParts.first() is Content.Text) {
            LiteRTMessage.of((contentParts.first() as Content.Text).text)
        } else {
            LiteRTMessage.of(*contentParts.toTypedArray())
        }
    }

    // Using Kotlin 2.2 guard conditions in when for cleaner conditional logic
    private fun convertImageContent(image: ContentPart.Image): Content = when (val content = image.content) {
        is AttachmentContent.Binary -> Content.ImageBytes(content.asBytes())
        is AttachmentContent.URL if content.url.startsWith("file://") ->
            Content.ImageFile(content.url.removePrefix("file://"))
        is AttachmentContent.URL -> throw LLMClientException(
            clientName = clientName,
            message = "Remote image URLs are not supported. Use file:// URLs or binary content."
        )
        is AttachmentContent.PlainText -> throw LLMClientException(
            clientName = clientName,
            message = "Image cannot have plain text content"
        )
    }

    private fun convertAudioContent(audio: ContentPart.Audio): Content = when (val content = audio.content) {
        is AttachmentContent.Binary -> Content.AudioBytes(content.asBytes())
        is AttachmentContent.URL if content.url.startsWith("file://") ->
            Content.AudioFile(content.url.removePrefix("file://"))
        is AttachmentContent.URL -> throw LLMClientException(
            clientName = clientName,
            message = "Remote audio URLs are not supported. Use file:// URLs or binary content."
        )
        is AttachmentContent.PlainText -> throw LLMClientException(
            clientName = clientName,
            message = "Audio cannot have plain text content"
        )
    }

    private fun checkNotClosed() {
        check(!isClosed()) { "Client has been closed." }
    }

    private fun LiteRTLMSamplerConfig.toNative() = SamplerConfig(
        topK = topK,
        topP = topP,
        temperature = temperature,
        seed = seed,
    )

    public companion object {
        /**
         * Creates and initializes a LiteRT-LM client.
         *
         * This is the recommended way to create a client. The engine is
         * initialized before returning, so the client is immediately ready for use.
         *
         * ## Tool Support
         *
         * To enable tool calling, provide a [ToolExecutor]:
         *
         * ```kotlin
         * val client = LiteRTLMClient.create(config) { toolName, args ->
         *     when (toolName) {
         *         "get_weather" -> getWeather(args["city"] as String)
         *         "calculate" -> calculate(args["expression"] as String)
         *         else -> "Unknown tool"
         *     }
         * }
         * ```
         *
         * @param config Configuration for the client.
         * @param toolExecutor Optional callback to execute tools. If provided, tool calling is enabled.
         * @return An initialized [LiteRTLMClient].
         * @throws LLMClientException if initialization fails.
         */
        @MustUseReturnValue("The returned LiteRTLMClient must be used and eventually closed")
        public suspend fun create(
            config: LiteRTLMClientConfig,
            toolExecutor: ToolExecutor? = null,
        ): LiteRTLMClient = withContext(Dispatchers.IO) {
            try {
                val engineConfig = EngineConfig(
                    modelPath = config.engineConfig.modelPath,
                    backend = config.engineConfig.backend.toLiteRTBackend(),
                    visionBackend = config.engineConfig.visionBackend?.toLiteRTBackend(),
                    audioBackend = config.engineConfig.audioBackend?.toLiteRTBackend(),
                    maxNumTokens = config.engineConfig.maxNumTokens,
                    cacheDir = config.engineConfig.cacheDir,
                )

                val engine = Engine(engineConfig).also { it.initialize() }
                logger.info { "LiteRT-LM engine initialized with model: ${config.engineConfig.modelPath}" }

                LiteRTLMClient(config, engine, toolExecutor)
            } catch (e: Exception) {
                throw LLMClientException(
                    clientName = "LiteRTLMClient",
                    message = "Failed to initialize LiteRT-LM engine: ${e.message}",
                    cause = e
                )
            }
        }

        private fun LiteRTLMBackend.toLiteRTBackend(): LiteRTBackend = when (this) {
            LiteRTLMBackend.CPU -> LiteRTBackend.CPU
            LiteRTLMBackend.GPU -> LiteRTBackend.GPU
            LiteRTLMBackend.NPU -> LiteRTBackend.NPU
        }

        /**
         * Sets the minimum log severity for all LiteRT-LM native libraries.
         */
        public fun setLogSeverity(level: LiteRTLMLogSeverity) {
            Engine.setNativeMinLogSeverity(level.toNative())
        }
    }
}

/**
 * Log severity levels for LiteRT-LM native libraries.
 */
public enum class LiteRTLMLogSeverity {
    VERBOSE,
    DEBUG,
    INFO,
    WARNING,
    ERROR,
    FATAL,
    SILENT;

    internal fun toNative(): LogSeverity = when (this) {
        VERBOSE -> LogSeverity.VERBOSE
        DEBUG -> LogSeverity.DEBUG
        INFO -> LogSeverity.INFO
        WARNING -> LogSeverity.WARNING
        ERROR -> LogSeverity.ERROR
        FATAL -> LogSeverity.FATAL
        SILENT -> LogSeverity.INFINITY
    }
}

/**
 * A managed conversation that maintains state across multiple turns.
 *
 * This class wraps a native LiteRT-LM [Conversation] and provides a
 * higher-level API for multi-turn interactions.
 *
 * Example:
 * ```kotlin
 * client.conversation(systemPrompt = "You are helpful.").use { conv ->
 *     val greeting = conv.send("Hello!")
 *     val followUp = conv.send("Tell me more about that.")
 * }
 * ```
 */
public class ManagedConversation internal constructor(
    private val conversation: Conversation,
    private val clock: Clock,
) : AutoCloseable {

    /**
     * Mutex to ensure thread-safe access to the conversation.
     *
     * LiteRT-LM conversations are stateful and not thread-safe for concurrent
     * message sending. This mutex ensures only one message is processed at a time.
     */
    private val mutex = Mutex()

    /**
     * Sends a text message and returns the response.
     *
     * This method is thread-safe. Concurrent calls will be serialized.
     *
     * @param text The message text to send.
     * @return The assistant's response.
     */
    @MustUseReturnValue("The assistant's response should be processed")
    public suspend fun send(text: String): Message.Assistant = mutex.withLock {
        val response = conversation.sendMessage(LiteRTMessage.of(text))
        Message.Assistant(
            content = response.toString(),
            metaInfo = ResponseMetaInfo.create(clock),
        )
    }

    /**
     * Sends a message with multimodal content.
     *
     * This method is thread-safe. Concurrent calls will be serialized.
     *
     * @param contents The content parts to send.
     * @return The assistant's response.
     */
    @MustUseReturnValue("The assistant's response should be processed")
    public suspend fun send(vararg contents: Content): Message.Assistant = mutex.withLock {
        val response = conversation.sendMessage(LiteRTMessage.of(*contents))
        Message.Assistant(
            content = response.toString(),
            metaInfo = ResponseMetaInfo.create(clock),
        )
    }

    /**
     * Sends a text message and streams the response.
     *
     * This method acquires a lock for the duration of streaming.
     * Concurrent calls will wait until the stream completes.
     *
     * @param text The message text to send.
     * @return A flow of response chunks.
     */
    @MustUseReturnValue("The returned Flow must be collected to receive the response")
    public fun sendStreaming(text: String): Flow<String> = kotlinx.coroutines.flow.flow {
        mutex.withLock {
            conversation.sendMessageAsync(LiteRTMessage.of(text))
                .collect { message ->
                    emit(message.toString())
                }
        }
    }

    /**
     * Cancels any ongoing inference in this conversation.
     *
     * This can be called concurrently with [send] or [sendStreaming] to
     * abort a long-running inference.
     */
    public fun cancel() {
        conversation.cancelProcess()
    }

    /**
     * Gets benchmark information for this conversation.
     *
     * @return Benchmark metrics including timing and token counts.
     */
    @MustUseReturnValue("Benchmark info should be used for performance analysis")
    @OptIn(ExperimentalApi::class)
    public suspend fun getBenchmarkInfo(): BenchmarkInfo = mutex.withLock {
        conversation.getBenchmarkInfo()
    }

    override fun close() {
        conversation.close()
    }
}
