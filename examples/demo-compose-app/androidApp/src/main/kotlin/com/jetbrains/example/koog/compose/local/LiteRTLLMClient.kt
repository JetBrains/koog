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

public data class LiteRTClientConfig(
    val defaultModel: LLModel = LiteRTLLModels.FunctionGemma,
    val modelsPath: String = "/data/local/tmp/llm",
    val cacheDir: String = "/data/local/tmp/llm/cache",
    val backend: Backend = Backend.CPU(),
    @OptIn(ExperimentalTime::class)
    val clock: Clock = Clock.System,
)

public class LiteRTLLMClient(config: LiteRTClientConfig) : LLMClient() {
    private val session = LiteRTLLMSession(config)

    override suspend fun execute(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ): List<Message.Response> {
        return session.execute(prompt, model, emptyList())
    }

    override suspend fun moderate(
        prompt: Prompt,
        model: LLModel
    ): ModerationResult {
        throw UnsupportedOperationException("Moderation is not supported for Android local models")
    }

    override fun llmProvider(): LLMProvider = LiteRTLLMProvider

    @OptIn(ExperimentalAtomicApi::class)
    override fun close() {
        session.close()
    }
}
