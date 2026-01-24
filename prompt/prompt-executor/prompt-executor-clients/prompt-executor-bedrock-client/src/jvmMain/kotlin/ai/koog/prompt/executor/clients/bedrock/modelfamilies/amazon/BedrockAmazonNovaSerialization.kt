package ai.koog.prompt.executor.clients.bedrock.modelfamilies.amazon

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.bedrock.modelfamilies.BedrockToolSerialization
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.streaming.StreamFrame
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
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
        val systemMessages = prompt.messages
            .filterIsInstance<Message.System>()
            .map { NovaSystemMessage(text = it.content) }
            .takeIf { it.isNotEmpty() }

        val conversationMessages = prompt.messages
            .filter { it !is Message.System }.map { msg ->
                when (msg) {
                    is Message.User -> NovaMessage(
                        role = "user",
                        content = NovaContent(text = msg.content)
                    )

                    is Message.Assistant -> NovaMessage(
                        role = "assistant",
                        content = NovaContent(text = msg.content)
                    )

                    is Message.Tool.Call -> NovaMessage(
                        role = "assistant",
                        content = NovaContent(
                            toolUse = NovaToolUse(
                                toolUseId = msg.id ?: Uuid.random().toString(),
                                name = msg.tool,
                                input = msg.contentJsonResult.getOrElse { JsonObject(emptyMap()) },
                            )
                        )
                    )

                    is Message.Tool.Result -> NovaMessage(
                        role = "user",
                        content = NovaContent(
                            toolResult = NovaToolResult(
                                msg.id ?: Uuid.random().toString(),
                                NovaToolResultContent(msg.content),
                                // right now, `Message.Tool.Result` does not know
                                // if the call was successful or not
                                "success"
                            )
                        )
                    )

                    else -> error("Unknown message type: $msg")
                }
            }

        val inferenceConfig = NovaInferenceConfig(
            maxTokens = prompt.params.maxTokens ?: NovaInferenceConfig.MAX_TOKENS_DEFAULT,
            temperature = if (model.capabilities.contains(LLMCapability.Temperature)) {
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
            system = systemMessages,
            toolConfig = novaToolConfig,
        )
    }

    @OptIn(ExperimentalUuidApi::class)
    internal fun parseNovaResponse(responseBody: String, clock: Clock = Clock.System): List<Message.Response> {
        val response = json.decodeFromString<NovaResponse>(responseBody)
        val metaInfo = parseMetaInfo(clock, response.usage)

        return response.output.message.content.map { content ->
            when {
                content.text != null -> Message.Assistant(
                    content = content.text,
                    finishReason = response.stopReason,
                    metaInfo = metaInfo
                )

                content.toolUse != null -> Message.Tool.Call(
                    id = content.toolUse.toolUseId,
                    tool = content.toolUse.name,
                    content = content.toolUse.input.toString(),
                    metaInfo = metaInfo
                )

                else -> error("Unknown content type: $content")
            }
        }
    }

    internal fun parseNovaStreamChunk(chunkJsonString: String, clock: Clock = Clock.System): List<StreamFrame> {
        val chunk = json.decodeFromString<NovaStreamChunk>(chunkJsonString)
        return buildList {
            chunk.contentBlockDelta?.delta?.text?.let(StreamFrame::Append)?.let(::add)
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
        clock: Clock,
        novaUsage: NovaUsage?
    ): ResponseMetaInfo = ResponseMetaInfo.create(
        clock = clock,
        totalTokensCount = novaUsage?.totalTokens,
        inputTokensCount = novaUsage?.inputTokens,
        outputTokensCount = novaUsage?.outputTokens,
        additionalInfo = mapOf(
            "cacheReadInputTokenCount" to novaUsage?.cacheReadInputTokenCount.toString(),
            "cacheWriteInputTokenCount" to novaUsage?.cacheWriteInputTokenCount.toString()
        ).filterValues { it != "null" }
    )
}
