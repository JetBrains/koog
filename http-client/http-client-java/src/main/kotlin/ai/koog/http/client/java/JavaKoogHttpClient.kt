package ai.koog.http.client.java

import ai.koog.http.client.KoogHttpClient
import ai.koog.utils.io.SuitableForIO
import io.github.oshai.kotlinlogging.KLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import org.jetbrains.annotations.ApiStatus.Experimental
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.reflect.KClass

/**
 * JavaKoogHttpClient is an implementation of the KoogHttpClient interface, utilizing Java 11's standard HttpClient
 * to perform HTTP operations, including GET, POST requests and Server-Sent Events (SSE) streaming.
 *
 * This client provides enhanced logging, flexible request and response handling, and supports
 * configurability for underlying Java HttpClient instances.
 *
 * @property clientName The name of the client, used for logging and traceability.
 * @property logger A logging instance of type KLogger for recording client-related events and errors.
 * @property httpClient The configured Java HttpClient instance used for making HTTP requests.
 */
@Experimental
public class JavaKoogHttpClient internal constructor(
    private val clientName: String,
    private val logger: KLogger,
    private val httpClient: HttpClient,
    private val json: Json
) : KoogHttpClient {
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
        val errorBody = response.body()
        val errorMessage = "Error from $clientName API: ${response.statusCode()}\nBody:\n$errorBody"

        logger.error { errorMessage }
        error(errorMessage)
    }

    override suspend fun <R : Any> get(
        path: String,
        responseType: KClass<R>
    ): R = withContext(Dispatchers.SuitableForIO) {
        val httpRequest = HttpRequest.newBuilder()
            .uri(URI.create(path))
            .GET()
            .build()

        val response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString())

        processResponse(response, responseType)
    }

    override suspend fun <T : Any, R : Any> post(
        path: String,
        request: T,
        requestBodyType: KClass<T>,
        responseType: KClass<R>
    ): R = withContext(Dispatchers.SuitableForIO) {
        val requestBody = prepareRequestBody(request, requestBodyType)

        val httpRequest = HttpRequest.newBuilder()
            .uri(URI.create(path))
            .POST(HttpRequest.BodyPublishers.ofString(requestBody.body))
            .header("Content-Type", requestBody.contentType)
            .build()

        val response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString())

        processResponse(response, responseType)
    }

    override fun <T : Any, R : Any, O : Any> sse(
        path: String,
        request: T,
        requestBodyType: KClass<T>,
        dataFilter: (String?) -> Boolean,
        decodeStreamingResponse: (String) -> R,
        processStreamingChunk: (R) -> O?
    ): Flow<O> = callbackFlow {
        val requestBody = prepareRequestBody(request, requestBodyType)

        val httpRequest = HttpRequest.newBuilder()
            .uri(URI.create(path))
            .POST(HttpRequest.BodyPublishers.ofString(requestBody.body))
            .header("Content-Type", requestBody.contentType)
            .header("Accept", "text/event-stream")
            .header("Cache-Control", "no-cache")
            // Note: "Connection" header is restricted in Java HttpClient and managed automatically
            .build()

        try {
            val response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofLines())

            if (response.statusCode() !in 200..299) {
                val errorMessage = "Error from $clientName API: ${response.statusCode()}"
                logger.error { errorMessage }
                close(IllegalStateException(errorMessage))
                return@callbackFlow
            }

            logger.debug { "SSE connection opened for $clientName" }

            // Process the stream of lines
            response.body().forEach { line ->
                try {
                    val dataPrefix = "data: "
                    // SSE format: "data: <content>"
                    val data = if (line.startsWith(dataPrefix)) {
                        line.substring(dataPrefix.length)
                    } else if (line.isNotEmpty() && !line.startsWith(":")) {
                        line
                    } else {
                        null
                    }

                    if (data != null && dataFilter(data)) {
                        data.trim()
                            .let(decodeStreamingResponse)
                            .let(processStreamingChunk)
                            ?.let { trySend(it) }
                    }
                } catch (e: Exception) {
                    logger.error(e) { "Error processing SSE event from $clientName: ${e.message}" }
                    close(e)
                }
            }

            logger.debug { "SSE connection closed for $clientName" }
            close()
        } catch (e: Exception) {
            logger.error(e) { "Exception during streaming from $clientName" }
            close(e)
        }

        awaitClose {
            // Cleanup if needed
        }
    }

    /**
     * Common logic of preparing the request body.
     */
    private fun <T : Any> prepareRequestBody(
        request: T,
        requestBodyType: KClass<T>,
    ): RequestBody {
        return if (requestBodyType == String::class) {
            @Suppress("UNCHECKED_CAST")
            RequestBody(body = request as String, contentType = "text/plain")
        } else {
            val serializer = serializer(requestBodyType.java)
            RequestBody(body = json.encodeToString(serializer, request), contentType = "application/json")
        }
    }
}

/**
 * Creates a new instance of `KoogHttpClient` using Java 11's standard HttpClient for performing HTTP operations.
 *
 * This function allows configuring the underlying Java `HttpClient` and provides enhanced logging,
 * flexibility, and customization in HTTP interactions.
 *
 * @param clientName The name of the client instance, used for identifying or logging client operations.
 * @param logger A `KLogger` instance used for logging client events and errors.
 * @param httpClient The Java HttpClient instance to be used. Defaults to a new HttpClient instance.
 * @param json The Json instance used for serialization/deserialization. Defaults to a default Json instance.
 * @return An instance of `KoogHttpClient` configured with the provided parameters.
 */
@Experimental
public fun KoogHttpClient.Companion.fromJavaHttpClient(
    clientName: String,
    logger: KLogger,
    httpClient: HttpClient = HttpClient.newHttpClient(),
    json: Json = Json
): KoogHttpClient = JavaKoogHttpClient(clientName, logger, httpClient, json)
