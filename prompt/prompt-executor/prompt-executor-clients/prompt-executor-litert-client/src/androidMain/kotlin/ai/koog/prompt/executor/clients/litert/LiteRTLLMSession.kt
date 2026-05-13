package ai.koog.prompt.executor.clients.litert

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.tool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Manages a LiteRT inference session for a single [LiteRTLLMClient].
 *
 * Maintains a lazily created [Engine] and a long-lived [Conversation] that are
 * reused across [execute] calls while the requested model, tool set, sampling
 * parameters and system instruction remain unchanged. Only newly appended
 * messages (since the previous call) are forwarded to the native conversation
 * via `sendMessage`; the conversation owns its full chat history natively. This
 * matches LiteRT's intended usage and avoids the native crashes that occur when
 * a fresh conversation is repeatedly seeded with the full prior history via
 * `initialMessages`.
 *
 * The conversation is rebuilt only when the cache key (model, tools, sampler
 * config, system instruction) changes or when the incoming prompt's prefix
 * diverges from what was previously sent (e.g. history truncation / branching).
 *
 * Session access is serialized via a coroutine [Mutex] and all blocking LiteRT
 * native calls are dispatched on [Dispatchers.IO] so callers (including the Android
 * main thread) are never blocked.
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
     * Initialization is performed before the engine is published to session state so
     * a failed `initialize()` leaves the session usable: the new engine is closed
     * (best-effort) and the previous engine (if any) is retained.
     */
    private fun getCurrentEngine(model: LLModel): Engine {
        val modelPath = "${config.modelsPath}/${model.id}"
        val cached = engine
        if (cached != null && engineConfig?.modelPath == modelPath) {
            return cached
        }

        val newEngineConfig = EngineConfig(
            modelPath = modelPath,
            backend = config.backend,
            cacheDir = config.cacheDir,
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

    /**
     * Sends new messages from [prompt] to the model and returns the response.
     *
     * The underlying [Conversation] is created on the first call and reused on
     * subsequent calls. On each call, only messages that were not previously sent
     * are forwarded to the native conversation; the last new message produces the
     * response. If the cache key (model, tools, sampler config, system instruction)
     * changes, or if the incoming prefix diverges from the previously sent history,
     * the conversation is closed and recreated.
     *
     * All native calls run on [Dispatchers.IO]; the whole operation is serialized
     * with [mutex] so concurrent calls cannot race with each other or with [close].
     *
     * @param prompt Prompt containing at least one message.
     * @param model The LLM to run inference with.
     * @param tools Tool descriptors registered with the conversation.
     * @return List of [Message.Response] objects produced by the model.
     * @throws IllegalArgumentException if [prompt] contains no messages.
     */
    suspend fun execute(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
    ): List<Message.Response> {
        require(prompt.messages.isNotEmpty()) { "There should be at least one message" }

        return mutex.withLock {
            withContext(Dispatchers.IO) {
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
                    ?.let { (it as Message.System).content }
                val nonSystemMessages = prompt.messages.filter { it !is Message.System }
                require(nonSystemMessages.isNotEmpty()) {
                    "Prompt must contain at least one non-system message"
                }

                val androidTools = tools.map { AndroidLocalTool(it) }
                val key = ConversationKey(
                    modelPath = engineConfig?.modelPath,
                    toolNames = androidTools.map { it.tool.name },
                    sampler = SamplerKey(
                        topK = samplerConfig.topK,
                        topP = samplerConfig.topP,
                        temperature = samplerConfig.temperature,
                        seed = samplerConfig.seed,
                    ),
                    systemInstruction = systemInstruction,
                )

                // The cached conversation can be reused only when the cache key
                // matches AND the only new message vs. previously sent history is
                // exactly the last one (`sentMessages == nonSystemMessages.dropLast(1)`).
                // In all other cases (different key, divergent history, or more than
                // one new message) we rebuild the conversation and seed prior turns
                // via `initialMessages` — which only adds to history without running
                // inference. We never replay prior messages through `sendMessage()`,
                // because each `sendMessage()` triggers a native generation.
                val priorHistory = nonSystemMessages.dropLast(1)
                val canReuse = conversation != null &&
                    conversationKey == key &&
                    sentMessages == priorHistory

                val activeConversation: Conversation = if (canReuse) {
                    conversation!!
                } else {
                    closeConversationQuietly()
                    val newConfig = ConversationConfig(
                        samplerConfig = samplerConfig,
                        systemInstruction = systemInstruction?.let { Contents.of(it) },
                        tools = androidTools.map { tool(it) },
                        automaticToolCalling = false,
                        initialMessages = priorHistory.map { it.toLitertMessage() },
                    )
                    val created = currentEngine.createConversation(newConfig)
                    conversation = created
                    conversationKey = key
                    sentMessages = priorHistory
                    created
                }

                val lastMessage = nonSystemMessages.last()
                try {
                    val response = activeConversation.sendMessage(lastMessage.toLitertMessage())
                    // Commit on success: the native conversation has now consumed
                    // the last user/tool message AND produced the response, both of
                    // which it keeps in its internal history. Mirror that in
                    // `sentMessages` so the next call's prefix check matches the
                    // prompt that Koog will build (which appends our response).
                    val responseMessages = response.toKoogMessages(config.clock)
                    sentMessages = nonSystemMessages + responseMessages
                    responseMessages
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
     * Closes and releases all LiteRT resources held by this session.
     *
     * Tolerates partially initialized or already-closed objects: each native close()
     * is wrapped in a `runCatching` so cleanup always completes. Safe to call
     * multiple times and from any dispatcher.
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
