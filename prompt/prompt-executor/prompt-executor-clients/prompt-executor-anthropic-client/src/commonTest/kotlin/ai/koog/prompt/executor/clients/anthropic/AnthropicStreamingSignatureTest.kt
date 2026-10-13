package ai.koog.prompt.executor.clients.anthropic

import ai.koog.http.client.KoogHttpClient
import ai.koog.prompt.Prompt
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.streaming.IncompleteStreamException
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.prompt.streaming.toMessageResponse
import kotlinx.coroutines.flow.Flow
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
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class AnthropicStreamingSignatureTest {
    private val model = AnthropicModels.Sonnet_4
    private val prompt = Prompt(listOf(Message.User("hello", RequestMetaInfo.Empty)), "signature-test")

    private fun start(index: Int = 0, text: String = "", signature: String = "") =
        """{"type":"content_block_start","index":$index,"content_block":{"type":"thinking","thinking":"$text","signature":"$signature"}}"""

    private fun text(index: Int = 0, value: String = "thought") =
        """{"type":"content_block_delta","index":$index,"delta":{"type":"thinking_delta","thinking":"$value"}}"""

    private fun signature(index: Int = 0, value: String = "sig-a") =
        """{"type":"content_block_delta","index":$index,"delta":{"type":"signature_delta","signature":"$value"}}"""

    private fun stop(index: Int = 0) = """{"type":"content_block_stop","index":$index}"""

    private val end =
        """{"type":"message_delta","delta":{"stop_reason":"end_turn"},"usage":{"output_tokens":5}}"""

    private val tool = listOf(
        """{"type":"content_block_start","index":2,"content_block":{"type":"tool_use","id":"call-1","name":"lookup","input":{}}}""",
        """{"type":"content_block_delta","index":2,"delta":{"type":"input_json_delta","partial_json":"{}"}}""",
        stop(2)
    )

    private suspend fun checkReplay(
        events: List<String>,
        expected: List<Pair<String, String>>,
        withToolResult: Boolean = false
    ): List<StreamFrame> {
        AnthropicLLMClient(httpClient = SignatureHttpClient(events)).use { client ->
            val frames = client.executeStreaming(prompt, model).toList()
            val reasoning = frames.filterIsInstance<StreamFrame.ReasoningComplete>()
            assertEquals(expected.map { it.second }, reasoning.map { it.encrypted })

            val messages = buildList {
                add(frames.toMessageResponse())
                if (withToolResult) {
                    add(
                        Message.User(
                            parts = listOf(MessagePart.Tool.Result(id = "call-1", tool = "lookup", output = "ok")),
                            metaInfo = RequestMetaInfo.Empty
                        )
                    )
                }
            }
            val request = Json.parseToJsonElement(
                client.createAnthropicRequest(Prompt(messages, "replay"), emptyList(), model, false)
            ).jsonObject
            val requestMessages = request.getValue("messages").jsonArray
            val blocks = requestMessages
                .filter { it.jsonObject.getValue("role").jsonPrimitive.content == "assistant" }
                .flatMap { it.jsonObject.getValue("content").jsonArray }
            val thoughts = blocks.filter { it.jsonObject["type"]?.jsonPrimitive?.content == "thinking" }
            assertEquals(
                expected,
                thoughts.map {
                    val block = it.jsonObject
                    block.getValue("thinking").jsonPrimitive.content to block.getValue("signature").jsonPrimitive.content
                }
            )
            if (withToolResult) {
                assertEquals("tool_use", blocks.last().jsonObject.getValue("type").jsonPrimitive.content)
                assertEquals("call-1", blocks.last().jsonObject.getValue("id").jsonPrimitive.content)
                val result = requestMessages.last().jsonObject.getValue("content").jsonArray.single().jsonObject
                assertEquals("tool_result", result.getValue("type").jsonPrimitive.content)
                assertEquals("call-1", result.getValue("tool_use_id").jsonPrimitive.content)
                val output = result.getValue("content").jsonArray.single().jsonObject
                assertEquals("text", output.getValue("type").jsonPrimitive.content)
                assertEquals("ok", output.getValue("text").jsonPrimitive.content)
            }
            return frames
        }
    }

    @Test
    fun testInitialSignatureReplay() = runTest {
        checkReplay(listOf(start(text = "thought", signature = "sig-a"), stop(), end), listOf("thought" to "sig-a"))
    }

    @Test
    fun testDeltaSignatureReplayThroughTool() = runTest {
        checkReplay(
            listOf(start(), text(), signature(), stop()) + tool + end,
            listOf("thought" to "sig-a"),
            withToolResult = true
        )
    }

    @Test
    fun testSignatureOnlyReplayThroughTool() = runTest {
        checkReplay(listOf(start(), signature(), stop()) + tool + end, listOf("" to "sig-a"), withToolResult = true)
    }

    @Test
    fun testSignatureUpdateDoesNotEmitContentlessDelta() = runTest {
        val frames = checkReplay(listOf(start(), text(), signature(), stop(), end), listOf("thought" to "sig-a"))
        assertFalse(frames.filterIsInstance<StreamFrame.ReasoningDelta>().any { it.text == null && it.summary == null })
    }

    @Test
    fun testConsecutiveThinkingBlocksReplay() = runTest {
        checkReplay(
            listOf(start(), text(), signature(), stop(), start(1), text(1, "second"), signature(1, "sig-b"), stop(1)) +
                tool + end,
            listOf("thought" to "sig-a", "second" to "sig-b"),
            withToolResult = true
        )
    }

    @Test
    fun testEndFlushWithMessageDelta() = runTest {
        checkReplay(listOf(start(), text(), signature(), end), listOf("thought" to "sig-a"))
    }

    @Test
    fun testTruncatedStreamRejected() = runTest {
        AnthropicLLMClient(httpClient = SignatureHttpClient(listOf(start(), text(), signature(), stop()))).use { client ->
            assertFailsWith<IncompleteStreamException> { client.executeStreaming(prompt, model).toList() }
        }
    }
}

private class SignatureHttpClient(private val events: List<String>) : KoogHttpClient {
    override val clientName = "signature-test"

    override suspend fun <R : Any> get(
        path: String,
        responseType: KClass<R>,
        parameters: Map<String, String>,
        headers: Map<String, String>
    ): R = error("Unexpected GET")

    override suspend fun <T : Any, R : Any> post(
        path: String,
        requestBody: T,
        requestBodyType: KClass<T>,
        responseType: KClass<R>,
        parameters: Map<String, String>,
        headers: Map<String, String>
    ): R = error("Unexpected POST")

    override fun <T : Any, R : Any, O : Any> sse(
        path: String,
        requestBody: T,
        requestBodyType: KClass<T>,
        dataFilter: (String?) -> Boolean,
        decodeStreamingResponse: (String) -> R,
        processStreamingChunk: (R) -> O?,
        parameters: Map<String, String>,
        headers: Map<String, String>
    ): Flow<O> = flow {
        events.forEach { raw ->
            if (dataFilter(raw)) {
                processStreamingChunk(decodeStreamingResponse(raw))?.let { emit(it) }
            }
        }
    }

    override fun <T : Any> lines(
        path: String,
        requestBody: T,
        requestBodyType: KClass<T>,
        parameters: Map<String, String>,
        headers: Map<String, String>
    ): Flow<String> = error("Unexpected lines")

    override fun close() = Unit
}
