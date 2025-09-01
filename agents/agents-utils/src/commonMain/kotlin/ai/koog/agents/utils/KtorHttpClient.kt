package ai.koog.agents.utils

import io.github.oshai.kotlinlogging.KLogger
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
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
import kotlin.reflect.KClass

public class KtorHttpClient(
    private val clientName: String,
    private val logger: KLogger,
    baseClient: io.ktor.client.HttpClient = io.ktor.client.HttpClient(),
    configurer: HttpClientConfig<*>.() -> Unit
) : KoogHttpClient {

    public val ktorClient: io.ktor.client.HttpClient = baseClient.config(configurer)

    public override suspend fun <T : Any, R : Any> post(
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
            logger.error { "Error from $clientName API: ${response.status}: $errorBody" }
            error("Error from $clientName API: ${response.status}: $errorBody")
        }
    }

    public override fun <T : Any, R : Any> sse(
        path: String,
        request: T,
        requestBodyType: KClass<T>,
        dataFilter: (String?) -> Boolean,
        decodeStreamingResponse: (String) -> R,
        processStreamingChunk: (R) -> String?
    ): Flow<String> = flow {
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
                logger.error(e) { "Error from $clientName API: ${response.status}: ${e.message}.\nBody:\n$body" }
                error("Error from $clientName API: ${response.status}: ${e.message}")
            }
        } catch (e: Exception) {
            logger.error { "Exception during streaming from $clientName: $e" }
            error(e.message ?: "Unknown error during streaming from $clientName: $e")
        }
    }
}
