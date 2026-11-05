package ai.koog.a2a.transport.server.jsonrpc.http

import ai.koog.a2a.model.AgentCapabilities
import ai.koog.a2a.model.AgentCard
import ai.koog.a2a.model.AgentInterface
import ai.koog.a2a.model.TransportProtocol
import ai.koog.a2a.transport.jsonrpc.serialization.JSONRPCJson
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/** Tests the HTTP caching behavior of [a2aAgentCardRoute]. */
class AgentCardRouteTest {
    private val agentCard = AgentCard(
        name = "Test agent",
        description = "An agent for tests",
        supportedInterfaces = listOf(
            AgentInterface(url = "http://localhost/a2a", protocolBinding = TransportProtocol.JSONRPC, protocolVersion = "1.0")
        ),
        version = "1.0.0",
        defaultInputModes = listOf("text"),
        defaultOutputModes = listOf("text"),
        skills = emptyList(),
        capabilities = AgentCapabilities(),
    )

    private val lastModified = Instant.parse("2026-03-01T12:00:00Z")
    private val lastModifiedHeader = "Sun, 01 Mar 2026 12:00:00 GMT"

    private fun cardTest(
        card: AgentCard = agentCard,
        cacheMaxAge: Duration? = 1.hours,
        lastModified: Instant? = this.lastModified,
        block: suspend ApplicationTestBuilder.() -> Unit,
    ) = runTest {
        testApplication {
            routing {
                a2aAgentCardRoute("/card", card, cacheMaxAge, lastModified)
            }
            block()
        }
    }

    private suspend fun ApplicationTestBuilder.getCard(vararg headers: Pair<String, String>): HttpResponse =
        client.get("/card") { headers.forEach { (name, value) -> header(name, value) } }

    private suspend fun ApplicationTestBuilder.etag(): String = checkNotNull(getCard().headers[HttpHeaders.ETag])

    private suspend fun assertNotModified(response: HttpResponse, etag: String) {
        assertEquals(HttpStatusCode.NotModified, response.status)
        assertEquals("", response.bodyAsText())
        assertEquals(etag, response.headers[HttpHeaders.ETag])
        assertEquals("max-age=3600", response.headers[HttpHeaders.CacheControl])
        assertEquals(lastModifiedHeader, response.headers[HttpHeaders.LastModified])
    }

    @Test
    fun testDefaultResponseHeadersAndBody() = cardTest {
        val response = getCard()

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ContentType.Application.Json, response.contentType()?.withoutParameters())
        assertEquals(agentCard, JSONRPCJson.decodeFromString<AgentCard>(response.bodyAsText()))
        assertEquals("max-age=3600", response.headers[HttpHeaders.CacheControl])
        assertEquals(lastModifiedHeader, response.headers[HttpHeaders.LastModified])
        assertTrue(Regex("W/\"[0-9a-f]{40}\"").matches(response.headers[HttpHeaders.ETag].orEmpty()))
    }

    @Test
    fun testNullParametersOmitHeaders() = cardTest(cacheMaxAge = null, lastModified = null) {
        val response = getCard(HttpHeaders.IfModifiedSince to "Mon, 02 Mar 2026 12:00:00 GMT")

        assertEquals(HttpStatusCode.OK, response.status)
        assertNull(response.headers[HttpHeaders.CacheControl])
        assertNull(response.headers[HttpHeaders.LastModified])
        assertTrue(response.headers[HttpHeaders.ETag].orEmpty().startsWith("W/\""))
    }

    @Test
    fun testEtagDiffersForDifferentCards() = runTest {
        suspend fun etagOf(card: AgentCard): String {
            var etag: String? = null
            testApplication {
                routing { a2aAgentCardRoute("/card", card) }
                etag = client.get("/card").headers[HttpHeaders.ETag]
            }
            return checkNotNull(etag)
        }

        assertNotEquals(etagOf(agentCard), etagOf(agentCard.copy(version = "2.0.0")))
    }

    @Test
    fun testIfNoneMatchMatchingReturnsNotModified() = cardTest {
        val etag = etag()

        listOf(etag, etag.removePrefix("W/"), "*", "\"other\", W/\"another\" , $etag").forEach { value ->
            assertNotModified(getCard(HttpHeaders.IfNoneMatch to value), etag)
        }
    }

    @Test
    fun testIfNoneMatchWithoutMatchReturnsCard() = cardTest {
        val etag = etag()

        listOf("W/\"stale\"", "\"stale-1\", W/\"stale-2\"").forEach { value ->
            val response = getCard(HttpHeaders.IfNoneMatch to value)

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(etag, response.headers[HttpHeaders.ETag])
            assertEquals(agentCard, JSONRPCJson.decodeFromString<AgentCard>(response.bodyAsText()))
        }
    }

    @Test
    fun testIfModifiedSinceNotEarlierReturnsNotModified() = cardTest {
        val etag = etag()

        listOf(lastModifiedHeader, "Mon, 02 Mar 2026 12:00:00 GMT").forEach { date ->
            assertNotModified(getCard(HttpHeaders.IfModifiedSince to date), etag)
        }
    }

    @Test
    fun testIfModifiedSinceEarlierOrInvalidReturnsCard() = cardTest {
        listOf("Sun, 01 Mar 2026 11:59:59 GMT", "not a date").forEach { date ->
            val response = getCard(HttpHeaders.IfModifiedSince to date)

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(agentCard, JSONRPCJson.decodeFromString<AgentCard>(response.bodyAsText()))
        }
    }

    @Test
    fun testIfNoneMatchTakesPrecedenceOverIfModifiedSince() = cardTest {
        val response = getCard(
            HttpHeaders.IfNoneMatch to "W/\"stale\"",
            HttpHeaders.IfModifiedSince to "Mon, 02 Mar 2026 12:00:00 GMT",
        )

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(agentCard, JSONRPCJson.decodeFromString<AgentCard>(response.bodyAsText()))
    }
}
