package ai.koog.http.client.java

import ai.koog.http.client.KoogHttpClientFactory
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.Json
import java.net.http.HttpClient
import java.time.Duration

/**
 * [ai.koog.http.client.KoogHttpClientFactory] implementation backed by Java's [java.net.http.HttpClient].
 *
 * Note: [KoogHttpClientFactory.create]'s `socketTimeoutMillis` parameter is accepted but not applied.
 * Java's [HttpClient] API does not expose read/write socket timeout configuration; only connection
 * timeout ([HttpClient.Builder.connectTimeout]) and per-request timeout are available.
 *
 * @property logger Logger used by created clients.
 */
public class JavaHttpClientFactory(
    private val logger: KLogger = KotlinLogging.logger {}
) : KoogHttpClientFactory {
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
