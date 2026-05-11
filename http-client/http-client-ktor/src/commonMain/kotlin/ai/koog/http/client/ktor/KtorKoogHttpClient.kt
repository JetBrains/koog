package ai.koog.http.client.ktor

import ai.koog.http.client.KoogHttpClient
import ai.koog.http.client.KoogHttpClientException
import ai.koog.http.client.mergeHeaders
import ai.koog.utils.io.SuitableForIO
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.plugins.sse.SSEClientException
import io.ktor.client.plugins.sse.sse
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.URLBuilder
import io.ktor.http.contentType
import io.ktor.http.encodedPath
import io.ktor.http.headers
import io.ktor.http.isSuccess
import io.ktor.http.takeFrom
import io.ktor.serialization.kotlinx.json.json
import io.ktor.util.reflect.TypeInfo
import io.ktor.utils.io.CancellationException
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.jetbrains.annotations.ApiStatus.Experimental
import kotlin.jvm.JvmOverloads
import kotlin.reflect.KClass

/**
 * KtorHttpClient is an implementation of the KoogHttpClient interface, utilizing Ktor's HttpClient
 * to perform HTTP operations, including GET, POST requests and Server-Sent Events (SSE) streaming.
 *
 * This client provides enhanced logging, flexible request and response handling, and supports
 * configurability for underlying Ktor HttpClient instances.
 *
 * @constructor Creates a KtorHttpClient instance with an optional base Ktor HttpClient and configuration block.

 * @property clientName The name of the client, used for logging and traceability.
 * @property logger A logging instance of type KLogger for recording client-related events and errors.
 * @property ktorClient The configured Ktor HttpClient instance used for making HTTP requests.
 * The configuration is applied using the Ktor `HttpClient.config` method.
 */
