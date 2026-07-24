package ai.koog.agentcore.runtime

import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.routing.routing
import io.ktor.server.sse.SSE
import io.ktor.server.testing.testApplication
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

class AgentCoreRuntimeTest {

    private fun Application.installAgentCoreTestModule(
        handler: suspend io.ktor.server.routing.RoutingContext.(String, AgentCoreContext) -> String = { body, _ -> """{"echo":"$body"}""" },
    ) {
        install(ContentNegotiation) { json() }
        install(SSE)
        routing {
            agentCoreRuntime {
                // Adapt the legacy String->String test lambdas to the unified handler.
                this.handler = { input, ctx ->
                    val body = (input as InvocationInput.Text).body
                    InvocationResult.Text(handler(this, body, ctx))
                }
            }
        }
    }

    @Test
    fun `ping returns healthy status`() = testApplication {
        application { installAgentCoreTestModule() }
        client.get("/ping").apply {
            assertEquals(HttpStatusCode.OK, status)
            val json = Json.parseToJsonElement(bodyAsText()).jsonObject
            assertEquals("Healthy", json["status"]?.jsonPrimitive?.content)
            assertTrue(json["time_of_last_update"]?.jsonPrimitive?.long!! > 0)
        }
    }

    @Test
    fun `invocations returns handler result`() = testApplication {
        application { installAgentCoreTestModule() }
        client.post("/invocations") {
            contentType(ContentType.Application.Json)
            setBody("""{"prompt":"hello"}""")
        }.apply {
            assertEquals(HttpStatusCode.OK, status)
            assertTrue(bodyAsText().contains("echo"))
        }
    }

    @Test
    fun `invocations passes headers via context`() = testApplication {
        application {
            installAgentCoreTestModule(handler = { _, context ->
                val sessionId = context.getHeader(AgentCoreHeaders.SESSION_ID)
                """{"sessionId":"$sessionId"}"""
            })
        }
        client.post("/invocations") {
            contentType(ContentType.Application.Json)
            header(AgentCoreHeaders.SESSION_ID, "test-session-123")
            setBody("""{"prompt":"hello"}""")
        }.apply {
            assertEquals(HttpStatusCode.OK, status)
            assertTrue(bodyAsText().contains("test-session-123"))
        }
    }

    @Test
    fun `invocations returns 500 on handler error`() = testApplication {
        application {
            installAgentCoreTestModule(handler = { _, _ ->
                throw AgentCoreInvocationException("test error")
            })
        }
        client.post("/invocations") {
            contentType(ContentType.Application.Json)
            setBody("""{"prompt":"hello"}""")
        }.apply {
            assertEquals(HttpStatusCode.InternalServerError, status)
        }
    }

    @Test
    fun `rate limiting returns 429 when exceeded`() = testApplication {
        application {
            install(ContentNegotiation) { json() }
            install(RateLimit) {
                global {
                    rateLimiter(limit = 2, refillPeriod = 1.minutes)
                }
            }
            routing {
                agentCoreRuntime {
                    handler = { _, _ -> InvocationResult.Text("ok") }
                }
            }
        }
        repeat(2) {
            client.get("/ping").apply {
                assertEquals(HttpStatusCode.OK, status)
            }
        }
        client.get("/ping").apply {
            assertEquals(HttpStatusCode.TooManyRequests, status)
        }
    }

    @Test
    fun `ordinary invocation does not mark agent as busy`() = testApplication {
        application { installAgentCoreTestModule() }
        client.post("/invocations") {
            contentType(ContentType.Application.Json)
            setBody("""{"prompt":"hello"}""")
        }
        client.get("/ping").apply {
            val json = Json.parseToJsonElement(bodyAsText()).jsonObject
            assertEquals("Healthy", json["status"]?.jsonPrimitive?.content)
        }
    }

    @Test
    fun `handler can report busy via exposed task tracker`() = testApplication {
        application {
            installAgentCoreTestModule(handler = { _, context ->
                context.taskTracker.increment()
                """{"started":true}"""
            })
        }
        client.post("/invocations") {
            contentType(ContentType.Application.Json)
            setBody("""{"prompt":"hello"}""")
        }
        client.get("/ping").apply {
            val json = Json.parseToJsonElement(bodyAsText()).jsonObject
            assertEquals("HealthyBusy", json["status"]?.jsonPrimitive?.content)
        }
    }

