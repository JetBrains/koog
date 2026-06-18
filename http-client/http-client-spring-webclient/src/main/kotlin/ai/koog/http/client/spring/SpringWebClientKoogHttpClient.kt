package ai.koog.http.client.spring

import ai.koog.http.client.KoogHttpClient
import ai.koog.http.client.KoogHttpClientException
import ai.koog.http.client.mergeHeaders
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import io.netty.channel.ChannelOption
import io.netty.handler.timeout.ReadTimeoutHandler
import io.netty.handler.timeout.WriteTimeoutHandler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.serialization.json.Json
import org.springframework.core.ParameterizedTypeReference
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.core.io.buffer.DataBufferUtils
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.http.codec.ClientCodecConfigurer
import org.springframework.http.codec.ServerSentEvent
import org.springframework.http.codec.json.KotlinSerializationJsonDecoder
import org.springframework.http.codec.json.KotlinSerializationJsonEncoder
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToFlux
import org.springframework.web.reactive.function.client.bodyToMono
import org.springframework.web.util.UriBuilder
import org.springframework.web.util.UriComponentsBuilder
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.netty.http.client.HttpClient
import java.net.URI
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.jvm.JvmOverloads
import kotlin.math.min
import kotlin.reflect.KClass

/**
 * Spring WebClient-backed implementation of [KoogHttpClient].
 * This adapter is specific to Spring WebFlux [WebClient] and does not wrap `RestTemplate`.
 *
 * @property clientName The name of the client, used for logging and traceability.
 * @property webClient The configured Spring [WebClient] used to execute HTTP requests.
 * @property headers Default headers to be applied to all requests.
 * @property queryParameters Default query parameters to be applied to all requests.
 */
