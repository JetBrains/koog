package ai.koog.prompt.executor.ollama.client

import ai.koog.http.client.ktor.KtorKoogHttpClient
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.ollama.client.dto.OllamaChatMessageDTO
import ai.koog.prompt.executor.ollama.client.dto.OllamaChatResponseDTO
import ai.koog.prompt.message.Message
import io.ktor.client.HttpClient
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Regression tests for https://github.com/JetBrains/koog/issues/1237.
 *
 * Ollama can reply to a non-streaming chat request with `Content-Type: text/plain; charset=utf-8`
 * even though the body is valid JSON. The client must still be able to deserialize such responses.
 */
class OllamaContentTypeTest {

    @Test
    fun `test non-streaming chat response with text-plain content type is parsed`() = runTest {
        val responseContent = "Hello from Ollama"

        val mockServer = MockOllamaChatServer(contentType = "text/plain; charset=utf-8") { request ->
            OllamaChatResponseDTO(
                model = request.model,
                message = OllamaChatMessageDTO(role = "assistant", content = responseContent),
                done = true
            )
        }

        val ollamaClient = OllamaClient(httpClientFactory = KtorKoogHttpClient.Factory(HttpClient(mockServer.mockEngine)))

        val responses = ollamaClient.execute(
            prompt = prompt("test") { user("Hi") },
            model = OllamaModels.Meta.LLAMA_3_2
        )

        assertEquals(1, responses.size)
        val assistant = assertIs<Message.Assistant>(responses.first())
        assertEquals(responseContent, assistant.content)
    }
}
