package ai.koog.prompt.executor.clients.bedrock.modelfamilies.anthropic

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.anthropic.AnthropicContent
import ai.koog.prompt.executor.clients.anthropic.AnthropicMessage
import ai.koog.prompt.executor.clients.anthropic.AnthropicMessageRequest
import ai.koog.prompt.executor.clients.anthropic.AnthropicResponse
import ai.koog.prompt.executor.clients.anthropic.AnthropicResponseContent
import ai.koog.prompt.executor.clients.anthropic.AnthropicStreamResponse
import ai.koog.prompt.executor.clients.anthropic.AnthropicTool
import ai.koog.prompt.executor.clients.anthropic.AnthropicToolChoice
import ai.koog.prompt.executor.clients.anthropic.AnthropicToolSchema
import ai.koog.prompt.executor.clients.anthropic.ImageSource
import ai.koog.prompt.executor.clients.anthropic.SystemAnthropicMessage
import ai.koog.prompt.executor.clients.bedrock.modelfamilies.BedrockToolSerialization
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Attachment
import ai.koog.prompt.message.AttachmentContent
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.params.LLMParams
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

internal object BedrockAnthropicClaudeSerialization {

    private val logger = KotlinLogging.logger {}

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    // Anthropic Claude specific methods
    @OptIn(ExperimentalUuidApi::class)
    internal fun createAnthropicRequest(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ): AnthropicMessageRequest {
        val messages = mutableListOf<AnthropicMessage>()
        val systemMessages = mutableListOf<SystemAnthropicMessage>()

        prompt.messages.forEach { msg ->
            when (msg) {
                is Message.System -> systemMessages.add(SystemAnthropicMessage(text = msg.content))
                is Message.User -> {
                    val contentParts = mutableListOf<AnthropicContent>()

                    if (msg.content.isNotEmpty()) {
                        contentParts.add(AnthropicContent.Text(msg.content))
                    }

                    // Check for image content if present
                    if (msg.attachments.isNotEmpty()) {
                        require(model.capabilities.contains(LLMCapability.Vision.Image)) {
                            "Model ${model.id} does not support image input"
                        }

                        msg.attachments.forEach { attachment ->
                            when (attachment) {
                                is Attachment.Image -> {
                                    val imageSource = when (val content = attachment.content) {
                                        is AttachmentContent.URL -> {
                                            throw IllegalArgumentException(
                                                "URL images not yet supported, please provide base64 encoded images"
                                            )
                                        }

                                        is AttachmentContent.Binary -> {
                                            ImageSource.Base64(
                                                data = content.base64,
                                                mediaType = attachment.mimeType
                                            )
                                        }

                                        else -> throw IllegalArgumentException(
                                            "Unsupported image content type: ${content::class.simpleName}"
                                        )
                                    }
                                    contentParts.add(AnthropicContent.Image(source = imageSource))
                                }
                                else -> throw IllegalArgumentException(
                                    "Unsupported attachment type: ${attachment::class.simpleName}"
                                )
                            }
                        }
                    }

                    messages.add(AnthropicMessage(role = "user", content = contentParts))
                }

                is Message.Assistant -> messages.add(
                    AnthropicMessage(
                        role = "assistant",
                        content = listOf(AnthropicContent.Text(msg.content))
                    )
                )

                is Message.Tool.Call -> {
                    messages.add(
                        AnthropicMessage(
                            role = "assistant",
                            content = listOf(
                                AnthropicContent.ToolUse(
                                    id = msg.id ?: Uuid.Companion.random().toString(),
                                    name = msg.tool,
                                    input = json.parseToJsonElement(msg.content).jsonObject
                                )
                            )
                        )
                    )
                }

                is Message.Tool.Result -> {
                    messages.add(
                        AnthropicMessage(
                            role = "user",
                            content = listOf(
                                AnthropicContent.ToolResult(
                                    toolUseId = msg.id ?: Uuid.Companion.random().toString(),
                                    content = msg.content
                                )
                            )
                        )
                    )
                }
            }
        }

        val anthropicTools = if (tools.isNotEmpty()) {
            tools.map { tool ->
                AnthropicTool(
                    name = tool.name,
                    description = tool.description,
                    inputSchema = AnthropicToolSchema(
                        properties = buildJsonObject {
                            (tool.requiredParameters + tool.optionalParameters).forEach { param ->
                                put(param.name, BedrockToolSerialization.buildToolParameterSchema(param))
                            }
                        },
                        required = tool.requiredParameters.map { it.name }
                    )
                )
            }
        } else {
            null
        }

        val toolChoice = if (tools.isNotEmpty()) {
            when (val choice = prompt.params.toolChoice) {
                LLMParams.ToolChoice.Auto -> AnthropicToolChoice.Auto
                LLMParams.ToolChoice.None -> AnthropicToolChoice.None
                LLMParams.ToolChoice.Required -> AnthropicToolChoice.Any
                is LLMParams.ToolChoice.Named -> AnthropicToolChoice.Tool(choice.name)
                null -> null
            }
        } else {
            null
        }

        return AnthropicMessageRequest(
            model = model.id,
            messages = messages,
            maxTokens = 4096,
            temperature = if (model.capabilities.contains(
                    LLMCapability.Temperature
                )
            ) {
                prompt.params.temperature
            } else {
                null
            },
            system = systemMessages.takeIf { it.isNotEmpty() },
            tools = anthropicTools,
            toolChoice = toolChoice
        )
    }

    @OptIn(ExperimentalUuidApi::class)
    internal fun parseAnthropicResponse(responseBody: String, clock: Clock = Clock.System): List<Message.Response> {
        val response = json.decodeFromString<AnthropicResponse>(responseBody)

        val inputTokens = response.usage?.inputTokens
        val outputTokens = response.usage?.outputTokens
        val totalTokens = inputTokens?.let { input -> outputTokens?.let { output -> input + output } }

        return response.content.map { content ->
            when (content) {
                is AnthropicResponseContent.Text -> Message.Assistant(
                    content = content.text,
                    finishReason = response.stopReason,
                    metaInfo = ResponseMetaInfo.Companion.create(
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
                    metaInfo = ResponseMetaInfo.Companion.create(
                        clock,
                        totalTokensCount = totalTokens,
                        inputTokensCount = inputTokens,
                        outputTokensCount = outputTokens
                    )
                )
            }
        }
    }

    internal fun parseAnthropicStreamChunk(chunkJsonString: String): String {
        val streamResponse = json.decodeFromString<AnthropicStreamResponse>(chunkJsonString)

        return when (streamResponse.type) {
            "content_block_delta" -> {
                streamResponse.delta?.text ?: ""
            }

            "message_delta" -> {
                streamResponse.message?.content?.firstOrNull()?.let { content ->
                    when (content) {
                        is AnthropicResponseContent.Text -> content.text
                        else -> ""
                    }
                } ?: ""
            }

            "message_start" -> {
                val inputTokens = streamResponse.message?.usage?.inputTokens
                logger.debug { "Bedrock stream starts. Input tokens: $inputTokens" }
                ""
            }

            "message_stop" -> {
                val outputTokens = streamResponse.message?.usage?.outputTokens
                logger.debug { "Bedrock stream stops. Output tokens: $outputTokens" }
                ""
            }

            else -> ""
        }
    }
}
