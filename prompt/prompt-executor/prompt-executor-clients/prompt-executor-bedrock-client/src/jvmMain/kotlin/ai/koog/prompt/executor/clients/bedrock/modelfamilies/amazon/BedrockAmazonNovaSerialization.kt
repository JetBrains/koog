package ai.koog.prompt.executor.clients.bedrock.modelfamilies.amazon

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.Prompt
import ai.koog.prompt.executor.clients.bedrock.modelfamilies.BedrockToolSerialization
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.utils.time.KoogClock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private fun ToolDescriptor.asNovaToolSpec() = NovaToolSpec(
    toolSpec = NovaToolSpecDetails(
        name = name,
        description = description,
        inputSchema = NovaInputSchema(
            json = NovaJsonSchema(
                properties = buildJsonObject {
                    (requiredParameters + optionalParameters).forEach { param ->
                        put(param.name, BedrockToolSerialization.buildToolParameterSchema(param))
                    }
                },
                required = requiredParameters.map { it.name }
            )
        )
    )
)

internal object BedrockAmazonNovaSerialization {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    // Amazon Nova specific methods
    @OptIn(ExperimentalUuidApi::class)
    internal fun createNovaRequest(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): NovaRequest {
        val systemMessages = mutableListOf<NovaSystemMessage>()

        val conversationMessages = buildList {
            prompt.messages.forEach { message ->
                when (message) {
                    is Message.System -> {
                        message.parts.forEach { part ->
                            systemMessages.add(
                                NovaSystemMessage(
                                    text = part.text
                                )
                            )
                        }
                    }

                    is Message.User -> add(message.toNovaMessage())

                    is Message.Assistant -> add(message.toNovaMessage())
                }
            }
        }

        val inferenceConfig = NovaInferenceConfig(
            maxTokens = prompt.params.maxTokens ?: NovaInferenceConfig.MAX_TOKENS_DEFAULT,
            temperature = if (model.supports(LLMCapability.Temperature)) {
                prompt.params.temperature
            } else {
                null
            }
        )

        val novaToolConfig = if (tools.isNotEmpty()) {
            NovaToolConfig(
                tools = tools.map { tool -> tool.asNovaToolSpec() }
            )
        } else {
            null
        }

        return NovaRequest(
            messages = conversationMessages,
            inferenceConfig = inferenceConfig,
            system = systemMessages.takeIf { it.isNotEmpty() },
            toolConfig = novaToolConfig,
        )
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun Message.User.toNovaMessage(): NovaMessage {
        return NovaMessage(
            role = "user",
            content = buildList {
                parts.forEach { part ->
                    when (part) {
                        is MessagePart.Text -> add(NovaContent(text = part.text))
                        is MessagePart.Attachment -> throw IllegalArgumentException("No attachments are supported in user messages")
                        is MessagePart.Tool.Result -> add(
                            NovaContent(
                                toolResult = NovaToolResult(
                                    toolUseId = part.id ?: Uuid.random().toString(),
                                    content = NovaToolResultContent(part.output),
                                    status = if (part.isError) "error" else "success"
                                )
                            )
                        )
                    }
                }
            }
        )
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun Message.Assistant.toNovaMessage(): NovaMessage {
        return NovaMessage(
            role = "assistant",
            content = buildList {
                parts.forEach { part ->
                    when (part) {
                        is MessagePart.Text -> add(NovaContent(text = part.text))
                        is MessagePart.Attachment -> throw IllegalArgumentException("No attachments are supported in user messages")
                        is MessagePart.Reasoning -> throw IllegalArgumentException("No reasoning messages are supported in assistant messages")
                        is MessagePart.Tool.Call -> add(
                            NovaContent(
                                toolUse = NovaToolUse(
                                    toolUseId = part.id ?: Uuid.random().toString(),
                                    name = part.tool,
                                    input = part.argsJson
                                )
                            )
                        )
                    }
                }
            }
        )
    }

    @OptIn(ExperimentalUuidApi::class)
    internal fun parseNovaResponse(responseBody: String, clock: KoogClock = KoogClock.System): Message.Assistant {
        val response = json.decodeFromString<NovaResponse>(responseBody)
        val metaInfo = parseMetaInfo(clock, response.usage)

        return Message.Assistant(
            parts = buildList {
                response.output.message.content.forEach { content ->
                    when {
                        content.text != null -> add(MessagePart.Text(content.text))
                        content.toolUse != null -> add(
                            MessagePart.Tool.Call(
                                content.toolUse.toolUseId,
                                content.toolUse.name,
                                content.toolUse.input
                            )
                        )

                        content.toolResult != null -> error("Unknown content type: $content")
                    }
                }
            },
            metaInfo = metaInfo,
            finishReason = response.stopReason
        )
    }

    internal fun parseNovaStreamChunk(chunkJsonString: String, clock: KoogClock = KoogClock.System): List<StreamFrame> {
        val chunk = json.decodeFromString<NovaStreamChunk>(chunkJsonString)
        return buildList {
            chunk.contentBlockDelta?.delta?.text?.let(StreamFrame::TextDelta)?.let(::add)
            chunk.messageStop?.let { stop ->
                add(
                    StreamFrame.End(
                        finishReason = stop.stopReason,
                        metaInfo = parseMetaInfo(clock, chunk.metadata?.usage)
                    )
                )
            }
        }
    }

    private fun parseMetaInfo(
        clock: KoogClock,
        novaUsage: NovaUsage?
    ): ResponseMetaInfo = ResponseMetaInfo.create(
        clock = clock,
        totalTokensCount = novaUsage?.totalTokens,
        inputTokensCount = novaUsage?.inputTokens,
        outputTokensCount = novaUsage?.outputTokens,
        metadata = buildJsonObject {
            put("cacheReadInputTokenCount", JsonPrimitive(novaUsage?.cacheReadInputTokenCount))
            put("cacheWriteInputTokenCount", JsonPrimitive(novaUsage?.cacheWriteInputTokenCount))
        }
    )
}
