package ai.koog.prompt.executor.clients.bedrock.modelfamilies.ai21

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.bedrock.BedrockModels
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.params.LLMParams
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.jsonObject
import kotlin.test.*

class BedrockAI21JambaSerializationTest {

    private val mockClock = object : Clock {
        override fun now(): Instant = Clock.System.now()
    }

    private val model = BedrockModels.AI21JambaMini
    private val systemMessage = "You are a helpful assistant."
    private val userMessage = "Tell me about Paris."
    private val toolName = "get_weather"

    @Test
    fun `createJambaRequest with basic prompt`() {
        val temperature = 0.7

        val prompt = Prompt.build("test", params = LLMParams(temperature = temperature)) {
            system(systemMessage)
            user(userMessage)
        }

        val request = BedrockAI21JambaSerialization.createJambaRequest(prompt, model, emptyList())

        assertNotNull(request)
        assertEquals(model.id, request.model)
        assertEquals(4096, request.maxTokens)
        assertEquals(temperature, request.temperature)

        assertEquals(2, request.messages.size)

        assertEquals("system", request.messages[0].role)
        assertEquals(systemMessage, request.messages[0].content)

        assertEquals("user", request.messages[1].role)
        assertEquals(userMessage, request.messages[1].content)
    }

    @Test
    fun `createJambaRequest with conversation history`() {
        val userNewMessage = "Hello, who are you?"
        val assistantMessage = "I'm an AI assistant. How can I help you today?"

        val prompt = Prompt.build("test") {
            system(systemMessage)
            user(userNewMessage)
            assistant(assistantMessage)
            user(userMessage)
        }

        val request = BedrockAI21JambaSerialization.createJambaRequest(prompt, model, emptyList())

        assertNotNull(request)

        assertEquals(4, request.messages.size)

        assertEquals("system", request.messages[0].role)
        assertEquals(systemMessage, request.messages[0].content)

        assertEquals("user", request.messages[1].role)
        assertEquals(userNewMessage, request.messages[1].content)

        assertEquals("assistant", request.messages[2].role)
        assertEquals(assistantMessage, request.messages[2].content)

        assertEquals("user", request.messages[3].role)
        assertEquals(userMessage, request.messages[3].content)
    }

    @Test
    fun `createJambaRequest with tools`() {
        val description = "Get current weather for a city"

        val tools = listOf(
            ToolDescriptor(
                name = toolName,
                description = description,
                requiredParameters = listOf(
                    ToolParameterDescriptor("city", "The city name", ToolParameterType.String)
                ),
                optionalParameters = listOf(
                    ToolParameterDescriptor("units", "Temperature units", ToolParameterType.String)
                )
            )
        )

        val prompt = Prompt.build("test") {
            user("What's the weather in Paris?")
        }

        val request = BedrockAI21JambaSerialization.createJambaRequest(prompt, model, tools)

        assertNotNull(request)

        assertNotNull(request.tools)
        assertEquals(1, request.tools.size)
        assertEquals(toolName, request.tools[0].function.name)
        assertEquals(description, request.tools[0].function.description)

        val parameters = request.tools[0].function.parameters
        assertNotNull(parameters)
        assertTrue(
            parameters.jsonObject.toString().contains("city"),
            "Required parameter \"city\" not found"
        )
    }

    @Test
    fun `createJambaRequest default temperature`() {
        val prompt = Prompt.build("test") {
            user("Tell me a story.")
        }

        val request = BedrockAI21JambaSerialization.createJambaRequest(prompt, model, emptyList())
        assertEquals(null, request.temperature)
    }

    @Test
    fun `createJambaRequest respects model temperature capability`() {
        val temperature = 0.3

        val promptWithTemperature = Prompt.build("test", params = LLMParams(temperature = temperature)) {
            user("Tell me a story.")
        }

        val request = BedrockAI21JambaSerialization.createJambaRequest(promptWithTemperature, model, emptyList())
        assertEquals(temperature, request.temperature)

        val modelWithoutTemperature = LLModel(
            provider = LLMProvider.Bedrock,
            id = "test-model",
            capabilities = listOf(LLMCapability.Completion) // No temperature capability
        )

        val requestWithoutTemp = BedrockAI21JambaSerialization.createJambaRequest(
            promptWithTemperature,
            modelWithoutTemperature,
            emptyList()
        )
        assertEquals(null, requestWithoutTemp.temperature)
    }

