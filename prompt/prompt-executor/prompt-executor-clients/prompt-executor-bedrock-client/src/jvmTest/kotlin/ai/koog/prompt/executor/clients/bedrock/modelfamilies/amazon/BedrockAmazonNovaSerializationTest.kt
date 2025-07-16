package ai.koog.prompt.executor.clients.bedrock.modelfamilies.amazon

import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.bedrock.BedrockModels
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.params.LLMParams
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.*

class BedrockAmazonNovaSerializationTest {

    private val mockClock = object : Clock {
        override fun now(): Instant = Clock.System.now()
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

        val request = BedrockAmazonNovaSerialization.createNovaRequest(prompt, model)

        assertNotNull(request)

        assertNotNull(request.system)
        assertEquals(1, request.system.size)
        assertEquals(systemMessage, request.system[0].text)

        assertEquals(1, request.messages.size)
        assertEquals("user", request.messages[0].role)
        assertEquals(1, request.messages[0].content.size)
        assertEquals(userMessage, request.messages[0].content[0].text)

        assertNotNull(request.inferenceConfig)
        assertEquals(4096, request.inferenceConfig.maxTokens)
        assertEquals(temperature, request.inferenceConfig.temperature)
    }

    @Test
    fun `createNovaRequest with conversation history`() {
        val prompt = Prompt.build("test") {
            system(systemMessage)
            user(userNewMessage)
            assistant(assistantMessage)
            user(userMessage)
        }

        val request = BedrockAmazonNovaSerialization.createNovaRequest(prompt, model)

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

        val request = BedrockAmazonNovaSerialization.createNovaRequest(promptWithTemperature, model)
        assertEquals(temperature, request.inferenceConfig?.temperature)

        val modelWithoutTemperature = LLModel(
            provider = LLMProvider.Bedrock,
            id = "test-model",
            capabilities = listOf(LLMCapability.Completion) // No temperature capability
        )

        val requestWithoutTemp = BedrockAmazonNovaSerialization.createNovaRequest(
            promptWithTemperature,
            modelWithoutTemperature,
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
        assertEquals(20, message.metaInfo.outputTokensCount)
        assertEquals(null, message.metaInfo.inputTokensCount)
        assertEquals(null, message.metaInfo.totalTokensCount)
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
        assertEquals(chunkContent, content)
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
        assertEquals("", content)
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
        assertEquals("", content)
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

        val content = BedrockAmazonNovaSerialization.parseNovaStreamChunk(chunkJson)
        assertEquals("", content)
    }
}