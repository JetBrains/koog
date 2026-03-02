package ai.koog.prompt.streaming

import ai.koog.prompt.message.ContentPart
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

internal class StreamFrameExtTest {

    @Test
    fun testMessageAssistantToStreamFrames() {
        val message = Message.Assistant(
            content = "Hello, World!",
            metaInfo = ResponseMetaInfo.Empty,
            finishReason = "stop"
        )

        val expectedFrames = listOf(
            StreamFrame.TextDelta("Hello, World!"),
            StreamFrame.TextComplete("Hello, World!"),
        )

        assertEquals(expectedFrames, message.toStreamFrames())
    }

    @Test
    fun testMessageAssistantWithMultiplePartsToStreamFrames() {
        val message = Message.Assistant(
            parts = listOf(
                ContentPart.Text("Hello"),
                ContentPart.Text("World")
            ),
            metaInfo = ResponseMetaInfo.Empty
        )

        val expectedFrames = listOf(
            StreamFrame.TextDelta("Hello"),
            StreamFrame.TextDelta("World"),
            StreamFrame.TextComplete("Hello\nWorld"),
        )

        assertEquals(expectedFrames, message.toStreamFrames())
    }

    @Test
    fun testMessageReasoningToStreamFrames() {
        val message = Message.Reasoning(
            parts = listOf(
                ContentPart.Text("Thinking step 1"),
                ContentPart.Text("Thinking step 2")
            ),
            summary = listOf(
                ContentPart.Text("Summary")
            ),
            encrypted = "encrypted_content",
            metaInfo = ResponseMetaInfo.Empty
        )

        val expectedFrames = listOf(
            StreamFrame.ReasoningDelta(text = "Thinking step 1"),
            StreamFrame.ReasoningDelta(text = "Thinking step 2"),
            StreamFrame.ReasoningDelta(summary = "Summary"),
            StreamFrame.ReasoningComplete(
                listOf("Thinking step 1", "Thinking step 2"),
                listOf("Summary"),
                "encrypted_content"
            ),
        )

        assertEquals(expectedFrames, message.toStreamFrames())
    }

    @Test
    fun testMessageToolCallToStreamFrames() {
        val message = Message.Tool.Call(
            id = "call_123",
            tool = "calculator",
            content = """{"operation": "add", "a": 1, "b": 2}""",
            metaInfo = ResponseMetaInfo.Empty
        )

        val expectedFrames = listOf(
            StreamFrame.ToolCallDelta("call_123", "calculator", """{"operation": "add", "a": 1, "b": 2}"""),
            StreamFrame.ToolCallComplete("call_123", "calculator", """{"operation": "add", "a": 1, "b": 2}"""),
        )

        assertEquals(expectedFrames, message.toStreamFrames())
    }

    @Test
    fun testListOfMessageResponsesToStreamFrames() {
        val messages = listOf(
            Message.Reasoning(
                parts = listOf(
                    ContentPart.Text("Thinking step 1"),
                    ContentPart.Text("Thinking step 2")
                ),
                summary = listOf(
                    ContentPart.Text("Summary")
                ),
                encrypted = "encrypted_content",
                metaInfo = ResponseMetaInfo.Empty
            ),
            Message.Assistant(
                parts = listOf(
                    ContentPart.Text("Hello"),
                    ContentPart.Text("World")
                ),
                metaInfo = ResponseMetaInfo.Empty
            ),
            Message.Tool.Call(
                id = "call_123",
                tool = "calculator",
                content = """{"operation": "add", "a": 1, "b": 2}""",
                metaInfo = ResponseMetaInfo.Empty
            )
        )

        val expectedFrames = listOf(
            StreamFrame.ReasoningDelta(text = "Thinking step 1", index = 0),
            StreamFrame.ReasoningDelta(text = "Thinking step 2", index = 0),
            StreamFrame.ReasoningDelta(summary = "Summary", index = 0),
            StreamFrame.ReasoningComplete(
                listOf("Thinking step 1", "Thinking step 2"),
                listOf("Summary"),
                "encrypted_content",
                0
            ),
            StreamFrame.TextDelta("Hello", 1),
            StreamFrame.TextDelta("World", 1),
            StreamFrame.TextComplete("Hello\nWorld", 1),
            StreamFrame.ToolCallDelta("call_123", "calculator", """{"operation": "add", "a": 1, "b": 2}""", 2),
            StreamFrame.ToolCallComplete("call_123", "calculator", """{"operation": "add", "a": 1, "b": 2}""", 2),
            StreamFrame.End(null, ResponseMetaInfo.Empty)
        )

        assertEquals(expectedFrames, messages.toStreamFrames())
    }

    @Test
    fun testStreamFramesToMessageAssistant() {
        val frames = listOf(
            StreamFrame.TextDelta("Hello, World!"),
            StreamFrame.TextComplete("Hello, World!"),
            StreamFrame.End("stop", ResponseMetaInfo.Empty)
        )

        val expectedMessages = listOf(
            Message.Assistant(
                parts = listOf(ContentPart.Text("Hello, World!")),
                finishReason = "stop",
                metaInfo = ResponseMetaInfo.Empty
            )
        )
        val messages = frames.toMessageResponses()

        assertEquals(expectedMessages, messages)
    }

    @Test
    fun testStreamFramesToMessageAssistantWithMultipleParts() {
        val frames = listOf(
            StreamFrame.TextDelta("Hello"),
            StreamFrame.TextDelta("World"),
            StreamFrame.TextComplete("Hello\nWorld"),
            StreamFrame.End(null, ResponseMetaInfo.Empty)
        )

        val expectedMessages = listOf(
            Message.Assistant(
                parts = listOf(ContentPart.Text("Hello\nWorld")),
                metaInfo = ResponseMetaInfo.Empty
            )
        )
        val messages = frames.toMessageResponses()

        assertEquals(expectedMessages, messages)
    }

