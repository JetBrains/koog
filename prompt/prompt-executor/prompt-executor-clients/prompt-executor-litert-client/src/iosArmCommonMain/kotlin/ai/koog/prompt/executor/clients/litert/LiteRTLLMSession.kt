package ai.koog.prompt.executor.clients.litert

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.Prompt
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.prompt.streaming.buildStreamFrameFlow
import io.github.johnrocky.litertlm.Contents
import io.github.johnrocky.litertlm.Conversation
import io.github.johnrocky.litertlm.ConversationConfig
import io.github.johnrocky.litertlm.Engine
import io.github.johnrocky.litertlm.EngineConfig
import io.github.johnrocky.litertlm.SamplerConfig
import io.github.johnrocky.litertlm.tool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Manages a LiteRT inference session for a single [LiteRTLLMClient].
 *
 * The same lifecycle as the Android client's session: a lazily created [Engine] and a long-lived
 * [Conversation] are reused across calls while the requested model, tool set, sampling parameters and
 * system instruction remain unchanged, and only the newly appended message is sent. The conversation is
 * rebuilt when that key changes or when the incoming prompt's prefix diverges from what was previously sent.
 *
 * Session access is serialized via a coroutine [Mutex] and the work runs on [Dispatchers.IO], so callers
 * (including the main thread) are never blocked.
 *
 * @param config Configuration for the LiteRT client, including model paths and backend.
 */
internal class LiteRTLLMSession(private val config: LiteRTClientConfig) {
    private val mutex = Mutex()

    private var engine: Engine? = null
    private var engineConfig: EngineConfig? = null

    private var conversation: Conversation? = null
    private var conversationKey: ConversationKey? = null

    // Snapshot of koog messages already sent to the native conversation (system
    // instruction excluded — it lives in ConversationConfig). Used to detect
    // history divergence and to compute the incremental delta to send.
    private var sentMessages: List<Message> = emptyList()

    /**
     * Returns the current [Engine] if it already targets [model], otherwise builds and
     * initializes a new engine, then atomically replaces the previously cached one.
     *
     * A failed `initialize()` leaves the session usable: the new engine is closed
     * and the previous engine (if any) is retained.
     */
    private suspend fun getCurrentEngine(model: LLModel): Engine {
        val modelPath = "${config.modelsPath}/${model.id}"
        val cached = engine
        if (cached != null && engineConfig?.modelPath == modelPath) {
            return cached
        }

        val newEngineConfig = EngineConfig(
            modelPath = modelPath,
            backend = config.backend,
            cacheDir = config.cacheDir,
            minAvailableMemoryBytes = config.minAvailableMemoryBytes,
        )

        val newEngine = Engine(newEngineConfig)
        try {
            newEngine.initialize()
        } catch (t: Throwable) {
            runCatching { newEngine.close() }
            throw t
        }

        // Initialization succeeded — tear down old state and publish the new engine.
        closeQuietly()
        engineConfig = newEngineConfig
        engine = newEngine
        return newEngine
    }

    /** A turn ready to run: its conversation, every non-system message, and the last of them to send. */
    private class Turn(val conversation: Conversation, val messages: List<Message>) {
        val lastMessage = messages.last()
    }

    /**
     * Returns the conversation for this prompt, reusing the cached one when the cache key matches and the only
     * new message is the last one. Otherwise it rebuilds the conversation and seeds the earlier turns through
     * `initialMessages`, which adds to the history without running inference.
     */
    private suspend fun prepareTurn(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): Turn {
        val currentEngine = getCurrentEngine(model)

        val params: AndroidLocalLLMParams = prompt.params.toAndroidLocalParams()
        val samplerConfig = SamplerConfig(
            topK = params.topK,
            topP = params.topP,
            temperature = params.exactTemperature,
            seed = params.seed ?: 0,
        )

        // System instruction is part of the ConversationConfig in LiteRT and
        // cannot change without recreating the conversation. We extract the
        // first leading system message (if any) and exclude all system
        // messages from the per-turn message stream.
        val systemInstruction = prompt.messages
            .firstOrNull { it is Message.System }
            ?.let { (it as Message.System).parts.joinToString("\n") { part -> part.text } }
        val nonSystemMessages = prompt.messages.filter { it !is Message.System }
        require(nonSystemMessages.isNotEmpty()) {
            "Prompt must contain at least one non-system message"
        }

        val localTools = tools.map { LocalTool(it) }
        val key = ConversationKey(
            modelPath = engineConfig?.modelPath,
            toolNames = localTools.map { it.tool.name },
            sampler = SamplerKey(
                topK = samplerConfig.topK,
                topP = samplerConfig.topP,
                temperature = samplerConfig.temperature,
                seed = samplerConfig.seed,
            ),
            systemInstruction = systemInstruction,
        )

        val priorHistory = nonSystemMessages.dropLast(1)
        val cached = conversation
        if (cached != null && conversationKey == key && sentMessages == priorHistory) {
            return Turn(cached, nonSystemMessages)
        }

        closeConversationQuietly()
        val created = currentEngine.createConversation(
            ConversationConfig(
                samplerConfig = samplerConfig,
                systemInstruction = systemInstruction?.let { Contents.of(it) },
                tools = localTools.map { tool(it) },
                automaticToolCalling = false,
                initialMessages = priorHistory.map { it.toLitertMessage() },
            )
        )
        conversation = created
        conversationKey = key
        sentMessages = priorHistory
        return Turn(created, nonSystemMessages)
    }

