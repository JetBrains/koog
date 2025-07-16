package ai.koog.prompt.executor.clients.bedrock.modelfamilies.amazon

import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json

internal object BedrockAmazonNovaSerialization {

    private val logger = KotlinLogging.logger {}

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    // Amazon Nova specific methods
    internal fun createNovaRequest(prompt: Prompt, model: LLModel): NovaRequest {
        val systemMessages = prompt.messages
            .filterIsInstance<Message.System>()
            .map { NovaSystemMessage(text = it.content) }
            .takeIf { it.isNotEmpty() }

        val conversationMessages = prompt.messages
            .filter { it !is Message.System }.mapNotNull { msg ->
                when (msg) {
                    is Message.User -> NovaMessage(
                        role = "user",
                        content = listOf(NovaContent(text = msg.content))
                    )

                    is Message.Assistant -> NovaMessage(
                        role = "assistant",
                        content = listOf(NovaContent(text = msg.content))
                    )

                    else -> null
                }
            }

        val inferenceConfig = NovaInferenceConfig(
            maxTokens = 4096,
            temperature = if (model.capabilities.contains(LLMCapability.Temperature)) {
                prompt.params.temperature
            } else null
        )

        return NovaRequest(
            messages = conversationMessages,
            inferenceConfig = inferenceConfig,
            system = systemMessages
        )
    }

    internal fun parseNovaResponse(responseBody: String, clock: Clock = Clock.System): List<Message.Response> {
        val response = json.decodeFromString<NovaResponse>(responseBody)
        val messageContent = response.output.message.content.firstOrNull()?.text ?: ""
        val outputTokens = response.usage?.outputTokens

        return listOf(
            Message.Assistant(
                content = messageContent,
                metaInfo = ResponseMetaInfo.create(clock, outputTokensCount = outputTokens)
            )
        )
    }

    internal fun parseNovaStreamChunk(chunkJsonString: String): String {
        val chunk = json.decodeFromString<NovaStreamChunk>(chunkJsonString)
        return chunk.contentBlockDelta?.delta?.text ?: ""
    }
}
