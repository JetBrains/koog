package ai.koog.prompt.executor.clients.google

import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.params.LLMParams
import kotlin.test.Test
import kotlin.test.assertEquals

class GoogleLLMClientTest {

    @Test
    fun `createGoogleRequest should use null maxTokens if unspecified`() {
        val client = GoogleLLMClient(apiKey = "apiKey")
        val model = GoogleModels.Gemini2_5Pro
        val request = client.createGoogleRequest(
            prompt = Prompt(
                messages = emptyList(),
                id = "id"
            ),
            model = model,
            tools = emptyList()
        )
        assertEquals(null, request.generationConfig!!.maxOutputTokens)
    }

    @Test
    fun `createGoogleRequest should use maxTokens from user specified parameters when available`() {
        val client = GoogleLLMClient(apiKey = "apiKey")
        val model = GoogleModels.Gemini2_5Pro
        val request = client.createGoogleRequest(
            prompt = Prompt(
                messages = emptyList(),
                id = "id",
                params = LLMParams(maxTokens = 100)
            ),
            model = model,
            tools = emptyList()
        )
        assertEquals(100, request.generationConfig!!.maxOutputTokens)
    }
}