    /**
     * Sends the new message of [prompt] to the model and returns the response.
     *
     * @param prompt Prompt containing at least one message.
     * @param model The LLM to run inference with.
     * @param tools Tool descriptors registered with the conversation.
     * @return [Message.Assistant] produced by the model.
     * @throws IllegalArgumentException if [prompt] contains no messages.
     */
    suspend fun execute(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
    ): Message.Assistant {
        require(prompt.messages.isNotEmpty()) { "There should be at least one message" }

        return mutex.withLock {
            withContext(Dispatchers.IO) {
                val turn = prepareTurn(prompt, model, tools)
                try {
                    val response = turn.conversation.sendMessage(turn.lastMessage.toLitertMessage())
                    // Commit on success: the native conversation has now consumed
                    // the last user/tool message AND produced the response, both of
                    // which it keeps in its internal history. Mirror that in
                    // `sentMessages` so the next call's prefix check matches the
                    // prompt that Koog will build (which appends our response).
                    val responseMessage = response.toKoogMessage(config.clock)
                    sentMessages = turn.messages + responseMessage
                    responseMessage
                } catch (t: Throwable) {
                    // The native conversation state may be inconsistent after a
                    // failure; drop it so the next call rebuilds cleanly.
                    closeConversationQuietly()
                    throw t
                }
            }
        }
    }

    /**
     * Sends the new message of [prompt] to the model and emits the response as it is generated, with
     * the same session handling as [execute]. The mutex is held until the stream ends.
     *
     * @throws IllegalArgumentException in the flow if [prompt] contains no messages.
     */
    fun executeStreaming(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
    ): Flow<StreamFrame> = buildStreamFrameFlow {
        require(prompt.messages.isNotEmpty()) { "There should be at least one message" }

        mutex.withLock {
            withContext(Dispatchers.IO) {
                val turn = prepareTurn(prompt, model, tools)
                val text = StringBuilder()
                val toolCalls = mutableListOf<MessagePart.Tool.Call>()
                try {
                    turn.conversation.sendMessageAsync(turn.lastMessage.toLitertMessage()).collect { chunk ->
                        chunk.toKoogMessage(config.clock).parts.forEach { part ->
                            when (part) {
                                is MessagePart.Text -> if (part.text.isNotEmpty()) {
                                    text.append(part.text)
                                    emitTextDelta(part.text)
                                }

                                is MessagePart.Tool.Call -> {
                                    toolCalls += part
                                    emitToolCallDelta(part.id, part.tool, part.args)
                                }

                                else -> Unit
                            }
                        }
                    }
                    val metaInfo = ResponseMetaInfo.create(config.clock)
                    val parts = buildList {
                        if (text.isNotEmpty()) add(MessagePart.Text(text.toString()))
                        addAll(toolCalls)
                    }
                    sentMessages = turn.messages + Message.Assistant(parts, metaInfo)
                    emitEnd(metaInfo = metaInfo)
                } catch (t: Throwable) {
                    closeConversationQuietly()
                    throw t
                }
            }
        }
    }

    /**
     * Closes and releases all LiteRT resources held by this session.
     *
     * Each native close is wrapped in a `runCatching` so cleanup always completes.
     * Safe to call multiple times and from any dispatcher.
     */
    suspend fun close() {
        mutex.withLock {
            withContext(Dispatchers.IO) {
                closeQuietly()
            }
        }
    }

    /** Best-effort tear-down of all cached LiteRT objects. Resets all session state. */
    private fun closeQuietly() {
        closeConversationQuietly()
        runCatching { engine?.close() }
        engine = null
        engineConfig = null
    }

    /** Best-effort tear-down of the cached [Conversation]. Resets conversation state. */
    private fun closeConversationQuietly() {
        runCatching { conversation?.close() }
        conversation = null
        conversationKey = null
        sentMessages = emptyList()
    }

    private data class SamplerKey(
        val topK: Int,
        val topP: Double,
        val temperature: Double,
        val seed: Int,
    )

    private data class ConversationKey(
        val modelPath: String?,
        val toolNames: List<String>,
        val sampler: SamplerKey,
        val systemInstruction: String?,
    )
}
