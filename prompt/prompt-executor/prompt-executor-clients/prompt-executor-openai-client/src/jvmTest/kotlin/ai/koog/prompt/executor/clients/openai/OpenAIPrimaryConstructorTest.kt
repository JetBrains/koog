package ai.koog.prompt.executor.clients.openai

import ai.koog.http.client.KoogHttpClient
import ai.koog.prompt.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.models.Item
import ai.koog.prompt.executor.clients.openai.models.OpenAIInputStatus
import ai.koog.prompt.executor.clients.openai.models.OpenAIResponsesAPIResponse
import ai.koog.prompt.executor.clients.openai.models.OpenAIStreamEvent
import ai.koog.prompt.executor.clients.openai.models.OpenAITextConfig
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.test.utils.CapturingKoogHttpClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class OpenAIPrimaryConstructorTest {
    private val streamingJson = Json {
        encodeDefaults = true
        explicitNulls = false
        namingStrategy = JsonNamingStrategy.SnakeCase
    }

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
    fun testResponsesStreamingToolCallDeltasUseCanonicalIdentity() = runTest {
        val firstItemId = "item_first"
        val firstCallId = "call_first"
        val firstName = "firstTool"
        val secondItemId = "item_second"
        val secondCallId = "call_second"
        val secondName = "secondTool"
        val events = listOf(
            OpenAIStreamEvent.ResponseOutputItemAdded(
                item = Item.FunctionToolCall(
                    arguments = "",
                    callId = firstCallId,
                    name = firstName,
                    id = firstItemId,
                    status = OpenAIInputStatus.IN_PROGRESS
                ),
                outputIndex = 0,
                sequenceNumber = 1
            ),
            OpenAIStreamEvent.ResponseFunctionCallArgumentsDelta(
                itemId = firstItemId,
                outputIndex = 0,
                delta = "{\"first\":",
                sequenceNumber = 2
            ),
            OpenAIStreamEvent.ResponseOutputItemAdded(
                item = Item.FunctionToolCall(
                    arguments = "",
                    callId = secondCallId,
                    name = secondName,
                    id = secondItemId,
                    status = OpenAIInputStatus.IN_PROGRESS
                ),
                outputIndex = 1,
                sequenceNumber = 3
            ),
            OpenAIStreamEvent.ResponseFunctionCallArgumentsDelta(
                itemId = secondItemId,
                outputIndex = 1,
                delta = "{\"second\":true}",
                sequenceNumber = 4
            ),
            OpenAIStreamEvent.ResponseFunctionCallArgumentsDelta(
                itemId = firstItemId,
                outputIndex = 0,
                delta = "true}",
                sequenceNumber = 5
            ),
            OpenAIStreamEvent.ResponseOutputItemDone(
                item = Item.FunctionToolCall(
                    arguments = "{\"second\":true}",
                    callId = secondCallId,
                    name = secondName,
                    id = secondItemId,
                    status = OpenAIInputStatus.COMPLETED
                ),
                outputIndex = 1,
                sequenceNumber = 6
            ),
            OpenAIStreamEvent.ResponseOutputItemDone(
                item = Item.FunctionToolCall(
                    arguments = "{\"first\":true}",
                    callId = firstCallId,
                    name = firstName,
                    id = firstItemId,
                    status = OpenAIInputStatus.COMPLETED
                ),
                outputIndex = 0,
                sequenceNumber = 7
            ),
            completedResponseEvent(sequenceNumber = 8)
        )
        val client = OpenAILLMClient(
            settings = OpenAIClientSettings(baseUrl = "https://unused.test"),
            httpClient = streamingTransport { events }
        )

        val frames = client.executeStreaming(
            prompt = Prompt(
                messages = listOf(Message.User("Use both tools", RequestMetaInfo.Empty)),
                id = "test",
                params = OpenAIResponsesParams()
            ),
            model = OpenAIModels.Chat.GPT4o
        ).toList()

        assertEquals(
            listOf(
                StreamFrame.ToolCallDelta(firstCallId, firstName, "{\"first\":", 0),
                StreamFrame.ToolCallDelta(secondCallId, secondName, "{\"second\":true}", 1),
                StreamFrame.ToolCallDelta(firstCallId, firstName, "true}", 0),
                StreamFrame.ToolCallComplete(secondCallId, secondName, "{\"second\":true}", 1),
                StreamFrame.ToolCallComplete(firstCallId, firstName, "{\"first\":true}", 0)
            ),
            frames.dropLast(1)
        )
        assertIs<StreamFrame.End>(frames.last())
    }

    @Test
    fun testResponsesStreamingFallsBackForUnknownAndCompletedItems() = runTest {
        val events = listOf(
            OpenAIStreamEvent.ResponseOutputItemAdded(Item.Text("ignored"), 0, 1),
            OpenAIStreamEvent.ResponseOutputItemAdded(toolCall(id = null), 0, 2),
            argumentDelta("unknown", 3),
            OpenAIStreamEvent.ResponseOutputItemAdded(toolCall(), 0, 4),
            argumentDelta("item", 5),
            OpenAIStreamEvent.ResponseOutputItemDone(toolCall(), 0, 6),
            argumentDelta("item", 7),
            OpenAIStreamEvent.ResponseOutputItemDone(toolCall(id = null), 0, 8),
            completedResponseEvent(9)
        )
        val frames = toolCallStream(streamingTransport { events }).toList()

        assertEquals(
            listOf(
                StreamFrame.ToolCallDelta("unknown", null, "{}", 0),
                StreamFrame.ToolCallDelta("call", "lookup", "{}", 0),
                StreamFrame.ToolCallComplete("call", "lookup", "{}", 0),
                StreamFrame.ToolCallDelta("item", null, "{}", 0),
                StreamFrame.ToolCallComplete("call", "lookup", "{}", 0)
            ),
            frames.dropLast(1)
        )
        assertIs<StreamFrame.End>(frames.last())
    }

    @Test
    fun testResponsesStreamingDoesNotRetainIdentityAfterCancelledCollection() = runTest {
        var collection = 0
        val stream = toolCallStream(
            streamingTransport {
                collection++
                if (collection == 1) {
                    listOf(
                        OpenAIStreamEvent.ResponseOutputItemAdded(toolCall(), 0, 1),
                        argumentDelta("item", 2),
                        completedResponseEvent(3)
                    )
                } else {
                    listOf(argumentDelta("item", 1), completedResponseEvent(2))
                }
            }
        )

        assertEquals(listOf(StreamFrame.ToolCallDelta("call", "lookup", "{}", 0)), stream.take(1).toList())
        val recollected = stream.toList()
        assertEquals(StreamFrame.ToolCallDelta("item", null, "{}", 0), recollected.first())
        assertIs<StreamFrame.End>(recollected.last())
    }

    private fun toolCall(id: String? = "item") = Item.FunctionToolCall(
        arguments = "{}",
        callId = "call",
        name = "lookup",
        id = id
    )

    private fun argumentDelta(itemId: String, sequenceNumber: Int) =
        OpenAIStreamEvent.ResponseFunctionCallArgumentsDelta(itemId, 0, "{}", sequenceNumber)

    private fun toolCallStream(transport: KoogHttpClient): Flow<StreamFrame> = OpenAILLMClient(
        settings = OpenAIClientSettings(baseUrl = "https://unused.test"),
        httpClient = transport
    ).executeStreaming(
        prompt = Prompt(
            messages = listOf(Message.User("Use lookup", RequestMetaInfo.Empty)),
            id = "test",
            params = OpenAIResponsesParams()
        ),
        model = OpenAIModels.Chat.GPT4o
    )

    private fun streamingTransport(events: () -> List<OpenAIStreamEvent>): KoogHttpClient = object : KoogHttpClient {
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
        ): Flow<O> = flow {
            events().forEach { event ->
                val data = streamingJson.encodeToString(OpenAIStreamEvent.serializer(), event)
                processStreamingChunk(decodeStreamingResponse(data))?.let { emit(it) }
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

    private fun completedResponseEvent(sequenceNumber: Int): OpenAIStreamEvent.ResponseCompleted =
        OpenAIStreamEvent.ResponseCompleted(
            response = OpenAIResponsesAPIResponse(
                created = 1716920005,
                id = "response_id",
                model = "gpt-5",
                output = emptyList(),
                parallelToolCalls = true,
                status = OpenAIInputStatus.COMPLETED,
                text = OpenAITextConfig(),
                usage = null
            ),
            sequenceNumber = sequenceNumber
        )
}
