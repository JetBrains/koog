package ai.koog.prompt.streaming

import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.message.ResponseMetaInfo
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

internal class StreamFrameExtTest {

    @Test
    fun testMessageAssistantToStreamFrames() {
        val message = Message.Assistant(
            content = "Hello, World!",
            metaInfo = ResponseMetaInfo.Empty,
            finishReason = "stop"
        )

        val expectedFrames = listOf(
            StreamFrame.TextDelta("Hello, World!", index = 0),
            StreamFrame.TextComplete("Hello, World!", index = 0),
            StreamFrame.End("stop", ResponseMetaInfo.Empty)
        )

        assertEquals(expectedFrames, message.toStreamFrames())
    }

    @Test
    fun testMessageAssistantWithMultiplePartsToStreamFrames() {
        val message = Message.Assistant(
            parts = listOf(
                MessagePart.Text("Hello"),
                MessagePart.Text("World")
            ),
            metaInfo = ResponseMetaInfo.Empty
        )

        val expectedFrames = listOf(
            StreamFrame.TextDelta("Hello", index = 0),
            StreamFrame.TextComplete("Hello", index = 0),
            StreamFrame.TextDelta("World", index = 1),
            StreamFrame.TextComplete("World", index = 1),
            StreamFrame.End(null, ResponseMetaInfo.Empty)
        )

        assertEquals(expectedFrames, message.toStreamFrames())
    }

    @Test
    fun testMessageReasoningToStreamFrames() {
        val reasoningPart = MessagePart.Reasoning(
            id = "rs_123",
            content = listOf("Thinking step 1", "Thinking step 2"),
            summary = listOf("Summary"),
            encrypted = "encrypted_content",
        )

        val message = Message.Assistant(
            parts = listOf(reasoningPart),
            finishReason = "stop",
            metaInfo = ResponseMetaInfo.Empty
        )

        val expectedFrames = listOf(
            StreamFrame.ReasoningDelta(id = "rs_123", text = "Thinking step 1", index = 0),
            StreamFrame.ReasoningDelta(id = "rs_123", text = "Thinking step 2", index = 0),
            StreamFrame.ReasoningDelta(id = "rs_123", summary = "Summary", index = 0),
            StreamFrame.ReasoningComplete(
                id = "rs_123",
                content = listOf("Thinking step 1", "Thinking step 2"),
                summary = listOf("Summary"),
                encrypted = "encrypted_content",
                index = 0
            ),
            StreamFrame.End("stop", ResponseMetaInfo.Empty)
        )

        assertEquals(expectedFrames, message.toStreamFrames())
    }

    @Test
    fun testMessageToolCallToStreamFrames() {
        val toolCallPart = MessagePart.Tool.Call(
            id = "call_123",
            tool = "calculator",
            args = JsonObject(
                mapOf("operation" to JsonPrimitive("add"), "a" to JsonPrimitive(1), "b" to JsonPrimitive(2))
            ),
        )

        val message = Message.Assistant(
            parts = listOf(toolCallPart),
            finishReason = "tool_calls",
            metaInfo = ResponseMetaInfo.Empty
        )

        val expectedFrames = listOf(
            StreamFrame.ToolCallDelta("call_123", "calculator", """{"operation":"add","a":1,"b":2}""", index = 0),
            StreamFrame.ToolCallComplete("call_123", "calculator", """{"operation":"add","a":1,"b":2}""", index = 0),
            StreamFrame.End("tool_calls", ResponseMetaInfo.Empty)
        )

        assertEquals(expectedFrames, message.toStreamFrames())
    }

