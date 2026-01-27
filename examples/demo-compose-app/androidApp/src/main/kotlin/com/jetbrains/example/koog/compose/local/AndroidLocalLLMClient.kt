package com.jetbrains.example.koog.compose.local

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.ToolParam
import kotlinx.datetime.Clock
import kotlin.time.ExperimentalTime

data class AndroidLocalLLMClientConfig(
    val modelsPath: String = "/data/local/tmp/llm",
    val backend: Backend = Backend.CPU,
    @OptIn(ExperimentalTime::class)
    val clock: Clock = Clock.System
)

class AndroidLocalLLMClient(private val config: AndroidLocalLLMClientConfig) : LLMClient {
    override suspend fun execute(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ): List<Message.Response> {
        val engineConfig = EngineConfig(
            modelPath = "${config.modelsPath}/${model.id}",
            backend = config.backend,
        )

        val engine = Engine(engineConfig)
        engine.initialize()

        val conversationConfig = ConversationConfig(
            samplerConfig = SamplerConfig(topK = 10, topP = 0.95, temperature = 0.8),
            tools = listOf(SampleToolSet())
        )

        val conversation = engine.createConversation(conversationConfig)

        val response = conversation.sendMessage(
            Contents.of(listOf(Content.Text("What is 10.3 + 10.21? CALL TOOL!!!"))),
        )

        conversation.close()
        engine.close()

        return response.contents.contents.mapNotNull { content ->
            when (content) {
                is Content.Text -> Message.Assistant(
                    content = content.text,
                    metaInfo = ResponseMetaInfo.create(config.clock),
                )

                else -> {
                    null
                }
            }
        }
    }

    override suspend fun moderate(
        prompt: Prompt,
        model: LLModel
    ): ModerationResult {
        throw UnsupportedOperationException("Moderation is not supported for Android local models")
    }

    override fun llmProvider(): LLMProvider = AndroidLocalLLMProvider
    override fun close() {

    }

}

class SampleToolSet {

    @Tool
    fun sum(
        @ToolParam(description = "The numbers, could be floating point.") numbers: List<Double>,
    ): Double {
        return numbers.sum()
    }
}
