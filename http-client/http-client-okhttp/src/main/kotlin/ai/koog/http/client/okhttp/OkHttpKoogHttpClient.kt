package ai.koog.http.client.okhttp

import ai.koog.http.client.KoogHttpClient
import ai.koog.http.client.KoogHttpClientException
import ai.koog.http.client.mergeHeaders
import ai.koog.utils.io.SuitableForIO
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import okhttp3.Headers.Companion.toHeaders
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import org.jetbrains.annotations.ApiStatus.Experimental
import java.util.concurrent.TimeUnit
import kotlin.reflect.KClass

/**
 * OkHttpKoogHttpClient is an implementation of the KoogHttpClient interface, utilizing OkHttp's client
 * to perform HTTP operations, including GET, POST requests and Server-Sent Events (SSE) streaming.
 *
 * This client provides enhanced logging, flexible request and response handling, and supports
 * configurability for underlying OkHttp client instances.
 *
 * @property clientName The name of the client, used for logging and traceability.
 * @property logger A logging instance of type KLogger for recording client-related events and errors.
 * @property okHttpClient The configured OkHttp client instance used for making HTTP requests.
 */
@Experimental
public class OkHttpKoogHttpClient internal constructor(
    override val clientName: String,
    private val logger: KLogger,
    private val okHttpClient: OkHttpClient,
    private val json: Json,
    private val baseUrl: String = "",
    headers: Map<String, String> = emptyMap(),
    private val queryParameters: Map<String, String> = emptyMap(),
) : KoogHttpClient {

    private val defaultHeaders: Map<String, String> = headers

    private fun Map<String, String>.headerValue(name: String): String? =
        entries.firstOrNull { (key, _) -> key.equals(name, ignoreCase = true) }?.value

    private fun <R : Any> processResponse(response: Response, responseType: KClass<R>): R {
        if (response.isSuccessful) {
            val responseBody = response.body.string()
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
            statusCode = response.code,
            errorBody = response.body.string(),
        )
    }

    /**
     * Builds a complete URL with the specified base path and optional query parameters.
     *
     * @param path The base path to which the URL will be built. It must be a valid URL string.
     * @param parameters A map containing query parameter key-value pairs to be appended to the URL.
     * @return An [HttpUrl] object representing the constructed URL with any specified query parameters.
     */
    private fun buildUrl(path: String, parameters: Map<String, String>?): HttpUrl {
        return resolvePath(path).toHttpUrl().newBuilder().apply {
            (queryParameters + parameters.orEmpty()).forEach { (key, value) ->
                addQueryParameter(key, value)
            }
        }.build()
    }

    private fun resolvePath(path: String): String {
        if (path.startsWith("http://") || path.startsWith("https://") || baseUrl.isBlank()) {
            return path
        }

        return "${baseUrl.trimEnd('/')}/${path.trimStart('/')}"
    }

    override suspend fun <R : Any> get(
        path: String,
        responseType: KClass<R>,
        parameters: Map<String, String>,
        headers: Map<String, String>
    ): R = withContext(Dispatchers.SuitableForIO) {
        val httpRequest = Request.Builder()
            .url(buildUrl(path, parameters))
            .headers(mergeHeaders(defaultHeaders, headers).toHeaders())
            .get()
            .build()

        val response: Response = okHttpClient.newCall(httpRequest).execute()
        response.use {
            processResponse(response, responseType)
        }
    }

    override suspend fun <T : Any, R : Any> post(
        path: String,
        request: T,
        requestBodyType: KClass<T>,
        responseType: KClass<R>,
        parameters: Map<String, String>,
        headers: Map<String, String>
    ): R = withContext(Dispatchers.SuitableForIO) {
        val requestBody = prepareRequestBody(request, requestBodyType, headers.headerValue("Content-Type"))

        val httpRequest = Request.Builder()
            .url(buildUrl(path, parameters))
            .headers(
                mergeHeaders(
                    defaultHeaders,
                    mapOf("Content-Type" to requestBody.contentType().toString()),
                    headers,
                ).toHeaders()
            )
            .post(requestBody)
            .build()

        val response: Response = okHttpClient.newCall(httpRequest).execute()

        response.use {
            processResponse(response, responseType)
        }
    }

    override fun <T : Any, R : Any, O : Any> sse(
        path: String,
        request: T,
        requestBodyType: KClass<T>,
        dataFilter: (String?) -> Boolean,
        decodeStreamingResponse: (String) -> R,
        processStreamingChunk: (R) -> O?,
        parameters: Map<String, String>,
        headers: Map<String, String>
    ): Flow<O> = callbackFlow {
        val requestBody = prepareRequestBody(request, requestBodyType, headers.headerValue("Content-Type"))

        val httpRequest = Request.Builder()
            .url(buildUrl(path, parameters))
            .headers(
                mergeHeaders(
                    defaultHeaders,
                    mapOf(
                        "Content-Type" to requestBody.contentType().toString(),
                        "Accept" to "text/event-stream",
                        "Cache-Control" to "no-cache",
                        "Connection" to "keep-alive",
                    ),
                    headers,
                )
                    .toHeaders()
            )
            .post(requestBody)
            .build()

        val eventSourceListener = object : EventSourceListener() {
            override fun onOpen(eventSource: EventSource, response: Response) {
                logger.debug { "SSE connection opened for $clientName" }
            }

            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                try {
                    if (dataFilter(data)) {
                        data.trim()
                            .let(decodeStreamingResponse)
                            .let(processStreamingChunk)
                            ?.let { trySend(it) }
                    }
                } catch (e: Exception) {
                    val exception = KoogHttpClientException(
                        clientName = clientName,
                        message = "Error processing SSE event: ${e.message}"
                    )
                    logger.error(exception) { exception.message }
                    close(exception)
                }
            }

            override fun onClosed(eventSource: EventSource) {
                logger.debug { "SSE connection closed for $clientName" }
                close()
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                val exception = KoogHttpClientException(
                    clientName = clientName,
                    statusCode = response?.code,
                    errorBody = response?.body?.string(),
                    message = t?.message,
                    cause = t
                )
                logger.error(exception) { exception.message }
                close(exception)
            }
        }

        val eventSource = EventSources.createFactory(okHttpClient)
            .newEventSource(httpRequest, eventSourceListener)

        awaitClose {
            eventSource.cancel()
        }
    }

    override fun <T : Any> lines(
        path: String,
        request: T,
        requestBodyType: KClass<T>,
        parameters: Map<String, String>,
        headers: Map<String, String>
    ): Flow<String> = callbackFlow {
        val requestBody = prepareRequestBody(request, requestBodyType, headers.headerValue("Content-Type"))

        val httpRequest = Request.Builder()
            .url(buildUrl(path, parameters))
            .headers(
                mergeHeaders(
                    defaultHeaders,
                    mapOf("Content-Type" to requestBody.contentType().toString()),
                    headers,
                ).toHeaders()
            )
            .post(requestBody)
            .build()

        val call = okHttpClient.newCall(httpRequest)

        val readerJob = launch(Dispatchers.SuitableForIO) {
            try {
                val response: Response = call.execute()

                if (!response.isSuccessful) {
                    val errorBody = response.body.string()
                    response.close()
                    close(
                        KoogHttpClientException(
                            clientName = clientName,
                            statusCode = response.code,
                            errorBody = errorBody,
                        )
                    )
                    return@launch
                }

                logger.debug { "Lines flow opened for $clientName" }
                response.use {
                    val source = response.body.source()
                    while (!source.exhausted()) {
                        val line = source.readUtf8Line() ?: break
                        if (line.isBlank()) continue
                        if (trySend(line).isClosed) {
                            call.cancel()
                            return@launch
                        }
                    }
                }
                logger.debug { "Lines flow closed for $clientName" }
                close()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                close(
                    KoogHttpClientException(
                        clientName = clientName,
                        message = "Exception during streaming: ${e.message}",
                        cause = e
                    )
                )
            }
        }

        awaitClose {
            call.cancel()
            readerJob.cancel()
        }
    }

    /**
     * Common logic of preparing the request body.
     */
    private fun <T : Any> prepareRequestBody(
        request: T,
        requestBodyType: KClass<T>,
        contentType: String? = null,
    ): RequestBody {
        return if (requestBodyType == String::class) {
            @Suppress("UNCHECKED_CAST")
            (request as String).toRequestBody((contentType ?: "text/plain").toMediaType())
        } else {
            val serializer = serializer(requestBodyType.java)
            val jsonString = json.encodeToString(serializer, request)
            jsonString.toRequestBody((contentType ?: "application/json").toMediaType())
        }
    }

    override fun close() {
        logger.debug { "Closing $clientName" }
        okHttpClient.dispatcher.executorService.shutdown()
    }

    /**
     * [ai.koog.http.client.KoogHttpClient.Factory] implementation backed by OkHttp [okhttp3.OkHttpClient].
     *
     * @property logger Logger used by created clients.
     */
    public class Factory(
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
        ): OkHttpKoogHttpClient {
            val configuredClient = OkHttpClient.Builder()
                .callTimeout(requestTimeoutMillis, TimeUnit.MILLISECONDS)
                .connectTimeout(connectTimeoutMillis, TimeUnit.MILLISECONDS)
                .readTimeout(socketTimeoutMillis, TimeUnit.MILLISECONDS)
                .writeTimeout(socketTimeoutMillis, TimeUnit.MILLISECONDS)
                .build()

            return OkHttpKoogHttpClient(
                clientName = clientName,
                logger = logger,
                okHttpClient = configuredClient,
                json = json,
                baseUrl = baseUrl,
                headers = headers,
                queryParameters = queryParameters
            )
        }
    }
}

/**
 * Creates a new instance of `KoogHttpClient` wrapping the given [OkHttpClient].
 *
 * Use this function when you have a pre-configured [OkHttpClient] instance and want to wrap it
 * in a [KoogHttpClient]. For standard use cases where the client should be built from
 * configuration, prefer [OkHttpKoogHttpClient.Factory] instead.
 *
 * @param clientName The name of the client instance, used for identifying or logging client operations.
 * @param logger A `KLogger` instance used for logging client events and errors.
 * @param okHttpClient The OkHttp client instance to be used. Defaults to a new OkHttpClient instance.
 * @param json The Json instance used for serialization/deserialization. Defaults to a default Json instance.
 * @return An instance of `KoogHttpClient` configured with the provided parameters.
 */
@Experimental
public fun KoogHttpClient.Companion.fromOkHttpClient(
    clientName: String,
    logger: KLogger,
    okHttpClient: OkHttpClient = OkHttpClient(),
    json: Json = Json
): KoogHttpClient = OkHttpKoogHttpClient(clientName, logger, okHttpClient, json)
