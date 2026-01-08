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
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.Backend as LiteRTBackend
import com.google.ai.edge.litertlm.Message as LiteRTMessage
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlin.jvm.Volatile

/**
 * Client for interacting with LiteRT-LM for on-device LLM inference.
 *
 * LiteRT-LM is Google's on-device inference engine that enables running LLMs locally
 * on Android and JVM platforms without requiring network connectivity.
 *
 * Example usage:
 * ```kotlin
 * val config = LiteRTLMClientConfig(
 *     engineConfig = LiteRTLMEngineConfig(
 *         modelPath = "/path/to/model.litertlm",
 *         backend = LiteRTLMBackend.GPU,
 *     ),
 *     samplerConfig = LiteRTLMSamplerConfig(
 *         topK = 40,
 *         topP = 0.95,
 *         temperature = 0.8,
 *     ),
 * )
 * val client = LiteRTLMClient(config)
 * client.initialize()
 * // ... use client ...
 * client.close()
 * ```
 *
 * @param config The configuration for the client.
 */
public class LiteRTLMClient(
    private val config: LiteRTLMClientConfig,
) : LLMClient {

    private companion object {
        private val logger = KotlinLogging.logger { }
    }

    // A lock to protect access to the engine's state and native handle.
    private val lock = Any()

    /**
     * The underlying LiteRT-LM engine. A non-null value indicates an initialized engine.
     *
     * `@Volatile` ensures that changes to the engine are immediately visible across all threads.
     */
    @Volatile
    private var engine: Engine? = null

    /** Returns `true` if the engine is initialized and ready for use; `false` otherwise. */
    public fun isInitialized(): Boolean {
        return engine != null
    }

    /**
     * Initializes the LiteRT-LM engine.
     *
     * **Note:** This operation can take a significant amount of time (e.g., 10 seconds) depending on
     * the model size and device hardware. It is strongly recommended to call this method on a
     * background thread to avoid blocking the main thread.
     *
     * @throws IllegalStateException if the engine has already been initialized.
     * @throws LLMClientException if initialization fails.
     */
    public suspend fun initialize(): Unit = withContext(Dispatchers.IO) {
        synchronized(lock) {
            check(!isInitialized()) { "Engine is already initialized." }

            try {
                val engineConfig = createEngineConfig()
                engine = Engine(engineConfig).also { it.initialize() }
                logger.info { "LiteRT-LM engine initialized with model: ${config.engineConfig.modelPath}" }
            } catch (e: Exception) {
                throw LLMClientException(
                    clientName = clientName,
                    message = "Failed to initialize LiteRT-LM engine: ${e.message}",
                    cause = e
                )
            }
        }
    }

    /**
     * Provides the type of Language Learning Model (LLM) provider used by the client.
     *
     * @return The specific LLMProvider implementation, which is of type LLMProvider.LiteRTLM.
     */
    override fun llmProvider(): LLMProvider = LLMProvider.LiteRTLM

    override suspend fun execute(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ): List<Message.Response> = withContext(Dispatchers.IO) {
        require(model.provider == LLMProvider.LiteRTLM) { "Model not supported by LiteRT-LM" }
        checkInitialized()

        val currentEngine = engine ?: throw LLMClientException(
            clientName = clientName,
            message = "LiteRT-LM engine not initialized"
        )

        try {
            val conversationConfig = createConversationConfig(prompt, tools)

            currentEngine.createConversation(conversationConfig).use { conversation ->
                // Build and send conversation history
                val conversationMessages = buildConversationMessages(prompt, model)

                // Send each message in the conversation history
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
        checkInitialized()

        val currentEngine = engine ?: throw LLMClientException(
            clientName = clientName,
            message = "LiteRT-LM engine not initialized"
        )

        val conversationConfig = createConversationConfig(prompt, tools)

        currentEngine.createConversation(conversationConfig).use { conversation ->
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
     * Cancels any ongoing inference process.
     *
     * If there is no ongoing inference process, it is a no-op.
     *
     * Note: This method requires an active conversation context. For conversation-level
     * cancellation, use the conversation's cancelProcess() method directly.
     *
     * @throws IllegalStateException if the engine is not initialized.
     */
    public fun cancelProcess(conversation: Conversation) {
        checkInitialized()
        conversation.cancelProcess()
    }

    /**
     * Closes the engine and releases the native LiteRT-LM engine's resources.
     *
     * @throws IllegalStateException if the engine is not initialized.
     */
    override fun close() {
        synchronized(lock) {
            checkInitialized()
            engine?.close()
            engine = null
            logger.info { "LiteRT-LM engine closed" }
        }
    }

    /**
     * Creates an EngineConfig from the client configuration.
     */
    private fun createEngineConfig(): EngineConfig {
        val cfg = config.engineConfig
        return EngineConfig(
            modelPath = cfg.modelPath,
            backend = cfg.backend.toLiteRTBackend(),
            visionBackend = cfg.visionBackend?.toLiteRTBackend(),
            audioBackend = cfg.audioBackend?.toLiteRTBackend(),
            maxNumTokens = cfg.maxNumTokens,
            cacheDir = cfg.cacheDir,
        )
    }

    /**
     * Creates a ConversationConfig from the prompt parameters.
     */
    @Suppress("UNUSED_PARAMETER")
    private fun createConversationConfig(prompt: Prompt, tools: List<ToolDescriptor>): ConversationConfig {
        val systemMessage = prompt.messages
            .filterIsInstance<Message.System>()
            .firstOrNull()
            ?.content

        // Use prompt temperature if provided, otherwise use config sampler or default
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

        return ConversationConfig(
            systemMessage = systemMessage?.let { LiteRTMessage.of(it) },
            samplerConfig = finalSamplerConfig,
            // Note: LiteRT-LM tools are registered via annotation-based classes.
            // Integration with ToolDescriptor would require runtime code generation
            // or a bridge layer. For now, tools parameter is accepted but not used.
            // TODO: Implement tool bridge when LiteRT-LM provides programmatic tool registration
        )
    }

    /**
     * Builds a list of LiteRT-LM messages from the prompt, handling conversation history.
     * Excludes system messages (handled in ConversationConfig).
     */
    private fun buildConversationMessages(prompt: Prompt, model: LLModel): List<LiteRTMessage> {
        val messages = mutableListOf<LiteRTMessage>()

        for (message in prompt.messages) {
            when (message) {
                is Message.System -> {
                    // System message is handled in ConversationConfig, skip here
                }
                is Message.User -> {
                    messages.add(buildUserMessage(message, model))
                }
                is Message.Assistant -> {
                    // Add assistant messages to maintain conversation context
                    messages.add(LiteRTMessage.of(message.content))
                }
                is Message.Tool.Call -> {
                    // Tool calls from previous turns - add as context
                    messages.add(LiteRTMessage.of("[Tool Call: ${message.tool}] ${message.content}"))
                }
                is Message.Tool.Result -> {
                    // Tool results from previous turns
                    messages.add(LiteRTMessage.of("[Tool Result: ${message.tool}] ${message.content}"))
                }
                is Message.Reasoning -> {
                    // Reasoning messages - include if model supports it
                    messages.add(LiteRTMessage.of("[Reasoning] ${message.content}"))
                }
            }
        }

        if (messages.isEmpty()) {
            throw LLMClientException(
                clientName = clientName,
                message = "Prompt must contain at least one non-system message"
            )
        }

        return messages
    }

    /**
     * Builds a LiteRT-LM message from a User message, handling multimodal content.
     */
    private fun buildUserMessage(userMessage: Message.User, model: LLModel): LiteRTMessage {
        val contentParts = mutableListOf<Content>()

        for (part in userMessage.parts) {
            when (part) {
                is ContentPart.Text -> {
                    contentParts.add(Content.Text(part.text))
                }
                is ContentPart.Image -> {
                    if (LLMCapability.Vision.Image !in model.capabilities) {
                        throw LLMClientException(
                            clientName = clientName,
                            message = "Model ${model.id} does not support image inputs"
                        )
                    }
                    contentParts.add(convertImageContent(part))
                }
                is ContentPart.Audio -> {
                    if (LLMCapability.Audio !in model.capabilities) {
                        throw LLMClientException(
                            clientName = clientName,
                            message = "Model ${model.id} does not support audio inputs"
                        )
                    }
                    contentParts.add(convertAudioContent(part))
                }
                is ContentPart.Video -> {
                    throw LLMClientException(
                        clientName = clientName,
                        message = "Video content is not yet supported by LiteRT-LM"
                    )
                }
                is ContentPart.File -> {
                    // Convert file to text content
                    val fileText = when (val content = part.content) {
                        is AttachmentContent.PlainText -> content.text
                        is AttachmentContent.Binary -> content.asBase64()
                        is AttachmentContent.URL -> "[File URL: ${content.url}]"
                    }
                    contentParts.add(Content.Text("[File: ${part.fileName ?: "unnamed"}]\n$fileText"))
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
            // Simple text message
            LiteRTMessage.of((contentParts.first() as Content.Text).text)
        } else {
            // Multimodal message
            LiteRTMessage.of(*contentParts.toTypedArray())
        }
    }

    /**
     * Converts an image ContentPart to LiteRT-LM Content.
     */
    private fun convertImageContent(image: ContentPart.Image): Content {
        return when (val content = image.content) {
            is AttachmentContent.Binary -> {
                Content.ImageBytes(content.asBytes())
            }
            is AttachmentContent.URL -> {
                // Check if it's a file:// URL (local file path)
                val url = content.url
                if (url.startsWith("file://")) {
                    Content.ImageFile(url.removePrefix("file://"))
                } else {
                    throw LLMClientException(
                        clientName = clientName,
                        message = "Remote image URLs are not supported. Use file:// URLs or binary content."
                    )
                }
            }
            is AttachmentContent.PlainText -> {
                throw LLMClientException(
                    clientName = clientName,
                    message = "Image cannot have plain text content"
                )
            }
        }
    }

    /**
     * Converts an audio ContentPart to LiteRT-LM Content.
     */
    private fun convertAudioContent(audio: ContentPart.Audio): Content {
        return when (val content = audio.content) {
            is AttachmentContent.Binary -> {
                Content.AudioBytes(content.asBytes())
            }
            is AttachmentContent.URL -> {
                // Check if it's a file:// URL (local file path)
                val url = content.url
                if (url.startsWith("file://")) {
                    Content.AudioFile(url.removePrefix("file://"))
                } else {
                    throw LLMClientException(
                        clientName = clientName,
                        message = "Remote audio URLs are not supported. Use file:// URLs or binary content."
                    )
                }
            }
            is AttachmentContent.PlainText -> {
                throw LLMClientException(
                    clientName = clientName,
                    message = "Audio cannot have plain text content"
                )
            }
        }
    }

    /** Throws [IllegalStateException] if the engine is not initialized. */
    private fun checkInitialized() {
        check(isInitialized()) { "Engine is not initialized." }
    }

    /** Converts LiteRTLMBackend to the native LiteRT Backend. */
    private fun LiteRTLMBackend.toLiteRTBackend(): LiteRTBackend = when (this) {
        LiteRTLMBackend.CPU -> LiteRTBackend.CPU
        LiteRTLMBackend.GPU -> LiteRTBackend.GPU
        LiteRTLMBackend.NPU -> LiteRTBackend.NPU
    }
}
