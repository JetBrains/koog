package ai.koog.prompt.streaming

import ai.koog.prompt.message.ResponseMetaInfo
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class StreamFrameFlowExtTest {

    @Test
    fun testRequireEndFrameWithEndFrameCompletesNormally() = runTest {
        val frames = flowOf(
            StreamFrame.TextDelta("Hello"),
            StreamFrame.End(finishReason = "stop")
        ).requireEndFrame().toList()

        assertContentEquals(
            listOf(
                StreamFrame.TextDelta("Hello"),
                StreamFrame.End(finishReason = "stop")
            ),
            frames
        )
    }

    @Test
    fun testRequireEndFrameWithoutEndFrameThrowsIncompleteStreamException() = runTest {
        val flow = flowOf(
            StreamFrame.TextDelta("Hello"),
            StreamFrame.TextDelta(" World")
        ).requireEndFrame()

        assertFailsWith<IncompleteStreamException> {
            flow.toList()
        }
    }

    @Test
    fun testRequireEndFrameOnEmptyFlowThrowsIncompleteStreamException() = runTest {
        val flow = emptyFlow<StreamFrame>().requireEndFrame()

        assertFailsWith<IncompleteStreamException> {
            flow.toList()
        }
    }

    @Test
    fun testRequireEndFrameDoesNotMaskExistingErrors() = runTest {
        val flow = flow<StreamFrame> {
            emit(StreamFrame.TextDelta("Hello"))
            throw RuntimeException("Connection lost")
        }.requireEndFrame()

        val exception = assertFailsWith<RuntimeException> {
            flow.toList()
        }

        assertFalse(
            exception is IncompleteStreamException,
            "Should propagate original exception, not IncompleteStreamException"
        )
    }

    @Test
    fun testRequireEndFrameWithEndFrameAmongMultipleFrames() = runTest {
        val meta = ResponseMetaInfo.Empty
        val frames = flowOf(
            StreamFrame.TextDelta("Hello", 0),
            StreamFrame.TextDelta(" World", 0),
            StreamFrame.TextComplete("Hello World", 0),
            StreamFrame.End(finishReason = "stop", metaInfo = meta)
        ).requireEndFrame().toList()

        assertContentEquals(
            listOf(
                StreamFrame.TextDelta("Hello", 0),
                StreamFrame.TextDelta(" World", 0),
                StreamFrame.TextComplete("Hello World", 0),
                StreamFrame.End(finishReason = "stop", metaInfo = meta)
            ),
            frames
        )
    }
}
