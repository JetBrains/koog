package ai.koog.http.client.java

import ai.koog.http.client.KoogHttpClient
import ai.koog.http.client.KoogHttpClientException
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.net.InetSocketAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class JavaKoogHttpClientStreamingTest {
    @Test
    fun testSsePreservesAllEventsWithRendezvousBackpressure() = runTest {
        assertDelivery(sse = true)
    }

    @Test
    fun testLinesPreserveAllChunksWithRendezvousBackpressure() = runTest {
        assertDelivery(sse = false)
    }

    @Test
    fun testLinesRespectTheDeclaredResponseCharset() = runTest {
        withServer({
            responseHeaders.add("Content-Type", "text/plain; charset=ISO-8859-1")
            val bytes = "café\n".toByteArray(Charsets.ISO_8859_1)
            sendResponseHeaders(200, bytes.size.toLong())
            responseBody.write(bytes)
        }) { client ->
            assertEquals(listOf("café"), client.stream(sse = false).flowOn(Dispatchers.IO).toList())
        }
    }

    @Test
    fun testSseBackpressureBoundsDecodedEvents() = runTest {
        withContext(Dispatchers.Default) {
            val expected = (0..199).map(Int::toString)
            withServer({ respond(expected.joinToString("") { "data: $it\n\n" }) }) { client ->
                val decoded = AtomicInteger()
                val firstCollected = CompletableDeferred<Unit>()
                val secondDecoded = CompletableDeferred<Unit>()
                val releaseCollector = CompletableDeferred<Unit>()
                val received = mutableListOf<String>()
                val collection = launch(Dispatchers.IO) {
                    client.sse(
                        path = "/stream",
                        requestBody = "{}",
                        requestBodyType = String::class,
                        decodeStreamingResponse = {
                            if (decoded.incrementAndGet() == 2) secondDecoded.complete(Unit)
                            it
                        },
                        processStreamingChunk = { it },
                    ).buffer(0).collect {
                        received.add(it)
                        if (received.size == 1) {
                            firstCollected.complete(Unit)
                            releaseCollector.await()
                        }
                    }
                }
                try {
                    withTimeout(5_000) {
                        firstCollected.await()
                        secondDecoded.await()
                    }
                    assertEquals(2, decoded.get())
                    releaseCollector.complete(Unit)
                    withTimeout(5_000) { collection.join() }
                    assertEquals(expected, received)
                } finally {
                    releaseCollector.complete(Unit)
                    collection.cancelAndJoin()
                }
            }
        }
    }

    @Test
    fun testSseCancelsWhileWaitingForHeaders() = runTest {
        assertCancellation(sse = true, headers = true)
    }

    @Test
    fun testLinesCancelWhileWaitingForHeaders() = runTest {
        assertCancellation(sse = false, headers = true)
    }

    @Test
    fun testSseCancelsWhileWaitingForBody() = runTest {
        assertCancellation(sse = true, headers = false)
    }

    @Test
    fun testLinesCancelWhileWaitingForBody() = runTest {
        assertCancellation(sse = false, headers = false)
    }

    @Test
    fun testSseFirstEventClosesIdleBody() = runTest {
        assertFirst(sse = true)
    }

    @Test
    fun testLinesFirstChunkClosesIdleBody() = runTest {
        assertFirst(sse = false)
    }

    @Test
    fun testSseDownstreamFailureClosesIdleBody() = runTest {
        assertDownstreamFailure(sse = true)
    }

    @Test
    fun testLinesDownstreamFailureClosesIdleBody() = runTest {
        assertDownstreamFailure(sse = false)
    }

    @Test
    fun testSseCallbackCancellationRetainsItsIdentity() = runTest {
        withContext(Dispatchers.Default) {
            for (stage in listOf("filter", "decoder", "processor")) {
                withServer({ respond("data: first\n\n") }) { client ->
                    val cancellation = CallbackCancellation()
                    val error = assertFailsWith<CancellationException> {
                        withTimeout(5_000) {
                            client.sse(
                                path = "/stream",
                                requestBody = "{}",
                                requestBodyType = String::class,
                                dataFilter = {
                                    if (stage == "filter") throw cancellation
                                    true
                                },
                                decodeStreamingResponse = {
                                    if (stage == "decoder") throw cancellation
                                    it
                                },
                                processStreamingChunk = {
                                    if (stage == "processor") throw cancellation
                                    it
                                },
                            ).flowOn(Dispatchers.IO).toList()
                        }
                    }
                    assertSame(cancellation, error)
                }
            }
        }
    }

    @Test
    fun testSseDecoderFailureStopsProcessingLaterEvents() = runTest {
        withServer({ respond("data: bad\n\ndata: later\n\n") }) { client ->
            var calls = 0
            val cause = IllegalArgumentException("invalid event")
            val error = assertFailsWith<KoogHttpClientException> {
                client.sse(
                    path = "/stream",
                    requestBody = "{}",
                    requestBodyType = String::class,
                    decodeStreamingResponse = {
                        calls++
                        throw cause
                    },
                    processStreamingChunk = { value: String -> value },
                ).flowOn(Dispatchers.IO).toList()
            }
            assertEquals(1, calls)
            assertSame(cause, error.cause)
        }
    }

    private class CallbackCancellation : CancellationException("callback cancelled") {
        // An extra field prevents coroutine stack-trace recovery from copying this exception.
        val marker = Any()
    }

    private suspend fun assertDelivery(sse: Boolean) {
        val expected = (0..199).map(Int::toString)
        val body = expected.joinToString("") { if (sse) "data: $it\n\n" else "$it\n" }
        withServer({ respond(body) }) { client ->
            assertEquals(expected, client.stream(sse).buffer(0).flowOn(Dispatchers.IO).toList())
        }
    }

    private suspend fun assertCancellation(sse: Boolean, headers: Boolean) = withContext(Dispatchers.Default) {
        val entered = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        withServer({
            if (!headers) {
                sendResponseHeaders(200, 0)
                responseBody.write(if (sse) "data: preview\n\n".toByteArray() else "preview\n".toByteArray())
                responseBody.flush()
            }
            entered.complete(Unit)
            check(release.await(10, TimeUnit.SECONDS))
            if (headers) respond("late")
        }) { client ->
            val received = CompletableDeferred<Unit>()
            val collection = launch(Dispatchers.IO) { client.stream(sse).collect { received.complete(Unit) } }
            try {
                withTimeout(5_000) { entered.await() }
                if (!headers) withTimeout(5_000) { received.await() }
                withTimeout(2_000) { collection.cancelAndJoin() }
                assertTrue(collection.isCancelled)
                assertEquals(1L, release.count)
            } finally {
                release.countDown()
                collection.cancelAndJoin()
            }
        }
    }

    private suspend fun assertFirst(sse: Boolean) = withIdleBody(sse) { client, release ->
        val first = async(Dispatchers.IO) { client.stream(sse).first() }
        try {
            assertEquals("preview", withTimeout(5_000) { first.await() })
            assertEquals(1L, release.count)
        } finally {
            release.countDown()
            first.cancelAndJoin()
        }
    }

    private suspend fun assertDownstreamFailure(sse: Boolean) = withIdleBody(sse) { client, release ->
        val cause = ConsumerFailure()
        val result = async(Dispatchers.IO) {
            assertFailsWith<ConsumerFailure> { client.stream(sse).collect { throw cause } }
        }
        try {
            assertSame(cause, withTimeout(5_000) { result.await() })
            assertEquals(1L, release.count)
        } finally {
            release.countDown()
            result.cancelAndJoin()
        }
    }

    private class ConsumerFailure : IllegalStateException("consumer failed") {
        val marker = Any()
    }

    private suspend fun withIdleBody(
        sse: Boolean,
        block: suspend kotlinx.coroutines.CoroutineScope.(KoogHttpClient, CountDownLatch) -> Unit,
    ) = withContext(Dispatchers.Default) {
        val release = CountDownLatch(1)
        withServer({
            sendResponseHeaders(200, 0)
            responseBody.write(if (sse) "data: preview\n\n".toByteArray() else "preview\n".toByteArray())
            responseBody.flush()
            check(release.await(10, TimeUnit.SECONDS))
        }) { client ->
            try {
                block(client, release)
            } finally {
                release.countDown()
            }
        }
    }

    private suspend fun withServer(handler: HttpExchange.() -> Unit, block: suspend (KoogHttpClient) -> Unit) {
        val executor = Executors.newCachedThreadPool()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.executor = executor
        server.createContext("/stream") { exchange -> exchange.use { it.handler() } }
        server.start()
        val client = JavaKoogHttpClient.Factory().create(
            clientName = "java-streaming",
            baseUrl = "http://127.0.0.1:${server.address.port}",
        )
        try {
            block(client)
        } finally {
            client.close()
            server.stop(0)
            executor.shutdownNow()
        }
    }

    private fun HttpExchange.respond(body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        sendResponseHeaders(200, bytes.size.toLong())
        responseBody.write(bytes)
    }

    private fun KoogHttpClient.stream(sse: Boolean): Flow<String> = if (sse) {
        sse(
            path = "/stream",
            requestBody = "{}",
            requestBodyType = String::class,
            decodeStreamingResponse = { it },
            processStreamingChunk = { it },
        )
    } else {
        lines(path = "/stream", requestBody = "{}", requestBodyType = String::class)
    }
}
