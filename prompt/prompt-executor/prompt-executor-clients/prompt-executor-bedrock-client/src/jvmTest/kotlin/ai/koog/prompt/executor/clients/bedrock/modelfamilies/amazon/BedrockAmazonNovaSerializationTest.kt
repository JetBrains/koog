package ai.koog.prompt.executor.clients.bedrock.modelfamilies.amazon

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.bedrock.BedrockModels
import ai.koog.prompt.executor.clients.bedrock.modelfamilies.amazon.NovaInferenceConfig.Companion.MAX_TOKENS_DEFAULT
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.params.LLMParams
import ai.koog.prompt.streaming.StreamFrame
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BedrockAmazonNovaSerializationTest {

    private val mockClock = object : Clock {
        override fun now(): Instant = Instant.DISTANT_FUTURE
    }

    private val model = BedrockModels.AmazonNovaPro
    private val systemMessage = "You are a helpful assistant."
    private val userMessage = "Tell me about Paris."
    private val userNewMessage = "Hello, who are you?"
    private val assistantMessage = "I'm an AI assistant. How can I help you today?"

    @Test
    fun `createNovaRequest with system and user messages`() {
        val temperature = 0.7

        val prompt = Prompt.build("test", params = LLMParams(temperature = temperature)) {
            system(systemMessage)
            user(userMessage)
        }

        val request = BedrockAmazonNovaSerialization.createNovaRequest(prompt, model, emptyList())

        assertNotNull(request)

        assertNotNull(request.system)
        assertEquals(1, request.system.size)
        assertEquals(systemMessage, request.system[0].text)

        assertEquals(1, request.messages.size)
        assertEquals("user", request.messages[0].role)
        assertEquals(1, request.messages[0].content.size)
        assertEquals(userMessage, request.messages[0].content[0].text)

        assertNotNull(request.inferenceConfig)
        assertEquals(MAX_TOKENS_DEFAULT, request.inferenceConfig.maxTokens)
        assertEquals(temperature, request.inferenceConfig.temperature)
    }

    @Test
    fun `createNovaRequest with default maxTokens`() {
        val maxTokens = 1000

        val prompt = Prompt.build("test", params = LLMParams(maxTokens = maxTokens)) {
            system(systemMessage)
            user(userMessage)
        }

        val request = BedrockAmazonNovaSerialization.createNovaRequest(prompt, model, emptyList())
        assertEquals(maxTokens, request.inferenceConfig!!.maxTokens)
    }

    @Test
    fun `createNovaRequest with conversation history`() {
        val prompt = Prompt.build("test") {
            system(systemMessage)
            user(userNewMessage)
            assistant(assistantMessage)
            user(userMessage)
        }

        val request = BedrockAmazonNovaSerialization.createNovaRequest(prompt, model, emptyList())

        assertNotNull(request)

        assertNotNull(request.system)
        assertEquals(1, request.system.size)
        assertEquals(systemMessage, request.system[0].text)

        assertEquals(3, request.messages.size)

        assertEquals("user", request.messages[0].role)
        assertEquals(userNewMessage, request.messages[0].content[0].text)

        assertEquals("assistant", request.messages[1].role)
        assertEquals(assistantMessage, request.messages[1].content[0].text)

        assertEquals("user", request.messages[2].role)
        assertEquals(userMessage, request.messages[2].content[0].text)
    }

    @Test
    fun `createNovaRequest respects model temperature capability`() {
        val temperature = 0.3

        val promptWithTemperature = Prompt.build("test", params = LLMParams(temperature = temperature)) {
            user("Tell me a story.")
        }

        val request = BedrockAmazonNovaSerialization.createNovaRequest(promptWithTemperature, model, emptyList())
        assertEquals(temperature, request.inferenceConfig?.temperature)

        val modelWithoutTemperature = LLModel(
            provider = LLMProvider.Bedrock,
            id = "test-model",
            capabilities = listOf(LLMCapability.Completion), // No temperature capability
            contextLength = 1_000L,
        )

        val requestWithoutTemp = BedrockAmazonNovaSerialization.createNovaRequest(
            promptWithTemperature,
            modelWithoutTemperature,
            emptyList()
        )
        assertEquals(null, requestWithoutTemp.inferenceConfig?.temperature)
    }

    @Test
    fun testParseNovaResponse() {
        val responseContent = "Paris is the capital of France and one of the most visited cities in the world."
        val responseJson = """
            {
                "output": {
                    "message": {
                        "role": "assistant",
                        "content": [
                            {
                                "text": "$responseContent"
                            }
                        ]
                    }
                },
                "usage": {
                    "inputTokens": 25,
                    "outputTokens": 20,
                    "totalTokens": 45
                },
                "stopReason": "stop"
            }
        """.trimIndent()

        val messages = BedrockAmazonNovaSerialization.parseNovaResponse(responseJson, mockClock)

        assertNotNull(messages)
        assertEquals(1, messages.size)

        val message = messages.first()
        assertTrue(message is Message.Assistant)
        assertContains(message.content, responseContent)

        // Check token counts - Note: Nova only provides outputTokens in the metaInfo
        assertEquals(25, message.metaInfo.inputTokensCount)
        assertEquals(20, message.metaInfo.outputTokensCount)
        assertEquals(45, message.metaInfo.totalTokensCount)
    }

    @Test
    fun `parseNovaResponse with missing usage`() {
        val responseContent = "Paris is the capital of France."
        val responseJson = """
            {
                "output": {
                    "message": {
                        "role": "assistant",
                        "content": [
                            {
                                "text": "$responseContent"
                            }
                        ]
                    }
                },
                "stopReason": "stop"
            }
        """.trimIndent()

        val messages = BedrockAmazonNovaSerialization.parseNovaResponse(responseJson, mockClock)

        assertNotNull(messages)
        assertEquals(1, messages.size)

        val message = messages.first()
        assertTrue(message is Message.Assistant)
        assertEquals(responseContent, message.content)

        assertEquals(null, message.metaInfo.inputTokensCount)
        assertEquals(null, message.metaInfo.outputTokensCount)
        assertEquals(null, message.metaInfo.totalTokensCount)
    }

    @Test
    fun testParseNovaStreamChunk() {
        val chunkContent = "Paris is "
        val chunkJson = """
            {
                "contentBlockDelta": {
                    "delta": {
                        "text": "$chunkContent"
                    }
                }
            }
        """.trimIndent()

        val content = BedrockAmazonNovaSerialization.parseNovaStreamChunk(chunkJson)
        assertEquals(listOf(chunkContent).map(StreamFrame::Append), content)
    }

    @Test
    fun `parseNovaStreamChunk with empty text`() {
        val chunkJson = """
            {
                "contentBlockDelta": {
                    "delta": {
                        "text": ""
                    }
                }
            }
        """.trimIndent()

        val content = BedrockAmazonNovaSerialization.parseNovaStreamChunk(chunkJson)
        assertEquals(listOf("").map(StreamFrame::Append), content)
    }

    @Test
    fun `parseNovaStreamChunk with null text`() {
        val chunkJson = """
            {
                "contentBlockDelta": {
                    "delta": {
                        "text": null
                    }
                }
            }
        """.trimIndent()

        val content = BedrockAmazonNovaSerialization.parseNovaStreamChunk(chunkJson)
        assertEquals(emptyList(), content)
    }

    @Test
    fun `parseNovaStreamChunk with message stop`() {
        val chunkJson = """
            {
                "messageStop": {
                    "stopReason": "stop"
                },
                "metadata": {
                    "usage": {
                        "outputTokens": 20
                    }
                }
            }
        """.trimIndent()

        assertEquals(
            expected = listOf(
                StreamFrame.End(
                    finishReason = "stop",
                    metaInfo = ResponseMetaInfo.create(
                        clock = mockClock,
                        totalTokensCount = null,
                        inputTokensCount = null,
                        outputTokensCount = 20
                    )
                )
            ),
            actual = BedrockAmazonNovaSerialization.parseNovaStreamChunk(chunkJson, mockClock)
        )
    }

    @Test
    fun `createNovaRequest with tools`() {
        // Define test tools
        val tool = ToolDescriptor(
            name = "get_weather",
            description = "Get current weather for a city",
            requiredParameters = listOf(
                ToolParameterDescriptor("city", "The city name", ToolParameterType.String)
            ),
            optionalParameters = listOf(
                ToolParameterDescriptor("units", "Temperature units", ToolParameterType.String)
            )
        )
        val tools = listOf(tool)

        val prompt = Prompt.build("test") {
            system("You are a helpful assistant that can use tools.")
            user("What's the weather in Paris?")
        }

        val request = BedrockAmazonNovaSerialization.createNovaRequest(prompt, model, tools)

        // Verify toolConfig is included in the request
        assertNotNull(request.toolConfig)
        assertNotNull(request.toolConfig.tools)
        assertEquals(1, request.toolConfig.tools.size)

        // Verify tool details
        val toolSpec = request.toolConfig.tools[0].toolSpec
        assertEquals(tool.name, toolSpec.name)
        assertEquals(tool.description, toolSpec.description)

        // Verify tool schema
        val inputSchema = toolSpec.inputSchema
        assertNotNull(inputSchema)
        val jsonSchema = inputSchema.json
        assertEquals("object", jsonSchema.type)
        assertTrue(jsonSchema.properties.contains("city"))
        assertEquals(listOf("city"), jsonSchema.required)
    }

    @Test
    fun `parseNovaResponse with tool call`() {
        val responseJson = """
            {
                "output": {
                    "message": {
                        "role": "assistant",
                        "content": [
                            {
                                "toolUse": {
                                    "toolUseId": "tool_123",
                                    "name": "get_weather",
                                    "input": {
                                        "city": "Paris",
                                        "units": "celsius"
                                    }
                                }
                            }
                        ]
                    }
                },
                "usage": {
                    "inputTokens": 25,
                    "outputTokens": 20,
                    "totalTokens": 45
                },
                "stopReason": "tool_use"
            }
        """.trimIndent()

        val messages = BedrockAmazonNovaSerialization.parseNovaResponse(responseJson, mockClock)

        assertNotNull(messages)
        assertEquals(1, messages.size)

        val message = messages.first()
        assertIs<Message.Tool.Call>(message)

        val toolCall = message
        assertEquals("tool_123", toolCall.id)
        assertEquals("get_weather", toolCall.tool)
        assertTrue(toolCall.content.contains("Paris"))
        assertTrue(toolCall.content.contains("celsius"))

        // Check token counts
        assertEquals(20, toolCall.metaInfo.outputTokensCount)
        assertEquals(25, toolCall.metaInfo.inputTokensCount)
        assertEquals(45, toolCall.metaInfo.totalTokensCount)
    }
}
