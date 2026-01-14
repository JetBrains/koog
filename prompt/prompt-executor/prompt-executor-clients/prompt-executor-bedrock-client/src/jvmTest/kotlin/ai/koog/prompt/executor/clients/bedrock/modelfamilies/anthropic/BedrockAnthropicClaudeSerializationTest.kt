package ai.koog.prompt.executor.clients.bedrock.modelfamilies.anthropic

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.bedrock.modelfamilies.BedrockAnthropicInvokeModel
import ai.koog.prompt.executor.clients.bedrock.modelfamilies.BedrockAnthropicInvokeModelContent
import ai.koog.prompt.executor.clients.bedrock.modelfamilies.BedrockAnthropicInvokeModelMessage
import ai.koog.prompt.executor.clients.bedrock.modelfamilies.BedrockAnthropicToolChoice
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.params.LLMParams
import ai.koog.prompt.streaming.StreamFrame
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
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
        assertTrue(userMessageActual is BedrockAnthropicInvokeModelMessage.User)
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
        assertTrue(userMessageActual is BedrockAnthropicInvokeModelMessage.User)
        assertEquals("Hello, who are you?", (userMessageActual.content[0] as BedrockAnthropicInvokeModelContent.Text).text)

        assertTrue(assistantMessage is BedrockAnthropicInvokeModelMessage.Assistant)
        assertEquals(
            "I'm Claude, an AI assistant created by Anthropic. How can I help you today?",
            (assistantMessage.content[0] as BedrockAnthropicInvokeModelContent.Text).text
        )

        assertTrue(userMessageActual2 is BedrockAnthropicInvokeModelMessage.User)
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
                "stop_reason": "$stopReason",
                "usage": {
                    "input_tokens": 25,
                    "output_tokens": 20
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
                "stop_reason": "tool_use",
                "usage": {
                    "input_tokens": 25,
                    "output_tokens": 15
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
                "stop_reason": "tool_use",
                "usage": {
                    "input_tokens": 25,
                    "output_tokens": 30
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
    fun `transformAnthropicStreamChunks with simple message`() = runTest {
        val chunkJsonStringFlow = flowOf(
            """
                {
                    "type" : "content_block_start",
                    "index" : 0,
                    "content_block" : {
                        "type" : "text",
                        "text" : "hello"
                   }
                }
            """.trimIndent(),
            """
                {
                    "type" : "content_block_delta",
                    "index" : 0,
                    "delta" : {
                        "type" : "text_delta",
                        "text" : "world"
                    }
                }
            """.trimIndent(),
            """
                {
                    "type" : "content_block_stop",
                    "index" : 0
                }
            """.trimIndent(),
        )

        val content =
            BedrockAnthropicClaudeSerialization.transformAnthropicStreamChunks(chunkJsonStringFlow, mockClock).toList()
        val expected = listOf(
            StreamFrame.Append("hello"),
            StreamFrame.Append("world"),
        )
        assertEquals(expected, content)
    }

    @Test
    fun `transformAnthropicStreamChunks with metainfo`() = runTest {
        val stopReason = "end_turn"
        val chunkJsonStringFlow = flowOf(
            """
                {
                    "type" : "message_start",
                    "message" : {
                        "model" : "claude-3-5-haiku-20241022",
                        "id" : "msg_12345",
                        "type" : "message",
                        "role" : "assistant",
                        "content" : [ ],
                        "stop_reason" : null,
                        "stop_sequence" : null,
                        "usage" : {
                            "input_tokens" : 22,
                            "cache_creation_input_tokens" : 0,
                            "cache_read_input_tokens" : 0,
                            "output_tokens" : 3
                        }
                    }
                }
            """.trimIndent(),
            """
                {
                    "type" : "message_delta",
                    "delta" : {
                        "stop_reason" : "$stopReason",
                        "stop_sequence" : null
                    },
                    "usage" : {
                        "output_tokens" : 13
                    }
                }
            """.trimIndent(),
            """
                {
                    "type" : "message_stop",
                    "amazon-bedrock-invocationMetrics" : {
                        "inputTokenCount" : 22,
                        "outputTokenCount" : 13,
                        "invocationLatency" : 536,
                        "firstByteLatency" : 421
                    }
                }
            """.trimIndent()
        )

        val content =
            BedrockAnthropicClaudeSerialization.transformAnthropicStreamChunks(chunkJsonStringFlow, mockClock).toList()
        val expected = listOf(
            StreamFrame.End(
                finishReason = stopReason,
                metaInfo = ResponseMetaInfo.create(
                    clock = mockClock,
                    totalTokensCount = 35,
                    inputTokensCount = 22,
                    outputTokensCount = 13
                )
            )
        )
        assertEquals(expected, content)
    }

    @Test
    fun `transformAnthropicStreamChunks with single tool call`() = runTest {
        val chunkJsonStringFlow = flowOf(
            """
                {
                    "type": "content_block_start",
                    "index": 0,
                    "content_block": {
                        "type": "tool_use",
                        "id": "$toolId",
                        "name": "$toolName",
                        "input": {}
                    }
                }
            """.trimIndent(),
            """
                {
                    "type": "content_block_delta",
                    "index": 0,
                    "delta": {
                        "type": "input_json_delta",
                        "partial_json": "{\"location\":"
                    }
                }
            """.trimIndent(),
            """
                {
                    "type": "content_block_delta",
                    "index": 0,
                    "delta": {
                        "type": "input_json_delta",
                        "partial_json": "\"Paris\"}"
                    }
                }
            """.trimIndent(),
            """
                {
                    "type": "content_block_stop",
                    "index": 0
                }
            """.trimIndent()
        )

        val content =
            BedrockAnthropicClaudeSerialization.transformAnthropicStreamChunks(chunkJsonStringFlow, mockClock).toList()
        val expected = listOf(
            StreamFrame.ToolCall(
                id = toolId,
                name = toolName,
                content = "{\"location\":\"Paris\"}"
            )
        )
        assertEquals(expected, content)
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
