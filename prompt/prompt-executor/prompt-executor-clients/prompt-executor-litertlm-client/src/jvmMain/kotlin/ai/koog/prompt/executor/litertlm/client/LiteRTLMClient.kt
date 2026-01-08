package ai.koog.prompt.executor.litertlm.client

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.LLMClientException
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.prompt.streaming.emitAppend
import ai.koog.prompt.streaming.streamFrameFlow
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.Backend as LiteRTBackend
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import com.google.ai.edge.litertlm.Message as LiteRTMessage

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
 * @param clock Clock instance used for tracking response metadata timestamps.
 */
public class LiteRTLMClient(
    private val modelPath: String,
    private val backend: Backend = Backend.CPU,
    private val cacheDir: String? = null,
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
            val conversationConfig = createConversationConfig(prompt)

            currentEngine.createConversation(conversationConfig).use { conversation ->
                val userMessage = buildUserMessage(prompt)
                val response = conversation.sendMessage(userMessage)

                val responseMetadata = ResponseMetaInfo.create(clock)

                listOf(
                    Message.Assistant(
                        content = response.toString(),
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

        val conversationConfig = createConversationConfig(prompt)

        currentEngine.createConversation(conversationConfig).use { conversation ->
            val userMessage = buildUserMessage(prompt)

            conversation.sendMessageAsync(userMessage)
                .collect { chunk ->
                    emitAppend(chunk.toString())
                }
        }
    }

    override suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult {
        throw LLMClientException(
            clientName = clientName,
            message = "Moderation is not supported by LiteRT-LM"
        )
    }

    private fun createConversationConfig(prompt: Prompt): ConversationConfig {
        val systemMessage = prompt.messages
            .filterIsInstance<Message.System>()
            .firstOrNull()
            ?.content

        val temperature = prompt.params.temperature?.toFloat() ?: DEFAULT_TEMPERATURE

        return ConversationConfig(
            systemMessage = systemMessage?.let { LiteRTMessage.of(it) },
            samplerConfig = SamplerConfig(
                topK = DEFAULT_TOP_K,
                topP = DEFAULT_TOP_P,
                temperature = temperature
            )
        )
    }

    private fun buildUserMessage(prompt: Prompt): LiteRTMessage {
        // Get the last user message from the prompt
        val userMessages = prompt.messages
            .filterIsInstance<Message.User>()

        val lastUserMessage = userMessages.lastOrNull()
            ?: throw LLMClientException(
                clientName = clientName,
                message = "Prompt must contain at least one user message"
            )

        return LiteRTMessage.of(lastUserMessage.content)
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
