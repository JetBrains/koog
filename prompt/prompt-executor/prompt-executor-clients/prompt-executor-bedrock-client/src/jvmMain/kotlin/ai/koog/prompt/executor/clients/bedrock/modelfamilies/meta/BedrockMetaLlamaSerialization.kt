package ai.koog.prompt.executor.clients.bedrock.modelfamilies.meta

import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json

internal object BedrockMetaLlamaSerialization {

    private val logger = KotlinLogging.logger {}

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    // Meta Llama specific methods
    internal fun createLlamaRequest(prompt: Prompt, model: LLModel): LlamaRequest {
        val promptText = prompt.messages.joinToString("\n") { msg ->
            when (msg) {
                is Message.System -> "<|begin_of_text|><|start_header_id|>system<|end_header_id|>\n\n${msg.content}<|eot_id|>"
                is Message.User -> "<|start_header_id|>user<|end_header_id|>\n\n${msg.content}<|eot_id|>"
                is Message.Assistant -> "<|start_header_id|>assistant<|end_header_id|>\n\n${msg.content}<|eot_id|>"
                else -> ""
            }
        } + "<|start_header_id|>assistant<|end_header_id|>\n\n"

        return LlamaRequest(
            prompt = promptText,
            maxGenLen = 2048,
            temperature = if (model.capabilities.contains(LLMCapability.Temperature)) {
                prompt.params.temperature
            } else null
        )
    }

    internal fun parseLlamaResponse(responseBody: String, clock: Clock = Clock.System): List<Message.Response> {
        val response = json.decodeFromString<LlamaResponse>(responseBody)

        return listOf(
            Message.Assistant(
                content = response.generation,
                finishReason = response.stopReason,
                metaInfo = ResponseMetaInfo.Companion.create(
                    clock,
                    inputTokensCount = response.promptTokenCount,
                    outputTokensCount = response.generationTokenCount,
                    totalTokensCount = response.promptTokenCount?.let { input ->
                        response.generationTokenCount?.let { output -> input + output }
                    }
                )
            )
        )
    }

    internal fun parseLlamaStreamChunk(chunkJsonString: String): String {
        val chunk = json.decodeFromString<LlamaStreamChunk>(chunkJsonString)
        return chunk.generation ?: ""
    }
}
