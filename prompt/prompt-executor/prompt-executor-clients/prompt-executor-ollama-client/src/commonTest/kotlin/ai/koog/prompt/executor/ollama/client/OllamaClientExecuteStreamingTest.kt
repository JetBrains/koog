package ai.koog.prompt.executor.ollama.client

import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.llm.OllamaModels
import ai.koog.prompt.params.LLMParams
import ai.koog.prompt.streaming.StreamFrame
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class OllamaClientExecuteStreamingTest {

    private companion object {

        const val TEST_PROMPT_ID = "test_id"
        const val TEST_USER_MESSAGE = "Hello World!"

        const val START_PART_STREAMING_RESPONSE =
            "{\"model\":\"llama3.2:4b\"," +
                "\"created_at\":\"2025-09-25T18:06:14.69926367Z\"," +
                "\"message\":{\"role\":\"assistant\",\"content\":\"Hello\"}," +
                "\"done\":false}"

        const val SECOND_PART_STREAMING_RESPONSE =
            "{\"model\":\"llama3.2:4b\"," +
                "\"created_at\":\"2025-09-25T18:06:14.707835046Z\"," +
                "\"message\":{\"role\":\"assistant\",\"content\":\" Koog!\"}," +
                "\"done\":false}"

        const val FINISH_PART_STREAMING_RESPONSE =
            "{\"model\":\"llama3.2:4b\"," +
                "\"created_at\":\"2025-09-25T18:06:18.076319558Z\"," +
                "\"message\":{\"role\":\"assistant\",\"content\":\"\"}," +
                "\"done\":true," +
                "\"done_reason\":\"stop\"," +
                "\"total_duration\":11904140967," +
                "\"load_duration\":82197934," +
                "\"prompt_eval_count\":71," +
                "\"prompt_eval_duration\":55799582," +
                "\"eval_count\":1400," +
                "\"eval_duration\":11765729971}"
    }

    private fun createStreamingResponseChunks(): List<String> {
        return listOf(
            START_PART_STREAMING_RESPONSE,
            SECOND_PART_STREAMING_RESPONSE,
            FINISH_PART_STREAMING_RESPONSE
        )
    }

    private fun createExpectedResponseChunks(): List<StreamFrame> {
        return listOf(
            StreamFrame.Append("Hello"),
            StreamFrame.Append(" Koog!"),
            StreamFrame.Append(""),
        )
    }

    @Test
    fun `GIVEN OllamaClient WHEN executeStreaming is invoked THEN chek that all chunks are received`() =
        runTest(timeout = 30.seconds) {
            val signalChannel = Channel<Unit>(Channel.RENDEZVOUS)

            val mockEngine = MockEngine {
                val responseChannel = ByteChannel(autoFlush = true)

                launch {
                    createStreamingResponseChunks().forEach { chunk ->
                        responseChannel.writeStringUtf8("$chunk\n")
                        signalChannel.receive()
                    }
                    responseChannel.close()
                }

                respond(
                    content = responseChannel,
                    status = HttpStatusCode.OK,
                    headers = headersOf(
                        name = HttpHeaders.ContentType,
                        value = ContentType.Application.Json.toString()
                    )
                )
            }

            val client = OllamaClient(baseClient = HttpClient(mockEngine))

            val result = client.executeStreaming(
                prompt = prompt(
                    id = TEST_PROMPT_ID,
                    params = LLMParams()
                ) { user(content = TEST_USER_MESSAGE) },
                model = OllamaModels.Meta.LLAMA_3_2
            ).onEach { _ ->
                // Client received a frame, signal the server to send the next one.
                signalChannel.send(Unit)
            }.catch { error ->
                // close channel to avoid freezing the test
                signalChannel.close()
                throw error
            }.toList()

            signalChannel.close()
            assertEquals(createExpectedResponseChunks(), result)
        }
}