    @Test
    fun testStreamFramesToMessageReasoning() {
        val frames = listOf(
            StreamFrame.ReasoningDelta("Thinking step 1"),
            StreamFrame.ReasoningDelta("Thinking step 2"),
            StreamFrame.ReasoningDelta(summary = "Summary"),
            StreamFrame.ReasoningComplete(
                listOf("Thinking step 1", "Thinking step 2"),
                listOf("Summary"),
                "encrypted_content"
            ),
            StreamFrame.End(null, ResponseMetaInfo.Empty)
        )

        val expectedMessages = listOf(
            Message.Reasoning(
                parts = listOf(ContentPart.Text("Thinking step 1"), ContentPart.Text("Thinking step 2")),
                summary = listOf(ContentPart.Text("Summary")),
                encrypted = "encrypted_content",
                metaInfo = ResponseMetaInfo.Empty
            )
        )

        val messages = frames.toMessageResponses()

        assertEquals(expectedMessages, messages)
    }

    @Test
    fun testStreamFramesToMessageToolCall() {
        val frames = listOf(
            StreamFrame.ToolCallDelta("call_123", "calculator", """{"operation": "add", "a": 1, "b": 2}"""),
            StreamFrame.ToolCallComplete("call_123", "calculator", """{"operation": "add", "a": 1, "b": 2}"""),
            StreamFrame.End(null, ResponseMetaInfo.Empty)
        )

        val expectedMessages = listOf(
            Message.Tool.Call(
                id = "call_123",
                tool = "calculator",
                content = """{"operation": "add", "a": 1, "b": 2}""",
                metaInfo = ResponseMetaInfo.Empty
            )
        )

        val messages = frames.toMessageResponses()

        assertEquals(expectedMessages, messages)
    }

    @Test
    fun testStreamFramesToListOfMessageResponses() {
        val frames = listOf(
            StreamFrame.ReasoningDelta(text = "Thinking step 1", index = 0),
            StreamFrame.ReasoningDelta(text = "Thinking step 2", index = 0),
            StreamFrame.ReasoningDelta(summary = "Summary", index = 0),
            StreamFrame.ReasoningComplete(
                listOf("Thinking step 1", "Thinking step 2"),
                listOf("Summary"),
                "encrypted_content",
                0
            ),
            StreamFrame.TextDelta("Hello", 1),
            StreamFrame.TextDelta("World", 1),
            StreamFrame.TextComplete("Hello\nWorld", 1),
            StreamFrame.ToolCallDelta("call_123", "calculator", """{"operation": "add", "a": 1, "b": 2}""", 2),
            StreamFrame.ToolCallComplete("call_123", "calculator", """{"operation": "add", "a": 1, "b": 2}""", 2),
            StreamFrame.End("stop", ResponseMetaInfo.Empty)
        )

        val expectedMessages = listOf(
            Message.Reasoning(
                parts = listOf(ContentPart.Text("Thinking step 1"), ContentPart.Text("Thinking step 2")),
                summary = listOf(ContentPart.Text("Summary")),
                encrypted = "encrypted_content",
                metaInfo = ResponseMetaInfo.Empty
            ),
            Message.Assistant(
                parts = listOf(ContentPart.Text("Hello\nWorld")),
                finishReason = "stop",
                metaInfo = ResponseMetaInfo.Empty
            ),
            Message.Tool.Call(
                id = "call_123",
                tool = "calculator",
                content = """{"operation": "add", "a": 1, "b": 2}""",
                metaInfo = ResponseMetaInfo.Empty
            )
        )

        val messages = frames.toMessageResponses()

        assertEquals(expectedMessages, messages)
    }

    @Test
    fun testToAssistantMessageOrNull() {
        val framesWithAssistant = listOf(
            StreamFrame.TextDelta("Hello"),
            StreamFrame.TextComplete("Hello"),
            StreamFrame.End(null, ResponseMetaInfo.Empty)
        )

        val assistant = framesWithAssistant.toAssistantMessageOrNull()

        assertIs<Message.Assistant>(assistant)
        assertEquals("Hello", assistant.content)
    }

    @Test
    fun testToAssistantMessageOrNullReturnsNull() {
        val framesWithoutAssistant = listOf(
            StreamFrame.ToolCallComplete("call_1", "tool", "{}"),
            StreamFrame.End(null, ResponseMetaInfo.Empty)
        )

        val assistant = framesWithoutAssistant.toAssistantMessageOrNull()

        assertNull(assistant)
    }

    @Test
    fun testToReasoningMessageOrNull() {
        val framesWithReasoning = listOf(
            StreamFrame.ReasoningComplete(listOf("Thinking")),
            StreamFrame.End(null, ResponseMetaInfo.Empty)
        )

        val reasoning = framesWithReasoning.toReasoningMessageOrNull()

        assertIs<Message.Reasoning>(reasoning)
        assertEquals("Thinking", reasoning.content)
    }

    @Test
    fun testToReasoningMessageOrNullReturnsNull() {
        val framesWithoutReasoning = listOf(
            StreamFrame.TextComplete("Hello"),
            StreamFrame.End(null, ResponseMetaInfo.Empty)
        )

        val reasoning = framesWithoutReasoning.toReasoningMessageOrNull()

        assertNull(reasoning)
    }

    @Test
    fun testToMessageResponsesWithEmptyFrames() {
        val frames = emptyList<StreamFrame>()

        val messages = frames.toMessageResponses()

        assertEquals(0, messages.size)
    }
}
