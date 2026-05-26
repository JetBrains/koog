package ai.koog.agents.core.agent.config

import ai.koog.prompt.message.MessagePart
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ToolCallDescriberTest {

    private companion object {
        private val describer = ToolCallDescriber.JSON

        private val testToolCall = MessagePart.Tool.Call(
            id = "test-call-id",
            tool = "test-tool",
            args = """{"param": "value"}""",
        )

        private val testToolResult = MessagePart.Tool.Result(
            id = "test-call-id",
            tool = "test-tool",
            output = "Test result content",
        )
    }

    @Test
    fun testDescribeToolCall() {
        val result = describer.describeToolCall(testToolCall)
        val expectedContent =
            "{\"tool_call_id\":\"test-call-id\",\"tool_name\":\"test-tool\",\"tool_args\":{\"param\":\"value\"}}"

        assertEquals(expectedContent, result)
    }

    @Test
    fun testDescribeToolResult() {
        val result = describer.describeToolResult(testToolResult)
        val expectedContent =
            "{\"tool_call_id\":\"test-call-id\",\"tool_name\":\"test-tool\",\"tool_result\":\"Test result content\"}"

        assertEquals(expectedContent, result)
    }

    @Test
    fun testDescribeToolCallWithNullId() {
        val nullIdToolCall = MessagePart.Tool.Call(
            id = null,
            tool = "test-tool",
            args = """{"param": "value"}""",
        )

        val result = describer.describeToolCall(nullIdToolCall)
        val expectedContent = "{\"tool_name\":\"test-tool\",\"tool_args\":{\"param\":\"value\"}}"

        assertEquals(expectedContent, result)
    }

    @Test
    fun testDescribeToolResultWithNullId() {
        val nullIdToolResult = MessagePart.Tool.Result(
            id = null,
            tool = "test-tool",
            output = "Test result content",
        )

        val result = describer.describeToolResult(nullIdToolResult)
        val expectedContent =
            "{\"tool_name\":\"test-tool\",\"tool_result\":\"Test result content\"}"

        assertEquals(expectedContent, result)
    }

    @Test
    fun testDescribeToolCallWithEmptyContent() {
        val emptyContentToolCall = MessagePart.Tool.Call(
            id = "test-call-id",
            tool = "test-tool",
            args = "{}",
        )

        val result = describer.describeToolCall(emptyContentToolCall)
        val expectedContent = "{\"tool_call_id\":\"test-call-id\",\"tool_name\":\"test-tool\",\"tool_args\":{}}"

        assertEquals(expectedContent, result)
    }

    @Test
    fun testDescribeToolResultWithEmptyContent() {
        val emptyContentToolResult = MessagePart.Tool.Result(
            id = "test-call-id",
            tool = "test-tool",
            output = "",
        )

        val result = describer.describeToolResult(emptyContentToolResult)
        val expectedContent = "{\"tool_call_id\":\"test-call-id\",\"tool_name\":\"test-tool\",\"tool_result\":\"\"}"

        assertEquals(expectedContent, result)
    }

    @Test
    fun testDescribeToolCallWithSpecialCharacters() {
        val specialCharsToolCall = MessagePart.Tool.Call(
            id = "test-call-id",
            tool = "test-tool",
            args = """{"param": "value with \"quotes\" and \\ backslashes"}""",
        )

        val result = describer.describeToolCall(specialCharsToolCall)
        val expectedContent =
            "{\"tool_call_id\":\"test-call-id\",\"tool_name\":\"test-tool\",\"tool_args\":{\"param\":\"value with \\\"quotes\\\" and \\\\ backslashes\"}}"

        assertEquals(expectedContent, result)
    }

    @Test
    fun testDescribeToolResultWithSpecialCharacters() {
        val specialCharsToolResult = MessagePart.Tool.Result(
            id = "test-call-id",
            tool = "test-tool",
            output = "Result with \"quotes\" and \\ backslashes",
        )

        val result = describer.describeToolResult(specialCharsToolResult)
        val expectedContent =
            "{\"tool_call_id\":\"test-call-id\",\"tool_name\":\"test-tool\",\"tool_result\":\"Result with \\\"quotes\\\" and \\\\ backslashes\"}"

        assertEquals(expectedContent, result)
    }

    @Test
    fun testDescribeToolCallWithInvalidJsonContent() {
        val invalidJsonToolCall = MessagePart.Tool.Call(
            id = "test-call-id",
            tool = "test-tool",
            args = "{invalid json",
        )

        val result = describer.describeToolCall(invalidJsonToolCall)

        assertTrue(result.contains("\"tool_call_id\":\"test-call-id\""))
        assertTrue(result.contains("\"tool_name\":\"test-tool\""))
        assertTrue(result.contains("\"tool_args_error\":\"Failed to parse tool arguments:"))
    }

    @Test
    fun testDescribeToolResultWithInvalidJsonContent() {
        val invalidJsonToolResult = MessagePart.Tool.Result(
            id = "test-call-id",
            tool = "test-tool",
            output = "{invalid json",
        )

        val result = describer.describeToolResult(invalidJsonToolResult)
        val expectedContent =
            "{\"tool_call_id\":\"test-call-id\",\"tool_name\":\"test-tool\",\"tool_result\":\"{invalid json\"}"

        assertEquals(expectedContent, result)
    }

    @Test
    fun testDescribeToolCallWithEmptyToolName() {
        val emptyToolNameCall = MessagePart.Tool.Call(
            id = "test-call-id",
            tool = "",
            args = """{"param": "value"}""",
        )

        val result = describer.describeToolCall(emptyToolNameCall)
        val expectedContent =
            "{\"tool_call_id\":\"test-call-id\",\"tool_name\":\"\",\"tool_args\":{\"param\":\"value\"}}"

        assertEquals(expectedContent, result)
    }

    @Test
    fun testDescribeToolResultWithEmptyToolName() {
        val emptyToolNameResult = MessagePart.Tool.Result(
            id = "test-call-id",
            tool = "",
            output = "Test result content",
        )

        val result = describer.describeToolResult(emptyToolNameResult)
        val expectedContent =
            "{\"tool_call_id\":\"test-call-id\",\"tool_name\":\"\",\"tool_result\":\"Test result content\"}"

        assertEquals(expectedContent, result)
    }

    @Test
    fun testDescribeToolCallWithNullContent() {
        val nullContentToolCall = MessagePart.Tool.Call(
            id = "test-call-id",
            tool = "test-tool",
            args = "null",
        )

        val result = describer.describeToolCall(nullContentToolCall)

        assertTrue(result.contains("\"tool_call_id\":\"test-call-id\""))
        assertTrue(result.contains("\"tool_name\":\"test-tool\""))
        assertTrue(result.contains("\"tool_args_error\":\"Failed to parse tool arguments:"))
        assertTrue(result.contains("IllegalArgumentException"))
    }

    @Test
    fun testDescribeToolResultWithNullContent() {
        val nullContentToolResult = MessagePart.Tool.Result(
            id = "test-call-id",
            tool = "test-tool",
            output = "null",
        )

        val result = describer.describeToolResult(nullContentToolResult)
        val expectedContent = "{\"tool_call_id\":\"test-call-id\",\"tool_name\":\"test-tool\",\"tool_result\":\"null\"}"

        assertEquals(expectedContent, result)
    }

    @Test
    fun testDescribeToolCallWithLargeContent() {
        val largeContent = buildString {
            append("{")
            for (i in 1..1000) {
                if (i > 1) append(",")
                append("\"key$i\":\"value$i\"")
            }
            append("}")
        }

        val largeContentToolCall = MessagePart.Tool.Call(
            id = "test-call-id",
            tool = "test-tool",
            args = largeContent,
        )

        val result = describer.describeToolCall(largeContentToolCall)

        assertTrue(result.isNotEmpty())
    }

    @Test
    fun testDescribeToolCallWithNonJsonContent() {
        val nonJsonToolCall = MessagePart.Tool.Call(
            id = "test-call-id",
            tool = "test-tool",
            args = "This is not JSON",
        )

        val result = describer.describeToolCall(nonJsonToolCall)

        assertTrue(result.contains("\"tool_call_id\":\"test-call-id\""))
        assertTrue(result.contains("\"tool_name\":\"test-tool\""))
        assertTrue(result.contains("\"tool_args_error\":\"Failed to parse tool arguments:"))
    }

    @Test
    fun testDescribeResultCallWithNonJsonContent() {
        val nonJsonToolResult = MessagePart.Tool.Result(
            id = "test-call-id",
            tool = "test-tool",
            output = "This is not JSON",
        )

        val result = describer.describeToolResult(nonJsonToolResult)
        val expectedContent =
            "{\"tool_call_id\":\"test-call-id\",\"tool_name\":\"test-tool\",\"tool_result\":\"This is not JSON\"}"

        assertEquals(expectedContent, result)
    }
}
