package ai.koog.a2a.transport.client.jsonrpc.http

import ai.koog.a2a.transport.ClientCallContext
import ai.koog.a2a.transport.jsonrpc.JSONRPCClientTransport
import ai.koog.a2a.transport.jsonrpc.model.JSONRPCJson
import ai.koog.a2a.transport.jsonrpc.model.JSONRPCRequest
import ai.koog.a2a.transport.jsonrpc.model.JSONRPCResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.plugins.sse.sse
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Implementation of a JSON-RPC client transport using HTTP as the underlying communication protocol.
 *
 * This transport sends JSON-RPC requests over HTTP and processes the responses. It also supports
 * both standard requests and Server-Sent Events (SSE) for streaming responses.
 *
 * @param url The URL of the JSON-RPC server endpoint.
 * @param baseHttpClient The base [HttpClient] instance, which will be configured internally.
 */
public class HttpJSONRPCClientTransport(
    url: String,
    baseHttpClient: HttpClient = HttpClient()
) : JSONRPCClientTransport() {
    private val httpClient: HttpClient = baseHttpClient.config {
        defaultRequest {
            url(url)
            contentType(ContentType.Application.Json)
        }

        install(ContentNegotiation) {
            json(JSONRPCJson)
        }

        install(SSE)

        expectSuccess = true
    }

    override suspend fun request(
        request: JSONRPCRequest,
        ctx: ClientCallContext
    ): JSONRPCResponse {
        val response = httpClient.post {
            headers {
                ctx.additionalHeaders.forEach { (key, values) ->
                    appendAll(key, values)
                }
            }

            setBody(request)
        }

        return response.body<JSONRPCResponse>()
    }

    override fun requestStreaming(
        request: JSONRPCRequest,
        ctx: ClientCallContext
    ): Flow<JSONRPCResponse> = flow {
        httpClient.sse(
            request = {
                method = HttpMethod.Post

                headers {
                    ctx.additionalHeaders.forEach { (key, values) ->
                        appendAll(key, values)
                    }
                }

                setBody(request)
            }
        ) {
            incoming.collect { event ->
                requireNotNull(event.data) { "SSE data must not be null" }
                    .let { data ->
                        val response = JSONRPCJson.decodeFromString<JSONRPCResponse>(data)
                        emit(response)
                    }
            }
        }
    }

    override fun close() {
        httpClient.close()
    }
}
