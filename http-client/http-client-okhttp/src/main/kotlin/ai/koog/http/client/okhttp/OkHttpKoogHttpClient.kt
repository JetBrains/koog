package ai.koog.http.client.okhttp

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
import kotlin.reflect.KClass

/**
 * OkHttpKoogHttpClient is an implementation of the KoogHttpClient interface, utilizing OkHttp's client
 * to perform HTTP operations, including POST requests and Server-Sent Events (SSE) streaming.
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
    private val clientName: String,
    private val logger: KLogger,
    private val okHttpClient: OkHttpClient,
    private val json: Json
) : KoogHttpClient {

    override suspend fun <T : Any, R : Any> post(
        path: String,
        request: T,
        requestBodyType: KClass<T>,
        responseType: KClass<R>
    ): R = withContext(Dispatchers.SuitableForIO) {
        val requestBody = prepareRequestBody(request, requestBodyType)

        val httpRequest = Request.Builder()
            .url(path)
            .post(requestBody)
            .build()

        val response: Response = okHttpClient.newCall(httpRequest).execute()

        response.use {
            if (response.isSuccessful) {
                val responseBody = response.body.string()
                if (responseType == String::class) {
                    @Suppress("UNCHECKED_CAST")
                    responseBody as R
                } else {
                    val serializer = serializer(responseType.java)
                    @Suppress("UNCHECKED_CAST")
                    json.decodeFromString(serializer, responseBody) as R
                }
            } else {
                val errorBody = response.body.string()
                val errorMessage = "Error from $clientName API: ${response.code}"

                logger.error { errorMessage }
                logger.trace { "$errorMessage\nBody:\n$errorBody" }

                error(errorMessage)
            }
        }
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

        val httpRequest = Request.Builder()
            .url(path)
            .post(requestBody)
            .header("Accept", "text/event-stream")
            .header("Cache-Control", "no-cache")
            .header("Connection", "keep-alive")
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
                    logger.error(e) { "Error processing SSE event from $clientName: ${e.message}" }
                    close(e)
                }
            }

            override fun onClosed(eventSource: EventSource) {
                logger.debug { "SSE connection closed for $clientName" }
                close()
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                val body = response?.body?.string()
                val errorMessage = if (response != null) {
                    "Error from $clientName API: ${response.code}: ${t?.message}"
                } else {
                    "Exception during streaming from $clientName: ${t?.message ?: "Unknown error"}"
                }

                logger.error(t) { errorMessage }
                logger.trace(t) { "$errorMessage\nBody:\n$body" }

                close(IllegalStateException(errorMessage, t))
            }
        }

        val eventSource = EventSources.createFactory(okHttpClient)
            .newEventSource(httpRequest, eventSourceListener)

        awaitClose {
            eventSource.cancel()
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
            (request as String).toRequestBody("text/plain".toMediaType())
        } else {
            val serializer = serializer(requestBodyType.java)
            val jsonString = json.encodeToString(serializer, request)
            jsonString.toRequestBody("application/json".toMediaType())
        }
    }
}

/**
 * Creates a new instance of `KoogHttpClient` using an OkHttp-based HTTP client for performing HTTP operations.
 *
 * This function allows configuring the underlying OkHttp `OkHttpClient` and provides enhanced logging,
 * flexibility, and customization in HTTP interactions.
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