    @Test
    fun `invocations honors accept header for response content type`() = testApplication {
        application { installAgentCoreTestModule() }
        client.post("/invocations") {
            contentType(ContentType.Application.Json)
            accept(ContentType.Text.EventStream)
            setBody("""{"prompt":"hello"}""")
        }.apply {
            assertEquals(HttpStatusCode.OK, status)
            assertEquals(ContentType.Text.EventStream, contentType()?.withoutParameters())
            val text = bodyAsText()
            assertTrue(text.startsWith("data: "), "SSE body should be framed as a data event: $text")
            assertTrue(text.endsWith("\n\n"), "SSE body should end with a blank line: $text")
        }
    }

    @Test
    fun `invocations honors accept header for binary response`() = testApplication {
        application { installAgentCoreTestModule(handler = { _, _ -> "binary-payload" }) }
        client.post("/invocations") {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.OctetStream)
            setBody("""{"prompt":"hello"}""")
        }.apply {
            assertEquals(HttpStatusCode.OK, status)
            assertEquals(ContentType.Application.OctetStream, contentType()?.withoutParameters())
            assertContentEquals("binary-payload".toByteArray(), bodyAsBytes())
        }
    }

    @Test
    fun `rate limited request does not execute route handler twice`() = testApplication {
        var handlerCalls = 0
        application {
            install(ContentNegotiation) { json() }
            install(RateLimit) {
                register(RateLimitName("agentcore")) {
                    rateLimiter(limit = 1, refillPeriod = 1.minutes)
                }
            }
            routing {
                rateLimit(RateLimitName("agentcore")) {
                    agentCoreRuntime {
                        handler = { _, _ ->
                            handlerCalls++
                            InvocationResult.Text("ok")
                        }
                    }
                }
            }
        }
        client.post("/invocations") {
            contentType(ContentType.Application.Json)
            setBody("""{"prompt":"a"}""")
        }.apply { assertEquals(HttpStatusCode.OK, status) }
        client.post("/invocations") {
            contentType(ContentType.Application.Json)
            setBody("""{"prompt":"b"}""")
        }.apply {
            assertEquals(HttpStatusCode.TooManyRequests, status)
        }
        assertEquals(1, handlerCalls, "Throttled request must not reach the invocation handler")
    }

    @Test
    fun `invocations with text plain content type`() = testApplication {
        application { installAgentCoreTestModule() }
        client.post("/invocations") {
            contentType(ContentType.Text.Plain)
            setBody("hello world")
        }.apply {
            assertEquals(HttpStatusCode.OK, status)
            assertTrue(bodyAsText().contains("hello world"))
        }
    }

    @Serializable
    data class TypedRequest(val prompt: String, val count: Int = 1)

    @Serializable
    data class TypedResponse(val answer: String, val repeated: Int)

    private fun Application.installTypedTestModule(
        handler: suspend io.ktor.server.routing.RoutingContext.(TypedRequest, AgentCoreContext) -> TypedResponse =
            { input, _ -> TypedResponse(answer = "hi ${input.prompt}", repeated = input.count) }
    ) {
        install(ContentNegotiation) { json() }
        install(SSE)
        routing {
            agentCoreRuntime {
                handle(handler)
            }
        }
    }

    @Test
    fun `typed handler maps json body into input type and serializes output`() = testApplication {
        application { installTypedTestModule() }
        client.post("/invocations") {
            contentType(ContentType.Application.Json)
            setBody("""{"prompt":"world","count":3}""")
        }.apply {
            assertEquals(HttpStatusCode.OK, status)
            val json = Json.parseToJsonElement(bodyAsText()).jsonObject
            assertEquals("hi world", json["answer"]?.jsonPrimitive?.content)
            assertEquals(3, json["repeated"]?.jsonPrimitive?.int)
        }
    }

    @Test
    fun `typed handler applies serializer defaults for missing fields`() = testApplication {
        application { installTypedTestModule() }
        client.post("/invocations") {
            contentType(ContentType.Application.Json)
            setBody("""{"prompt":"solo"}""")
        }.apply {
            assertEquals(HttpStatusCode.OK, status)
            val json = Json.parseToJsonElement(bodyAsText()).jsonObject
            assertEquals("hi solo", json["answer"]?.jsonPrimitive?.content)
            assertEquals(1, json["repeated"]?.jsonPrimitive?.int)
        }
    }

    @Test
    fun `typed handler exposes headers through context`() = testApplication {
        application {
            installTypedTestModule(handler = { input, context ->
                val sessionId = context.getHeader(AgentCoreHeaders.SESSION_ID)
                TypedResponse(answer = "${input.prompt}:$sessionId", repeated = input.count)
            })
        }
        client.post("/invocations") {
            contentType(ContentType.Application.Json)
            header(AgentCoreHeaders.SESSION_ID, "sess-1")
            setBody("""{"prompt":"p"}""")
        }.apply {
            assertEquals(HttpStatusCode.OK, status)
            val json = Json.parseToJsonElement(bodyAsText()).jsonObject
            assertEquals("p:sess-1", json["answer"]?.jsonPrimitive?.content)
        }
    }

    @Test
    fun `no handler configured throws AgentCoreInvocationException`() {
        assertFailsWith<AgentCoreInvocationException> {
            testApplication {
                application {
                    install(ContentNegotiation) { json() }
                    install(SSE)
                    routing {
                        agentCoreRuntime {
                            // no handler configured
                        }
                    }
                }
                client.get("/ping")
            }
        }
    }

    @Test
    fun `invocations rate limiting returns 429 when exceeded`() = testApplication {
        application {
            install(ContentNegotiation) { json() }
            install(RateLimit) {
                register(RateLimitName("agentcore")) {
                    rateLimiter(limit = 1, refillPeriod = 1.minutes)
                }
            }
            routing {
                rateLimit(RateLimitName("agentcore")) {
                    agentCoreRuntime {
                        handler = { _, _ -> InvocationResult.Text("ok") }
                    }
                }
            }
        }
        client.post("/invocations") {
            contentType(ContentType.Application.Json)
            setBody("""{"prompt":"a"}""")
        }.apply { assertEquals(HttpStatusCode.OK, status) }
        client.post("/invocations") {
            contentType(ContentType.Application.Json)
            setBody("""{"prompt":"b"}""")
        }.apply { assertEquals(HttpStatusCode.TooManyRequests, status) }
    }

    @Test
    fun `task tracker decrement returns status to Healthy`() = testApplication {
        application {
            installAgentCoreTestModule(handler = { _, context ->
                context.taskTracker.increment()
                context.taskTracker.decrement()
                """{"done":true}"""
            })
        }
        client.post("/invocations") {
            contentType(ContentType.Application.Json)
            setBody("""{"prompt":"hello"}""")
        }
        client.get("/ping").apply {
            val json = Json.parseToJsonElement(bodyAsText()).jsonObject
            assertEquals("Healthy", json["status"]?.jsonPrimitive?.content)
        }
    }

    @Test
    fun `output handler returns binary content with derived content type`() = testApplication {
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
        application {
            install(ContentNegotiation) { json() }
            install(SSE)
            routing {
                agentCoreRuntime {
                    handler = { _, _ -> InvocationResult.Binary(png, ContentType.Image.PNG) }
                }
            }
        }
        client.post("/invocations") {
            contentType(ContentType.Application.Json)
            setBody("""{"prompt":"draw"}""")
        }.apply {
            assertEquals(HttpStatusCode.OK, status)
            assertEquals(ContentType.Image.PNG, contentType()?.withoutParameters())
            assertContentEquals(png, bodyAsBytes())
        }
    }

    @Test
    fun `streaming handler writes each chunk as its own SSE data event`() = testApplication {
        application {
            install(ContentNegotiation) { json() }
            install(SSE)
            routing {
                agentCoreRuntime {
                    handler = { _, _ ->
                        InvocationResult.TextStream(kotlinx.coroutines.flow.flowOf("Hello", " ", "world"))
                    }
                }
            }
        }
        client.post("/invocations") {
            contentType(ContentType.Application.Json)
            setBody("""{"prompt":"hi"}""")
        }.apply {
            assertEquals(HttpStatusCode.OK, status)
            assertEquals(ContentType.Text.EventStream, contentType()?.withoutParameters())
            assertEquals("data: Hello\n\ndata:  \n\ndata: world\n\n", bodyAsText())
        }
    }

    @Test
    fun `unified handler can dispatch on input variant`() = testApplication {
        application {
            install(ContentNegotiation) { json() }
            install(SSE)
            routing {
                agentCoreRuntime {
                    handler = { input, _ ->
                        when (input) {
                            is InvocationInput.Text -> InvocationResult.Text("text:${input.body}")
                            is InvocationInput.Binary -> InvocationResult.Text("binary:${input.bytes.size}")
                            is InvocationInput.Stream -> InvocationResult.Text("stream")
                            is InvocationInput.Multipart -> InvocationResult.Text("multipart")
                        }
                    }
                }
            }
        }
        client.post("/invocations") {
            contentType(ContentType.Text.Plain)
            setBody("body")
        }.apply {
            assertEquals(HttpStatusCode.OK, status)
            assertTrue(bodyAsText().contains("text:body"))
        }
    }

    @Test
    fun `output handler returns text content`() = testApplication {
        application {
            install(ContentNegotiation) { json() }
            install(SSE)
            routing {
                agentCoreRuntime {
                    handler = { input, _ ->
                        val body = (input as InvocationInput.Text).body
                        InvocationResult.Text("echo:$body")
                    }
                }
            }
        }
        client.post("/invocations") {
            contentType(ContentType.Text.Plain)
            setBody("hi")
        }.apply {
            assertEquals(HttpStatusCode.OK, status)
            assertTrue(bodyAsText().contains("echo:hi"))
        }
    }

    @Test
    fun `image content block maps to binary invocation result`() {
        val bytes = byteArrayOf(1, 2, 3, 4)
        val block = aws.sdk.kotlin.services.bedrockruntime.model.ContentBlock.Image(
            aws.sdk.kotlin.services.bedrockruntime.model.ImageBlock {
                format = aws.sdk.kotlin.services.bedrockruntime.model.ImageFormat.Png
                source = aws.sdk.kotlin.services.bedrockruntime.model.ImageSource.Bytes(bytes)
            }
        )
        val result = block.toInvocationResult()
        assertTrue(result is InvocationResult.Binary)
        assertEquals(ContentType("image", "png"), result.contentType)
        assertContentEquals(bytes, result.bytes)
    }

    @Test
    fun `audio mp3 content block maps to audio mpeg`() {
        val bytes = byteArrayOf(9, 8, 7)
        val block = aws.sdk.kotlin.services.bedrockruntime.model.ContentBlock.Audio(
            aws.sdk.kotlin.services.bedrockruntime.model.AudioBlock {
                format = aws.sdk.kotlin.services.bedrockruntime.model.AudioFormat.Mp3
                source = aws.sdk.kotlin.services.bedrockruntime.model.AudioSource.Bytes(bytes)
            }
        )
        val result = block.toInvocationResult()
        assertTrue(result is InvocationResult.Binary)
        assertEquals(ContentType("audio", "mpeg"), result.contentType)
    }

    @Test
    fun `text content block maps to text invocation result`() {
        val block = aws.sdk.kotlin.services.bedrockruntime.model.ContentBlock.Text("hello")
        val result = block.toInvocationResult()
        assertTrue(result is InvocationResult.Text)
        assertEquals("hello", result.value)
    }

    @Test
    fun `context exposes all request headers`() = testApplication {
        application {
            installAgentCoreTestModule(handler = { _, context ->
                val headers = context.getHeaders()
                val custom = headers["X-Custom-Header"]
                """{"custom":"$custom"}"""
            })
        }
        client.post("/invocations") {
            contentType(ContentType.Application.Json)
            header("X-Custom-Header", "my-value")
            setBody("""{"prompt":"hello"}""")
        }.apply {
            assertEquals(HttpStatusCode.OK, status)
            assertTrue(bodyAsText().contains("my-value"))
        }
    }

    @Test
    fun `non-AgentCore throwables are caught and returned as structured 500`() = testApplication {
        application {
            installAgentCoreTestModule(handler = { _, _ ->
                throw IllegalStateException("user-side bug")
            })
        }
        client.post("/invocations") {
            contentType(ContentType.Application.Json)
            setBody("""{"prompt":"x"}""")
        }.apply {
            assertEquals(HttpStatusCode.InternalServerError, status)
            val json = Json.parseToJsonElement(bodyAsText()).jsonObject
            assertEquals("user-side bug", json["error"]?.jsonPrimitive?.content)
            assertEquals("IllegalStateException", json["type"]?.jsonPrimitive?.content)
        }
    }

    @Test
    fun `payload exceeding maxRequestBytes is rejected with 413 before handler runs`() = testApplication {
        var handlerCalled = false
        application {
            install(SSE)
            routing {
                agentCoreRuntime {
                    maxRequestBytes = 16
                    handler = { _, _ ->
                        handlerCalled = true
                        InvocationResult.Text("should not run")
                    }
                }
            }
        }
        client.post("/invocations") {
            contentType(ContentType.Application.Json)
            setBody("""{"prompt":"this body is definitely longer than 16 bytes"}""")
        }.apply {
            assertEquals(HttpStatusCode.PayloadTooLarge, status)
        }
        assertEquals(false, handlerCalled, "Handler must not run when payload exceeds maxRequestBytes")
    }

    @Test
    fun `handlerTimeoutMillis triggers 504 when handler runs too long`() = testApplication {
        application {
            install(SSE)
            routing {
                agentCoreRuntime {
                    handlerTimeoutMillis = 100
                    handler = { _, _ ->
                        kotlinx.coroutines.delay(2_000)
                        InvocationResult.Text("never")
                    }
                }
            }
        }
        client.post("/invocations") {
            contentType(ContentType.Application.Json)
            setBody("""{"prompt":"slow"}""")
        }.apply {
            assertEquals(HttpStatusCode.GatewayTimeout, status)
            val json = Json.parseToJsonElement(bodyAsText()).jsonObject
            assertTrue(json["error"]?.jsonPrimitive?.content?.contains("timed out") == true)
        }
    }

    @Test
    fun `typed handler uses host-installed ContentNegotiation`() = testApplication {
        application {
            install(ContentNegotiation) { json() }
            routing {
                agentCoreRuntime {
                    handle<TypedRequest, TypedResponse> { input, _ ->
                        TypedResponse(answer = "configured:${input.prompt}", repeated = input.count)
                    }
                }
            }
        }
        client.post("/invocations") {
            contentType(ContentType.Application.Json)
            setBody("""{"prompt":"yo"}""")
        }.apply {
            assertEquals(HttpStatusCode.OK, status)
            val json = Json.parseToJsonElement(bodyAsText()).jsonObject
            assertEquals("configured:yo", json["answer"]?.jsonPrimitive?.content)
        }
    }

    @Test
    fun `multipart request is exposed via InvocationInput Multipart variant`() = testApplication {
        application {
            install(SSE)
            routing {
                agentCoreRuntime {
                    handler = { input, _ ->
                        if (input is InvocationInput.Multipart) {
                            val parts = mutableListOf<String>()
                            var part = input.parts.readPart()
                            while (part != null) {
                                parts.add("${part::class.simpleName}:${part.name}")
                                part.dispose()
                                part = input.parts.readPart()
                            }
                            InvocationResult.Text("parts=${parts.joinToString(",")}")
                        } else {
                            InvocationResult.Text("not-multipart:${input::class.simpleName}")
                        }
                    }
                }
            }
        }
        // Build a multipart request body manually.
        val boundary = "----test-boundary-1"
        val body = """
            ------test-boundary-1
            Content-Disposition: form-data; name="field1"
            
            hello
            ------test-boundary-1--
        """.trimIndent().replace("\n", "\r\n") + "\r\n"
        client.post("/invocations") {
            header(HttpHeaders.ContentType, "multipart/form-data; boundary=$boundary")
            setBody(body)
        }.apply {
            assertEquals(HttpStatusCode.OK, status)
            assertTrue(bodyAsText().contains("FormItem:field1"))
        }
    }
}
