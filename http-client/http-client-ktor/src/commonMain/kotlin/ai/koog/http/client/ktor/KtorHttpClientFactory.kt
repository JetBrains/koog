package ai.koog.http.client.ktor

import ai.koog.http.client.KoogHttpClientFactory
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.URLBuilder
import io.ktor.http.contentType
import io.ktor.http.encodedPath
import io.ktor.http.takeFrom
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlin.jvm.JvmOverloads

/**
 * [KoogHttpClientFactory] implementation backed by Ktor [HttpClient].
 *
 * @property baseClient Base Ktor client used to create configured clients.
 * @property withSse Whether created clients should install Ktor SSE support.
 * @property logger Logger used by created clients.
 */
public class KtorHttpClientFactory(
    private val baseClient: HttpClient = HttpClient(),
    private val withSse: Boolean = true,
    private val logger: KLogger = KotlinLogging.logger {}
) : KoogHttpClientFactory {

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
