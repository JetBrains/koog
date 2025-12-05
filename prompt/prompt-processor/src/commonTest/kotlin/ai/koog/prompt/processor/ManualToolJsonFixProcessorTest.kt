package ai.koog.prompt.processor

import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class ManualToolJsonFixProcessorTest {
    private companion object {
        private val testClock: Clock = object : Clock {
            override fun now(): Instant = Instant.parse("2023-01-01T00:00:00Z")
        }

        private val testMetaInfo = ResponseMetaInfo.create(testClock)

        private const val ID = "123"
        private const val TOOL = "plus"
        private const val ARGS = """{"a":5,"b":3}"""

        private val validJson = """
            {
                "tool": "$TOOL",
                "args": $ARGS
            }
        """.trimIndent()

        private val validJsonWithId = """
            {
                "id": "$ID",
                "tool": "$TOOL",
                "args": $ARGS
            }
        """.trimIndent()

        private val validJsonWithAlternativeJsonKeys = """
            {
                "tool_call_id": "$ID",
                "tool_name": "$TOOL",
                "parameters": $ARGS
            }
        """.trimIndent()

        private val nestedJson = """
            {
                "function_call": {
                    "name": "$TOOL",
                    "arguments": $ARGS
                }
            }
        """.trimIndent()

        private val invalidJson = """
            {
                "args": $ARGS
            }
        """.trimIndent()

        private val taggedJson = """
            Some text before the tool call
            <tool_call>
            $validJson
            </tool_call>
            Some text after the tool call
        """.trimIndent()

        private val multipleToolCallsJson = """
            $validJson
            Some text in between
            <tool_call>
            {
                "tool": "weather",
                "args": {
                    "location": "New York"
                }
            }
        """.trimIndent()

        private val executor = getMockExecutor { }
        private val prompt = prompt("test-prompt") { }
        private val model = OpenAIModels.Chat.GPT4o
        private val toolRegistry = Tools.toolRegistry
        private val tools = toolRegistry.tools.map { it.descriptor }

        private val processor = ManualToolCallFixProcessor(toolRegistry)

        private fun validateToolCallResult(result: Message.Response, checkId: Boolean = false) {
            assertIs<Message.Tool.Call>(result)
            if (checkId) assertEquals(ID, result.id)
            assertEquals(TOOL, result.tool)
            assertEquals(ARGS, result.content)
        }
    }

    @Test
    fun test_shouldParseValidJson() = runTest {
        val message = Message.Assistant(validJson, metaInfo = testMetaInfo)
        val result = process(message)

        validateToolCallResult(result)
    }

    @Test
    fun test_shouldParseToolCallWithId() = runTest {
        val message = Message.Assistant(validJsonWithId, metaInfo = testMetaInfo)
        val result = process(message)

        validateToolCallResult(result, checkId = true)
    }

    @Test
    fun test_shouldParseAlternativeJsonKeys() = runTest {
        val message = Message.Assistant(validJsonWithAlternativeJsonKeys, metaInfo = testMetaInfo)
        val result = process(message)

        validateToolCallResult(result)
    }

    @Test
    fun test_shouldParseNestedJson() = runTest {
        val message = Message.Assistant(nestedJson, metaInfo = testMetaInfo)
        val result = process(message)

        validateToolCallResult(result)
    }

    @Test
    fun test_shouldNotParseInvalidJson() = runTest {
        val message = Message.Assistant(invalidJson, metaInfo = testMetaInfo)
        val result = process(message)

        assertEquals(message, result)
    }

    @Test
    fun test_shouldParseTaggedJson() = runTest {
        val message = Message.Assistant(taggedJson, metaInfo = testMetaInfo)
        val result = process(message)

        validateToolCallResult(result)
    }

    @Test
    fun test_shouldParseFirstOfMultipleJsons() = runTest {
        val message = Message.Assistant(multipleToolCallsJson, metaInfo = testMetaInfo)
        val result = process(message)

        validateToolCallResult(result)
    }

    @Test
    fun test_shouldParseCorrectEscapes() = runTest {
        val text1 = "Test \"quoted\" string"
        val text2 = "Test a string with\na new line"

        val validJson = """
            {
                "tool": "string_tool",
                "args": {
                    "text1": "Test \"quoted\" string",
                    "text2": "Test a string with\na new line"
                }
            }
        """.trimIndent()

        val message = Message.Assistant(validJson, metaInfo = testMetaInfo)
        val result = process(message)

        assertNotNull(result)
        assertIs<Message.Tool.Call>(result)
        assertEquals("string_tool", result.tool)

        val expectedContentJson = buildJsonObject {
            put("text1", JsonPrimitive(text1))
            put("text2", JsonPrimitive(text2))
        }
        assertEquals(expectedContentJson.toString(), result.content)
    }

    @Test
    fun test_shouldParseIncorrectEscapes() = runTest {
        val text1 = "Test \"quoted\" string"
        val text2 = "Test a string with\na new line"

        val malformedJson = """
            {
                "tool": "string_tool",
                "args": {
                    "text1": "$text1",
                    "text2": "Test a string with\na new line"
                }
            }
        """.trimIndent()

        val message = Message.Assistant(malformedJson, metaInfo = testMetaInfo)
        val result = process(message)

        assertNotNull(result)
        assertIs<Message.Tool.Call>(result)
        assertEquals("string_tool", result.tool)

        val expectedContentJson = buildJsonObject {
            put("text1", JsonPrimitive(text1))
            put("text2", JsonPrimitive(text2))
        }
        assertEquals(expectedContentJson.toString(), result.content)
    }

    private suspend fun process(response: Message.Response) =
        processor.process(executor, prompt, model, tools, response)
}
