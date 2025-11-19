package ai.koog.prompt.executor.clients.bedrock.modelfamilies.ai21

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
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

internal object BedrockAI21JambaSerialization {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    @OptIn(ExperimentalUuidApi::class)
    internal fun createJambaRequest(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): JambaRequest {
        val messages = mutableListOf<JambaMessage>()

        prompt.messages.forEach { msg ->
            when (msg) {
                is Message.System -> messages.add(
                    JambaMessage(role = "system", content = msg.content)
                )

                is Message.User -> messages.add(
                    JambaMessage(role = "user", content = msg.content)
                )

                is Message.Assistant -> messages.add(
                    JambaMessage(role = "assistant", content = msg.content)
                )

                is Message.Reasoning -> throw NotImplementedError("Reasoning is not supported by Jamba")

                is Message.Tool.Call -> {
                    // Find or create assistant message with tool calls
                    val lastMessage = messages.lastOrNull()
                    if (lastMessage?.role == "assistant" && lastMessage.toolCalls != null) {
                        // Add to existing tool calls
                        val updatedToolCalls = lastMessage.toolCalls + JambaToolCall(
                            id = msg.id ?: Uuid.random().toString(),
                            function = JambaFunctionCall(
                                name = msg.tool,
                                arguments = msg.content
                            )
                        )
                        messages[messages.lastIndex] = lastMessage.copy(toolCalls = updatedToolCalls)
                    } else {
                        // Create new assistant message with tool call
                        messages.add(
                            JambaMessage(
                                role = "assistant",
                                content = null,
                                toolCalls = listOf(
                                    JambaToolCall(
                                        id = msg.id ?: Uuid.random().toString(),
                                        function = JambaFunctionCall(
                                            name = msg.tool,
                                            arguments = msg.content
                                        )
                                    )
                                )
                            )
                        )
                    }
                }

                is Message.Tool.Result -> messages.add(
                    JambaMessage(
                        role = "tool",
                        content = msg.content,
                        toolCallId = msg.id ?: Uuid.random().toString()
                    )
                )
            }
        }

        val jambaTools = if (tools.isNotEmpty()) {
            tools.map { tool ->
                JambaTool(
                    function = JambaFunction(
                        name = tool.name,
                        description = tool.description,
                        parameters = buildJsonObject {
                            put("type", "object")
                            put(
                                "properties",
                                buildJsonObject {
                                    (tool.requiredParameters + tool.optionalParameters).forEach { param ->
                                        put(param.name, BedrockToolSerialization.buildToolParameterSchema(param))
                                    }
                                }
                            )
                            if (tool.requiredParameters.isNotEmpty()) {
                                put(
                                    "required",
                                    buildJsonObject {
                                        tool.requiredParameters.forEachIndexed { index, param ->
                                            put(index.toString(), param.name)
                                        }
                                    }
                                )
                            }
                        }
                    )
                )
            }
        } else {
            null
        }

        return JambaRequest(
            model = model.id,
            messages = messages,
            maxTokens = JambaRequest.MAX_TOKENS_DEFAULT,
            temperature = if (model.capabilities.contains(
                    LLMCapability.Temperature
                )
            ) {
                prompt.params.temperature
            } else {
                null
            },
            tools = jambaTools
        )
    }

    @OptIn(ExperimentalUuidApi::class)
    internal fun parseJambaResponse(responseBody: String, clock: Clock = Clock.System): List<Message.Response> {
        val response = json.decodeFromString<JambaResponse>(responseBody)

        val metaInfo = parseMetaInfo(clock, response.usage)

        return response.choices.flatMap { choice ->
            val messages = mutableListOf<Message.Response>()

            // Handle text content
            choice.message.content?.let { content ->
                messages.add(
                    Message.Assistant(
                        content = content,
                        finishReason = choice.finishReason,
                        metaInfo = metaInfo
                    )
                )
            }

            // Handle tool calls
            choice.message.toolCalls?.forEach { toolCall ->
                messages.add(
                    Message.Tool.Call(
                        id = toolCall.id,
                        tool = toolCall.function.name,
                        content = toolCall.function.arguments,
                        metaInfo = metaInfo
                    )
                )
            }

            messages
        }
    }

    internal fun parseJambaStreamChunk(chunkJsonString: String, clock: Clock = Clock.System): List<StreamFrame> {
        val streamResponse = json.decodeFromString<JambaStreamResponse>(chunkJsonString)
        return buildList {
            val choice = streamResponse.choices.firstOrNull()
            choice?.delta?.let { delta ->
                delta.content?.let(StreamFrame::Append)?.let(::add)
                delta.toolCalls?.map { jambaToolCall ->
                    StreamFrame.ToolCall(
                        id = jambaToolCall.id,
                        name = jambaToolCall.function.name,
                        content = jambaToolCall.function.arguments
                    )
                }?.let(::addAll)
            }
            choice?.finishReason?.let { finishReason ->
                add(
                    StreamFrame.End(
                        finishReason = finishReason,
                        metaInfo = parseMetaInfo(clock, streamResponse.usage)
                    )
                )
            }
        }
    }

    private fun parseMetaInfo(
        clock: Clock,
        usage: JambaUsage?
    ): ResponseMetaInfo = ResponseMetaInfo.create(
        clock = clock,
        totalTokensCount = usage?.totalTokens,
        inputTokensCount = usage?.promptTokens,
        outputTokensCount = usage?.completionTokens
    )
}
