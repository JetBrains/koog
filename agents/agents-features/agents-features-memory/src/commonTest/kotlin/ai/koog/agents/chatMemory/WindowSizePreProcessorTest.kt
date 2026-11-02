package ai.koog.agents.chatMemory

import ai.koog.agents.chatMemory.feature.WindowSizePreProcessor
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class WindowSizePreProcessorTest {

    private val responseMeta = ResponseMetaInfo(Instant.DISTANT_PAST)

    private fun user(text: String) = Message.User(text, RequestMetaInfo.Empty)

    private fun assistant(text: String) = Message.Assistant(text, responseMeta)

    private fun toolCalls(vararg calls: MessagePart.Tool.Call) = Message.Assistant(calls.toList(), responseMeta)

    private fun toolResults(vararg results: MessagePart.Tool.Result) = Message.User(results.toList(), RequestMetaInfo.Empty)

    private fun call(id: String?, tool: String = "search") = MessagePart.Tool.Call(id = id, tool = tool, args = "{}")

    private fun result(id: String?, tool: String = "search") = MessagePart.Tool.Result(id = id, tool = tool, output = "ok")

    @Test
    fun testKeepsLastMessages() {
        val messages = listOf(user("1"), assistant("2"), user("3"), assistant("4"))

        assertEquals(messages.takeLast(2), WindowSizePreProcessor(2).preprocess(messages))
    }

    @Test
    fun testKeepsToolResultWhenCallIsInsideWindow() {
        val messages = listOf(user("q"), toolCalls(call("1")), toolResults(result("1")), assistant("a"))

        assertEquals(messages.takeLast(3), WindowSizePreProcessor(3).preprocess(messages))
    }

    @Test
    fun testDropsToolResultWhenCallIsOutsideWindow() {
        val messages = listOf(user("q"), toolCalls(call("1")), toolResults(result("1")), assistant("a"))

        assertEquals(listOf(assistant("a")), WindowSizePreProcessor(2).preprocess(messages))
    }

    @Test
    fun testDropsAllParallelToolResultsWhenCallsAreOutsideWindow() {
        val messages = listOf(
            toolCalls(call("1"), call("2")),
            toolResults(result("1"), result("2")),
            assistant("a"),
        )

        assertEquals(listOf(assistant("a")), WindowSizePreProcessor(2).preprocess(messages))
    }

    @Test
    fun testKeepsTextPartsWhenOnlyToolResultIsOrphaned() {
        val mixed = Message.User(listOf(result("1"), MessagePart.Text("also this")), RequestMetaInfo.Empty)
        val messages = listOf(toolCalls(call("1")), mixed, assistant("a"))

        assertEquals(
            listOf(Message.User("also this", RequestMetaInfo.Empty), assistant("a")),
            WindowSizePreProcessor(2).preprocess(messages),
        )
    }

    @Test
    fun testMatchesByToolNameWhenIdsAreMissing() {
        val messages = listOf(user("q"), toolCalls(call(null)), toolResults(result(null)), assistant("a"))

        assertEquals(messages.takeLast(3), WindowSizePreProcessor(3).preprocess(messages))
        assertEquals(listOf(assistant("a")), WindowSizePreProcessor(2).preprocess(messages))
    }

    @Test
    fun testWindowLargerThanHistoryIsUnchanged() {
        val messages = listOf(toolCalls(call("1")), toolResults(result("1")))

        assertEquals(messages, WindowSizePreProcessor(10).preprocess(messages))
    }
}
