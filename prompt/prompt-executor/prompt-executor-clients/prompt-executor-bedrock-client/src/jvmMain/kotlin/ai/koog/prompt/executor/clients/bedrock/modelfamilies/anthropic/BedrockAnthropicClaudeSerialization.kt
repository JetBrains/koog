package ai.koog.prompt.executor.clients.bedrock.modelfamilies.anthropic

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.anthropic.AnthropicResponseContent
import ai.koog.prompt.executor.clients.anthropic.AnthropicStreamResponse
import ai.koog.prompt.executor.clients.bedrock.modelfamilies.BedrockAnthropicInvokeModel
import ai.koog.prompt.executor.clients.bedrock.modelfamilies.BedrockAnthropicInvokeModelContent
import ai.koog.prompt.executor.clients.bedrock.modelfamilies.BedrockAnthropicInvokeModelMessage
import ai.koog.prompt.executor.clients.bedrock.modelfamilies.BedrockAnthropicInvokeModelTool
import ai.koog.prompt.executor.clients.bedrock.modelfamilies.BedrockAnthropicResponse
import ai.koog.prompt.executor.clients.bedrock.modelfamilies.BedrockAnthropicToolChoice
import ai.koog.prompt.executor.clients.bedrock.modelfamilies.BedrockToolSerialization
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.params.LLMParams
import ai.koog.prompt.streaming.StreamFrame
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import kotlin.uuid.ExperimentalUuidApi

internal object BedrockAnthropicClaudeSerialization {

    private val logger = KotlinLogging.logger {}

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    private fun buildMessagesHistory(prompt: Prompt): MutableList<BedrockAnthropicInvokeModelMessage> {
        val messages = mutableListOf<BedrockAnthropicInvokeModelMessage>()
        prompt.messages.forEach { msg ->
            when (msg) {
                is Message.User -> {
                    require(msg.attachments.isEmpty()) {
                        "Amazon Bedrock Anthropic requests currently supports text-only user messages"
                    }
                    if (msg.content.isNotEmpty()) {
                        messages.add(
                            BedrockAnthropicInvokeModelMessage(
                                role = "user",
                                content = listOf(BedrockAnthropicInvokeModelContent.Text(text = msg.content))
                            )
                        )
                    }
                }

                is Message.Assistant -> {
                    if (msg.content.isNotEmpty()) {
                        messages.add(
                            BedrockAnthropicInvokeModelMessage(
                                role = "assistant",
                                content = listOf(BedrockAnthropicInvokeModelContent.Text(text = msg.content))
                            )
                        )
                    }
                }

                is Message.Tool.Result -> {
                    if (msg.content.isNotEmpty()) {
                        messages.add(
                            BedrockAnthropicInvokeModelMessage(
                                role = "user",
                                content = listOf(
                                    BedrockAnthropicInvokeModelContent.ToolResult(
                                        toolUseId = msg.id!!,
                                        content = msg.content
                                    )
                                )
                            )
                        )
                    }
                }

                is Message.Tool.Call -> {
                    if (msg.content.isNotEmpty()) {
                        messages.add(
                            BedrockAnthropicInvokeModelMessage(
                                role = "assistant",
                                content = listOf(
                                    BedrockAnthropicInvokeModelContent.ToolCall(
                                        msg.id!!,
                                        msg.tool,
                                        json.decodeFromString(msg.content)
                                    )
                                )
                            )
                        )
                    }
                }

                is Message.System -> {} // skip
            }
        }

        return messages
    }

