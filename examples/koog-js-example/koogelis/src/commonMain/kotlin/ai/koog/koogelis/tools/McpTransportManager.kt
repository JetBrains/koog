package ai.koog.koogelis.tools

import ai.koog.koogelis.AgentConfiguration
import io.github.oshai.kotlinlogging.KLogger
import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.request.header
import io.ktor.serialization.kotlinx.json.json
import io.modelcontextprotocol.kotlin.sdk.client.mcpSseTransport
import io.modelcontextprotocol.kotlin.sdk.client.mcpStreamableHttpTransport
import io.modelcontextprotocol.kotlin.sdk.shared.Transport
import kotlinx.serialization.json.Json

class McpTransportManager(private val logger: KLogger) {

    private val transports = mutableListOf<Transport>()

    fun createTransport(transportType: AgentConfiguration.TransportType, url: String, headers: Map<String, String>): Transport {
        val httpClient = createHttpClient(headers)
        try {
            val transport = if (transportType == AgentConfiguration.TransportType.SSE) httpClient.mcpSseTransport(url) else httpClient.mcpStreamableHttpTransport(url)
            transport.onClose { closeHttpClient(httpClient) }
            transports.add(transport)
            return transport
        } catch (e: Exception) {
            logger.error(e) { "Failed to createTransport to $url" }
            closeHttpClient(httpClient)
            throw e
        }
    }

    suspend fun closeAll() {
        transports.forEach { transport ->
            try {
                transport.close()
            } catch (e: Exception) {
                logger.error(e) { "Failed to close an MCP transport" }
            }
        }
        transports.clear()
    }

    private fun createHttpClient(headers: Map<String, String>) = HttpClient(Js) {
        defaultRequest {
            headers.forEach { (key, value) -> header(key, value)}
        }
        install(SSE)
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 30_000
            socketTimeoutMillis = 30_000
        }
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                prettyPrint = false
                isLenient = true
            })
        }
    }

    private fun closeHttpClient(httpClient: HttpClient) {
        try {
            httpClient.close()
        } catch (e: Exception) {
            logger.error(e) { "Failed to closeHttpClient" }
        }
    }
}