    @Test
    fun testListOfMessageResponsesToStreamFrames() {
        val message = Message.Assistant(
            parts = listOf(
                MessagePart.Reasoning(
                    id = "rs_123",
                    content = listOf("Thinking step 1", "Thinking step 2"),
                    summary = listOf("Summary"),
                    encrypted = "encrypted_content",
                ),
                MessagePart.Text("Hello"),
                MessagePart.Text("World"),
                MessagePart.Tool.Call(
                    id = "call_123",
                    tool = "calculator",
                    args = JsonObject(
                        mapOf("operation" to JsonPrimitive("add"), "a" to JsonPrimitive(1), "b" to JsonPrimitive(2))
                    ),
                )
            ),
            finishReason = "stop",
            metaInfo = ResponseMetaInfo.Empty
        )

        val expectedFrames = listOf(
            StreamFrame.ReasoningDelta(id = "rs_123", text = "Thinking step 1", index = 0),
            StreamFrame.ReasoningDelta(id = "rs_123", text = "Thinking step 2", index = 0),
            StreamFrame.ReasoningDelta(id = "rs_123", summary = "Summary", index = 0),
            StreamFrame.ReasoningComplete(
                id = "rs_123",
                content = listOf("Thinking step 1", "Thinking step 2"),
                summary = listOf("Summary"),
                encrypted = "encrypted_content",
                index = 0
            ),
            StreamFrame.TextDelta("Hello", 1),
            StreamFrame.TextComplete("Hello", 1),
            StreamFrame.TextDelta("World", 2),
            StreamFrame.TextComplete("World", 2),
            StreamFrame.ToolCallDelta("call_123", "calculator", """{"operation":"add","a":1,"b":2}""", 3),
            StreamFrame.ToolCallComplete("call_123", "calculator", """{"operation":"add","a":1,"b":2}""", 3),
            StreamFrame.End("stop", ResponseMetaInfo.Empty)
        )

        assertEquals(expectedFrames, message.toStreamFrames())
    }

    @Test
    fun testStreamFramesToMessageAssistant() {
        val frames = listOf(
            StreamFrame.TextDelta("Hello, World!"),
            StreamFrame.TextComplete("Hello, World!"),
            StreamFrame.End("stop", ResponseMetaInfo.Empty)
        )

        val expectedMessages = Message.Assistant(
            parts = listOf(MessagePart.Text("Hello, World!")),
            finishReason = "stop",
            metaInfo = ResponseMetaInfo.Empty
        )

        val message = frames.toMessageResponse()

        assertEquals(expectedMessages, message)
    }

    @Test
    fun testStreamFramesToMessageAssistantWithMultipleParts() {
        val frames = listOf(
            StreamFrame.TextDelta("Hello"),
            StreamFrame.TextDelta("World"),
            StreamFrame.TextComplete("Hello\nWorld"),
            StreamFrame.End(null, ResponseMetaInfo.Empty)
        )

        val expected = Message.Assistant(
            parts = listOf(MessagePart.Text("Hello\nWorld")),
            metaInfo = ResponseMetaInfo.Empty
        )
        val message = frames.toMessageResponse()

        assertEquals(expected, message)
    }

    @Test
    fun testStreamFramesToMessageReasoning() {
        val frames = listOf(
            StreamFrame.ReasoningDelta(id = "rs_123", "Thinking step 1"),
            StreamFrame.ReasoningDelta(id = "rs_123", "Thinking step 2"),
            StreamFrame.ReasoningDelta(id = "rs_123", summary = "Summary"),
            StreamFrame.ReasoningComplete(
                id = "rs_123",
                listOf("Thinking step 1", "Thinking step 2"),
                listOf("Summary"),
                "encrypted_content"
            ),
            StreamFrame.End(null, ResponseMetaInfo.Empty)
        )

        val expected = Message.Assistant(
            parts = listOf(
                MessagePart.Reasoning(
                    id = "rs_123",
                    content = listOf("Thinking step 1", "Thinking step 2"),
                    summary = listOf("Summary"),
                    encrypted = "encrypted_content",
                )
            ),
            metaInfo = ResponseMetaInfo.Empty
        )

        val message = frames.toMessageResponse()

        assertEquals(expected, message)
    }

