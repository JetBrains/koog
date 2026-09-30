package ai.koog.http.client.java

import ai.koog.http.client.KoogHttpClient
import ai.koog.http.client.KoogHttpClientException
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.FilterInputStream
import java.io.InputStream
import java.net.Authenticator
import java.net.CookieHandler
import java.net.ProxySelector
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpHeaders
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.ByteBuffer
import java.time.Duration
import java.util.Optional
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.Flow
import java.util.concurrent.atomic.AtomicInteger
import java.util.stream.Stream
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLParameters
import javax.net.ssl.SSLSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class JavaKoogHttpClientBodyClosureTest {
    @Test
    fun testSseClosesBodyAfterEof() = runTest { assertClosure(sse = true, status = 200) }

    @Test
    fun testLinesCloseBodyAfterEof() = runTest { assertClosure(sse = false, status = 200) }

    @Test
    fun testSseClosesBodyAfterHttpFailure() = runTest { assertClosure(sse = true, status = 429) }

    @Test
    fun testLinesCloseBodyAfterHttpFailure() = runTest { assertClosure(sse = false, status = 429) }

    @Test
    fun testSseClosesBodyAfterCallbackFailure() = runTest {
        val transport = BodyClient(200, "data: bad\n\n")
        val client = KoogHttpClient.fromJavaHttpClient("body-closure", KotlinLogging.logger {}, transport)
        assertFailsWith<KoogHttpClientException> {
            client.sse(
                path = "http://localhost/stream",
                requestBody = "{}",
                requestBodyType = String::class,
                decodeStreamingResponse = { throw IllegalArgumentException("bad") },
                processStreamingChunk = { value: String -> value },
            ).flowOn(Dispatchers.IO).toList()
        }
        assertEquals(1, transport.closures.get())
    }

    @Test
    fun testCancellationWhenHeadersArriveClosesReceivedBody() = runTest {
        withContext(Dispatchers.Default) {
            lateinit var collection: Job
            val responseReady = CompletableDeferred<Unit>()
            val transport = BodyClient(200, "data: payload\n\n") {
                responseReady.complete(Unit)
                collection.cancel()
            }
            val client = KoogHttpClient.fromJavaHttpClient("body-race", KotlinLogging.logger {}, transport)
            collection = launch(Dispatchers.IO, start = CoroutineStart.LAZY) {
                client.sse(
                    path = "http://localhost/stream",
                    requestBody = "{}",
                    requestBodyType = String::class,
                    decodeStreamingResponse = { it },
                    processStreamingChunk = { it },
                ).toList()
            }
            collection.start()
            withTimeout(5_000) {
                responseReady.await()
                collection.join()
            }
            assertEquals(1, transport.closures.get())
        }
    }

    private suspend fun assertClosure(sse: Boolean, status: Int) {
        val transport = BodyClient(status, if (sse) "data: payload\n\n" else "payload\n")
        val client = KoogHttpClient.fromJavaHttpClient("body-closure", KotlinLogging.logger {}, transport)
        val stream = if (sse) {
            client.sse(
                path = "http://localhost/stream",
                requestBody = "{}",
                requestBodyType = String::class,
                decodeStreamingResponse = { it },
                processStreamingChunk = { it },
            )
        } else {
            client.lines(path = "http://localhost/stream", requestBody = "{}", requestBodyType = String::class)
        }
        if (status == 200) {
            assertEquals(listOf("payload"), stream.flowOn(Dispatchers.IO).toList())
        } else {
            val error = assertFailsWith<KoogHttpClientException> { stream.flowOn(Dispatchers.IO).toList() }
            assertEquals(status, error.statusCode)
        }
        assertEquals(1, transport.closures.get(), "The owned body must be closed exactly once")
    }

    /** Supplies bytes through the actual JDK body handler, observing response-body closure. */
    private class BodyClient(
        private val status: Int,
        private val content: String,
        private val onResponseReady: () -> Unit = {},
    ) : HttpClient() {
        val closures = AtomicInteger()
        private val responseHeaders = HttpHeaders.of(emptyMap()) { _, _ -> true }

        override fun <T : Any?> send(request: HttpRequest, handler: HttpResponse.BodyHandler<T>): HttpResponse<T> {
            val subscriber = handler.apply(object : HttpResponse.ResponseInfo {
                override fun statusCode(): Int = status
                override fun headers(): HttpHeaders = responseHeaders
                override fun version(): Version = Version.HTTP_1_1
            })
            subscriber.onSubscribe(object : Flow.Subscription {
                override fun request(n: Long) {}
                override fun cancel() {}
            })
            subscriber.onNext(listOf(ByteBuffer.wrap(content.toByteArray(Charsets.UTF_8))))
            subscriber.onComplete()
            val original = subscriber.body.toCompletableFuture().get()
            val observed = when (original) {
                is InputStream -> object : FilterInputStream(original) {
                    override fun close() {
                        closures.incrementAndGet()
                        super.close()
                    }
                }
                is Stream<*> -> original.onClose { closures.incrementAndGet() }
                else -> error("Expected a streaming response body")
            }

            // These tests request only InputStream or Stream<String>; wrappers preserve that body type.
            @Suppress("UNCHECKED_CAST")
            val body = observed as T
            onResponseReady()
            return object : HttpResponse<T> {
                override fun statusCode(): Int = status
                override fun request(): HttpRequest = request
                override fun previousResponse(): Optional<HttpResponse<T>> = Optional.empty()
                override fun headers(): HttpHeaders = responseHeaders
                override fun body(): T = body
                override fun sslSession(): Optional<SSLSession> = Optional.empty()
                override fun uri(): URI = request.uri()
                override fun version(): Version = Version.HTTP_1_1
            }
        }

        override fun <T : Any?> sendAsync(request: HttpRequest, handler: HttpResponse.BodyHandler<T>): CompletableFuture<HttpResponse<T>> =
            CompletableFuture.completedFuture(send(request, handler))

        override fun <T : Any?> sendAsync(
            request: HttpRequest,
            handler: HttpResponse.BodyHandler<T>,
            pushPromiseHandler: HttpResponse.PushPromiseHandler<T>,
        ): CompletableFuture<HttpResponse<T>> = sendAsync(request, handler)

        override fun cookieHandler(): Optional<CookieHandler> = Optional.empty()
        override fun connectTimeout(): Optional<Duration> = Optional.empty()
        override fun followRedirects(): Redirect = Redirect.NEVER
        override fun proxy(): Optional<ProxySelector> = Optional.empty()
        override fun sslContext(): SSLContext = SSLContext.getDefault()
        override fun sslParameters(): SSLParameters = SSLParameters()
        override fun authenticator(): Optional<Authenticator> = Optional.empty()
        override fun version(): Version = Version.HTTP_1_1
        override fun executor(): Optional<Executor> = Optional.empty()
    }
}
