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
            emitReasoningDelta("Thinking...", 0)
            emitReasoningDelta(" step 2", 0)
        }.toList()

        assertContentEquals(
            listOf(
                StreamFrame.ReasoningDelta("Thinking...", 0),
                StreamFrame.ReasoningDelta(" step 2", 0)
            ),
            frames
        )
    }

    @Test
    fun testEmitReasoningSummaryDelta() = runTest {
        val frames = buildStreamFrameFlow {
            emitReasoningSummaryDelta("Summary part 1", 0)
            emitReasoningSummaryDelta(" part 2", 0)
        }.toList()

        assertContentEquals(
            listOf(
                StreamFrame.ReasoningSummaryDelta("Summary part 1", 0),
                StreamFrame.ReasoningSummaryDelta(" part 2", 0)
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
    fun testEmitToolCallDeltaWithMismatchedIndexThrowsError() = runTest {
        assertFailsWith<StreamFrameFlowBuilderError.UnexpectedPartialToolCallIndex> {
            buildStreamFrameFlow {
                emitToolCallDelta(id = "call_1", name = "tool", args = "{", index = 0)
                emitToolCallDelta(args = "}", index = 1)
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
            emitReasoningDelta("Now thinking...", 1)
            emitEnd()
        }.toList()

        assertContentEquals(
            listOf(
                StreamFrame.ToolCallDelta("call_1", "search", "{}", 0),
                StreamFrame.ToolCallComplete("call_1", "search", "{}", 0),
                StreamFrame.ReasoningDelta("Now thinking...", 1),
                StreamFrame.ReasoningComplete(listOf("Now thinking..."), emptyList(), null, 1),
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
            emitReasoningDelta("Now switch from text to thinking...", 3)
            emitReasoningSummaryDelta("Summary thinking", 3)
            emitToolCallDelta(id = "call_2", name = "search", args = "{}", 4)
            emitReasoningDelta("Now switch from tool to thinking...", 5)
            emitReasoningSummaryDelta("Summary thinking", 5)
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
                StreamFrame.ReasoningDelta("Now switch from text to thinking...", 3),
                StreamFrame.ReasoningSummaryDelta("Summary thinking", 3),
                StreamFrame.ReasoningComplete(listOf("Now switch from text to thinking..."), listOf("Summary thinking"), null, 3),
                StreamFrame.ToolCallDelta("call_2", "search", "{}", 4),
                StreamFrame.ToolCallComplete("call_2", "search", "{}", 4),
                StreamFrame.ReasoningDelta("Now switch from tool to thinking...", 5),
                StreamFrame.ReasoningSummaryDelta("Summary thinking", 5),
                StreamFrame.ReasoningComplete(listOf("Now switch from tool to thinking..."), listOf("Summary thinking"), null, 5),
                StreamFrame.TextDelta("Finally switch from reasoning to text ", 6),
                StreamFrame.TextComplete("Finally switch from reasoning to text ", 6),
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

    @Test
    fun testToolCallWithIndexes() = runTest {
        val frames = buildStreamFrameFlow {
            emitToolCallDelta(id = "call_1", name = "tool1", args = "{", index = 0)
            emitToolCallDelta(args = "}", index = 0)
            emitToolCallDelta(id = "call_2", name = "tool2", args = "{", index = 1)
            emitToolCallDelta(args = "}", index = 1)
            emitEnd()
        }.toList()

        assertContentEquals(
            listOf(
                StreamFrame.ToolCallDelta("call_1", "tool1", "{", 0),
                StreamFrame.ToolCallDelta(null, null, "}", 0),
                StreamFrame.ToolCallComplete("call_1", "tool1", "{}", 0),
                StreamFrame.ToolCallDelta("call_2", "tool2", "{", 1),
                StreamFrame.ToolCallDelta(null, null, "}", 1),
                StreamFrame.ToolCallComplete("call_2", "tool2", "{}", 1),
                StreamFrame.End(null, ResponseMetaInfo.Empty)
            ),
            frames
        )
    }

    @Test
    fun testComplexMixedScenario() = runTest {
        val frames = buildStreamFrameFlow {
            emitReasoningDelta("Thinking...", 0)
            emitReasoningSummaryDelta("Summary", 0)
            emitTextDelta("Hello", 1)
            emitToolCallDelta(id = "call_1", name = "search", args = "{\"q\":\"test\"}", 2)
            emitEnd("stop")
        }.toList()

        assertContentEquals(
            listOf(
                StreamFrame.ReasoningDelta("Thinking...", 0),
                StreamFrame.ReasoningSummaryDelta("Summary", 0),
                StreamFrame.TextDelta("Hello", 1),
                StreamFrame.ToolCallDelta("call_1", "search", "{\"q\":\"test\"}", 2),
                StreamFrame.ToolCallComplete("call_1", "search", "{\"q\":\"test\"}", 2),
                StreamFrame.End("stop", ResponseMetaInfo.Empty)
            ),
            frames
        )
    }
}
