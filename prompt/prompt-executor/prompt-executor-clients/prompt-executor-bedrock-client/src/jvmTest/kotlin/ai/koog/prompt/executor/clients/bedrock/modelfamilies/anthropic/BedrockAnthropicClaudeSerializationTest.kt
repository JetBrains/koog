package ai.koog.prompt.executor.clients.bedrock.modelfamilies.anthropic

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.anthropic.AnthropicContent
import ai.koog.prompt.executor.clients.anthropic.AnthropicToolChoice
import ai.koog.prompt.executor.clients.bedrock.BedrockModels
import ai.koog.prompt.message.Message
import ai.koog.prompt.params.LLMParams
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.jsonObject
import kotlin.test.*

class BedrockAnthropicClaudeSerializationTest {

    private val mockClock = object : Clock {
        override fun now(): Instant = Clock.System.now()
    }

    private val model = BedrockModels.AnthropicClaude3Sonnet
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

        val request = BedrockAnthropicClaudeSerialization.createAnthropicRequest(prompt, model, emptyList())

        assertNotNull(request)
        assertEquals(model.id, request.model)
        assertEquals(4096, request.maxTokens)
        assertEquals(temperature, request.temperature)

        assertNotNull(request.system)
        assertEquals(1, request.system?.size)
        assertEquals(systemMessage, request.system?.first()?.text)

        assertEquals(1, request.messages.size)
        assertEquals("user", request.messages[0].role)
        assertEquals(1, request.messages[0].content.size)
        assertTrue(request.messages[0].content[0] is AnthropicContent.Text)
        assertEquals(userMessage, (request.messages[0].content[0] as AnthropicContent.Text).text)
    }

    @Test
    fun `createAnthropicRequest with conversation history`() {
        val prompt = Prompt.build("test") {
            system(systemMessage)
            user(userNewMessage)
            assistant(assistantMessage)
            user(userMessage)
        }

        val request = BedrockAnthropicClaudeSerialization.createAnthropicRequest(prompt, model, emptyList())

        assertNotNull(request)

        assertEquals(3, request.messages.size)

        assertEquals("user", request.messages[0].role)
        assertEquals("Hello, who are you?", (request.messages[0].content[0] as AnthropicContent.Text).text)

        assertEquals("assistant", request.messages[1].role)
        assertEquals(
            "I'm Claude, an AI assistant created by Anthropic. How can I help you today?",
            (request.messages[1].content[0] as AnthropicContent.Text).text
        )

        assertEquals("user", request.messages[2].role)
        assertEquals("Tell me about Paris.", (request.messages[2].content[0] as AnthropicContent.Text).text)
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

        val request = BedrockAnthropicClaudeSerialization.createAnthropicRequest(prompt, model, tools)

        assertNotNull(request)

        assertNotNull(request.tools)
        assertEquals(1, request.tools?.size)
        assertEquals(toolName, request.tools?.get(0)?.name)
        assertEquals(toolDescription, request.tools?.get(0)?.description)

        val schema = request.tools?.get(0)?.inputSchema
        assertNotNull(schema)

        assertEquals(listOf("city"), schema.required)

        val properties = schema.properties.jsonObject
        assertNotNull(properties["city"])
        assertNotNull(properties["units"])

        assertEquals(AnthropicToolChoice.Auto, request.toolChoice)
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
        val requestAuto = BedrockAnthropicClaudeSerialization.createAnthropicRequest(promptAuto, model, tools)
        assertEquals(AnthropicToolChoice.Auto, requestAuto.toolChoice)

        val promptNone = Prompt.build("test", params = LLMParams(toolChoice = LLMParams.ToolChoice.None)) {
            user(userMessageQuestion)
        }
        val requestNone = BedrockAnthropicClaudeSerialization.createAnthropicRequest(promptNone, model, tools)
        assertEquals(AnthropicToolChoice.None, requestNone.toolChoice)

        val promptRequired = Prompt.build("test", params = LLMParams(toolChoice = LLMParams.ToolChoice.Required)) {
            user(userMessageQuestion)
        }
        val requestRequired = BedrockAnthropicClaudeSerialization.createAnthropicRequest(promptRequired, model, tools)
        assertEquals(AnthropicToolChoice.Any, requestRequired.toolChoice)

        val promptNamed = Prompt.build("test", params = LLMParams(toolChoice = LLMParams.ToolChoice.Named(toolName))) {
            user(userMessageQuestion)
        }
        val requestNamed = BedrockAnthropicClaudeSerialization.createAnthropicRequest(promptNamed, model, tools)
        assertTrue(requestNamed.toolChoice is AnthropicToolChoice.Tool)
        assertEquals(toolName, (requestNamed.toolChoice as AnthropicToolChoice.Tool).name)
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

        val assistant = message
        assertEquals(stopReason, assistant.finishReason)

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

        val content = BedrockAnthropicClaudeSerialization.parseAnthropicStreamChunk(chunkJson)
        assertEquals("Paris is ", content)
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

        val content = BedrockAnthropicClaudeSerialization.parseAnthropicStreamChunk(chunkJson)
        assertEquals("the capital of France.", content)
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

        val content = BedrockAnthropicClaudeSerialization.parseAnthropicStreamChunk(chunkJson)
        assertEquals("", content)
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

        val content = BedrockAnthropicClaudeSerialization.parseAnthropicStreamChunk(chunkJson)
        assertEquals("", content)
    }
}