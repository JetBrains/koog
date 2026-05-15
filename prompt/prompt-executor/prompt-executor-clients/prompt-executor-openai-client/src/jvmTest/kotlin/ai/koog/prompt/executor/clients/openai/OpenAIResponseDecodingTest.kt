package ai.koog.prompt.executor.clients.openai

import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.LLMClientException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class OpenAIResponseDecodingTest {

    @Test
    fun testExecuteWrapsDecodeFailureInLLMClientException() = runTest {
        // Given: client returning malformed json
        val engine = MockEngine.Companion { _ ->
            respond(
                content = "{ not json",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        val http = HttpClient(engine) {}
        val client = OpenAILLMClient(apiKey = "test-key", baseClient = http)

        // And: sample prompt
        val prompt = Prompt.build(id = "p-decode-fail", params = OpenAIChatParams()) {
            user("Hi")
        }

        // When/Then: executing prompt fail due to serialization exception that is wrapped in LLMClientException
        val ex = assertFailsWith<LLMClientException> {
            client.execute(prompt, OpenAIModels.Chat.GPT4o)
        }
        assertIs<SerializationException>(ex.cause)
    }
}
