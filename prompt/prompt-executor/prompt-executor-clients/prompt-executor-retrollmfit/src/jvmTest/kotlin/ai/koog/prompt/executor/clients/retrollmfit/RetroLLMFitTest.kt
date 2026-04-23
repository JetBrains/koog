package ai.koog.prompt.executor.clients.retrollmfit

import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.message.Message
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

// ---- test fixtures ----

@LLMEndpoint(
    url = "https://test-server.example.com/api/prompt",
    authHeaderName = "X-User-Id",
    authHeaderValue = "test-user-123",
)
@Serializable
data class TestRequest(@PromptField val prompt: String, val stream: Boolean = false)

@Serializable
data class TestResponse(@ResponseTextField val text: String, val session_id: String = "", val type: String = "text")

// ---- Jaika shapes (same as what the integration test hits live) ----

@LLMEndpoint(
    url = "https://35-207-202-131.sslip.io/api/prompt",
    authHeaderName = "X-User-Id",
    authHeaderValue = "116542085266142929154",
)
@Serializable
data class JaikaRequest(@PromptField val prompt: String, val stream: Boolean = false)

@Serializable
data class JaikaResponse(
    @ResponseTextField val text: String,
    val session_id: String = "",
    val type: String = "text",
)

// Missing @LLMEndpoint — used for error tests
@Serializable
data class NoEndpointRequest(@PromptField val prompt: String)

// Missing @PromptField — used for error tests
@LLMEndpoint(url = "https://test-server.example.com/api/prompt")
@Serializable
data class NoPromptFieldRequest(val prompt: String)

// Missing @ResponseTextField — used for error tests
@Serializable
data class NoTextFieldResponse(val text: String)

// ---- fixed clock ----

private object FixedClock : Clock {
    override fun now(): Instant = Instant.fromEpochMilliseconds(0)
}

class RetroLLMFitTest {

