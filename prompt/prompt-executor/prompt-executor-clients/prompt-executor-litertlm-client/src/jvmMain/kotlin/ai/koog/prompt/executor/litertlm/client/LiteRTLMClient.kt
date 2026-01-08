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

/**
 * Client for interacting with LiteRT-LM for on-device LLM inference.
 *
 * LiteRT-LM is Google's on-device inference engine that enables running LLMs locally
 * on Android and JVM platforms without requiring network connectivity.
 *
 * Implements [LLMClient] for executing prompts and streaming responses.
 *
 * @param modelPath The absolute path to the LiteRT-LM model file (.litertlm format).
 * @param backend The compute backend to use for inference.
 * @param cacheDir Optional cache directory path to improve model load times.
 * @param defaultTopK Default top-K sampling parameter. Limits token selection to top N tokens.
 * @param defaultTopP Default top-P (nucleus) sampling parameter. Cumulative probability threshold.
 * @param defaultTemperature Default temperature for response randomness (lower = more deterministic).
 * @param clock Clock instance used for tracking response metadata timestamps.
 */
public class LiteRTLMClient(
    private val modelPath: String,
    private val backend: Backend = Backend.CPU,
    private val cacheDir: String? = null,
    private val defaultTopK: Int = DEFAULT_TOP_K,
    private val defaultTopP: Float = DEFAULT_TOP_P,
    private val defaultTemperature: Float = DEFAULT_TEMPERATURE,
    private val clock: Clock = Clock.System,
) : LLMClient {

    private companion object {
        private val logger = KotlinLogging.logger { }

        private const val DEFAULT_TOP_K = 40
        private const val DEFAULT_TOP_P = 0.95f
        private const val DEFAULT_TEMPERATURE = 0.8f
    }

    /**
     * Compute backend options for LiteRT-LM inference.
     */
    public enum class Backend {
        /** CPU-based inference */
        CPU,
        /** GPU-accelerated inference */
        GPU
    }

    private var engine: Engine? = null
    private var isInitialized = false

    /**
     * Initializes the LiteRT-LM engine.
     * This should be called before executing any prompts.
     * Initialization can take several seconds depending on model size.
     */
    public suspend fun initialize(): Unit = withContext(Dispatchers.IO) {
        if (isInitialized) return@withContext

        try {
            val engineConfig = EngineConfig(
                modelPath = modelPath,
                backend = when (backend) {
                    Backend.CPU -> LiteRTBackend.CPU
                    Backend.GPU -> LiteRTBackend.GPU
                },
                cacheDir = cacheDir
            )

            engine = Engine(engineConfig).also { it.initialize() }
            isInitialized = true
            logger.info { "LiteRT-LM engine initialized with model: $modelPath" }
        } catch (e: Exception) {
            val exception = LLMClientException(
                clientName = clientName,
                message = "Failed to initialize LiteRT-LM engine: ${e.message}",
                cause = e
            )
            logger.error(exception) { exception.message }
            throw exception
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
        ensureInitialized()

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

                val responseMetadata = ResponseMetaInfo.create(clock)
                val responseText = response?.toString() ?: ""

                listOf(
                    Message.Assistant(
                        content = responseText,
                        metaInfo = responseMetadata
                    )
                )
            }
        } catch (e: Exception) {
            val exception = LLMClientException(
                clientName = clientName,
                message = "Failed to execute prompt: ${e.message}",
                cause = e
            )
            logger.error(exception) { exception.message }
            throw exception
        }
    }

    override fun executeStreaming(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ): Flow<StreamFrame> = streamFrameFlow {
        require(model.provider == LLMProvider.LiteRTLM) { "Model not supported by LiteRT-LM" }
        ensureInitialized()

        val currentEngine = engine ?: throw LLMClientException(
            clientName = clientName,
            message = "LiteRT-LM engine not initialized"
        )

        val conversationConfig = createConversationConfig(prompt, emptyList())

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
     * Creates a ConversationConfig from the prompt parameters.
     */
    @Suppress("UNUSED_PARAMETER")
    private fun createConversationConfig(prompt: Prompt, tools: List<ToolDescriptor>): ConversationConfig {
        val systemMessage = prompt.messages
            .filterIsInstance<Message.System>()
            .firstOrNull()
            ?.content

        // Use prompt temperature if provided, otherwise use default
        val temperature = prompt.params.temperature?.toFloat() ?: defaultTemperature

        return ConversationConfig(
            systemMessage = systemMessage?.let { LiteRTMessage.of(it) },
            samplerConfig = SamplerConfig(
                topK = defaultTopK,
                topP = defaultTopP,
                temperature = temperature
            )
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
                // LiteRT-LM supports ImageFile for local paths
                // For URLs, we'd need to download first - throw for now
                throw LLMClientException(
                    clientName = clientName,
                    message = "Image URLs are not supported. Please provide image as binary content."
                )
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
                throw LLMClientException(
                    clientName = clientName,
                    message = "Audio URLs are not supported. Please provide audio as binary content."
                )
            }
            is AttachmentContent.PlainText -> {
                throw LLMClientException(
                    clientName = clientName,
                    message = "Audio cannot have plain text content"
                )
            }
        }
    }

    private fun ensureInitialized() {
        if (!isInitialized) {
            throw LLMClientException(
                clientName = clientName,
                message = "LiteRT-LM client not initialized. Call initialize() first."
            )
        }
    }

    override fun close() {
        engine?.close()
        engine = null
        isInitialized = false
        logger.info { "LiteRT-LM engine closed" }
    }
}
