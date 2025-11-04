package ai.koog.http.client.test

import ai.koog.http.client.KoogHttpClient
import ai.koog.http.client.get
import ai.koog.http.client.post
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.fail

/**
 * Abstract test suite for KoogHttpClient implementations.
 * Provides common test scenarios that can be reused across different HTTP client implementations.
 *
 * Subclasses must implement [createClient] to provide the specific client implementation to test.
 */
abstract class BaseKoogHttpClientTest {
    @Serializable
    data class TestRequest(val request: String)

    @Serializable
    data class TestResponse(val response: String)

    /**
     * Creates a client instance to be tested.
     * This method will be called for each test case.
     */
    protected abstract fun createClient(): KoogHttpClient

    @Suppress("FunctionName")
    open fun `test return success string response on post`(): Unit = runTest {
        val responseBody = "RESPONSE_OK"

        val mockServer = MockWebServer()
        mockServer.start(
            postEndpoints = listOf(
                MockWebServer.PostEndpointConfig(
                    path = "/echo",
                    responseBody = responseBody,
                    statusCode = HttpStatusCode.OK,
                    contentType = ContentType.Text.Plain
                )
            )
        )

        val client = createClient()

        val result: String = client.post(
            path = mockServer.url("/echo"),
            request = "PAYLOAD"
        )

        assertEquals(responseBody, result)

        mockServer.stop()
    }

    @Suppress("FunctionName")
    open fun `test return success string response on get`(): Unit = runTest {
        val responseBody = "RESPONSE_OK"

        val mockServer = MockWebServer()
        mockServer.start(
            getEndpoints = listOf(
                MockWebServer.GetEndpointConfig(
                    path = "/echo",
                    responseBody = responseBody,
                    statusCode = HttpStatusCode.OK,
                    contentType = ContentType.Text.Plain
                )
            )
        )

        val client = createClient()

        val result: String = client.get(
            path = mockServer.url("/echo")
        )

        assertEquals(responseBody, result)

        mockServer.stop()
    }

    @Suppress("FunctionName")
    open fun `test post JSON request and get JSON response`(): Unit = runTest {
        val responseBody = """{"response":"Okay"}"""

        val mockServer = MockWebServer()
        mockServer.start(
            postEndpoints = listOf(
                MockWebServer.PostEndpointConfig(
                    path = "/echo",
                    responseBody = responseBody,
                    statusCode = HttpStatusCode.OK,
                    contentType = ContentType.Application.Json
                )
            )
        )

        val client = createClient()

        val result: TestResponse = client.post(
            path = mockServer.url("/echo"),
            request = TestRequest("How are you?"),
            requestBodyType = TestRequest::class,
            responseType = TestResponse::class
        )

        assertEquals("Okay", result.response)

        mockServer.stop()
    }

    @Suppress("FunctionName")
    open fun `test handle on non-success status`(): Unit = runTest {
        val mockServer = MockWebServer()
        mockServer.start(
            postEndpoints = listOf(
                MockWebServer.PostEndpointConfig(
                    path = "/fail",
                    responseBody = "Bad things",
                    statusCode = HttpStatusCode.BadRequest,
                    contentType = ContentType.Text.Plain
                )
            )
        )

        val client = createClient()

        try {
            client.post<String, String>(
                path = mockServer.url("/fail"),
                request = "PAYLOAD",
            )
            fail("Expected an exception for non-success status")
        } catch (e: IllegalStateException) {
            assertNotNull(e.message) {
                assertContains(it, "Error from TestClient API")
                assertContains(it, "400")
            }
        } finally {
            mockServer.stop()
        }
    }

    @Suppress("FunctionName")
    open fun `test get SSE flow and collect events`(): Unit = runTest {
        val events = listOf("event1", "event2", "event3")

        val mockServer = MockWebServer()
        mockServer.start(
            sseEndpoints = listOf(
                MockWebServer.SSEEndpointConfig(
                    path = "/stream",
                    events = events
                )
            )
        )

        val client = createClient()

        val flow = client.sse(
            path = mockServer.url("/stream"),
            request = "{}",
            requestBodyType = String::class,
            dataFilter = { it != "[DONE]" },
            decodeStreamingResponse = { it },
            processStreamingChunk = { it }
        )

        val collected = flow.toList()

        assertEquals(events.size, collected.size)
        assertEquals(events, collected)

        mockServer.stop()
    }

    @Suppress("FunctionName")
    open fun `test filter SSE events`(): Unit = runTest {
        val events = listOf("event1", "[DONE]", "event2", "[DONE]", "event3")

        val mockServer = MockWebServer()
        mockServer.start(
            sseEndpoints = listOf(
                MockWebServer.SSEEndpointConfig(
                    path = "/stream",
                    events = events
                )
            )
        )

        val client = createClient()

        val flow = client.sse(
            path = mockServer.url("/stream"),
            request = "{}",
            requestBodyType = String::class,
            dataFilter = { it != "[DONE]" },
            decodeStreamingResponse = { it },
            processStreamingChunk = { it }
        )

        val collected = flow.toList()

        // Only non-[DONE] events should be collected
        assertEquals(3, collected.size)
        assertEquals(listOf("event1", "event2", "event3"), collected)

        mockServer.stop()
    }
}
