package ai.koog.prompt.executor.clients.bedrock.modelfamilies.anthropic

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.bedrock.modelfamilies.BedrockAnthropicInvokeModel
import ai.koog.prompt.executor.clients.bedrock.modelfamilies.BedrockAnthropicInvokeModelContent
import ai.koog.prompt.executor.clients.bedrock.modelfamilies.BedrockAnthropicToolChoice
import ai.koog.prompt.executor.clients.bedrock.modelfamilies.anthropic.BedrockAnthropicClaudeSerialization.parseAnthropicStreamChunk
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.params.LLMParams
import ai.koog.prompt.streaming.StreamFrame
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BedrockAnthropicClaudeSerializationTest {

    private val mockClock = object : Clock {
        override fun now(): Instant = Instant.DISTANT_FUTURE
    }

    private val systemMessage = "You are a helpful assistant."
    private val userMessage = "Tell me about Paris."
    private val userMessageQuestion = "What's the weather in Paris?"
    private val userNewMessage = "Hello, who are you?"
    private val assistantMessage = "I'm Claude, an AI assistant created by Anthropic. How can I help you today?"
    private val toolName = "get_weather"
    private val toolDescription = "Get current weather for a city"
    private val toolId = "toolu_01234567"

    @Test
    fun `createAnthropicRequest with basic prompt`() {
        val temperature = 0.7

        val prompt = Prompt.build("test", params = LLMParams(temperature = temperature)) {
            system(systemMessage)
            user(userMessage)
        }

        val request = BedrockAnthropicClaudeSerialization.createAnthropicRequest(prompt, emptyList())

        assertNotNull(request)
        assertEquals(BedrockAnthropicInvokeModel.MAX_TOKENS_DEFAULT, request.maxTokens)
        assertEquals(temperature, request.temperature)

        assertNotNull(request.system)

        val userMessageActual = request.messages[0]
        assertEquals(1, request.messages.size)
        assertEquals("user", userMessageActual.role)
        assertEquals(1, userMessageActual.content.size)
        assertEquals(userMessage, (userMessageActual.content[0] as BedrockAnthropicInvokeModelContent.Text).text)
    }

    @Test
    fun `createAnthropicRequest with conversation history`() {
        val prompt = Prompt.build("test") {
            system(systemMessage)
            user(userNewMessage)
            assistant(assistantMessage)
            user(userMessage)
        }

        val request = BedrockAnthropicClaudeSerialization.createAnthropicRequest(prompt, emptyList())

        assertNotNull(request)

        assertEquals(3, request.messages.size)
        val userMessageActual = request.messages[0]
        val userMessageActual2 = request.messages[2]
        val assistantMessage = request.messages[1]
        assertEquals("user", userMessageActual.role)
        assertEquals("Hello, who are you?", (userMessageActual.content[0] as BedrockAnthropicInvokeModelContent.Text).text)

        assertEquals("assistant", assistantMessage.role)
        assertEquals(
            "I'm Claude, an AI assistant created by Anthropic. How can I help you today?",
            (assistantMessage.content[0] as BedrockAnthropicInvokeModelContent.Text).text
        )

        assertEquals("user", userMessageActual2.role)
        assertEquals("Tell me about Paris.", (userMessageActual2.content[0] as BedrockAnthropicInvokeModelContent.Text).text)
    }

    @Test
    fun `createAnthropicRequest with tools`() {
        val tools = listOf(
            ToolDescriptor(
                name = toolName,
                description = toolDescription,
                requiredParameters = listOf(
                    ToolParameterDescriptor("city", "The city name", ToolParameterType.String)
                ),
                optionalParameters = listOf(
                    ToolParameterDescriptor("units", "Temperature units", ToolParameterType.String)
                )
            )
        )

        val prompt = Prompt.build("test", params = LLMParams(toolChoice = LLMParams.ToolChoice.Auto)) {
            user(userMessageQuestion)
        }

        val request = BedrockAnthropicClaudeSerialization.createAnthropicRequest(prompt, tools)

        assertNotNull(request)

        assertNotNull(request.tools)
        assertEquals(1, request.tools.size)
        assertEquals(toolName, request.tools[0].name)
        assertEquals(toolDescription, request.tools[0].description)

        val schema = request.tools[0].inputSchema
        assertNotNull(schema)
    }

    @Test
    fun `createAnthropicRequest with different tool choices`() {
        val tools = listOf(
            ToolDescriptor(
                name = toolName,
                description = toolDescription,
                requiredParameters = listOf(
                    ToolParameterDescriptor("city", "The city name", ToolParameterType.String)
                )
            )
        )

        val promptAuto = Prompt.build("test", params = LLMParams(toolChoice = LLMParams.ToolChoice.Auto)) {
            user(userMessageQuestion)
        }
        val requestAuto = BedrockAnthropicClaudeSerialization.createAnthropicRequest(promptAuto, tools)
        assertEquals("auto", requestAuto.toolChoice?.type)

        val promptNone = Prompt.build("test", params = LLMParams(toolChoice = LLMParams.ToolChoice.None)) {
            user(userMessageQuestion)
        }
        val requestNone = BedrockAnthropicClaudeSerialization.createAnthropicRequest(promptNone, tools)
        assertEquals("none", requestNone.toolChoice?.type)

        val promptRequired = Prompt.build("test", params = LLMParams(toolChoice = LLMParams.ToolChoice.Required)) {
            user(userMessageQuestion)
        }
        val requestRequired = BedrockAnthropicClaudeSerialization.createAnthropicRequest(promptRequired, tools)
        assertEquals("any", requestRequired.toolChoice?.type)

        val promptNamed = Prompt.build("test", params = LLMParams(toolChoice = LLMParams.ToolChoice.Named(toolName))) {
            user(userMessageQuestion)
        }
        val requestNamed = BedrockAnthropicClaudeSerialization.createAnthropicRequest(promptNamed, tools)
        assertTrue(requestNamed.toolChoice is BedrockAnthropicToolChoice)
        assertEquals(toolName, requestNamed.toolChoice.name)
    }

    @Test
    fun `parseAnthropicResponse with text content`() {
        val stopReason = "end_turn"
        val responseJson = """
            {
                "id": "msg_01234567",
                "type": "message",
                "role": "assistant",
                "content": [
                    {
                        "type": "text",
                        "text": "Paris is the capital of France and one of the most visited cities in the world."
                    }
                ],
                "model": "anthropic.claude-3-sonnet-20240229-v1:0",
                "stopReason": "$stopReason",
                "usage": {
                    "inputTokens": 25,
                    "outputTokens": 20
                }
            }
        """.trimIndent()

        val messages = BedrockAnthropicClaudeSerialization.parseAnthropicResponse(responseJson, mockClock)

        assertNotNull(messages)
        assertEquals(1, messages.size)

        val message = messages.first()
        assertTrue(message is Message.Assistant)
        assertContains(message.content, "Paris is the capital of France")

        assertEquals(stopReason, message.finishReason)

        assertEquals(25, message.metaInfo.inputTokensCount)
        assertEquals(20, message.metaInfo.outputTokensCount)
        assertEquals(45, message.metaInfo.totalTokensCount)
    }

    @Test
    fun `parseAnthropicResponse with tool use content`() {
        val responseJson = """
            {
                "id": "msg_01234567",
                "type": "message",
                "role": "assistant",
                "content": [
                    {
                        "type": "tool_use",
                        "id": "$toolId",
                        "name": "$toolName",
                        "input": {
                            "city": "Paris",
                            "units": "celsius"
                        }
                    }
                ],
                "model": "anthropic.claude-3-sonnet-20240229-v1:0",
                "stopReason": "tool_use",
                "usage": {
                    "inputTokens": 25,
                    "outputTokens": 15
                }
            }
        """.trimIndent()

        val messages = BedrockAnthropicClaudeSerialization.parseAnthropicResponse(responseJson, mockClock)

        assertNotNull(messages)
        assertEquals(1, messages.size)

        val message = messages.first()
        assertTrue(message is Message.Tool.Call)
        assertEquals(toolId, message.id)
        assertEquals(toolName, message.tool)
        assertContains(message.content, "Paris")
        assertContains(message.content, "celsius")

        assertEquals(25, message.metaInfo.inputTokensCount)
        assertEquals(15, message.metaInfo.outputTokensCount)
        assertEquals(40, message.metaInfo.totalTokensCount)
    }

    @Test
    fun `parseAnthropicResponse with multiple content blocks`() {
        val message = "I'll check the weather for you."

        val responseJson = """
            {
                "id": "msg_01234567",
                "type": "message",
                "role": "assistant",
                "content": [
                    {
                        "type": "text",
                        "text": "$message"
                    },
                    {
                        "type": "tool_use",
                        "id": "$toolId",
                        "name": "$toolName",
                        "input": {
                            "city": "Paris"
                        }
                    }
                ],
                "model": "anthropic.claude-3-sonnet-20240229-v1:0",
                "stopReason": "tool_use",
                "usage": {
                    "inputTokens": 25,
                    "outputTokens": 30
                }
            }
        """.trimIndent()

        val messages = BedrockAnthropicClaudeSerialization.parseAnthropicResponse(responseJson, mockClock)

        assertNotNull(messages)
        assertEquals(2, messages.size)

        val textMessage = messages[0]
        assertTrue(textMessage is Message.Assistant)
        assertEquals(message, textMessage.content)

        val toolMessage = messages[1]
        assertTrue(toolMessage is Message.Tool.Call)
        assertEquals(toolId, toolMessage.id)
        assertEquals(toolName, toolMessage.tool)
    }

    @Test
    fun `parseAnthropicStreamChunk with content_block_delta`() {
        val chunkJson = """
            {
                "type": "content_block_delta",
                "index": 0,
                "delta": {
                    "type": "text_delta",
                    "text": "Paris is "
                }
            }
        """.trimIndent()

        val content = parseAnthropicStreamChunk(chunkJson)
        assertEquals(listOf("Paris is ").map(StreamFrame::Append), content)
    }

    @Test
    fun `parseAnthropicStreamChunk with message_delta`() {
        val chunkJson = """
            {
                "type": "message_delta",
                "delta": {
                    "type": "text_delta",
                    "stopReason": "end_turn"
                },
                "message": {
                    "id": "msg_01234567",
                    "type": "message",
                    "role": "assistant",
                    "content": [
                        {
                            "type": "text",
                            "text": "the capital of France."
                        }
                    ],
                    "model": "anthropic.claude-3-sonnet-20240229-v1:0"
                }
            }
        """.trimIndent()

        val content = parseAnthropicStreamChunk(chunkJson)
        assertEquals(listOf("the capital of France.").map(StreamFrame::Append), content)
    }

    @Test
    fun `parseAnthropicStreamChunk with message_start`() {
        val chunkJson = """
            {
                "type": "message_start",
                "message": {
                    "id": "msg_01234567",
                    "type": "message",
                    "role": "assistant",
                    "content": [],
                    "model": "anthropic.claude-3-sonnet-20240229-v1:0",
                    "usage": {
                        "inputTokens": 25,
                        "outputTokens": 0
                    }
                }
            }
        """.trimIndent()

        val content = parseAnthropicStreamChunk(chunkJson)
        assertEquals(emptyList(), content)
    }

    @Test
    fun `parseAnthropicStreamChunk with message_stop`() {
        val chunkJson = """
            {
                "type": "message_stop",
                "message": {
                    "id": "msg_01234567",
                    "type": "message",
                    "role": "assistant",
                    "content": [
                        {
                            "type": "text",
                            "text": "Paris is the capital of France."
                        }
                    ],
                    "model": "anthropic.claude-3-sonnet-20240229-v1:0",
                    "stopReason": "end_turn",
                    "usage": {
                        "inputTokens": 25,
                        "outputTokens": 20
                    }
                }
            }
        """.trimIndent()
        val content = parseAnthropicStreamChunk(chunkJson, mockClock)
        assertEquals(
            expected = listOf(
                StreamFrame.End(
                    finishReason = "end_turn",
                    metaInfo = ResponseMetaInfo.create(
                        clock = mockClock,
                        totalTokensCount = 45,
                        inputTokensCount = 25,
                        outputTokensCount = 20
                    )
                )
            ),
            actual = content
        )
    }

    @Test
    fun `createAnthropicRequest with tools serializes type field correctly`() {
        val tools = listOf(
            ToolDescriptor(
                name = toolName,
                description = toolDescription,
                requiredParameters = listOf(
                    ToolParameterDescriptor("city", "The city name", ToolParameterType.String)
                ),
                optionalParameters = listOf(
                    ToolParameterDescriptor("units", "Temperature units", ToolParameterType.String)
                )
            )
        )
        val prompt = Prompt.build("test", params = LLMParams(toolChoice = LLMParams.ToolChoice.Auto)) {
            user(userMessageQuestion)
        }
        val request = BedrockAnthropicClaudeSerialization.createAnthropicRequest(prompt, tools)
        assertNotNull(request)
        assertNotNull(request.tools)
        assertEquals(1, request.tools.size)
        val tool = request.tools[0]
        assertNotNull(tool)
        assertEquals(toolName, tool.name)
        assertEquals(toolDescription, tool.description)
        val schema = tool.inputSchema
        assertNotNull(schema)

        // Verify that the type field is always "object" and gets serialized
        assertEquals("custom", tool.type)

        val props = schema["properties"] as JsonObject
        assertNotNull(props["city"])
        assertNotNull(props["units"])
    }
}