public class SpringWebClientKoogHttpClient(
    override val clientName: String,
    private val logger: KLogger,
    private val webClient: WebClient,
    private val headers: Map<String, String> = emptyMap(),
    private val queryParameters: Map<String, String> = emptyMap(),
) : KoogHttpClient {

    override suspend fun <R : Any> get(
        path: String,
        responseType: KClass<R>,
        parameters: Map<String, String>,
        headers: Map<String, String>
    ): R {
        return webClient.get()
            .uriWithParameters(path, this.queryParameters + parameters)
            .applyRequestHeaders(mergeHeaders(this.headers, headers))
            .handleResponse(responseType)
            .awaitSingle()
    }

    override suspend fun <T : Any, R : Any> post(
        path: String,
        requestBody: T,
        requestBodyType: KClass<T>,
        responseType: KClass<R>,
        parameters: Map<String, String>,
        headers: Map<String, String>
    ): R {
        return webClient.post()
            .uriWithParameters(path, this.queryParameters + parameters)
            .body(BodyInserters.fromValue(requestBody))
            .applyRequestHeaders(
                mergeHeaders(
                    this.headers,
                    mapOf(HttpHeaders.CONTENT_TYPE to requestBodyType.defaultContentType()),
                    headers,
                )
            )
            .handleResponse(responseType)
            .awaitSingle()
    }

    override fun <T : Any, R : Any, O : Any> sse(
        path: String,
        requestBody: T,
        requestBodyType: KClass<T>,
        dataFilter: (String?) -> Boolean,
        decodeStreamingResponse: (String) -> R,
        processStreamingChunk: (R) -> O?,
        parameters: Map<String, String>,
        headers: Map<String, String>
    ): Flow<O> = flow {
        logger.debug { "Opening sse connection for $clientName" }
        try {
            webClient.post()
                .uriWithParameters(path, this@SpringWebClientKoogHttpClient.queryParameters + parameters)
                .body(BodyInserters.fromValue(requestBody))
                .applyRequestHeaders(
                    mergeHeaders(
                        this@SpringWebClientKoogHttpClient.headers,
                        mapOf(
                            HttpHeaders.CONTENT_TYPE to requestBodyType.defaultContentType(),
                            HttpHeaders.ACCEPT to MediaType.TEXT_EVENT_STREAM_VALUE,
                            HttpHeaders.CACHE_CONTROL to "no-cache",
                        ),
                        headers,
                    )
                )
                .exchangeToFlux { response ->
                    if (response.statusCode().is2xxSuccessful) {
                        response.bodyToFlux(SERVER_SENT_EVENT_TYPE)
                    } else {
                        response.handleErrorFlux()
                    }
                }
                .asFlow()
                .collect { event ->
                    val data = event.data()
                    if (dataFilter(data)) {
                        data?.trim()
                            ?.let(decodeStreamingResponse)
                            ?.let(processStreamingChunk)
                            ?.let { emit(it) }
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
        } finally {
            logger.debug { "SSE connection closed for $clientName" }
        }
    }

    override fun <T : Any> lines(
        path: String,
        requestBody: T,
        requestBodyType: KClass<T>,
        parameters: Map<String, String>,
        headers: Map<String, String>
    ): Flow<String> = flow {
        logger.debug { "Opening lines flow for $clientName" }

        try {
            webClient.post()
                .uriWithParameters(path, this@SpringWebClientKoogHttpClient.queryParameters + parameters)
                .body(BodyInserters.fromValue(requestBody))
                .applyRequestHeaders(
                    mergeHeaders(
                        this@SpringWebClientKoogHttpClient.headers,
                        mapOf(HttpHeaders.CONTENT_TYPE to requestBodyType.defaultContentType()),
                        headers,
                    )
                )
                .exchangeToFlux { response ->
                    if (response.statusCode().is2xxSuccessful) {
                        response.bodyToFlux<DataBuffer>()
                    } else {
                        response.handleErrorFlux()
                    }
                }
                .decodeUtf8Lines()
                .chunkedToLines()
                .collect { emit(it) }
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
        } finally {
            logger.debug { "Lines flow closed for $clientName" }
        }
    }

    override fun close() {}

    /**
     * [KoogHttpClient.Factory] implementation backed by Spring [WebClient].
     * This factory creates WebClient-based clients only (not `RestTemplate`-based clients).
     *
     * @property webClientBuilder Base WebClient builder used to create configured clients.
     * @property logger Logger used by created clients.
     */
    public class Factory @JvmOverloads public constructor(
        private val webClientBuilder: WebClient.Builder = WebClient.builder(),
        private val logger: KLogger = KotlinLogging.logger {}
    ) : KoogHttpClient.Factory {

        override fun create(
            clientName: String,
            baseUrl: String,
            headers: Map<String, String>,
            queryParameters: Map<String, String>,
            requestTimeoutMillis: Long,
            connectTimeoutMillis: Long,
            socketTimeoutMillis: Long,
            json: Json
        ): SpringWebClientKoogHttpClient {
            val reactorClient = HttpClient.create()
                .responseTimeout(Duration.ofMillis(requestTimeoutMillis))
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMillis.toConnectTimeoutInt())
                .doOnConnected { connection ->
                    connection
                        .addHandlerLast(ReadTimeoutHandler(socketTimeoutMillis, TimeUnit.MILLISECONDS))
                        .addHandlerLast(WriteTimeoutHandler(socketTimeoutMillis, TimeUnit.MILLISECONDS))
                }

            val configuredWebClientBuilder = webClientBuilder.clone()
                .clientConnector(ReactorClientHttpConnector(reactorClient))
                .codecs { codecs -> codecs.configureKotlinSerialization(json) }

            if (baseUrl.isNotBlank()) {
                configuredWebClientBuilder.baseUrl(baseUrl.trimEnd('/'))
            }

            val configuredWebClient = configuredWebClientBuilder.build()
            return SpringWebClientKoogHttpClient(
                clientName = clientName,
                logger = logger,
                webClient = configuredWebClient,
                headers = headers,
                queryParameters = queryParameters,
            )
        }

        private fun Long.toConnectTimeoutInt(): Int = min(this, Int.MAX_VALUE.toLong()).toInt()

        private fun ClientCodecConfigurer.configureKotlinSerialization(json: Json) {
            defaultCodecs().kotlinSerializationJsonEncoder(KotlinSerializationJsonEncoder(json))
            defaultCodecs().kotlinSerializationJsonDecoder(KotlinSerializationJsonDecoder(json))
        }
    }

    private fun <S : WebClient.RequestHeadersSpec<S>> WebClient.RequestHeadersUriSpec<S>.uriWithParameters(
        path: String,
        parameters: Map<String, String>
    ): S {
        return if (URI.create(path).isAbsolute) {
            uri(UriComponentsBuilder.fromUriString(path).applyQueryParameters(parameters).build().encode().toUri())
        } else {
            uri { uriBuilder -> uriBuilder.path(path.asRelativePath()).applyQueryParameters(parameters).build() }
        }
    }

    private fun String.asRelativePath(): String = if (startsWith("/")) this else "/$this"

    private fun UriBuilder.applyQueryParameters(parameters: Map<String, String>): UriBuilder = apply {
        parameters.forEach { (key, value) ->
            queryParam(key, value)
        }
    }

    private fun UriComponentsBuilder.applyQueryParameters(parameters: Map<String, String>): UriComponentsBuilder =
        apply {
            parameters.forEach { (key, value) ->
                queryParam(key, value)
            }
        }

    private fun <S : WebClient.RequestHeadersSpec<*>> S.applyRequestHeaders(headers: Map<String, String>): S =
        apply {
            headers.forEach { (key, value) -> header(key, value) }
        }

    private fun <R : Any> WebClient.RequestHeadersSpec<*>.handleResponse(responseType: KClass<R>): Mono<R> {
        return exchangeToMono { response ->
            if (response.statusCode().is2xxSuccessful) {
                if (responseType == String::class) {
                    response.bodyToMono<String>()
                        .defaultIfEmpty("")
                        .map { responseBody ->
                            @Suppress("UNCHECKED_CAST")
                            responseBody as R
                        }
                } else {
                    response.bodyToMono(responseType.java)
                }
            } else {
                response.handleErrorMono()
            }
        }
    }

    private fun KClass<*>.defaultContentType(): String =
        if (this == String::class) MediaType.TEXT_PLAIN_VALUE else MediaType.APPLICATION_JSON_VALUE

    private fun <R> ClientResponse.handleErrorMono(): Mono<R> =
        bodyToMono<String>().defaultIfEmpty("").flatMap { Mono.error(createException(it)) }

    private fun <R> ClientResponse.handleErrorFlux(): Flux<R> =
        bodyToMono<String>().defaultIfEmpty("").flatMapMany { Flux.error(createException(it)) }

    private fun Flux<DataBuffer>.decodeUtf8Lines(): Flow<String> = flow {
        val decoder = Utf8StreamDecoder()
        asFlow().collect { dataBuffer ->
            try {
                emit(decoder.decode(dataBuffer))
            } finally {
                DataBufferUtils.release(dataBuffer)
            }
        }
        val tail = decoder.finish()
        if (tail.isNotEmpty()) {
            emit(tail)
        }
    }

    private fun ClientResponse.createException(errorBody: String): KoogHttpClientException =
        KoogHttpClientException(
            clientName = clientName,
            statusCode = statusCode().value(),
            errorBody = errorBody,
        )

    private fun Flow<String>.chunkedToLines(): Flow<String> = flow {
        var pendingLine = ""
        collect { chunk ->
            val parts = (pendingLine + chunk).split('\n')
            pendingLine = parts.last()
            parts.dropLast(1).forEach { line ->
                val emittedLine = line.trimEnd('\r')
                if (emittedLine.isNotBlank()) {
                    emit(emittedLine)
                }
            }
        }
        val finalLine = pendingLine.trimEnd('\r')
        if (finalLine.isNotBlank()) {
            emit(finalLine)
        }
    }

    private companion object {
        private val SERVER_SENT_EVENT_TYPE: ParameterizedTypeReference<ServerSentEvent<String>> =
            object : ParameterizedTypeReference<ServerSentEvent<String>>() {}
    }
}
