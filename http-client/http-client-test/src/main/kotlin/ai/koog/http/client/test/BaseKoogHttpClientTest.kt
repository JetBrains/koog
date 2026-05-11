package ai.koog.http.client.test

import ai.koog.http.client.KoogHttpClient
import ai.koog.http.client.KoogHttpClientException
import ai.koog.http.client.get
import ai.koog.http.client.lines
import ai.koog.http.client.post
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import kotlin.test.assertEquals
import kotlin.test.assertTrue
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

    private lateinit var mockServer: MockWebServer

    @BeforeEach
    fun setUp() {
        mockServer = MockWebServer()
    }

    @AfterEach
    fun tearDown() {
        mockServer.stop()
    }

    /**
     * Creates a client instance to be tested.
     * This method will be called for each test case.
     */
    protected abstract fun createClient(): KoogHttpClient

    @Suppress("FunctionName")
    open fun `test return success string response on post`(): Unit = runTest {
        val responseBody = "RESPONSE_OK"

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
    }

    @Suppress("FunctionName")
    open fun `test return success string response on get`(): Unit = runTest {
        val responseBody = "RESPONSE_OK"

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
    }

    @Suppress("FunctionName")
    open fun `test post JSON request and get JSON response`(): Unit = runTest {
        val responseBody = """{"response":"Okay"}"""

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
    }

    @Suppress("FunctionName")
    open fun `test handle on non-success status`(): Unit = runTest {
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
        } catch (e: KoogHttpClientException) {
            assertEquals(e.clientName, "TestClient")
            assertEquals(e.statusCode, 400)
        }
    }

    @Suppress("FunctionName")
    open fun `test get SSE flow and collect events`(): Unit = runTest {
        val events = listOf("event1", "event2", "event3")

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
    }

    @Suppress("FunctionName")
    open fun `test filter SSE events`(): Unit = runTest {
        val events = listOf("event1", "[DONE]", "event2", "[DONE]", "event3")

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
    }

    @Suppress("FunctionName")
    open fun `test return success string response on get with parameters`(): Unit = runTest {
        val responseBody = "RESPONSE_OK_WITH_PARAMS"
        val expectedParameters = mapOf("param1" to "value1", "param2" to "value2")

        mockServer.start(
            getEndpoints = listOf(
                MockWebServer.GetEndpointConfig(
                    path = "/echo",
                    responseBody = responseBody,
                    statusCode = HttpStatusCode.OK,
                    contentType = ContentType.Text.Plain,
                    expectedParameters = expectedParameters
                )
            )
        )

        val client = createClient()

        val result: String = client.get(
            path = mockServer.url("/echo"),
            parameters = expectedParameters
        )

        assertEquals(responseBody, result)
    }

    @Suppress("FunctionName")
    open fun `test lines emits non-blank lines`(): Unit = runTest {
        val lines = listOf("""{"i":1}""", """{"i":2}""", """{"i":3}""")

        mockServer.start(
            streamEndpoints = listOf(
                MockWebServer.StreamEndpointConfig(
                    path = "/stream",
                    lines = lines
                )
            )
        )

        val client = createClient()

        val collected = client.lines(
            path = mockServer.url("/stream"),
            request = "{}"
        ).toList()

        assertEquals(lines, collected)
    }

    @Suppress("FunctionName")
    open fun `test lines skips blank lines`(): Unit = runTest {
        val lines = listOf("""{"i":1}""", "", "   ", """{"i":2}""")

        mockServer.start(
            streamEndpoints = listOf(
                MockWebServer.StreamEndpointConfig(
                    path = "/stream",
                    lines = lines
                )
            )
        )

        val client = createClient()

        val collected = client.lines(
            path = mockServer.url("/stream"),
            request = "{}"
        ).toList()

        assertEquals(listOf("""{"i":1}""", """{"i":2}"""), collected)
    }

    @Suppress("FunctionName")
    open fun `test lines emits nothing for empty body`(): Unit = runTest {
        mockServer.start(
            streamEndpoints = listOf(
                MockWebServer.StreamEndpointConfig(
                    path = "/stream",
                    lines = emptyList()
                )
            )
        )

        val client = createClient()

        val collected = client.lines(
            path = mockServer.url("/stream"),
            request = "{}"
        ).toList()

        assertTrue(collected.isEmpty())
    }

    @Suppress("FunctionName")
    open fun `test lines surfaces non-2xx as KoogHttpClientException`(): Unit = runTest {
        mockServer.start(
            streamEndpoints = listOf(
                MockWebServer.StreamEndpointConfig(
                    path = "/stream",
                    lines = listOf("ignored"),
                    statusCode = HttpStatusCode.BadRequest,
                    contentType = ContentType.Text.Plain
                )
            )
        )

        val client = createClient()

        try {
            client.lines(
                path = mockServer.url("/stream"),
                request = "{}"
            ).toList()
            fail("Expected KoogHttpClientException for non-2xx response")
        } catch (e: KoogHttpClientException) {
            assertEquals("TestClient", e.clientName)
            assertEquals(400, e.statusCode)
        }
    }

    @Suppress("FunctionName")
    open fun `test lines propagates cancellation`(): Unit = runTest {
        val lines = List(100) { """{"i":$it}""" }

        mockServer.start(
            streamEndpoints = listOf(
                MockWebServer.StreamEndpointConfig(
                    path = "/stream",
                    lines = lines
                )
            )
        )

        val client = createClient()

        val collected = client.lines(
            path = mockServer.url("/stream"),
            request = "{}"
        ).take(3).toList()

        assertEquals(3, collected.size)
        assertEquals(lines.take(3), collected)
    }

    @Suppress("FunctionName")
    open fun `test return success string response on post with parameters`(): Unit = runTest {
        val responseBody = "RESPONSE_OK_WITH_PARAMS"
        val expectedParameters = mapOf("filter" to "active", "sort" to "desc")

        mockServer.start(
            postEndpoints = listOf(
                MockWebServer.PostEndpointConfig(
                    path = "/echo",
                    responseBody = responseBody,
                    statusCode = HttpStatusCode.OK,
                    contentType = ContentType.Text.Plain,
                    expectedParameters = expectedParameters
                )
            )
        )

        val client = createClient()

        val result: String = client.post(
            path = mockServer.url("/echo"),
            request = "PAYLOAD",
            parameters = expectedParameters
        )

        assertEquals(responseBody, result)
    }
}
