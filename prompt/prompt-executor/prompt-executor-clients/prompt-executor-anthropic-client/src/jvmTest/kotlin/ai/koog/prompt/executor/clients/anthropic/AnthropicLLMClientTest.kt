package ai.koog.prompt.executor.clients.anthropic

import ai.koog.http.client.ktor.KtorKoogHttpClient
import ai.koog.prompt.Prompt
import ai.koog.prompt.message.Message
import ai.koog.utils.time.KoogClock
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class AnthropicLLMClientTest {

    private object FixedClock : KoogClock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(0)
    }

    @Test
    fun testExecuteSplitsCacheReadAndWriteInputTokens() = runTest {
        val response = executeWithUsage(
            """
                "input_tokens": 100,
                "output_tokens": 10,
                "cache_read_input_tokens": 75,
                "cache_creation_input_tokens": 25
            """.trimIndent()
        )

        assertEquals(75, response.metaInfo.cacheReadInputTokensCount)
        assertEquals(25, response.metaInfo.cacheWriteInputTokensCount)
        assertEquals(JsonPrimitive(75), response.metaInfo.metadata?.get("cacheReadInputTokens"))
        assertEquals(JsonPrimitive(25), response.metaInfo.metadata?.get("cacheCreationInputTokens"))
    }

    @Test
    fun testExecuteLeavesCacheInputTokensNullWhenUsageOmitsThem() = runTest {
        val response = executeWithUsage(
            """
                "input_tokens": 10,
                "output_tokens": 5
            """.trimIndent()
        )

        assertNull(response.metaInfo.cacheReadInputTokensCount)
        assertNull(response.metaInfo.cacheWriteInputTokensCount)
        assertNull(response.metaInfo.metadata)
    }

    private suspend fun executeWithUsage(usage: String): Message.Assistant = AnthropicLLMClient(
        apiKey = "test-key",
        clock = FixedClock,
        httpClientFactory = KtorKoogHttpClient.Factory(
            HttpClient(
                MockEngine {
                    respond(
                        content = """
                            {
                                "id": "msg_test",
                                "type": "message",
                                "role": "assistant",
                                "content": [{ "type": "text", "text": "cached" }],
                                "model": "claude-sonnet-4-20250514",
                                "stop_reason": "end_turn",
                                "usage": {
                                    $usage
                                }
                            }
                        """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = headersOf(
                            HttpHeaders.ContentType,
                            ContentType.Application.Json.toString()
                        ),
                    )
                }
            )
        )
    ).use { client ->
        client.execute(
            prompt = Prompt.build(id = "cache-test", clock = FixedClock) {
                user("Use the cache")
            },
            model = AnthropicModels.Sonnet_4,
            tools = emptyList(),
        )
    }
}
