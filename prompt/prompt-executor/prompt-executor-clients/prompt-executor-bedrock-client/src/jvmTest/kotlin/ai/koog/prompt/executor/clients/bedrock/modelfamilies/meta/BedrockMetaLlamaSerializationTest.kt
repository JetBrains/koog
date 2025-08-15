package ai.koog.prompt.executor.clients.bedrock.modelfamilies.meta

import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.bedrock.BedrockModels
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.params.LLMParams
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BedrockMetaLlamaSerializationTest {

    private val mockClock = object : Clock {
        override fun now(): Instant = Clock.System.now()
    }

    private val model = BedrockModels.MetaLlama3_0_8BInstruct
    private val systemMessage = "You are a helpful assistant."
    private val userMessage = "Tell me about Paris."
    private val userNewMessage = "Hello, who are you?"
    private val assistantMessage = "I'm an AI assistant based on the Llama model. How can I help you today?"

    @Test
    fun `createLlamaRequest with system and user messages`() {
        val temperature = 0.7

        val prompt = Prompt.build("test", params = LLMParams(temperature = temperature)) {
            system(systemMessage)
            user(userMessage)
        }

        val request = BedrockMetaLlamaSerialization.createLlamaRequest(prompt, model)

        assertNotNull(request)
        assertEquals(2048, request.maxGenLen)
        assertEquals(temperature, request.temperature)

        val expectedSystemPart = "<|begin_of_text|><|start_header_id|>system<|end_header_id|>"
        val expectedUserPart = "<|start_header_id|>user<|end_header_id|>"
        val expectedAssistantStart = "<|start_header_id|>assistant<|end_header_id|>"

        assertContains(request.prompt, expectedSystemPart)
        assertContains(request.prompt, systemMessage)
        assertContains(request.prompt, expectedUserPart)
        assertContains(request.prompt, userMessage)
        assertContains(request.prompt, expectedAssistantStart)
    }

    @Test
    fun `createLlamaRequest with conversation history`() {
        val prompt = Prompt.build("test") {
            system(systemMessage)
            user(userNewMessage)
            assistant(assistantMessage)
            user("Tell me about machine learning.")
        }

        val request = BedrockMetaLlamaSerialization.createLlamaRequest(prompt, model)

        assertNotNull(request)

        assertContains(request.prompt, systemMessage)
        assertContains(request.prompt, userNewMessage)
        assertContains(request.prompt, assistantMessage)
        assertContains(request.prompt, "Tell me about machine learning.")
    }

    @Test
    fun `createLlamaRequest respects model temperature capability`() {
        val temperature = 0.3
        val promptWithTemperature = Prompt.build("test", params = LLMParams(temperature = temperature)) {
            user("Tell me a story.")
        }

        val request = BedrockMetaLlamaSerialization.createLlamaRequest(promptWithTemperature, model)
        assertEquals(temperature, request.temperature)

        val modelWithoutTemperature = LLModel(
            provider = LLMProvider.Bedrock,
            id = "test-model",
            capabilities = listOf(LLMCapability.Completion), // No temperature capability
            contextLength = 1_000L,
        )

        val requestWithoutTemp =
            BedrockMetaLlamaSerialization.createLlamaRequest(promptWithTemperature, modelWithoutTemperature)
        assertEquals(null, requestWithoutTemp.temperature)
    }

    @Test
    fun testParseLlamaResponse() {
        val responseJson = """
            {
                "generation": "Machine learning is a subset of artificial intelligence that focuses on developing systems that learn from data.",
                "prompt_token_count": 15,
                "generation_token_count": 20,
                "stop_reason": "stop"
            }
        """.trimIndent()

        val messages = BedrockMetaLlamaSerialization.parseLlamaResponse(responseJson, mockClock)

        assertNotNull(messages)
        assertEquals(1, messages.size)

        val message = messages.first()
        assertTrue(message is Message.Assistant)
        assertContains(message.content, "Machine learning is a subset of artificial intelligence")
        assertEquals("stop", message.finishReason)

        assertEquals(15, message.metaInfo.inputTokensCount)
        assertEquals(20, message.metaInfo.outputTokensCount)
        assertEquals(35, message.metaInfo.totalTokensCount)
    }

    @Test
    fun `parseLlamaResponse with missing token counts`() {
        val responseJson = """
            {
                "generation": "This is a test response.",
                "stop_reason": "length"
            }
        """.trimIndent()

        val messages = BedrockMetaLlamaSerialization.parseLlamaResponse(responseJson, mockClock)

        assertNotNull(messages)
        assertEquals(1, messages.size)

        val message = messages.first()
        assertTrue(message is Message.Assistant)
        assertEquals("This is a test response.", message.content)
        assertEquals("length", message.finishReason)

        assertEquals(null, message.metaInfo.inputTokensCount)
        assertEquals(null, message.metaInfo.outputTokensCount)
        assertEquals(null, message.metaInfo.totalTokensCount)
    }

    @Test
    fun testParseLlamaStreamChunk() {
        val chunkJson = """
            {
                "generation": "Hello, "
            }
        """.trimIndent()

        val content = BedrockMetaLlamaSerialization.parseLlamaStreamChunk(chunkJson)
        assertEquals("Hello, ", content)
    }

    @Test
    fun `parseLlamaStreamChunk with empty generation`() {
        val chunkJson = """
            {
                "generation": ""
            }
        """.trimIndent()

        val content = BedrockMetaLlamaSerialization.parseLlamaStreamChunk(chunkJson)
        assertEquals("", content)
    }

    @Test
    fun `parseLlamaStreamChunk with null generation`() {
        val chunkJson = """
            {
                "generation": null
            }
        """.trimIndent()

        val content = BedrockMetaLlamaSerialization.parseLlamaStreamChunk(chunkJson)
        assertEquals("", content)
    }
}
