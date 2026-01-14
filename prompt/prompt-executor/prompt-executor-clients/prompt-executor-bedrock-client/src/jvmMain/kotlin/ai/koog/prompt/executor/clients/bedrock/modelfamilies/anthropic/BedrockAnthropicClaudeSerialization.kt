package ai.koog.prompt.executor.clients.bedrock.modelfamilies.anthropic

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.anthropic.models.AnthropicContent
import ai.koog.prompt.executor.clients.anthropic.models.AnthropicStreamDeltaContentType
import ai.koog.prompt.executor.clients.anthropic.models.AnthropicStreamEventType
import ai.koog.prompt.executor.clients.anthropic.models.AnthropicStreamResponse
import ai.koog.prompt.executor.clients.anthropic.models.AnthropicUsage
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
import ai.koog.prompt.streaming.buildStreamFrameFlow
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put

internal object BedrockAnthropicClaudeSerialization {

    private val logger = KotlinLogging.logger {}

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        namingStrategy = JsonNamingStrategy.SnakeCase
    }

    private fun buildMessagesHistory(prompt: Prompt): MutableList<BedrockAnthropicInvokeModelMessage> {
        val messages = mutableListOf<BedrockAnthropicInvokeModelMessage>()
        prompt.messages.forEach { msg ->
            when (msg) {
                is Message.User -> {
                    require(!msg.hasAttachments()) {
                        "Amazon Bedrock Anthropic requests currently supports text-only user messages"
                    }
                    if (msg.content.isNotEmpty()) {
                        messages.add(
                            BedrockAnthropicInvokeModelMessage.User(
                                content = listOf(BedrockAnthropicInvokeModelContent.Text(text = msg.content))
                            )
                        )
                    }
                }

                is Message.Assistant -> {
                    if (msg.content.isNotEmpty()) {
                        messages.add(
                            BedrockAnthropicInvokeModelMessage.Assistant(
                                content = listOf(BedrockAnthropicInvokeModelContent.Text(text = msg.content))
                            )
                        )
                    }
                }

                is Message.Reasoning -> {
                    if (msg.content.isNotEmpty()) {
                        messages.add(
                            BedrockAnthropicInvokeModelMessage.Assistant(
                                content = listOf(
                                    BedrockAnthropicInvokeModelContent.Thinking(
                                        signature = msg.encrypted
                                            ?: error("Encrypted signature is required for reasoning messages but was null"),
                                        thinking = msg.content
                                    )
                                )
                            )
                        )
                    }
                }

                is Message.Tool.Result -> {
                    if (msg.content.isNotEmpty()) {
                        messages.add(
                            BedrockAnthropicInvokeModelMessage.User(
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
                            BedrockAnthropicInvokeModelMessage.Assistant(
                                content = listOf(
                                    BedrockAnthropicInvokeModelContent.ToolCall(
                                        msg.id!!,
                                        msg.tool,
                                        msg.contentJsonResult.getOrElse { JsonObject(emptyMap()) }
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

    internal fun parseAnthropicResponse(responseBody: String, clock: Clock = Clock.System): List<Message.Response> {
        val response = json.decodeFromString<BedrockAnthropicResponse>(responseBody)

        val inputTokens = response.usage?.inputTokens
        val outputTokens = response.usage?.outputTokens
        val totalTokens = inputTokens?.let { input -> outputTokens?.let { output -> input + output } }
        val metaInfo = ResponseMetaInfo.create(
            clock,
            totalTokensCount = totalTokens,
            inputTokensCount = inputTokens,
            outputTokensCount = outputTokens
        )

        return response.content.map { content ->
            when (content) {
                is AnthropicContent.Text -> Message.Assistant(
                    content = content.text,
                    finishReason = response.stopReason,
                    metaInfo = metaInfo
                )

                is AnthropicContent.Thinking -> Message.Reasoning(
                    encrypted = content.signature,
                    content = content.thinking,
                    metaInfo = metaInfo
                )

                is AnthropicContent.ToolUse -> Message.Tool.Call(
                    id = content.id,
                    tool = content.name,
                    content = content.input.toString(),
                    metaInfo = metaInfo
                )

                else -> throw IllegalArgumentException("Unhandled AnthropicContent type. Content: $content")
            }
        }
    }

    internal fun transformAnthropicStreamChunks(
        chunkJsonStringFlow: Flow<String>,
        clock: Clock = Clock.System
    ): Flow<StreamFrame> = buildStreamFrameFlow {
        var inputTokens: Int? = null
        var outputTokens: Int? = null

        fun updateUsage(usage: AnthropicUsage) {
            inputTokens = usage.inputTokens ?: inputTokens
            outputTokens = usage.outputTokens ?: outputTokens
        }

        fun getMetaInfo(): ResponseMetaInfo = ResponseMetaInfo.create(
            clock = clock,
            totalTokensCount = inputTokens?.plus(outputTokens ?: 0) ?: outputTokens,
            inputTokensCount = inputTokens,
            outputTokensCount = outputTokens,
        )

        chunkJsonStringFlow.collect { chunkJsonString ->
            val response = json.decodeFromString<AnthropicStreamResponse>(chunkJsonString)

            when (response.type) {
                AnthropicStreamEventType.MESSAGE_START.value -> {
                    response.message?.usage?.let(::updateUsage)
                }

                AnthropicStreamEventType.CONTENT_BLOCK_START.value -> {
                    when (val contentBlock = response.contentBlock) {
                        is AnthropicContent.Text -> {
                            emitAppend(contentBlock.text)
                        }

                        is AnthropicContent.ToolUse -> {
                            upsertToolCall(
                                index = response.index ?: error("Tool index is missing"),
                                id = contentBlock.id,
                                name = contentBlock.name,
                            )
                        }

                        else -> {
                            contentBlock?.let { logger.warn { "Unknown Anthropic stream content block type: ${it::class}" } }
                                ?: logger.warn { "Anthropic stream content block is missing" }
                        }
                    }
                }

                AnthropicStreamEventType.CONTENT_BLOCK_DELTA.value -> {
                    response.delta?.let { delta ->
                        // Handles deltas for tool calls and text

                        when (delta.type) {
                            AnthropicStreamDeltaContentType.INPUT_JSON_DELTA.value -> {
                                upsertToolCall(
                                    index = response.index ?: error("Tool index is missing"),
                                    args = delta.partialJson ?: error("Tool args are missing")
                                )
                            }

                            AnthropicStreamDeltaContentType.TEXT_DELTA.value -> {
                                emitAppend(
                                    delta.text ?: error("Text delta is missing")
                                )
                            }

                            else -> {
                                logger.warn { "Unknown Anthropic stream delta type: ${delta.type}" }
                            }
                        }
                    }
                }

                AnthropicStreamEventType.CONTENT_BLOCK_STOP.value -> {
                    tryEmitPendingToolCall()
                }

                AnthropicStreamEventType.MESSAGE_DELTA.value -> {
                    response.usage?.let(::updateUsage)
                    emitEnd(
                        finishReason = response.delta?.stopReason,
                        metaInfo = getMetaInfo()
                    )
                }

                AnthropicStreamEventType.MESSAGE_STOP.value -> {
                    logger.debug { "Received stop message event from Anthropic" }
                }

                AnthropicStreamEventType.ERROR.value -> {
                    error("Anthropic error: ${response.error}")
                }

                AnthropicStreamEventType.PING.value -> {
                    logger.debug { "Received ping from Anthropic" }
                }
            }
        }
    }
}
