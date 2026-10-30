package ai.koog.prompt.executor.clients.openai

import ai.koog.http.client.KoogHttpClient
import ai.koog.prompt.Prompt
import ai.koog.prompt.executor.clients.openai.models.Item
import ai.koog.prompt.executor.clients.openai.models.OpenAIInputStatus
import ai.koog.prompt.executor.clients.openai.models.OpenAIResponsesAPIResponse
import ai.koog.prompt.executor.clients.openai.models.OpenAITextConfig
import ai.koog.prompt.executor.clients.openai.models.OutputContent
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.prompt.streaming.toMessageResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Covers the reasoning output items of the Responses API streaming path. A reasoning item that carries only
 * `encrypted_content` has to reach the caller, because stateless multi-turn reasoning replays it on the next request.
 */
class OpenAIResponsesStreamingReasoningTest {

    private val encryptedContent = "gAAAAABencryptedreasoning"

    //language=json
    private val encryptedReasoningItemDone = """
        {
          "type": "response.output_item.done",
          "output_index": 0,
          "sequence_number": 1,
          "item": {
            "type": "reasoning",
            "id": "rs_encrypted",
            "summary": [],
            "encrypted_content": "$encryptedContent",
            "status": "completed"
          }
        }
    """.trimIndent()

    //language=json
    private val hiddenReasoningItemDone = """
        {
          "type": "response.output_item.done",
          "output_index": 0,
          "sequence_number": 1,
          "item": {
            "type": "reasoning",
            "id": "rs_hidden",
            "summary": [],
            "status": "completed"
          }
        }
    """.trimIndent()

    //language=json
    private val responseCompleted = """
        {
          "type": "response.completed",
          "sequence_number": 2,
          "response": {
            "created_at": 0,
            "id": "resp_1",
            "model": "gpt-5",
            "output": [],
            "parallel_tool_calls": false,
            "status": "completed",
            "text": {}
          }
        }
    """.trimIndent()

    @Test
    fun testStreamedEncryptedReasoningIsEmittedAndReplayedToProvider() = runTest {
        val transport = ResponsesStreamKoogHttpClient(listOf(encryptedReasoningItemDone, responseCompleted))
        val client = OpenAILLMClient(
            settings = OpenAIClientSettings(baseUrl = "https://unused.test"),
            httpClient = transport
        )
        val question = Message.User("What is 2 + 2?", RequestMetaInfo.Empty)

        val frames = client.executeStreaming(
            prompt = Prompt(messages = listOf(question), id = "p-stream", params = OpenAIResponsesParams()),
            model = OpenAIModels.Chat.GPT5
        ).toList()

        val reasoning = frames.filterIsInstance<StreamFrame.ReasoningComplete>().single()
        assertEquals("rs_encrypted", reasoning.id)
        assertEquals(emptyList<String>(), reasoning.content)
        assertEquals(emptyList<String>(), reasoning.summary)
        assertEquals(encryptedContent, reasoning.encrypted)
        assertEquals(0, reasoning.index)
        assertIs<StreamFrame.End>(frames.last())

        // Keeping the item only pays off if the next request carries it back, so follow the round trip through.
        val assistant = frames.toMessageResponse()
        val reasoningPart = assistant.parts.filterIsInstance<MessagePart.Reasoning>().single()
        assertEquals(encryptedContent, reasoningPart.encrypted)

        client.execute(
            prompt = Prompt(
                messages = listOf(question, assistant),
                id = "p-followup",
                params = OpenAIResponsesParams()
            ),
            model = OpenAIModels.Chat.GPT5
        )

        val body = Json.parseToJsonElement(assertNotNull(transport.lastPostBody)).jsonObject
        val replayed = body.getValue("input").jsonArray
            .single { it.jsonObject["type"]?.jsonPrimitive?.contentOrNull == "reasoning" }
            .jsonObject
        assertEquals("rs_encrypted", replayed["id"]?.jsonPrimitive?.contentOrNull)
        assertEquals(encryptedContent, replayed["encrypted_content"]?.jsonPrimitive?.contentOrNull)
    }

    @Test
    fun testHiddenReasoningWithoutEncryptedContentIsIgnored() = runTest {
        val client = OpenAILLMClient(
            settings = OpenAIClientSettings(baseUrl = "https://unused.test"),
            httpClient = ResponsesStreamKoogHttpClient(listOf(hiddenReasoningItemDone, responseCompleted))
        )

        val frames = client.executeStreaming(
            prompt = Prompt(
                messages = listOf(Message.User("What is 2 + 2?", RequestMetaInfo.Empty)),
                id = "p-stream",
                params = OpenAIResponsesParams()
            ),
            model = OpenAIModels.Chat.GPT5
        ).toList()

        assertTrue(frames.none { it is StreamFrame.ReasoningComplete })
        assertIs<StreamFrame.End>(frames.single())
    }
}

/**
 * A [KoogHttpClient] that replays the given SSE [events] through the decoding of the client under test, and captures
 * the body of a following request. MockEngine cannot drive Ktor SSE reliably in tests.
 */
private class ResponsesStreamKoogHttpClient(private val events: List<String>) : KoogHttpClient {

    var lastPostBody: String? = null
        private set

    override val clientName: String = "ResponsesStreamKoogHttpClient"

    override suspend fun <R : Any> get(
        path: String,
        responseType: KClass<R>,
        parameters: Map<String, String>,
        headers: Map<String, String>,
    ): R = error("GET is not expected in this test")

    @Suppress("UNCHECKED_CAST")
    override suspend fun <T : Any, R : Any> post(
        path: String,
        requestBody: T,
        requestBodyType: KClass<T>,
        responseType: KClass<R>,
        parameters: Map<String, String>,
        headers: Map<String, String>,
    ): R {
        lastPostBody = requestBody as String
        return OpenAIResponsesAPIResponse(
            created = 0,
            id = "resp_2",
            model = "gpt-5",
            output = listOf(
                Item.OutputMessage(content = listOf(OutputContent.Text(annotations = emptyList(), text = "4")))
            ),
            parallelToolCalls = false,
            status = OpenAIInputStatus.COMPLETED,
            text = OpenAITextConfig()
        ) as R
    }

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
        events.forEach { event ->
            if (dataFilter(event)) {
                processStreamingChunk(decodeStreamingResponse(event))?.let { emit(it) }
            }
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
