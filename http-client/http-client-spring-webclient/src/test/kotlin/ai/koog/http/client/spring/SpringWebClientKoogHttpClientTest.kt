package ai.koog.http.client.spring

import ai.koog.http.client.KoogHttpClient
import ai.koog.http.client.lines
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.springframework.core.io.buffer.DefaultDataBufferFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.codec.ClientCodecConfigurer
import org.springframework.http.codec.json.KotlinSerializationJsonDecoder
import org.springframework.http.codec.json.KotlinSerializationJsonEncoder
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import kotlin.test.Test
import kotlin.test.assertEquals

@Execution(ExecutionMode.SAME_THREAD)
class SpringWebClientKoogHttpClientTest : SpringWebClientKoogHttpClientTestBase() {
    override fun createClient(): KoogHttpClient {
        return SpringWebClientKoogHttpClient(
            clientName = "TestClient",
            logger = KotlinLogging.logger("TestLogger"),
            webClient = configuredWebClient()
        )
    }

    @Test
    fun testLinesDecodesUtf8SplitAcrossDataBuffers(): Unit = runBlocking {
        val bytes = "zażółć\njaźń\n".toByteArray(Charsets.UTF_8)
        val chunks = listOf(
            bytes.copyOfRange(fromIndex = 0, toIndex = 3),
            bytes.copyOfRange(fromIndex = 3, toIndex = bytes.size)
        )
        val dataBufferFactory = DefaultDataBufferFactory()
        val webClient = WebClient.builder()
            .exchangeFunction {
                Mono.just(
                    ClientResponse.create(HttpStatus.OK)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_VALUE)
                        .body(Flux.fromIterable(chunks.map(dataBufferFactory::wrap)))
                        .build()
                )
            }
            .build()
        val client = SpringWebClientKoogHttpClient(
            clientName = "TestClient",
            logger = KotlinLogging.logger("TestLogger"),
            webClient = webClient
        )

        val collected = client.lines(
            path = "http://unused.test",
            requestBody = "{}"
        ).toList()

        assertEquals(listOf("zażółć", "jaźń"), collected)
    }

    private fun configuredWebClient(): WebClient = WebClient.builder()
        .codecs { codecs -> codecs.configureKotlinSerialization(Json) }
        .build()

    private fun ClientCodecConfigurer.configureKotlinSerialization(json: Json) {
        defaultCodecs().kotlinSerializationJsonEncoder(KotlinSerializationJsonEncoder(json))
        defaultCodecs().kotlinSerializationJsonDecoder(KotlinSerializationJsonDecoder(json))
    }
}
