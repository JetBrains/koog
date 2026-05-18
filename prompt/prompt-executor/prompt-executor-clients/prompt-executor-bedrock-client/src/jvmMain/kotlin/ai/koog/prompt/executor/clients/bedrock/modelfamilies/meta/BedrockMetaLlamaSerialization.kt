package ai.koog.prompt.executor.clients.bedrock.modelfamilies.meta

import ai.koog.prompt.Prompt
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.utils.time.KoogClock
import io.github.oshai.kotlinlogging.KotlinLogging
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
        val messagesText = buildList {
            prompt.messages.forEach { msg ->
                val messageType = when (msg) {
                    is Message.System -> "system"
                    is Message.User -> "user"
                    is Message.Assistant -> "assistant"
                }
                msg.parts.filterIsInstance<MessagePart.Text>().forEach { part ->
                    add("<|start_header_id|>$messageType<|end_header_id|>\n\n${part.text}<|eot_id|>")
                }
            }
        }.joinToString("\n")

        val promptText = "<|begin_of_text|>$messagesText<|start_header_id|>assistant<|end_header_id|>\n\n"

        return LlamaRequest(
            prompt = promptText,
            maxGenLen = 2048,
            temperature = if (model.supports(LLMCapability.Temperature)) {
                prompt.params.temperature
            } else {
                null
            }
        )
    }

    internal fun parseLlamaResponse(responseBody: String, clock: KoogClock = KoogClock.System): Message.Assistant {
        val response = json.decodeFromString<LlamaResponse>(responseBody)

        return Message.Assistant(
            content = response.generation,
            finishReason = response.stopReason,
            metaInfo = ResponseMetaInfo.Companion.create(
                clock = clock,
                inputTokensCount = response.promptTokenCount,
                outputTokensCount = response.generationTokenCount,
                totalTokensCount = response.promptTokenCount?.let { input ->
                    response.generationTokenCount?.let { output -> input + output }
                }
            )
        )
    }

    internal fun parseLlamaStreamChunk(chunkJsonString: String, clock: KoogClock = KoogClock.System): List<StreamFrame> {
        val chunk = json.decodeFromString<LlamaStreamChunk>(chunkJsonString)
        return buildList {
            chunk.generation?.let(StreamFrame::TextDelta)?.let(::add)
            if (chunk.stopReason != null) {
                add(
                    StreamFrame.End(
                        finishReason = chunk.stopReason,
                        metaInfo = ResponseMetaInfo.create(
                            clock = clock,
                            inputTokensCount = chunk.promptTokenCount,
                            outputTokensCount = chunk.generationTokenCount,
                            totalTokensCount = chunk.promptTokenCount?.let { input ->
                                chunk.generationTokenCount?.let { output -> input + output }
                            }
                        )
                    )
                )
            }
        }
    }
}
