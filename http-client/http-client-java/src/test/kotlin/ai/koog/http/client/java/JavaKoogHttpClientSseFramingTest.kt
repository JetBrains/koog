package ai.koog.http.client.java

import ai.koog.http.client.KoogHttpClient
import ai.koog.http.client.test.MockWebServer
import io.ktor.http.ContentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class JavaKoogHttpClientSseFramingTest {
    @Test
    fun testMultilineDataDispatchesAsOneEvent() = runTest {
        assertEquals(listOf("first\nsecond"), collect("data: first\ndata:second\n\n"))
    }

    @Test
    fun testMetadataAndCommentsAreIgnored() = runTest {
        assertEquals(listOf("payload"), collect(": heartbeat\nevent: message\nid: 7\nretry: 100\nfuture: ignored\nraw text\ndata: payload\n\n"))
    }

    @Test
    fun testLeadingBomAndAllLineEndings() = runTest {
        assertEquals(listOf("one", "two", "three"), collect("\uFEFFdata: one\r\rdata: two\r\n\r\ndata: three\n\n"))
    }

    @Test
    fun testDataWhitespaceIsPreserved() = runTest {
        assertEquals(listOf(" leading  \n\ttab "), collect("data:  leading  \ndata:\ttab \n\n"))
    }

    @Test
    fun testEmptyDataAndColonlessDataDispatch() = runTest {
        assertEquals(listOf("", ""), collect("\nevent: ignored\n\ndata:\n\ndata\n\n"))
    }

    @Test
    fun testIncompleteEventIsDiscardedAtEof() = runTest {
        assertEquals(listOf("complete"), collect("data: complete\n\ndata: incomplete\n"))
    }

    @Test
    fun testFilterAndDecoderReceiveCompleteDataEvents() = runTest {
        val filtered = mutableListOf<String?>()
        withResponse("data: first\ndata: second\n\ndata: skip\n\ndata: keep\n\n") { client ->
            val result = client.sse(
                path = "/stream",
                requestBody = "{}",
                requestBodyType = String::class,
                dataFilter = {
                    filtered.add(it)
                    it != "skip"
                },
                decodeStreamingResponse = { it.uppercase() },
                processStreamingChunk = { it.takeUnless { it == "FIRST\nSECOND" } },
            ).flowOn(Dispatchers.IO).toList()
            assertEquals(listOf<String?>("first\nsecond", "skip", "keep"), filtered)
            assertEquals(listOf("KEEP"), result)
        }
    }

    private suspend fun collect(response: String): List<String> {
        var result = emptyList<String>()
        withResponse(response) { client ->
            result = client.sse(
                path = "/stream",
                requestBody = "{}",
                requestBodyType = String::class,
                decodeStreamingResponse = { it },
                processStreamingChunk = { it },
            ).flowOn(Dispatchers.IO).toList()
        }
        return result
    }

    private suspend fun withResponse(response: String, block: suspend (KoogHttpClient) -> Unit) {
        val server = MockWebServer()
        server.start(
            postEndpoints = listOf(
                MockWebServer.PostEndpointConfig(
                    path = "/stream",
                    responseBody = response,
                    contentType = ContentType.Text.EventStream,
                )
            )
        )
        val client = JavaKoogHttpClient.Factory().create(clientName = "java-sse", baseUrl = server.url(""))
        try {
            block(client)
        } finally {
            client.close()
            server.stop()
        }
    }
}
