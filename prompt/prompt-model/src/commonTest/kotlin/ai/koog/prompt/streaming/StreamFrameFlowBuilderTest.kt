package ai.koog.prompt.streaming

import ai.koog.prompt.message.ResponseMetaInfo
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

class StreamFrameFlowBuilderTest {

    @Test
    fun testEmitTextDelta() = runTest {
        val frames = buildStreamFrameFlow {
            emitTextDelta("Hello", 0)
            emitTextDelta(" World", 0)
        }.toList()

        assertContentEquals(
            listOf(
                StreamFrame.TextDelta("Hello", 0),
                StreamFrame.TextDelta(" World", 0)
            ),
            frames
        )
    }

    @Test
    fun testEmitReasoningDelta() = runTest {
        val frames = buildStreamFrameFlow {
            emitReasoningDelta(text = "Thinking...", index = 0)
            emitReasoningDelta(text = " step 2", index = 0)
        }.toList()

        assertContentEquals(
            listOf(
                StreamFrame.ReasoningDelta(text = "Thinking...", index = 0),
                StreamFrame.ReasoningDelta(text = " step 2", index = 0)
            ),
            frames
        )
    }

    @Test
    fun testEmitReasoningSummaryDelta() = runTest {
        val frames = buildStreamFrameFlow {
            emitReasoningDelta(summary = "Summary part 1", index = 0)
            emitReasoningDelta(summary = " part 2", index = 0)
        }.toList()

        assertContentEquals(
            listOf(
                StreamFrame.ReasoningDelta(summary = "Summary part 1", index = 0),
                StreamFrame.ReasoningDelta(summary = " part 2", index = 0)
            ),
            frames
        )
    }

    @Test
    fun testEmitToolCallDelta() = runTest {
        val frames = buildStreamFrameFlow {
            emitToolCallDelta(id = "call_1", name = "calculator", args = "{\"a\":", 0)
            emitToolCallDelta(args = " 5}", index = 0)
        }.toList()

        assertContentEquals(
            listOf(
                StreamFrame.ToolCallDelta("call_1", "calculator", "{\"a\":", 0),
                StreamFrame.ToolCallDelta(null, null, " 5}", 0),
            ),
            frames
        )
    }

    @Test
    fun testEmitEnd() = runTest {
        val frames = buildStreamFrameFlow {
            emitEnd("stop")
        }.toList()

        assertContentEquals(
            listOf(
                StreamFrame.End("stop", ResponseMetaInfo.Empty)
            ),
            frames
        )
    }

    @Test
    fun testEmitToolCallDeltaWithoutIdAppendsToExisting() = runTest {
        val frames = buildStreamFrameFlow {
            emitToolCallDelta(id = "call_1", name = "search", args = "{\"q")
            emitToolCallDelta(args = "uery\":")
            emitToolCallDelta(args = "\"test\"}")
            emitEnd()
        }.toList()

        assertContentEquals(
            listOf(
                StreamFrame.ToolCallDelta("call_1", "search", "{\"q"),
                StreamFrame.ToolCallDelta(null, null, "uery\":"),
                StreamFrame.ToolCallDelta(null, null, "\"test\"}"),
                StreamFrame.ToolCallComplete("call_1", "search", "{\"query\":\"test\"}"),
                StreamFrame.End(null, ResponseMetaInfo.Empty)
            ),
            frames
        )
    }

    @Test
    fun testEmitToolCallDeltaWithIdCreatesNewPendingToolCall() = runTest {
        val frames = buildStreamFrameFlow {
            emitToolCallDelta(id = "call_1", name = "calculator", args = "{\"a\":", index = 0)
            emitToolCallDelta(args = " 5}", index = 0)
            emitToolCallDelta(id = "call_2", name = "calculator", args = "{\"b\":", index = 1)
            emitToolCallDelta(args = " 6}", index = 1)
            emitEnd()
        }.toList()

        assertContentEquals(
            listOf(
                StreamFrame.ToolCallDelta("call_1", "calculator", "{\"a\":", 0),
                StreamFrame.ToolCallDelta(null, null, " 5}", 0),
                StreamFrame.ToolCallComplete("call_1", "calculator", "{\"a\": 5}", 0),
                StreamFrame.ToolCallDelta("call_2", "calculator", "{\"b\":", 1),
                StreamFrame.ToolCallDelta(null, null, " 6}", 1),
                StreamFrame.ToolCallComplete("call_2", "calculator", "{\"b\": 6}", 1),
                StreamFrame.End(null, ResponseMetaInfo.Empty),
            ),
            frames
        )
    }

    @Test
    fun testEmitToolCallDeltaWithoutPreviousCallThrowsError() = runTest {
        assertFailsWith<StreamFrameFlowBuilderError.NoPartialToolCallToComplete> {
            buildStreamFrameFlow {
                emitToolCallDelta(args = "{\"a\": 5}")
            }.collect()
        }
    }