    @Test
    fun testStreamFramesToMessageToolCall() {
        val frames = listOf(
            StreamFrame.ToolCallDelta("call_123", "calculator", """{"operation":"add","a":1,"b":2}"""),
            StreamFrame.ToolCallComplete("call_123", "calculator", """{"operation":"add","a":1,"b":2}"""),
            StreamFrame.End(null, ResponseMetaInfo.Empty)
        )

        val expected = Message.Assistant(
            parts = listOf(
                MessagePart.Tool.Call(
                    id = "call_123",
                    tool = "calculator",
                    args = JsonObject(
                        mapOf("operation" to JsonPrimitive("add"), "a" to JsonPrimitive(1), "b" to JsonPrimitive(2))
                    )
                )
            ),
            metaInfo = ResponseMetaInfo.Empty
        )

        val message = frames.toMessageResponse()

        assertEquals(expected, message)
    }

    @Test
    fun testStreamFramesToMessageWithMixedParts() {
        val frames = listOf(
            StreamFrame.ReasoningDelta(id = "rs_123", text = "Thinking step 1", index = 0),
            StreamFrame.ReasoningDelta(id = "rs_123", text = "Thinking step 2", index = 0),
            StreamFrame.ReasoningDelta(id = "rs_123", summary = "Summary", index = 0),
            StreamFrame.ReasoningComplete(
                id = "rs_123",
                listOf("Thinking step 1", "Thinking step 2"),
                listOf("Summary"),
                "encrypted_content",
                0
            ),
            StreamFrame.TextDelta("Hello", 1),
            StreamFrame.TextDelta("World", 1),
            StreamFrame.TextComplete("Hello\nWorld", 1),
            StreamFrame.ToolCallDelta("call_123", "calculator", """{"operation":"add","a":1,"b":2}""", 2),
            StreamFrame.ToolCallComplete("call_123", "calculator", """{"operation":"add","a":1,"b":2}""", 2),
            StreamFrame.End("stop", ResponseMetaInfo.Empty)
        )

        val expected = Message.Assistant(
            parts = listOf(
                MessagePart.Reasoning(
                    id = "rs_123",
                    content = listOf("Thinking step 1", "Thinking step 2"),
                    summary = listOf("Summary"),
                    encrypted = "encrypted_content",
                ),
                MessagePart.Text("Hello\nWorld"),
                MessagePart.Tool.Call(
                    id = "call_123",
                    tool = "calculator",
                    args = JsonObject(
                        mapOf("operation" to JsonPrimitive("add"), "a" to JsonPrimitive(1), "b" to JsonPrimitive(2))
                    )
                )
            ),
            finishReason = "stop",
            metaInfo = ResponseMetaInfo.Empty
        )

        val message = frames.toMessageResponse()

        assertEquals(expected, message)
    }

    @Test
    fun testToMessageResponsesDoNotSkipsEmptyTextCompleteFrames() {
        val frames = listOf(
            StreamFrame.TextComplete("Hello", index = 0),
            StreamFrame.ToolCallComplete("call_1", "tool", "{}", index = 1),
            StreamFrame.TextDelta("", index = 2),
            StreamFrame.TextComplete("", index = 2),
            StreamFrame.ToolCallComplete("call_2", "otherTool", """{"query":"test"}""", index = 3),
            StreamFrame.End("tool_calls", ResponseMetaInfo.Empty)
        )

        val message = frames.toMessageResponse()

        assertEquals(
            Message.Assistant(
                listOf(
                    MessagePart.Text("Hello"),
                    MessagePart.Tool.Call(
                        id = "call_1",
                        tool = "tool",
                        args = "{}",
                    ),
                    MessagePart.Text(""),
                    MessagePart.Tool.Call(
                        id = "call_2",
                        tool = "otherTool",
                        args = """{"query":"test"}""",
                    )
                ),
                finishReason = "tool_calls",
                metaInfo = ResponseMetaInfo.Empty
            ),
            message
        )
    }

    @Test
    fun testToMessageResponsesWithEmptyFrames() {
        val frames = emptyList<StreamFrame>()

        val message = frames.toMessageResponse()

        assertEquals(0, message.parts.size)
    }
}