    private fun mockClient(responseBody: String, capture: (io.ktor.client.request.HttpRequestData) -> Unit = {}): HttpClient {
        val engine = MockEngine { request ->
            capture(request)
            respond(
                content = responseBody,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        return HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
    }

    //language=json
    private val successResponse = """{"text": "Hello back!", "session_id": "abc123", "type": "text"}"""

    @Test
    fun testExecuteSendsCorrectUrlAndAuthHeader() = runTest {
        var capturedUrl = ""
        var capturedMethod: HttpMethod? = null
        var capturedAuth: String? = null

        val client = RetroLLMFit.create<TestRequest, TestResponse>(
            httpClient = mockClient(successResponse) { req ->
                capturedUrl = req.url.toString()
                capturedMethod = req.method
                capturedAuth = req.headers[HttpHeaders.Authorization]
                    ?: req.headers["X-User-Id"]
            }
        )

        val prompt = Prompt.build(id = "p1", clock = FixedClock) { user("Hello!") }
        client.execute(prompt, RetroLLMFitModel)

        assertEquals("https://test-server.example.com/api/prompt", capturedUrl)
        assertEquals(HttpMethod.Post, capturedMethod)
        assertEquals("test-user-123", capturedAuth)
    }

    @Test
    fun testExecuteInjectsPromptIntoRequestBody() = runTest {
        var capturedBody = ""

        val client = RetroLLMFit.create<TestRequest, TestResponse>(
            httpClient = mockClient(successResponse) { req ->
                capturedBody = (req.body as? TextContent)?.text ?: ""
            }
        )

        val prompt = Prompt.build(id = "p2", clock = FixedClock) { user("What is Koog?") }
        client.execute(prompt, RetroLLMFitModel)

        val json = Json.parseToJsonElement(capturedBody).jsonObject
        assertEquals("What is Koog?", json["prompt"]?.jsonPrimitive?.content)
        assertEquals(false.toString(), json["stream"]?.jsonPrimitive?.content)
    }

    @Test
    fun testExecuteReturnsAssistantMessageFromResponseTextField() = runTest {
        val client = RetroLLMFit.create<TestRequest, TestResponse>(
            httpClient = mockClient(successResponse)
        )

        val prompt = Prompt.build(id = "p3", clock = FixedClock) { user("Hi") }
        val responses = client.execute(prompt, RetroLLMFitModel)

        assertEquals(1, responses.size)
        val msg = assertIs<Message.Assistant>(responses.first())
        assertEquals("Hello back!", msg.content)
    }

    @Test
    fun testMultiTurnPromptFlattening() = runTest {
        var capturedBody = ""

        val client = RetroLLMFit.create<TestRequest, TestResponse>(
            httpClient = mockClient(successResponse) { req ->
                capturedBody = (req.body as? TextContent)?.text ?: ""
            }
        )

        val prompt = Prompt.build(id = "p4", clock = FixedClock) {
            system("You are a helpful assistant")
            user("What's 2+2?")
        }
        client.execute(prompt, RetroLLMFitModel)

        val promptText = Json.parseToJsonElement(capturedBody).jsonObject["prompt"]?.jsonPrimitive?.content
        assertNotNull(promptText)
        assertTrue(promptText.contains("System:"))
        assertTrue(promptText.contains("What's 2+2?"))
    }

    @Test
    fun testStreamDefaultsToFalseInRequest() = runTest {
        var capturedBody = ""

        val client = RetroLLMFit.create<TestRequest, TestResponse>(
            httpClient = mockClient(successResponse) { req ->
                capturedBody = (req.body as? TextContent)?.text ?: ""
            }
        )

        val prompt = Prompt.build(id = "p5", clock = FixedClock) { user("Hi") }
        client.execute(prompt, RetroLLMFitModel)

        val json = Json.parseToJsonElement(capturedBody).jsonObject
        assertEquals("false", json["stream"]?.jsonPrimitive?.content)
    }

    @Test
    fun testMissingLLMEndpointAnnotationThrows() {
        val ex = assertFailsWith<IllegalStateException> {
            RetroLLMFit.create<NoEndpointRequest, TestResponse>()
        }
        assertTrue(ex.message!!.contains("@LLMEndpoint"), "Error should mention @LLMEndpoint, was: ${ex.message}")
    }

    @Test
    fun testMissingPromptFieldAnnotationThrows() {
        val ex = assertFailsWith<IllegalStateException> {
            RetroLLMFit.create<NoPromptFieldRequest, TestResponse>()
        }
        assertTrue(ex.message!!.contains("@PromptField"), "Error should mention @PromptField, was: ${ex.message}")
    }

    @Test
    fun testMissingResponseTextFieldAnnotationThrows() {
        val ex = assertFailsWith<IllegalStateException> {
            RetroLLMFit.create<TestRequest, NoTextFieldResponse>()
        }
        assertTrue(ex.message!!.contains("@ResponseTextField"), "Error should mention @ResponseTextField, was: ${ex.message}")
    }

    @Test
    fun testLlmProviderIsRetroLLMFit() {
        val client = RetroLLMFit.create<TestRequest, TestResponse>(
            httpClient = mockClient(successResponse)
        )
        assertEquals(RetroLLMFitProvider, client.llmProvider())
    }

    // ---- Jaika-specific tests (prove the real shapes work end-to-end with a mock) ----

    @Test
    fun testJaikaSendsCorrectUrlAndAuthHeader() = runTest {
        var capturedUrl = ""
        var capturedAuth: String? = null

        val client = RetroLLMFit.create<JaikaRequest, JaikaResponse>(
            httpClient = mockClient(successResponse) { req ->
                capturedUrl = req.url.toString()
                capturedAuth = req.headers["X-User-Id"]
            }
        )

        val prompt = Prompt.build(id = "jaika-1", clock = FixedClock) { user("Hello Jaika!") }
        client.execute(prompt, RetroLLMFitModel)

        assertEquals("https://35-207-202-131.sslip.io/api/prompt", capturedUrl)
        assertEquals("116542085266142929154", capturedAuth)
    }

    @Test
    fun testJaikaRequestBodyHasPromptAndStream() = runTest {
        var capturedBody = ""

        val client = RetroLLMFit.create<JaikaRequest, JaikaResponse>(
            httpClient = mockClient(successResponse) { req ->
                capturedBody = (req.body as? TextContent)?.text ?: ""
            }
        )

        val prompt = Prompt.build(id = "jaika-2", clock = FixedClock) { user("Who are you?") }
        client.execute(prompt, RetroLLMFitModel)

        val json = Json.parseToJsonElement(capturedBody).jsonObject
        assertEquals("Who are you?", json["prompt"]?.jsonPrimitive?.content)
        assertEquals("false", json["stream"]?.jsonPrimitive?.content)
    }

    @Test
    fun testJaikaResponseTextIsExtracted() = runTest {
        //language=json
        val jaikaResponse = """{"text":"I am Jaika!","session_id":"sess-abc","type":"text"}"""

        val client = RetroLLMFit.create<JaikaRequest, JaikaResponse>(
            httpClient = mockClient(jaikaResponse)
        )

        val prompt = Prompt.build(id = "jaika-3", clock = FixedClock) { user("Who are you?") }
        val responses = client.execute(prompt, RetroLLMFitModel)

        assertEquals(1, responses.size)
        val msg = assertIs<Message.Assistant>(responses.first())
        assertEquals("I am Jaika!", msg.content)
    }

    @Test
    fun testJaikaResponseIgnoresExtraFields() = runTest {
        // Verifies that unknown or extra JSON fields don't break deserialization
        //language=json
        val extendedResponse = """{"text":"Hello!","session_id":"s1","type":"text","new_field":"future_value"}"""

        val client = RetroLLMFit.create<JaikaRequest, JaikaResponse>(
            httpClient = mockClient(extendedResponse)
        )

        val prompt = Prompt.build(id = "jaika-4", clock = FixedClock) { user("Hi") }
        val responses = client.execute(prompt, RetroLLMFitModel)
        assertEquals("Hello!", assertIs<Message.Assistant>(responses.first()).content)
    }
}
