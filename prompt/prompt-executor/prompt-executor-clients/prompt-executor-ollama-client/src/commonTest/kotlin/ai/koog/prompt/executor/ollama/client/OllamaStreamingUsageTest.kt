package ai.koog.prompt.executor.ollama.client

import ai.koog.http.client.ktor.KtorKoogHttpClient
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.streaming.StreamFrame
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class OllamaStreamingUsageTest {

    @Test
    fun `streaming emits End frame with usage and finish reason from the done chunk`() = runTest {
        val ndjson = listOf(
            """{"model":"m","message":{"role":"assistant","content":"Hi"},"done":false}""",
            """{"model":"m","message":{"role":"assistant","content":""},"done":true,"done_reason":"stop","prompt_eval_count":11,"eval_count":7}""",
        ).joinToString("\n")

        val engine = MockEngine {
            respond(
                content = ndjson,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType to listOf("application/json")),
            )
        }
        val client = OllamaClient(httpClientFactory = KtorKoogHttpClient.Factory(HttpClient(engine)))

        val frames = client.executeStreaming(
            prompt = prompt("test") { },
            model = OllamaModels.Meta.LLAMA_3_2
        ).toList()

        val end = assertIs<StreamFrame.End>(frames.last())
        assertEquals("stop", end.finishReason)
        assertEquals(11, end.metaInfo.inputTokensCount)
        assertEquals(7, end.metaInfo.outputTokensCount)
        assertEquals(18, end.metaInfo.totalTokensCount)
    }
}