@Experimental
public class KtorKoogHttpClient internal constructor(
    override val clientName: String,
    private val logger: KLogger,
    public val ktorClient: HttpClient
) : KoogHttpClient {

    /**
     * Secondary constructor for creating a KtorKoogHttpClient with a base Ktor HttpClient and a configurer function.
     *
     * @param clientName The name of the client, used for logging and traceability.
     * @param logger A logging instance of type KLogger for recording client-related events and errors.
     * @param baseClient The base Ktor HttpClient instance used as base to construct [ktorClient] via applying [configurer]
     * @param configurer A lambda function to configure the base Ktor HttpClient instance.
     */
    public constructor(
        clientName: String,
        logger: KLogger,
        baseClient: HttpClient = HttpClient(),
        configurer: HttpClientConfig<out HttpClientEngineConfig>.() -> Unit
    ) : this(clientName, logger, baseClient.config(configurer))

    private suspend fun <R : Any> processResponse(response: HttpResponse, responseType: KClass<R>): R {
        if (response.status.isSuccess()) {
            if (responseType == String::class) {
                @Suppress("UNCHECKED_CAST")
                return response.bodyAsText() as R
            } else {
                return response.body(TypeInfo(responseType))
            }
        }
        throw KoogHttpClientException(
            clientName = clientName,
            statusCode = response.status.value,
            errorBody = response.bodyAsText(),
        )
    }

    private fun HttpRequestBuilder.applyRequestHeaders(headers: Map<String, String>) {
        headers.forEach { (name, value) ->
            if (name.equals(HttpHeaders.ContentType, ignoreCase = true)) {
                contentType(ContentType.parse(value))
            } else {
                headers {
                    remove(name)
                    append(name, value)
                }
            }
        }
    }

    override suspend fun <R : Any> get(
        path: String,
        responseType: KClass<R>,
        parameters: Map<String, String>,
        headers: Map<String, String>
    ): R = withContext(Dispatchers.SuitableForIO) {
        val response = ktorClient.get(path) {
            parameters.forEach { (key, value) ->
                parameter(key, value)
            }
            applyRequestHeaders(headers)
        }
        processResponse(response, responseType)
    }

    override suspend fun <T : Any, R : Any> post(
        path: String,
        request: T,
        requestBodyType: KClass<T>,
        responseType: KClass<R>,
        parameters: Map<String, String>,
        headers: Map<String, String>
    ): R = withContext(Dispatchers.SuitableForIO) {
        val response = ktorClient.post(path) {
            if (requestBodyType == String::class) {
                @Suppress("UNCHECKED_CAST")
                setBody(request as String)
            } else {
                setBody(request, TypeInfo(requestBodyType))
            }
            parameters.forEach { (key, value) ->
                parameter(key, value)
            }
            applyRequestHeaders(headers)
        }

        processResponse(response, responseType)
    }

    override fun <T : Any, R : Any, O : Any> sse(
        path: String,
        request: T,
        requestBodyType: KClass<T>,
        dataFilter: (String?) -> Boolean,
        decodeStreamingResponse: (String) -> R,
        processStreamingChunk: (R) -> O?,
        parameters: Map<String, String>,
        headers: Map<String, String>,
    ): Flow<O> = flow {
        logger.debug { "Opening sse connection for $clientName" }

        @Suppress("TooGenericExceptionCaught")
        try {
            ktorClient.sse(
                urlString = path,
                request = {
                    method = HttpMethod.Post
                    parameters.forEach { (key, value) ->
                        parameter(key, value)
                    }
                    applyRequestHeaders(
                        mergeHeaders(
                            mapOf(
                                HttpHeaders.Accept to ContentType.Text.EventStream.toString(),
                                HttpHeaders.CacheControl to "no-cache",
                                HttpHeaders.Connection to "keep-alive",
                            ),
                            headers,
                        )
                    )
                    if (requestBodyType == String::class) {
                        @Suppress("UNCHECKED_CAST")
                        setBody(request as String)
                    } else {
                        setBody(request, TypeInfo(requestBodyType))
                    }
                }
            ) {
                incoming.collect { event ->
                    event
                        .takeIf { dataFilter.invoke(it.data) }
                        ?.data?.trim()
                        ?.let(decodeStreamingResponse)
                        ?.let(processStreamingChunk)
                        ?.let { emit(it) }
                }
            }
        } catch (e: SSEClientException) {
            val errorBody = try {
                e.response?.bodyAsText()
            } catch (ignored: Exception) {
                logger.debug(ignored) { "Unable to read SSE error response body (may already be consumed)" }
                null
            }
            throw KoogHttpClientException(
                clientName = clientName,
                statusCode = e.response?.status?.value,
                errorBody = errorBody,
                message = e.message,
                cause = e
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw KoogHttpClientException(
                clientName = clientName,
                message = "Exception during streaming: ${e.message}",
                cause = e,
            )
        }
    }

    override fun <T : Any> lines(
        path: String,
        request: T,
        requestBodyType: KClass<T>,
        parameters: Map<String, String>,
        headers: Map<String, String>,
    ): Flow<String> = flow {
        logger.debug { "Opening lines flow for $clientName" }

        try {
            ktorClient.preparePost(path) {
                parameters.forEach { (key, value) ->
                    parameter(key, value)
                }
                applyRequestHeaders(headers)
                if (requestBodyType == String::class) {
                    @Suppress("UNCHECKED_CAST")
                    setBody(request as String)
                } else {
                    setBody(request, TypeInfo(requestBodyType))
                }
            }.execute { response: HttpResponse ->
                if (!response.status.isSuccess()) {
                    throw KoogHttpClientException(
                        clientName = clientName,
                        statusCode = response.status.value,
                        errorBody = response.bodyAsText(),
                    )
                }

                val channel = response.bodyAsChannel()
                while (!channel.isClosedForRead) {
                    val line = channel.readUTF8Line() ?: break
                    if (line.isBlank()) continue
                    emit(line)
                }
            }
        } catch (e: KoogHttpClientException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw KoogHttpClientException(
                clientName = clientName,
                message = "Exception during streaming: ${e.message}",
                cause = e,
            )
        }
    }

    override fun close() {
        logger.debug { "Closing $clientName" }
        ktorClient.close()
    }

    /**
     * [KoogHttpClient.Factory] implementation backed by Ktor [HttpClient].
     *
     * @property baseClient Base Ktor client used to create configured clients.
     * @property withSse Whether created clients should install Ktor SSE support.
     * @property logger Logger used by created clients.
     */
    public class Factory(
        private val baseClient: HttpClient = HttpClient(),
        private val withSse: Boolean = true,
        private val logger: KLogger = KotlinLogging.logger {}
    ) : KoogHttpClient.Factory {

        @JvmOverloads
        override fun create(
            clientName: String,
            baseUrl: String,
            headers: Map<String, String>,
            queryParameters: Map<String, String>,
            requestTimeoutMillis: Long,
            connectTimeoutMillis: Long,
            socketTimeoutMillis: Long,
            json: Json
        ): KtorKoogHttpClient = KtorKoogHttpClient(
            clientName = clientName,
            logger = logger,
            baseClient = baseClient
        ) {
            val normalizedBaseUrl = URLBuilder(urlString = baseUrl).apply {
                if (!encodedPath.endsWith("/")) {
                    encodedPath += "/"
                }
            }.buildString()

            defaultRequest {
                url.takeFrom(normalizedBaseUrl)
                contentType(ContentType.Application.Json)
                headers.forEach { (name, value) -> header(name, value) }
                queryParameters.forEach { (name, value) -> url.parameters.append(name, value) }
            }

            if (withSse) {
                this.install(SSE)
            }

            this.install(ContentNegotiation) {
                json(json = json)
            }

            this.install(HttpTimeout) {
                this.requestTimeoutMillis = requestTimeoutMillis
                this.connectTimeoutMillis = connectTimeoutMillis
                this.socketTimeoutMillis = socketTimeoutMillis
            }
        }
    }
}

/**
 * Creates a new instance of `KoogHttpClient` using a Ktor-based HTTP client for performing HTTP operations.
 *
 * This function allows configuring the underlying Ktor `HttpClient` through the provided configuration lambda
 * and enables enhanced logging, flexibility, and customization in HTTP interactions.
 *
 * @param clientName The name of the client instance, used for identifying or logging client operations.
 * @param logger A `KLogger` instance used for logging client events and errors.
 * @param baseClient The base Ktor `HttpClient` instance to be used. Defaults to a new Ktor `HttpClient` instance.
 * @param configurer A lambda function to configure the base Ktor `HttpClient` instance. It is applied using
 * Ktor’s `HttpClientConfig`.
 * @return An instance of `KoogHttpClient` configured with the provided parameters.
 */
@Experimental
@JvmOverloads
public fun KoogHttpClient.Companion.fromKtorClient(
    clientName: String,
    logger: KLogger,
    baseClient: HttpClient = HttpClient(),
    configurer: HttpClientConfig<out HttpClientEngineConfig>.() -> Unit = {}
): KoogHttpClient = KtorKoogHttpClient(clientName, logger, baseClient, configurer)

/**
 * Creates an instance of `KoogHttpClient` using Ktor's `HttpClient` and additional configuration options.
 *
 * This method combines a base `HttpClient` with predefined configurations for request handling,
 * such as timeouts, headers, query parameters, content type, and optional Server-Sent Events (SSE) support.
 *
 * @param clientName The name assigned to the client instance, used for logging and traceability purposes.
 * @param logger A `KLogger` instance for logging client operations and errors.
 * @param baseClient The base Ktor `HttpClient` instance to be used. Defaults to a new `HttpClient` instance.
 * @param baseUrl The base URL for all HTTP requests made through this client.
 * @param requestTimeoutMillis The timeout in milliseconds for HTTP requests.
 * @param connectTimeoutMillis The timeout in milliseconds for establishing a connection.
 * @param socketTimeoutMillis The timeout in milliseconds for socket operations.
 * @param json A `Json` instance used for serializing request bodies and deserializing responses.
 * @param headers A map of default HTTP headers to include in every request. Defaults to an empty map.
 * @param queryParameters A map of default query parameters to include in every request. Defaults to an empty map.
 * @param withSse A flag indicating whether the client should support Server-Sent Events (SSE). Defaults to `true`.
 * @return A `KoogHttpClient` instance configured with the specified parameters and options.
 */
@Deprecated(
    "Use KtorKoogHttpClient.Factory instead",
    ReplaceWith("KtorKoogHttpClient.Factory(baseClient, withSse).create(clientName, baseUrl, headers, queryParameters, requestTimeoutMillis, connectTimeoutMillis, socketTimeoutMillis, json)")
)
@Experimental
@JvmOverloads
public fun KoogHttpClient.Companion.fromKtorClient(
    clientName: String,
    logger: KLogger,
    baseClient: HttpClient = HttpClient(),
    baseUrl: String,
    requestTimeoutMillis: Long = KoogHttpClient.Factory.DEFAULT_REQUEST_TIMEOUT_MS,
    connectTimeoutMillis: Long = KoogHttpClient.Factory.DEFAULT_CONNECT_TIMEOUT_MS,
    socketTimeoutMillis: Long = KoogHttpClient.Factory.DEFAULT_SOCKET_TIMEOUT_MS,
    json: Json,
    headers: Map<String, String> = emptyMap(),
    queryParameters: Map<String, String> = emptyMap(),
    withSse: Boolean = true,
): KtorKoogHttpClient =
    KtorKoogHttpClient.Factory(baseClient, withSse, logger).create(
        clientName,
        baseUrl,
        headers,
        queryParameters,
        requestTimeoutMillis,
        connectTimeoutMillis,
        socketTimeoutMillis,
        json
    )
