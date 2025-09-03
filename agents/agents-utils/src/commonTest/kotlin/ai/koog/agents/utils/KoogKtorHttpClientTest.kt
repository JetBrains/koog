package ai.koog.agents.utils

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.fail

class KoogKtorHttpClientTest {

    private val logger = KotlinLogging.logger("TestLogger")

    @Test
    fun `Should return success string response`() = runTest {
        var capturedMethod: HttpMethod? = null
        var capturedUrl: String? = null
        val responseBody = "RESPONSE_OK"

        val engine = MockEngine { req ->
            capturedMethod = req.method
            capturedUrl = req.url.toString()
            respond(
                content = responseBody,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Text.Plain.toString())
            )
        }
        val base = HttpClient(engine) {}
        val client = KoogHttpClient.fromKtorClient(
            clientName = "TestClient",
            logger = logger,
            baseClient = base
        ) { }

        val result: String = client.post(
            path = "https://example.com/echo",
            request = "PAYLOAD"
        )

        assertEquals(responseBody, result)
        assertEquals(HttpMethod.Post, capturedMethod)
        assertEquals("https://example.com/echo", capturedUrl)
    }

    @Test
    fun `Should post JSON request and get JSON response`() = runTest {
        var capturedMethod: HttpMethod? = null
        var capturedUrl: String? = null
        val responseBody = """{"response":"Okay"}"""

        @Serializable
        data class Request(val request: String)

        @Serializable
        data class Response(val response: String)

        val engine = MockEngine { req ->
            capturedMethod = req.method
            capturedUrl = req.url.toString()
            respond(
                content = responseBody,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        val base = HttpClient(engine) {}
        val client = KoogKtorHttpClient(
            clientName = "TestClient",
            logger = logger,
            baseClient = base
        ) {
            defaultRequest {
                contentType(ContentType.Application.Json)
            }
            install(ContentNegotiation) {
                json()
            }
        }

        val result: Response = client.post(
            path = "https://example.com/echo",
            request = Request("How are you?"),
            requestBodyType = Request::class,
            responseType = Response::class
        )

        assertEquals("Okay", result.response)
        assertEquals(HttpMethod.Post, capturedMethod)
        assertEquals("https://example.com/echo", capturedUrl)
    }

    @Test
    fun `Should handle on non-success status`() = runTest {
        val engine = MockEngine { _ ->
            respond(
                content = "Bad things",
                status = HttpStatusCode.BadRequest,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Text.Plain.toString())
            )
        }
        val base = HttpClient(engine) {}
        val client = KoogKtorHttpClient(
            clientName = "TestClient",
            logger = logger,
            baseClient = base
        ) { }

        try {
            client.post(
                path = "https://example.com/fail",
                request = "PAYLOAD",
            )
            fail("Expected an exception for non-success status")
        } catch (e: IllegalStateException) {
            assertNotNull(e.message) {
                assertContains(it, "Error from TestClient API")
                assertContains(it, "400")
                assertContains(it, "Bad things")
            }
        }
    }

    @Test
    fun `Should get SSE flow - without collecting`() {
        val engine = MockEngine { _ ->
            // No real SSE streaming with MockEngine; just ensure request is initiated without immediate failure
            respond(
                content = "",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Text.EventStream.toString())
            )
        }
        val base = HttpClient(engine) {}
        val client = KoogKtorHttpClient(
            clientName = "TestClient",
            logger = logger,
            baseClient = base
        ) { }

        val flow = client.sse(
            path = "https://example.com/stream",
            request = "{}",
            requestBodyType = String::class,
            dataFilter = { it != "[DONE]" },
            decodeStreamingResponse = { it },
            processStreamingChunk = { it }
        )

        assertNotNull(flow)
        // Do not collect; MockEngine SSE is not fully supported here.
    }
}
