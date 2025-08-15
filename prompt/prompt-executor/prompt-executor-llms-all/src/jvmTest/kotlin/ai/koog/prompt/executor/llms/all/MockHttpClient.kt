package ai.koog.prompt.executor.llms.all

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

internal data class MockResponse(
    val content: String,
    val status: HttpStatusCode,
)

internal fun createMockHttpClient(responses: Map<String, MockResponse>): HttpClient = HttpClient(MockEngine) {
    engine {
        addHandler { request ->
            val url = request.url.toString()
            val mock = responses[url] ?: error("No mock for $url")
            respond(
                content = mock.content,
                status = mock.status,
                headers = headersOf("Content-Type" to listOf("application/json"))
            )
        }
    }
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }
}
