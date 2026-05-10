package com.jetbrains.example.koog.compose.local

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import com.google.ai.edge.litertlm.Backend
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Configuration for [LiteRTLLMClient].
 *
 * @property defaultModel The [LLModel] used when no model is explicitly specified.
 *   Defaults to [LiteRTLLModels.FunctionGemma].
 * @property modelsPath Absolute path on the device where `.litertlm` model files are stored.
 * @property cacheDir Absolute path used by the LiteRT engine for intermediate caches.
 * @property backend LiteRT compute backend (e.g. CPU, GPU). Defaults to [Backend.CPU].
 * @property clock Clock instance used for response timestamp metadata.
 */
public data class LiteRTClientConfig(
    val defaultModel: LLModel = LiteRTLLModels.FunctionGemma,
    val modelsPath: String = "/data/local/tmp/llm",
    val cacheDir: String = "/data/local/tmp/llm/cache",
    val backend: Backend = Backend.CPU(),
    @OptIn(ExperimentalTime::class)
    val clock: Clock = Clock.System,
)

/**
 * [LLMClient] implementation that runs inference on-device using the LiteRT runtime.
 *
 * Delegates to an internal [LiteRTLLMSession] which manages the LiteRT [Engine] and
 * [Conversation] lifecycle. Tool support is not yet wired through to LiteRT; tool
 * arguments passed to [execute] are ignored and an empty list is forwarded to the session.
 *
 * @param config Configuration specifying model paths, backend, and runtime options.
 */
public class LiteRTLLMClient(config: LiteRTClientConfig) : LLMClient() {
    private val session = LiteRTLLMSession(config)

    /**
     * Runs inference for [prompt] using [model] and returns the model's response messages.
     *
     * Note: [tools] are currently not forwarded to the underlying LiteRT session.
     */
    override suspend fun execute(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ): List<Message.Response> {
        return session.execute(prompt, model, tools)
    }

    /**
     * Not supported — content moderation is unavailable for on-device models.
     *
     * @throws UnsupportedOperationException always.
     */
    override suspend fun moderate(
        prompt: Prompt,
        model: LLModel
    ): ModerationResult {
        throw UnsupportedOperationException("Moderation is not supported for Android local models")
    }

    /** Returns [LiteRTLLMProvider] as the provider for all models served by this client. */
    override fun llmProvider(): LLMProvider = LiteRTLLMProvider

    /** Closes the underlying [LiteRTLLMSession] and releases all LiteRT resources. */
    @OptIn(ExperimentalAtomicApi::class)
    override fun close() {
        session.close()
    }
}
