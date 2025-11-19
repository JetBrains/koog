package ai.koog.prompt.streaming

import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

internal class StreamFrameExtTest {

    @Test
    fun `convert Assistant Message to Append StreamFrame`() {
        assertEquals(
            expected = appendFrame("test"),
            actual = assistantMessage("test")
                .toStreamFrame()
        )
    }

    @Test
    fun `convert Call Tool Message to ToolCall StreamFrame`() {
        assertEquals(
            expected = toolCallFrame("123", "tool", "{}"),
            actual = toolCallMessage("123", "tool", "{}")
                .toStreamFrame()
        )
    }

    @Test
    fun `convert StreamFrame Iterable to Message List`() = runTest {
        val meta = ResponseMetaInfo.create(
            clock = Clock.System,
            inputTokensCount = 1337,
            outputTokensCount = 69,
            totalTokensCount = 420,
        )
        assertContentEquals(
            expected = listOf(
                toolCallMessage("123", "tool", "{}", meta),
                assistantMessage("test", null, meta),
            ),
            actual = listOf(
                appendFrame("te"),
                toolCallFrame("123", "tool", "{}"),
                appendFrame("st"),
                endFrame(null, meta),
            ).toMessageResponses()
        )
        assertContentEquals(
            expected = listOf(assistantMessage("test", "end", meta)),
            actual = listOf(
                appendFrame("test"),
                endFrame("end", meta),
            ).toMessageResponses()
        )
    }

    @Test
    fun `convert StreamFrame Iterable to Tool Call Message List`() {
        assertContentEquals(
            expected = listOf(toolCallMessage("456", "tool2", "{ a: 1 }")),
            actual = listOf(toolCallFrame("456", "tool2", "{ a: 1 }"))
                .toToolCallMessages()
        )
    }

    @Test
    fun `convert StreamFrame Iterable to Assistant Message`() {
        assertEquals(
            expected = assistantMessage("test"),
            actual = listOf(appendFrame("test")).toAssistantMessageOrNull()
        )
    }

    @Test
    fun `no Assistant Message in StreamFrame Iterable`() {
        assertEquals(
            expected = null,
            actual = listOf(endFrame()).toAssistantMessageOrNull()
        )
    }
}

private fun toolCallFrame(id: String, name: String, content: String) =
    StreamFrame.ToolCall(id, name, content)

private fun appendFrame(content: String) =
    StreamFrame.Append(content)

private fun endFrame(finishReason: String? = null, metaInfo: ResponseMetaInfo = ResponseMetaInfo.Empty) =
    StreamFrame.End(finishReason, metaInfo)

private fun toolCallMessage(id: String, name: String, content: String, info: ResponseMetaInfo = ResponseMetaInfo.Empty) =
    Message.Tool.Call(id, name, content, info)

private fun assistantMessage(content: String, finishReason: String? = null, metaInfo: ResponseMetaInfo = ResponseMetaInfo.Empty) =
    Message.Assistant(content, metaInfo, finishReason = finishReason)