    internal fun createAnthropicRequest(
        prompt: Prompt,
        tools: List<ToolDescriptor>
    ): BedrockAnthropicInvokeModel {
        val systemText = prompt.messages.filterIsInstance<Message.System>().joinToString("\n") { it.content }
        val messages = buildMessagesHistory(prompt)

        val params: LLMParams = prompt.params
        val temperature = params.temperature
        val maxTokens = params.maxTokens ?: 4000

        val bedrockTools = if (tools.isNotEmpty()) {
            tools.map { tool ->
                BedrockAnthropicInvokeModelTool(
                    name = tool.name,
                    description = tool.description,
                    inputSchema = buildJsonObject {
                        put("type", "object")
                        put(
                            "properties",
                            buildJsonObject {
                                (tool.requiredParameters + tool.optionalParameters).forEach { param ->
                                    put(param.name, BedrockToolSerialization.buildToolParameterSchema(param))
                                }
                            }
                        )
                        put(
                            "required",
                            buildJsonArray {
                                tool.requiredParameters.forEach { param ->
                                    add(json.encodeToJsonElement(param.name))
                                }
                            }
                        )
                    }
                )
            }
        } else {
            null
        }

        val bedrockToolChoice = if (tools.isNotEmpty()) {
            when (val choice = params.toolChoice) {
                LLMParams.ToolChoice.Auto -> BedrockAnthropicToolChoice(type = "auto")
                LLMParams.ToolChoice.None -> BedrockAnthropicToolChoice(type = "none")
                LLMParams.ToolChoice.Required -> BedrockAnthropicToolChoice(type = "any")
                is LLMParams.ToolChoice.Named -> BedrockAnthropicToolChoice(type = "tool", name = choice.name)
                null -> null
            }
        } else {
            null
        }

        return BedrockAnthropicInvokeModel(
            anthropicVersion = "bedrock-2023-05-31",
            maxTokens = maxTokens,
            system = systemText,
            temperature = temperature,
            messages = messages,
            tools = bedrockTools,
            toolChoice = bedrockToolChoice
        )
    }

    @OptIn(ExperimentalUuidApi::class)
    internal fun parseAnthropicResponse(responseBody: String, clock: Clock = Clock.System): List<Message.Response> {
        val response = json.decodeFromString<BedrockAnthropicResponse>(responseBody)

        val inputTokens = response.usage?.inputTokens
        val outputTokens = response.usage?.outputTokens
        val totalTokens = inputTokens?.let { input -> outputTokens?.let { output -> input + output } }

        return response.content.map { content ->
            when (content) {
                is AnthropicResponseContent.Text -> Message.Assistant(
                    content = content.text,
                    finishReason = response.stopReason,
                    metaInfo = ResponseMetaInfo.create(
                        clock,
                        totalTokensCount = totalTokens,
                        inputTokensCount = inputTokens,
                        outputTokensCount = outputTokens
                    )
                )

                is AnthropicResponseContent.ToolUse -> Message.Tool.Call(
                    id = content.id,
                    tool = content.name,
                    content = content.input.toString(),
                    metaInfo = ResponseMetaInfo.create(
                        clock,
                        totalTokensCount = totalTokens,
                        inputTokensCount = inputTokens,
                        outputTokensCount = outputTokens
                    )
                )
            }
        }
    }

    internal fun parseAnthropicStreamChunk(chunkJsonString: String, clock: Clock = Clock.System): List<StreamFrame> {
        val streamResponse = json.decodeFromString<AnthropicStreamResponse>(chunkJsonString)

        return when (streamResponse.type) {
            "content_block_delta" -> {
                streamResponse.delta?.let {
                    buildList {
                        it.text?.let(StreamFrame::Append)?.let(::add)
                        it.toolUse?.let { toolUse ->
                            StreamFrame.ToolCall(
                                id = toolUse.id,
                                name = toolUse.name,
                                content = toolUse.input.toString()
                            )
                        }?.let(::add)
                    }
                } ?: emptyList()
            }

            "message_delta" -> {
                streamResponse.message?.content?.map { content ->
                    when (content) {
                        is AnthropicResponseContent.Text ->
                            StreamFrame.Append(content.text)

                        is AnthropicResponseContent.ToolUse ->
                            StreamFrame.ToolCall(
                                id = content.id,
                                name = content.name,
                                content = content.input.toString()
                            )
                    }
                } ?: emptyList()
            }

            "message_start" -> {
                val inputTokens = streamResponse.message?.usage?.inputTokens
                logger.debug { "Bedrock stream starts. Input tokens: $inputTokens" }
                emptyList()
            }

            "message_stop" -> {
                val inputTokens = streamResponse.message?.usage?.inputTokens
                val outputTokens = streamResponse.message?.usage?.outputTokens
                logger.debug { "Bedrock stream stops. Output tokens: $outputTokens" }
                listOf(
                    StreamFrame.End(
                        finishReason = streamResponse.message?.stopReason,
                        metaInfo = ResponseMetaInfo.create(
                            clock = clock,
                            totalTokensCount = inputTokens?.let { it + (outputTokens ?: 0) } ?: outputTokens,
                            inputTokensCount = inputTokens,
                            outputTokensCount = outputTokens
                        )
                    )
                )
            }

            else -> emptyList()
        }
    }
}
