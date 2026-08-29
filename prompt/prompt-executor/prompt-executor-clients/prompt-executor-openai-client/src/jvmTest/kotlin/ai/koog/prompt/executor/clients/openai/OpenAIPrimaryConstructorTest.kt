package ai.koog.prompt.executor.clients.openai

import ai.koog.http.client.KoogHttpClient
import ai.koog.prompt.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.models.Item
import ai.koog.prompt.executor.clients.openai.models.OpenAIInputStatus
import ai.koog.prompt.executor.clients.openai.models.OpenAIResponsesAPIResponse
import ai.koog.prompt.executor.clients.openai.models.OpenAIStreamEvent
import ai.koog.prompt.executor.clients.openai.models.OpenAITextConfig
import ai.koog.prompt.executor.clients.openai.models.OutputContent
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.message.MessagePart.Text.Phase
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.test.utils.CapturingKoogHttpClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
        assertEquals(1, responses.parts.size)
        val textPart = assertIs<MessagePart.Text>(responses.parts.single())
        assertEquals("Hello from KoogHttpClient", textPart.text)
    }

    @Test
    fun testResponsesAPIPreservesAssistantPhaseForReplayAndKeepsCommentaryOutput() = runTest {
        val transport = CapturingKoogHttpClient(clientName = "CapturingOpenAIResponsesClient") { responseType ->
            when (responseType) {
                OpenAIResponsesAPIResponse::class -> OpenAIResponsesAPIResponse(
                    created = 1716920005,
                    id = "resp_123",
                    model = "gpt-5.4",
                    output = listOf(
                        Item.OutputMessage(
                            content = listOf(OutputContent.Text(annotations = emptyList(), text = "Working through it")),
                            id = "msg_commentary",
                            phase = Phase.COMMENTARY,
                            status = OpenAIInputStatus.COMPLETED
                        ),
                        Item.OutputMessage(
                            content = listOf(OutputContent.Text(annotations = emptyList(), text = "Final answer")),
                            id = "msg_final",
                            phase = Phase.FINAL_ANSWER,
                            status = OpenAIInputStatus.COMPLETED
                        )
                    ),
                    parallelToolCalls = false,
                    status = OpenAIInputStatus.COMPLETED,
                    text = OpenAITextConfig()
                )
                else -> error("Unexpected response type: $responseType")
            }
        }
        val client = OpenAILLMClient(
            settings = OpenAIClientSettings(baseUrl = "https://unused.test"),
            httpClient = transport
        )

        val responses = client.execute(
            prompt = Prompt(
                messages = listOf(
                    Message.Assistant(
                        part = MessagePart.Text(
                            text = "Earlier final answer",
                            phase = Phase.FINAL_ANSWER
                        ),
                        metaInfo = ResponseMetaInfo.Empty,
                    )
                ),
                id = "test",
                params = OpenAIResponsesParams()
            ),
            model = OpenAIModels.Chat.GPT4o
        )

        val request = Json.parseToJsonElement(transport.lastRequest.toString()).jsonObject
        val replayedAssistant = request.getValue("input").jsonArray.single().jsonObject
        assertEquals("final_answer", replayedAssistant.getValue("phase").jsonPrimitive.content)

        assertEquals(2, responses.parts.size)

        val commentary = assertIs<MessagePart.Text>(responses.parts[0])
        assertEquals("Working through it", commentary.text)
        assertEquals(Phase.COMMENTARY, commentary.phase)

        val finalAnswer = assertIs<MessagePart.Text>(responses.parts[1])
        assertEquals("Final answer", finalAnswer.text)
        assertEquals(Phase.FINAL_ANSWER, finalAnswer.phase)
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
                headers: Map<String, String>,
            ): R = error("GET is not expected in this test")

            override suspend fun <T : Any, R : Any> post(
                path: String,
                requestBody: T,
                requestBodyType: KClass<T>,
                responseType: KClass<R>,
                parameters: Map<String, String>,
                headers: Map<String, String>,
            ): R = error("POST is not expected in this test")

            override fun <T : Any, R : Any, O : Any> sse(
                path: String,
                requestBody: T,
                requestBodyType: KClass<T>,
                dataFilter: (String?) -> Boolean,
                decodeStreamingResponse: (String) -> R,
                processStreamingChunk: (R) -> O?,
                parameters: Map<String, String>,
                headers: Map<String, String>,
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

            override fun <T : Any> lines(
                path: String,
                requestBody: T,
                requestBodyType: KClass<T>,
                parameters: Map<String, String>,
                headers: Map<String, String>,
            ): Flow<String> = error("lines is not expected in this test")

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
                content = listOf(reasoningContent),
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

    @Test
    fun testResponsesStreamingEmitsCommentaryTextDeltas() = runTest {
        val responsesPath = "v1/responses"

        val transport = object : KoogHttpClient {
            override val clientName: String = "StreamingOpenAIClient"

            override suspend fun <R : Any> get(
                path: String,
                responseType: KClass<R>,
                parameters: Map<String, String>,
                headers: Map<String, String>,
            ): R = error("GET is not expected in this test")

            override suspend fun <T : Any, R : Any> post(
                path: String,
                requestBody: T,
                requestBodyType: KClass<T>,
                responseType: KClass<R>,
                parameters: Map<String, String>,
                headers: Map<String, String>,
            ): R = error("POST is not expected in this test")

            override fun <T : Any, R : Any, O : Any> sse(
                path: String,
                requestBody: T,
                requestBodyType: KClass<T>,
                dataFilter: (String?) -> Boolean,
                decodeStreamingResponse: (String) -> R,
                processStreamingChunk: (R) -> O?,
                parameters: Map<String, String>,
                headers: Map<String, String>,
            ): Flow<O> {
                assertEquals(responsesPath, path)

                val events = listOfNotNull(
                    processStreamingChunk(
                        OpenAIStreamEvent.ResponseOutputItemAdded(
                            item = Item.OutputMessage(
                                content = emptyList(),
                                id = "msg_commentary",
                                phase = Phase.COMMENTARY
                            ),
                            outputIndex = 0,
                            sequenceNumber = 1
                        ) as R
                    ),
                    processStreamingChunk(
                        OpenAIStreamEvent.ResponseOutputTextDelta(
                            itemId = "msg_commentary",
                            outputIndex = 0,
                            contentIndex = 0,
                            delta = "Working",
                            sequenceNumber = 2
                        ) as R
                    ),
                    processStreamingChunk(
                        OpenAIStreamEvent.ResponseOutputTextDone(
                            itemId = "msg_commentary",
                            outputIndex = 0,
                            contentIndex = 0,
                            text = "Working",
                            sequenceNumber = 3
                        ) as R
                    ),
                    processStreamingChunk(
                        OpenAIStreamEvent.ResponseOutputItemAdded(
                            item = Item.OutputMessage(
                                content = emptyList(),
                                id = "msg_final",
                                phase = Phase.FINAL_ANSWER
                            ),
                            outputIndex = 1,
                            sequenceNumber = 4
                        ) as R
                    ),
                    processStreamingChunk(
                        OpenAIStreamEvent.ResponseOutputTextDelta(
                            itemId = "msg_final",
                            outputIndex = 1,
                            contentIndex = 0,
                            delta = "Final",
                            sequenceNumber = 5
                        ) as R
                    ),
                    processStreamingChunk(
                        OpenAIStreamEvent.ResponseOutputTextDone(
                            itemId = "msg_final",
                            outputIndex = 1,
                            contentIndex = 0,
                            text = "Final",
                            sequenceNumber = 6
                        ) as R
                    ),
                    processStreamingChunk(
                        OpenAIStreamEvent.ResponseCompleted(
                            response = OpenAIResponsesAPIResponse(
                                created = 1716920005,
                                id = "resp_123",
                                model = "gpt-5.4",
                                output = emptyList(),
                                parallelToolCalls = false,
                                status = OpenAIInputStatus.COMPLETED,
                                text = OpenAITextConfig()
                            ),
                            sequenceNumber = 7
                        ) as R
                    )
                )

                return flow {
                    events.forEach { emit(it) }
                }
            }

            override fun <T : Any> lines(
                path: String,
                requestBody: T,
                requestBodyType: KClass<T>,
                parameters: Map<String, String>,
                headers: Map<String, String>,
            ): Flow<String> = error("lines is not expected in this test")

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

        assertEquals(5, frames.size)
        assertEquals(StreamFrame.TextDelta(text = "Working", index = 0, phase = Phase.COMMENTARY), frames[0])
        assertEquals(StreamFrame.TextComplete(text = "Working", index = 0, phase = Phase.COMMENTARY), frames[1])
        assertEquals(StreamFrame.TextDelta(text = "Final", index = 1, phase = Phase.FINAL_ANSWER), frames[2])
        assertEquals(StreamFrame.TextComplete(text = "Final", index = 1, phase = Phase.FINAL_ANSWER), frames[3])
        assertIs<StreamFrame.End>(frames[4])
    }
}