    @Test
    fun testSwitchingFromToolCallToTextEmitsPendingToolCall() = runTest {
        val frames = buildStreamFrameFlow {
            emitToolCallDelta(id = "call_1", name = "calculator", args = "{\"a\": 5}", 0)
            emitTextDelta("Result: ", 1)
            emitEnd()
        }.toList()

        assertContentEquals(
            listOf(
                StreamFrame.ToolCallDelta("call_1", "calculator", "{\"a\": 5}", 0),
                StreamFrame.ToolCallComplete("call_1", "calculator", "{\"a\": 5}", 0),
                StreamFrame.TextDelta("Result: ", 1),
                StreamFrame.TextComplete("Result: ", 1),
                StreamFrame.End(null, ResponseMetaInfo.Empty)
            ),
            frames
        )
    }

    @Test
    fun testSwitchingFromToolCallToReasoningEmitsPendingToolCall() = runTest {
        val frames = buildStreamFrameFlow {
            emitToolCallDelta(id = "call_1", name = "search", args = "{}", 0)
            emitReasoningDelta(text = "Now thinking...", index = 1)
            emitEnd()
        }.toList()

        assertContentEquals(
            listOf(
                StreamFrame.ToolCallDelta("call_1", "search", "{}", 0),
                StreamFrame.ToolCallComplete("call_1", "search", "{}", 0),
                StreamFrame.ReasoningDelta(text = "Now thinking...", index = 1),
                StreamFrame.ReasoningComplete(listOf("Now thinking..."), null, null, 1),
                StreamFrame.End(null, ResponseMetaInfo.Empty)
            ),
            frames
        )
    }

    @Test
    fun testSwitchingDifferentFramesEmitsPendingFrame() = runTest {
        val frames = buildStreamFrameFlow {
            emitTextDelta("Start with text", 0)
            emitToolCallDelta(id = "call_1", name = "calculator", args = "{\"a\": 5}", 1)
            emitTextDelta("Continue after tool with text", 2)
            emitReasoningDelta(text = "Now switch from text to thinking...", index = 3)
            emitReasoningDelta(summary = "Summary thinking", index = 3)
            emitToolCallDelta(id = "call_2", name = "search", args = "{}", 4)
            emitReasoningDelta(text = "Now switch from tool to thinking...", index = 5)
            emitReasoningDelta(summary = "Summary thinking", index = 5)
            emitTextDelta("Finally switch from reasoning to text ", 6)
            emitEnd()
        }.toList()

        assertContentEquals(
            listOf(
                StreamFrame.TextDelta("Start with text", 0),
                StreamFrame.TextComplete("Start with text", 0),
                StreamFrame.ToolCallDelta("call_1", "calculator", "{\"a\": 5}", 1),
                StreamFrame.ToolCallComplete("call_1", "calculator", "{\"a\": 5}", 1),
                StreamFrame.TextDelta("Continue after tool with text", 2),
                StreamFrame.TextComplete("Continue after tool with text", 2),
                StreamFrame.ReasoningDelta(text = "Now switch from text to thinking...", index = 3),
                StreamFrame.ReasoningDelta(summary = "Summary thinking", index = 3),
                StreamFrame.ReasoningComplete(
                    listOf("Now switch from text to thinking..."),
                    listOf("Summary thinking"),
                    null,
                    3
                ),
                StreamFrame.ToolCallDelta("call_2", "search", "{}", 4),
                StreamFrame.ToolCallComplete("call_2", "search", "{}", 4),
                StreamFrame.ReasoningDelta(text = "Now switch from tool to thinking...", index = 5),
                StreamFrame.ReasoningDelta(summary = "Summary thinking", index = 5),
                StreamFrame.ReasoningComplete(
                    listOf("Now switch from tool to thinking..."),
                    listOf("Summary thinking"),
                    null,
                    5
                ),
                StreamFrame.TextDelta("Finally switch from reasoning to text ", 6),
                StreamFrame.TextComplete("Finally switch from reasoning to text ", 6),
                StreamFrame.End(null, ResponseMetaInfo.Empty)
            ),
            frames
        )
    }

    @Test
    fun testEmitToolCallDeltaWithNullArgumentsDoesNotCorruptContent() = runTest {
        val frames = buildStreamFrameFlow {
            emitToolCallDelta(id = "call_1", name = "search", args = "{\"query\":")
            emitToolCallDelta(args = null)
            emitToolCallDelta(args = "\"test\"}")
            emitEnd()
        }.toList()

        assertContentEquals(
            listOf(
                StreamFrame.ToolCallDelta("call_1", "search", "{\"query\":"),
                StreamFrame.ToolCallDelta(null, null, null),
                StreamFrame.ToolCallDelta(null, null, "\"test\"}"),
                StreamFrame.ToolCallComplete("call_1", "search", "{\"query\":\"test\"}"),
                StreamFrame.End(null, ResponseMetaInfo.Empty)
            ),
            frames
        )
    }

    @Test
    fun testEmitEndFlushesAllPendingFrames() = runTest {
        val frames = buildStreamFrameFlow {
            emitToolCallDelta(id = "call_1", name = "tool", args = "{}")
            emitEnd("stop")
        }.toList()

        assertContentEquals(
            listOf(
                StreamFrame.ToolCallDelta("call_1", "tool", "{}"),
                StreamFrame.ToolCallComplete("call_1", "tool", "{}"),
                StreamFrame.End("stop", ResponseMetaInfo.Empty)
            ),
            frames
        )
    }
}
