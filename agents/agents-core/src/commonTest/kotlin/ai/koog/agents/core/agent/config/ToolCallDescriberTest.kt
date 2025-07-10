package ai.koog.agents.core.agent.config

import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant.Companion.fromEpochMilliseconds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ToolCallDescriberTest {

    private companion object {
        private val describer = ToolCallDescriber.JSON

        private val testClock = object : Clock {
            override fun now() = fromEpochMilliseconds(123)
        }

        private val testToolCall = Message.Tool.Call(
            id = "test-call-id",
            tool = "test-tool",
            content = """{"param": "value"}""",
            metaInfo = ResponseMetaInfo.create(testClock),
        )

        private val testToolResult = Message.Tool.Result(
            id = "test-call-id",
            tool = "test-tool",
            content = "Test result content",
            metaInfo = RequestMetaInfo.create(testClock),
        )
    }

    @Test
    fun testDescribeToolCall() {
        val result = describer.describeToolCall(testToolCall)
        val expectedContent =
            "{\"tool_call_id\":\"test-call-id\",\"tool_name\":\"test-tool\",\"tool_args\":{\"param\":\"value\"}}"

        assertEquals(expectedContent, result.content)
        assertEquals(testToolCall.metaInfo, result.metaInfo)
    }

    @Test
    fun testDescribeToolResult() {
        val result = describer.describeToolResult(testToolResult)
        val expectedContent =
            "{\"tool_call_id\":\"test-call-id\",\"tool_name\":\"test-tool\",\"tool_result\":\"Test result content\"}"

        assertEquals(expectedContent, result.content)
        assertEquals(testToolResult.metaInfo, result.metaInfo)
    }

    @Test
    fun testDescribeToolCallWithNullId() {
        val nullIdToolCall = Message.Tool.Call(
            id = null,
            tool = "test-tool",
            content = """{"param": "value"}""",
            metaInfo = ResponseMetaInfo.create(testClock),
        )

        val result = describer.describeToolCall(nullIdToolCall)
        val expectedContent = "{\"tool_name\":\"test-tool\",\"tool_args\":{\"param\":\"value\"}}"

        assertEquals(expectedContent, result.content)
        assertEquals(nullIdToolCall.metaInfo, result.metaInfo)
    }

    @Test
    fun testDescribeToolResultWithNullId() {
        val nullIdToolResult = Message.Tool.Result(
            id = null,
            tool = "test-tool",
            content = "Test result content",
            metaInfo = RequestMetaInfo.create(testClock),
        )

        val result = describer.describeToolResult(nullIdToolResult)
        val expectedContent =
            "{\"tool_name\":\"test-tool\",\"tool_result\":\"Test result content\"}"

        assertEquals(expectedContent, result.content)
        assertEquals(nullIdToolResult.metaInfo, result.metaInfo)
    }

    @Test
    fun testDescribeToolCallWithEmptyContent() {
        val emptyContentToolCall = Message.Tool.Call(
            id = "test-call-id",
            tool = "test-tool",
            content = "{}",
            metaInfo = ResponseMetaInfo.create(testClock),
        )

        val result = describer.describeToolCall(emptyContentToolCall)
        val expectedContent = "{\"tool_call_id\":\"test-call-id\",\"tool_name\":\"test-tool\",\"tool_args\":{}}"

        assertEquals(expectedContent, result.content)
        assertEquals(emptyContentToolCall.metaInfo, result.metaInfo)
    }

    @Test
    fun testDescribeToolResultWithEmptyContent() {
        val emptyContentToolResult = Message.Tool.Result(
            id = "test-call-id",
            tool = "test-tool",
            content = "",
            metaInfo = RequestMetaInfo.create(testClock),
        )

        val result = describer.describeToolResult(emptyContentToolResult)
        val expectedContent = "{\"tool_call_id\":\"test-call-id\",\"tool_name\":\"test-tool\",\"tool_result\":\"\"}"

        assertEquals(expectedContent, result.content)
        assertEquals(emptyContentToolResult.metaInfo, result.metaInfo)
    }

    @Test
    fun testDescribeToolCallWithSpecialCharacters() {
        val specialCharsToolCall = Message.Tool.Call(
            id = "test-call-id",
            tool = "test-tool",
            content = """{"param": "value with \"quotes\" and \\ backslashes"}""",
            metaInfo = ResponseMetaInfo.create(testClock),
        )

        val result = describer.describeToolCall(specialCharsToolCall)
        val expectedContent =
            "{\"tool_call_id\":\"test-call-id\",\"tool_name\":\"test-tool\",\"tool_args\":{\"param\":\"value with \\\"quotes\\\" and \\\\ backslashes\"}}"

        assertEquals(expectedContent, result.content)
        assertEquals(specialCharsToolCall.metaInfo, result.metaInfo)
    }

    @Test
    fun testDescribeToolResultWithSpecialCharacters() {
        val specialCharsToolResult = Message.Tool.Result(
            id = "test-call-id",
            tool = "test-tool",
            content = "Result with \"quotes\" and \\ backslashes",
            metaInfo = RequestMetaInfo.create(testClock),
        )

        val result = describer.describeToolResult(specialCharsToolResult)
        val expectedContent =
            "{\"tool_call_id\":\"test-call-id\",\"tool_name\":\"test-tool\",\"tool_result\":\"Result with \\\"quotes\\\" and \\\\ backslashes\"}"

        assertEquals(expectedContent, result.content)
        assertEquals(specialCharsToolResult.metaInfo, result.metaInfo)
    }

    @Test
    fun testDescribeToolCallWithInvalidJsonContent() {
        val invalidJsonToolCall = Message.Tool.Call(
            id = "test-call-id",
            tool = "test-tool",
            content = "{invalid json",
            metaInfo = ResponseMetaInfo.create(testClock),
        )

        assertFailsWith<Exception> {
            describer.describeToolCall(invalidJsonToolCall)
        }
    }

    @Test
    fun testDescribeToolResultWithInvalidJsonContent() {
        val invalidJsonToolCall = Message.Tool.Result(
            id = "test-call-id",
            tool = "test-tool",
            content = "{invalid json",
            metaInfo = RequestMetaInfo.create(testClock),
        )

        val result = describer.describeToolResult(invalidJsonToolCall)
        val expectedContent =
            "{\"tool_call_id\":\"test-call-id\",\"tool_name\":\"test-tool\",\"tool_result\":\"{invalid json\"}"

        assertEquals(expectedContent, result.content)
        assertEquals(invalidJsonToolCall.metaInfo, result.metaInfo)
    }

    @Test
    fun testDescribeToolCallWithEmptyToolName() {
        val emptyToolNameCall = Message.Tool.Call(
            id = "test-call-id",
            tool = "",
            content = """{"param": "value"}""",
            metaInfo = ResponseMetaInfo.create(testClock),
        )

        val result = describer.describeToolCall(emptyToolNameCall)
        val expectedContent =
            "{\"tool_call_id\":\"test-call-id\",\"tool_name\":\"\",\"tool_args\":{\"param\":\"value\"}}"

        assertEquals(expectedContent, result.content)
        assertEquals(emptyToolNameCall.metaInfo, result.metaInfo)
    }

    @Test
    fun testDescribeToolResultWithEmptyToolName() {
        val emptyToolNameResult = Message.Tool.Result(
            id = "test-call-id",
            tool = "",
            content = "Test result content",
            metaInfo = RequestMetaInfo.create(testClock),
        )

        val result = describer.describeToolResult(emptyToolNameResult)
        val expectedContent =
            "{\"tool_call_id\":\"test-call-id\",\"tool_name\":\"\",\"tool_result\":\"Test result content\"}"

        assertEquals(expectedContent, result.content)
        assertEquals(emptyToolNameResult.metaInfo, result.metaInfo)
    }

    @Test
    fun testDescribeToolCallWithNullContent() {
        val nullContentToolCall = Message.Tool.Call(
            id = "test-call-id",
            tool = "test-tool",
            content = "null",
            metaInfo = ResponseMetaInfo.create(testClock),
        )

        assertFailsWith<IllegalArgumentException> {
            describer.describeToolCall(nullContentToolCall)
        }
    }

    @Test
    fun testDescribeToolResultWithNullContent() {
        val nullContentToolResult = Message.Tool.Result(
            id = "test-call-id",
            tool = "test-tool",
            content = "null",
            metaInfo = RequestMetaInfo.create(testClock),
        )

        val result = describer.describeToolResult(nullContentToolResult)
        val expectedContent = "{\"tool_call_id\":\"test-call-id\",\"tool_name\":\"test-tool\",\"tool_result\":\"null\"}"

        assertEquals(expectedContent, result.content)
        assertEquals(nullContentToolResult.metaInfo, result.metaInfo)
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

        val largeContentToolCall = Message.Tool.Call(
            id = "test-call-id",
            tool = "test-tool",
            content = largeContent,
            metaInfo = ResponseMetaInfo.create(testClock),
        )

        val result = describer.describeToolCall(largeContentToolCall)

        assertTrue(result.content.isNotEmpty())
        assertEquals(largeContentToolCall.metaInfo, result.metaInfo)
    }

    @Test
    fun testDescribeToolCallWithNonJsonContent() {
        val nonJsonToolCall = Message.Tool.Call(
            id = "test-call-id",
            tool = "test-tool",
            content = "This is not JSON",
            metaInfo = ResponseMetaInfo.create(testClock),
        )

        assertFailsWith<Exception> {
            describer.describeToolCall(nonJsonToolCall)
        }
    }

    @Test
    fun testDescribeResultCallWithNonJsonContent() {
        val nonJsonToolCall = Message.Tool.Result(
            id = "test-call-id",
            tool = "test-tool",
            content = "This is not JSON",
            metaInfo = RequestMetaInfo.create(testClock),
        )

        val result = describer.describeToolResult(nonJsonToolCall)
        val expectedContent =
            "{\"tool_call_id\":\"test-call-id\",\"tool_name\":\"test-tool\",\"tool_result\":\"This is not JSON\"}"

        assertEquals(expectedContent, result.content)
        assertEquals(nonJsonToolCall.metaInfo, result.metaInfo)
    }
}