package ai.koog.http.client.ktor

import ai.koog.http.client.KoogHttpClient
import ai.koog.utils.io.SuitableForIO
import io.github.oshai.kotlinlogging.KLogger
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.plugins.sse.SSEClientException
import io.ktor.client.plugins.sse.sse
import io.ktor.client.request.accept
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.isSuccess
import io.ktor.util.reflect.TypeInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus.Experimental
import kotlin.jvm.JvmOverloads
import kotlin.reflect.KClass

/**
 * KtorHttpClient is an implementation of the KoogHttpClient interface, utilizing Ktor's HttpClient
 * to perform HTTP operations, including POST requests and Server-Sent Events (SSE) streaming.
 *
 * This client provides enhanced logging, flexible request and response handling, and supports
 * configurability for underlying Ktor HttpClient instances.
 *
 * @property clientName The name of the client, used for logging and traceability.
 * @property logger A logging instance of type KLogger for recording client-related events and errors.
 * @constructor Creates a KtorHttpClient instance with an optional base Ktor HttpClient and configuration block.
 *
 * @param baseClient The base Ktor HttpClient instance to be used. Default is a newly created instance.
 * @param configurer A lambda function to configure the base Ktor HttpClient instance.
 * The configuration is applied using the Ktor `HttpClient.config` method.
 */
@Experimental
public class KtorKoogHttpClient internal constructor(
    private val clientName: String,
    private val logger: KLogger,
    baseClient: HttpClient = HttpClient(),
    configurer: HttpClientConfig<out HttpClientEngineConfig>.() -> Unit
) : KoogHttpClient {

    /**
     * A configured instance of the Ktor HTTP client used for making HTTP requests.
     *
     * This property is initialized with a base client configuration, extended using a custom
     * `configurer` function to adapt to specific requirements or settings.
     *
     * It is designed to interact with various endpoints to perform HTTP operations such as
     * POST requests and Server-Sent Events (SSE) streaming, supporting request and response
     * serialization and deserialization for different data types.
     */
    public val ktorClient: HttpClient = baseClient.config(configurer)

    override suspend fun <T : Any, R : Any> post(
        path: String,
        request: T,
        requestBodyType: KClass<T>,
        responseType: KClass<R>
    ): R = withContext(Dispatchers.SuitableForIO) {
        val response = ktorClient.post(path) {
            if (requestBodyType == String::class) {
                @Suppress("UNCHECKED_CAST")
                setBody(request as String)
            } else {
                setBody(request, TypeInfo(requestBodyType))
            }
        }

        if (response.status.isSuccess()) {
            if (responseType == String::class) {
                @Suppress("UNCHECKED_CAST")
                response.bodyAsText() as R
            } else {
                response.body(TypeInfo(responseType))
            }
        } else {
            val errorBody = response.bodyAsText()
            val errorMessage = "Error from $clientName API: ${response.status}\nBody:\n$errorBody"

            logger.error { errorMessage }
            error(errorMessage)
        }
    }

    override fun <T : Any, R : Any, O : Any> sse(
        path: String,
        request: T,
        requestBodyType: KClass<T>,
        dataFilter: (String?) -> Boolean,
        decodeStreamingResponse: (String) -> R,
        processStreamingChunk: (R) -> O?
    ): Flow<O> = flow {
        @Suppress("TooGenericExceptionCaught")
        try {
            ktorClient.sse(
                urlString = path,
                request = {
                    method = HttpMethod.Post
                    accept(ContentType.Text.EventStream)
                    headers {
                        append(HttpHeaders.CacheControl, "no-cache")
                        append(HttpHeaders.Connection, "keep-alive")
                    }
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
            e.response?.let { response ->
                val body = response.readRawBytes().decodeToString()
                val errorMessage = "Error from $clientName API: ${response.status}: ${e.message}\nBody:\n$body"

                logger.error(e) { errorMessage }
                error(errorMessage)
            }
        } catch (e: Exception) {
            logger.error { "Exception during streaming from $clientName: $e" }
            error(e.message ?: "Unknown error during streaming from $clientName: $e")
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
    configurer: HttpClientConfig<out HttpClientEngineConfig>.() -> Unit
): KoogHttpClient = KtorKoogHttpClient(clientName, logger, baseClient, configurer)
