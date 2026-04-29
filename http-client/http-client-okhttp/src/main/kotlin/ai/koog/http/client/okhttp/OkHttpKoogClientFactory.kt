package ai.koog.http.client.okhttp

import ai.koog.http.client.KoogHttpClientFactory
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * [ai.koog.http.client.KoogHttpClientFactory] implementation backed by OkHttp [okhttp3.OkHttpClient].
 *
 * @property logger Logger used by created clients.
 */
public class OkHttpKoogClientFactory(
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
