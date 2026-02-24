package ai.koog.prompt.executor.ollama.client

import ai.koog.prompt.executor.ollama.client.dto.OllamaChatResponseDTO
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.serialization.json.Json

internal class MockStreamingOllamaChatServer(
    private val handler: (Unit) -> OllamaChatResponseDTO,
) {
    val mockEngine = MockEngine { _ ->
        // Build streaming response by collecting multiple responses
        val responses = mutableListOf<String>()
        var response = handler(Unit)
        while (!response.done) {
            responses.add(Json.encodeToString<OllamaChatResponseDTO>(response))
            response = handler(Unit)
        }
        // Add the final response
        responses.add(Json.encodeToString<OllamaChatResponseDTO>(response))

        // Join with newlines for NDJSON format
        val streamContent = responses.joinToString("\n")

        respond(
            content = streamContent,
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType to listOf("application/json")),
        )
    }
}
