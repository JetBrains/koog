package ai.koog.http.client.ktor

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Regression test for https://github.com/JetBrains/koog/issues/2245.
 *
 * Since Ktor 3.4, `HttpStatement.execute` runs its block on the engine dispatcher on non-JVM targets,
 * so [KtorKoogHttpClient.lines] must accept emissions from a coroutine context other than the collector's.
 * [MockEngine] answers on its own dispatcher while the collector runs on the test dispatcher.
 */
class KtorKoogHttpClientEngineDispatcherTest {

    private val logger = KotlinLogging.logger { }

    private fun client(engine: MockEngine): KtorKoogHttpClient =
        KtorKoogHttpClient(
            clientName = "TestClient",
            logger = logger,
            baseClient = HttpClient(engine),
        ) {}

    @Test
    fun testLinesCollectsWhenBlockRunsOnEngineDispatcher() = runTest {
        val engine = MockEngine {
            respond(
                content = ByteReadChannel("""{"i":1}""" + "\n" + """{"i":2}""" + "\n\n" + """{"i":3}""" + "\n"),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/x-ndjson"),
            )
        }

        val collected = client(engine).lines(
            path = "http://localhost/stream",
            requestBody = "{}",
            requestBodyType = String::class,
            parameters = emptyMap(),
            headers = emptyMap(),
        ).toList()

        assertEquals(listOf("""{"i":1}""", """{"i":2}""", """{"i":3}"""), collected)
    }
}