    @Test
    fun `parseJambaResponse with text content`() {
        val responseContent = "Paris is the capital of France"
        val responseJson = """
            {
                "id": "resp_01234567",
                "model": "ai21.jamba-1-5-large-v1:0",
                "choices": [
                    {
                        "index": 0,
                        "message": {
                            "role": "assistant",
                            "content": "$responseContent and one of the most visited cities in the world."
                        },
                        "finish_reason": "stop"
                    }
                ],
                "usage": {
                    "prompt_tokens": 25,
                    "completion_tokens": 20,
                    "total_tokens": 45
                }
            }
        """.trimIndent()

        val messages = BedrockAI21JambaSerialization.parseJambaResponse(responseJson, mockClock)

        assertNotNull(messages)
        assertEquals(1, messages.size)

        val message = messages.first()
        assertTrue(message is Message.Assistant)
        assertContains(message.content, responseContent)
        assertEquals("stop", message.finishReason)

        assertEquals(25, message.metaInfo.inputTokensCount)
        assertEquals(20, message.metaInfo.outputTokensCount)
        assertEquals(45, message.metaInfo.totalTokensCount)
    }

    @Test
    fun `parseJambaResponse with tool call content`() {
        val callId = "call_01234567"
        // language=json
        val responseJson = """
            {
                "id": "resp_01234567",
                "model": "ai21.jamba-1-5-large-v1:0",
                "choices": [
                    {
                        "index": 0,
                        "message": {
                            "role": "assistant",
                            "tool_calls": [
                                {
                                    "id": "$callId",
                                    "type": "function",
                                    "function": {
                                        "name": "$toolName",
                                        "arguments": "{\"city\":\"Paris\",\"units\":\"celsius\"}"
                                    }
                                }
                            ]
                        },
                        "finish_reason": "tool_calls"
                    }
                ],
                "usage": {
                    "prompt_tokens": 25,
                    "completion_tokens": 15,
                    "total_tokens": 40
                }
            }
        """.trimIndent()

        val messages = BedrockAI21JambaSerialization.parseJambaResponse(responseJson, mockClock)

        assertNotNull(messages)
        assertEquals(1, messages.size)

        val message = messages.first()
        assertTrue(message is Message.Tool.Call)
        assertEquals(callId, message.id)
        assertEquals(toolName, message.tool)

        assertEquals(25, message.metaInfo.inputTokensCount)
        assertEquals(15, message.metaInfo.outputTokensCount)
        assertEquals(40, message.metaInfo.totalTokensCount)
    }

    @Test
    fun `parseJambaResponse with both text and tool calls`() {
        val message = "I'll check the weather for you."
        val callId = "call_01234567"

        // language=json
        val responseJson = """
            {
                "id": "resp_01234567",
                "model": "ai21.jamba-1-5-large-v1:0",
                "choices": [
                    {
                        "index": 0,
                        "message": {
                            "role": "assistant",
                            "content": "$message",
                            "tool_calls": [
                                {
                                    "id": "$callId",
                                    "type": "function",
                                    "function": {
                                        "name": "$toolName",
                                        "arguments": "{\"city\":\"Paris\"}"
                                    }
                                }
                            ]
                        },
                        "finish_reason": "tool_calls"
                    }
                ],
                "usage": {
                    "prompt_tokens": 25,
                    "completion_tokens": 30,
                    "total_tokens": 55
                }
            }
        """.trimIndent()

        val messages = BedrockAI21JambaSerialization.parseJambaResponse(responseJson, mockClock)

        assertNotNull(messages)
        assertEquals(2, messages.size)

        val textMessage = messages[0]
        assertTrue(textMessage is Message.Assistant)
        assertEquals(message, textMessage.content)

        val toolMessage = messages[1]
        assertTrue(toolMessage is Message.Tool.Call)
        assertEquals(callId, toolMessage.id)
        assertEquals(toolName, toolMessage.tool)
    }

    @Test
    fun testParseJambaStreamChunk() {
        val chunkJson = """
            {
                "id": "resp_01234567",
                "choices": [
                    {
                        "index": 0,
                        "delta": {
                            "content": "Paris is "
                        }
                    }
                ]
            }
        """.trimIndent()

        val content = BedrockAI21JambaSerialization.parseJambaStreamChunk(chunkJson)
        assertEquals("Paris is ", content)
    }

    @Test
    fun `parseJambaStreamChunk with empty content`() {
        val chunkJson = """
            {
                "id": "resp_01234567",
                "choices": [
                    {
                        "index": 0,
                        "delta": {
                            "content": ""
                        }
                    }
                ]
            }
        """.trimIndent()

        val content = BedrockAI21JambaSerialization.parseJambaStreamChunk(chunkJson)
        assertEquals("", content)
    }

    @Test
    fun `parseJambaStreamChunk with null content`() {
        val chunkJson = """
            {
                "id": "resp_01234567",
                "choices": [
                    {
                        "index": 0,
                        "delta": {
                            "content": null
                        }
                    }
                ]
            }
        """.trimIndent()

        val content = BedrockAI21JambaSerialization.parseJambaStreamChunk(chunkJson)
        assertEquals("", content)
    }
}