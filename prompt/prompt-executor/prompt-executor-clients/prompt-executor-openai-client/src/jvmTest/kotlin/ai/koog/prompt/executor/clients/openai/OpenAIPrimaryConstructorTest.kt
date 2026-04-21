package ai.koog.prompt.executor.clients.openai

import ai.koog.http.client.KoogHttpClient
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.models.Item
import ai.koog.prompt.executor.clients.openai.models.OpenAIInputStatus
import ai.koog.prompt.executor.clients.openai.models.OpenAIResponsesAPIResponse
import ai.koog.prompt.executor.clients.openai.models.OpenAIStreamEvent
import ai.koog.prompt.executor.clients.openai.models.OpenAITextConfig
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.test.utils.CapturingKoogHttpClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class OpenAIPrimaryConstructorTest {
    private val responseJson = """
        {
          "id": "chatcmpl-123",
          "object": "chat.completion",
          "created": 1716920005,
          "model": "gpt-4o",
          "choices": [
            {
              "index": 0,
              "message": {
                "role": "assistant",
                "content": "Hello from KoogHttpClient"
              },
              "finish_reason": "stop"
            }
          ],
          "usage": {"total_tokens": 10, "prompt_tokens": 4, "completion_tokens": 6}
        }
    """.trimIndent()

    @Test
    fun `primary constructor should execute through provided koog http client`() = runTest {
        val transport = CapturingKoogHttpClient(clientName = "CapturingOpenAIClient") { responseType ->
            when (responseType) {
                String::class -> responseJson
                else -> error("Unexpected response type: $responseType")
            }
        }
        val client = OpenAILLMClient(
            settings = OpenAIClientSettings(baseUrl = "https://unused.test"),
            httpClient = transport
        )

        val responses = client.execute(
            prompt = prompt("test") { user("Hello?") },
            model = OpenAIModels.Chat.GPT4o
        )

        assertEquals("v1/chat/completions", transport.lastPath)
        assertEquals(LLMProvider.OpenAI, client.llmProvider())
        assertEquals(
            """{"role":"user","content":"Hello?"}""",
            transport.lastRequest.toString().substringAfter("\"messages\":[").substringBefore("]")
        )
        assertEquals(1, responses.size)
        val message = assertIs<Message.Assistant>(responses.single())
        assertEquals("Hello from KoogHttpClient", message.content)
    }

    @Test
    fun `primary constructor should stream reasoning frames through provided koog http client`() = runTest {
        val responsesPath = "v1/responses"
        val reasoningId = "reasoning_123"
        val reasoningDelta = "Thinking"
        val reasoningContent = "Thinking complete"
        val reasoningSummary = "Short summary"
        val encryptedReasoning = "enc_123"
        val responseId = "resp_123"
        val inputTokens = 3
        val outputTokens = 4
        val reasoningTokens = 2
        val totalTokens = 7

        val transport = object : KoogHttpClient {
            override val clientName: String = "StreamingOpenAIClient"

            override suspend fun <R : Any> get(
                path: String,
                responseType: KClass<R>,
                parameters: Map<String, String>,
            ): R = error("GET is not expected in this test")

            override suspend fun <T : Any, R : Any> post(
                path: String,
                request: T,
                requestBodyType: KClass<T>,
                responseType: KClass<R>,
                parameters: Map<String, String>,
            ): R = error("POST is not expected in this test")

            override fun <T : Any, R : Any, O : Any> sse(
                path: String,
                request: T,
                requestBodyType: KClass<T>,
                dataFilter: (String?) -> Boolean,
                decodeStreamingResponse: (String) -> R,
                processStreamingChunk: (R) -> O?,
                parameters: Map<String, String>,
            ): Flow<O> {
                assertEquals(responsesPath, path)

                val events = listOfNotNull(
                    processStreamingChunk(
                        OpenAIStreamEvent.ResponseReasoningTextDelta(
                            itemId = reasoningId,
                            outputIndex = 0,
                            contentIndex = 0,
                            delta = reasoningDelta,
                            sequenceNumber = 1
                        ) as R
                    ),
                    processStreamingChunk(OpenAIStreamEvent.ResponseKeepalive(sequenceNumber = 2) as R),
                    processStreamingChunk(
                        OpenAIStreamEvent.ResponseOutputItemDone(
                            item = Item.Reasoning(
                                id = reasoningId,
                                summary = listOf(Item.Reasoning.Summary(reasoningSummary)),
                                content = listOf(Item.Reasoning.Content(reasoningContent)),
                                encryptedContent = encryptedReasoning,
                                status = OpenAIInputStatus.COMPLETED
                            ),
                            outputIndex = 0,
                            sequenceNumber = 3
                        ) as R
                    ),
                    processStreamingChunk(
                        OpenAIStreamEvent.ResponseCompleted(
                            response = OpenAIResponsesAPIResponse(
                                created = 1716920005,
                                id = responseId,
                                model = "gpt-5",
                                output = emptyList(),
                                parallelToolCalls = false,
                                status = OpenAIInputStatus.COMPLETED,
                                text = OpenAITextConfig(),
                                usage = OpenAIResponsesAPIResponse.Usage(
                                    inputTokens = inputTokens,
                                    inputTokensDetails = OpenAIResponsesAPIResponse.Usage.InputTokensDetails(cachedTokens = 0),
                                    outputTokens = outputTokens,
                                    outputTokensDetails = OpenAIResponsesAPIResponse.Usage.OutputTokensDetails(reasoningTokens = reasoningTokens),
                                    totalTokens = totalTokens
                                )
                            ),
                            sequenceNumber = 4
                        ) as R
                    )
                )

                return if (events.isNotEmpty()) {
                    flow {
                        events.forEach { emit(it) }
                    }
                } else {
                    emptyFlow()
                }
            }

            override fun close(): Unit = Unit
        }
        val client = OpenAILLMClient(
            settings = OpenAIClientSettings(baseUrl = "https://unused.test"),
            httpClient = transport
        )

        val frames = client.executeStreaming(
            prompt = Prompt(
                messages = listOf(Message.User("Hello?", RequestMetaInfo.Empty)),
                id = "test",
                params = OpenAIResponsesParams()
            ),
            model = OpenAIModels.Chat.GPT4o
        ).toList()

        assertEquals(3, frames.size)
        assertEquals(
            StreamFrame.ReasoningDelta(id = reasoningId, text = reasoningDelta, index = 0),
            frames[0]
        )
        assertEquals(
            StreamFrame.ReasoningComplete(
                id = reasoningId,
                text = listOf(reasoningContent),
                summary = listOf(reasoningSummary),
                encrypted = encryptedReasoning,
                index = 0
            ),
            frames[1]
        )
        val end = assertIs<StreamFrame.End>(frames[2])
        assertEquals(null, end.finishReason)
        assertEquals(totalTokens, end.metaInfo.totalTokensCount)
        assertEquals(inputTokens, end.metaInfo.inputTokensCount)
        assertEquals(outputTokens, end.metaInfo.outputTokensCount)
    }
}
