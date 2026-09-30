package ai.koog.http.client.java

import ai.koog.http.client.KoogHttpClient
import ai.koog.http.client.KoogHttpClientException
import ai.koog.http.client.mergeHeaders
import ai.koog.utils.io.SuitableForIO
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.atomic.AtomicReference
import kotlin.reflect.KClass
import kotlin.time.Duration.Companion.minutes

/**
 * JavaKoogHttpClient is an implementation of the KoogHttpClient interface, utilizing Java 11's standard HttpClient
 * to perform HTTP operations, including GET, POST requests and Server-Sent Events (SSE) streaming.
 *
 * This client provides enhanced logging, flexible request and response handling, and supports
 * configurability for underlying Java HttpClient instances.
 *
 * SSE dispatches complete UTF-8 data events, preserving multiline payloads and ignoring event metadata.
 * Streaming delivery suspends when collectors are slow. Cancellation interrupts pending requests and
 * closes response bodies, including idle streams.
 *
 * @property clientName The name of the client, used for logging and traceability.
 * @property logger A logging instance of type KLogger for recording client-related events and errors.
 * @property httpClient The configured Java HttpClient instance used for making HTTP requests.
 */
public class JavaKoogHttpClient internal constructor(
    override val clientName: String,
    private val logger: KLogger,
    private val httpClient: HttpClient,
    private val json: Json,
    private val baseUrl: String = "",
    private val headers: Map<String, String> = emptyMap(),
    private val queryParameters: Map<String, String> = emptyMap(),
    private val requestTimeoutMillis: Long = DEFAULT_REQUEST_TIMEOUT_MS
) : KoogHttpClient {

    public companion object {
        public val DEFAULT_REQUEST_TIMEOUT_MS: Long = 15.minutes.inWholeMilliseconds
    }

    private data class RequestBody(
        val body: String,
        val contentType: String
    )

    private fun <R : Any> processResponse(response: HttpResponse<String>, responseType: KClass<R>): R {
        if (response.statusCode() in 200..299) {
            val responseBody = response.body()
            if (responseType == String::class) {
                @Suppress("UNCHECKED_CAST")
                return responseBody as R
            } else {
                val serializer = serializer(responseType.java)
                @Suppress("UNCHECKED_CAST")
                return json.decodeFromString(serializer, responseBody) as R
            }
        }
        throw KoogHttpClientException(
            clientName = clientName,
            statusCode = response.statusCode(),
            errorBody = response.body(),
        )
    }

    /**
     * Appends query parameters to the given URL path.
     *
     * @param path The base URL path to which the query parameters will be added.
     * @param parameters A map of query parameters to be added to the URL. The keys and values will be URL-encoded.
     * @return The URL path with the appended query parameters, or the original `path` if `parameters` is null or empty.
     */
    private fun buildUri(path: String, parameters: Map<String, String>?): URI {
        val fullPath = resolvePath(path)
        val mergedParameters = queryParameters + parameters.orEmpty()
        val fullPathWithParameters = if (mergedParameters.isNotEmpty()) {
            val query = mergedParameters.entries.joinToString("&") { (key, value) ->
                "${URLEncoder.encode(key, "UTF-8")}=${URLEncoder.encode(value, "UTF-8")}"
            }
            "$fullPath?$query"
        } else {
            fullPath
        }

        return URI.create(fullPathWithParameters)
    }

    private fun resolvePath(path: String): String {
        if (URI.create(path).isAbsolute || baseUrl.isBlank()) {
            return path
        }

        return "${baseUrl.trimEnd('/')}/${path.trimStart('/')}"
    }

    private fun HttpRequest.Builder.headers(headers: Map<String, String>): HttpRequest.Builder = apply {
        headers.forEach { (name, value) -> header(name, value) }
    }

    private fun HttpRequest.Builder.defaultTimeout(): HttpRequest.Builder = apply {
        timeout(Duration.ofMillis(requestTimeoutMillis))
    }

    override suspend fun <R : Any> get(
        path: String,
        responseType: KClass<R>,
        parameters: Map<String, String>,
        headers: Map<String, String>
    ): R = withContext(Dispatchers.SuitableForIO) {
        val httpRequest = HttpRequest.newBuilder()
            .uri(buildUri(path, parameters))
            .headers(mergeHeaders(this@JavaKoogHttpClient.headers, headers))
            .defaultTimeout()
            .GET()
            .build()

        val response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString())

        processResponse(response, responseType)
    }

    override suspend fun <T : Any, R : Any> post(
        path: String,
        requestBody: T,
        requestBodyType: KClass<T>,
        responseType: KClass<R>,
        parameters: Map<String, String>,
        headers: Map<String, String>
    ): R = withContext(Dispatchers.SuitableForIO) {
        val preparedRequestBody = prepareRequestBody(requestBody, requestBodyType)

        val httpRequest = HttpRequest.newBuilder()
            .uri(buildUri(path, parameters))
            .headers(
                mergeHeaders(
                    this@JavaKoogHttpClient.headers,
                    mapOf("Content-Type" to preparedRequestBody.contentType),
                    headers,
                )
            )
            .defaultTimeout()
            .POST(HttpRequest.BodyPublishers.ofString(preparedRequestBody.body))
            .build()

        val response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString())

        processResponse(response, responseType)
    }

    override fun <T : Any, R : Any, O : Any> sse(
        path: String,
        requestBody: T,
        requestBodyType: KClass<T>,
        dataFilter: (String?) -> Boolean,
        decodeStreamingResponse: (String) -> R,
        processStreamingChunk: (R) -> O?,
        parameters: Map<String, String>,
        headers: Map<String, String>
    ): Flow<O> = callbackFlow {
        val preparedRequestBody = prepareRequestBody(requestBody, requestBodyType)

        val httpRequest = HttpRequest.newBuilder()
            .uri(buildUri(path, parameters))
            .headers(
                mergeHeaders(
                    this@JavaKoogHttpClient.headers,
                    mapOf(
                        "Content-Type" to preparedRequestBody.contentType,
                        "Accept" to "text/event-stream",
                        "Cache-Control" to "no-cache",
                    ),
                    headers,
                )
            )
            .defaultTimeout()
            .POST(HttpRequest.BodyPublishers.ofString(preparedRequestBody.body))
            // Note: "Connection" header is restricted in Java HttpClient and managed automatically
            .build()

        readStreamingResponse(httpRequest, HttpResponse.BodyHandlers.ofInputStream()) { body ->
            val reader = body.bufferedReader(Charsets.UTF_8)
            logger.debug { "SSE connection opened for $clientName" }
            val data = StringBuilder()
            var firstLine = true
            while (isActive) {
                val rawLine = reader.readLine() ?: break
                val line = if (firstLine) rawLine.removePrefix("\uFEFF") else rawLine
                firstLine = false
                if (line.isEmpty()) {
                    if (data.isNotEmpty()) {
                        val payload = data.dropLast(1).toString()
                        data.setLength(0)
                        if (dataFilter(payload)) {
                            processStreamingChunk(decodeStreamingResponse(payload))?.let { send(it) }
                        }
                    }
                } else if (!line.startsWith(":")) {
                    val colon = line.indexOf(':')
                    val field = if (colon < 0) line else line.substring(0, colon)
                    if (field == "data") {
                        val value = if (colon < 0) "" else line.substring(colon + 1).removePrefix(" ")
                        data.append(value).append('\n')
                    }
                }
            }
            // Pending data without a terminating empty line belongs to an incomplete event.
            logger.debug { "SSE connection closed for $clientName" }
        }
    }

    override fun <T : Any> lines(
        path: String,
        requestBody: T,
        requestBodyType: KClass<T>,
        parameters: Map<String, String>,
        headers: Map<String, String>
    ): Flow<String> = callbackFlow {
        val preparedRequestBody = prepareRequestBody(requestBody, requestBodyType)

        val httpRequest = HttpRequest.newBuilder()
            .uri(buildUri(path, parameters))
            .headers(
                mergeHeaders(
                    this@JavaKoogHttpClient.headers,
                    mapOf("Content-Type" to preparedRequestBody.contentType),
                    headers,
                )
            )
            .defaultTimeout()
            .POST(HttpRequest.BodyPublishers.ofString(preparedRequestBody.body))
            .build()

        readStreamingResponse(httpRequest, HttpResponse.BodyHandlers.ofLines()) { lines ->
            logger.debug { "Lines flow opened for $clientName" }
            val iterator = lines.iterator()
            while (isActive && iterator.hasNext()) {
                val line = iterator.next()
                if (line.isBlank()) continue
                send(line)
            }
            logger.debug { "Lines flow closed for $clientName" }
        }
    }

    private suspend fun <O : Any, B : AutoCloseable> ProducerScope<O>.readStreamingResponse(
        request: HttpRequest,
        bodyHandler: HttpResponse.BodyHandler<B>,
        process: suspend ProducerScope<O>.(B) -> Unit,
    ) {
        val body = AtomicReference<B?>()
        val readerJob = launch(Dispatchers.SuitableForIO) {
            try {
                // Keep ownership if cancellation races with receiving the response headers.
                val response = runInterruptible {
                    httpClient.send(request, bodyHandler).also {
                        body.set(it.body())
                    }
                }
                if (response.statusCode() !in 200..299) {
                    throw KoogHttpClientException(
                        clientName = clientName,
                        statusCode = response.statusCode(),
                    )
                }
                process(response.body())
                close()
            } catch (e: CancellationException) {
                // Callback cancellation must also terminate the producer waiting in awaitClose.
                this@readStreamingResponse.cancel(e)
                throw e
            } catch (e: KoogHttpClientException) {
                coroutineContext.ensureActive()
                close(e)
            } catch (e: Exception) {
                coroutineContext.ensureActive()
                close(
                    KoogHttpClientException(
                        clientName = clientName,
                        message = "Exception during streaming: ${e.message}",
                        cause = e,
                    )
                )
            } finally {
                body.getAndSet(null)?.close()
            }
        }
        awaitClose {
            readerJob.cancel()
            // JDK body reads can ignore interruption, so closing the body releases an idle reader.
            body.getAndSet(null)?.close()
        }
    }

    /**
     * Common logic of preparing the request body.
     */
    private fun <T : Any> prepareRequestBody(
        requestBody: T,
        requestBodyType: KClass<T>,
    ): RequestBody {
        return if (requestBodyType == String::class) {
            @Suppress("UNCHECKED_CAST")
            RequestBody(body = requestBody as String, contentType = "text/plain")
        } else {
            val serializer = serializer(requestBodyType.java)
            RequestBody(body = json.encodeToString(serializer, requestBody), contentType = "application/json")
        }
    }

    override fun close() {}

    /**
     * [ai.koog.http.client.KoogHttpClient.Factory] implementation backed by the JDK
     * [java.net.http.HttpClient].
     *
     * @property logger Logger used by created clients.
     */
    public class Factory @JvmOverloads public constructor(
        private val logger: KLogger = KotlinLogging.logger {}
    ) : KoogHttpClient.Factory {
        override fun create(
            clientName: String,
            baseUrl: String,
            headers: Map<String, String>,
            queryParameters: Map<String, String>,
            requestTimeoutMillis: Long,
            connectTimeoutMillis: Long,
            socketTimeoutMillis: Long,
            json: Json
        ): JavaKoogHttpClient {
            val configuredClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMillis))
                .build()

            return JavaKoogHttpClient(
                clientName = clientName,
                logger = logger,
                httpClient = configuredClient,
                json = json,
                baseUrl = baseUrl,
                headers = headers,
                queryParameters = queryParameters,
                requestTimeoutMillis = requestTimeoutMillis
            )
        }
    }
}

/**
 * Creates a new instance of `KoogHttpClient` wrapping the given Java [HttpClient].
 *
 * Use this function when you have a pre-configured [HttpClient] instance and want to wrap it
 * in a [KoogHttpClient]. For standard use cases where the client should be built from
 * configuration, prefer [JavaKoogHttpClient.Factory] instead.
 *
 * @param clientName The name of the client instance, used for identifying or logging client operations.
 * @param logger A `KLogger` instance used for logging client events and errors.
 * @param httpClient The Java HttpClient instance to be used. Defaults to a new HttpClient instance.
 * @param json The Json instance used for serialization/deserialization. Defaults to a default Json instance.
 * @return An instance of `KoogHttpClient` configured with the provided parameters.
 */
public fun KoogHttpClient.Companion.fromJavaHttpClient(
    clientName: String,
    logger: KLogger,
    httpClient: HttpClient = HttpClient.newHttpClient(),
    json: Json = Json
): KoogHttpClient = JavaKoogHttpClient(clientName, logger, httpClient, json)
