package ai.koog.prompt.executor.ollama.client

import ai.koog.prompt.executor.ollama.client.dto.OllamaChatMessageDTO
import ai.koog.prompt.executor.ollama.client.dto.OllamaToolCallDTO
import ai.koog.prompt.executor.ollama.client.dto.getFirstToolCall
import ai.koog.prompt.executor.ollama.client.dto.getToolCalls
import ai.koog.prompt.message.ResponseMetaInfo
import kotlinx.datetime.Instant
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.*

/**
 * Tests for Ollama tool call ID generation and extraction.
 */
class OllamaToolCallTest {

    val testResponseMetadata = ResponseMetaInfo(timestamp = Instant.parse("2023-01-01T00:00:00Z"))

    @Test
    fun testGetToolCallWithNoToolCalls() {
        val message = OllamaChatMessageDTO(
            role = "assistant",
            content = "Hello",
            toolCalls = null
        )

        val toolCall = message.getFirstToolCall(testResponseMetadata)
        assertNull(toolCall)
    }

    @Test
    fun testGetToolCallWithEmptyToolCalls() {
        val message = OllamaChatMessageDTO(
            role = "assistant",
            content = "Hello",
            toolCalls = emptyList()
        )

        val toolCall = message.getFirstToolCall(testResponseMetadata)
        assertNull(toolCall)
    }

    @Test
    fun testGetToolCallWithSingleToolCall() {
        val arguments = JsonObject(
            mapOf(
                "city" to JsonPrimitive("New York"),
                "units" to JsonPrimitive("celsius")
            )
        )

        val message = OllamaChatMessageDTO(
            role = "assistant",
            content = "",
            toolCalls = listOf(
                OllamaToolCallDTO(
                    function = OllamaToolCallDTO.Call(
                        name = "get_weather",
                        arguments = arguments
                    )
                )
            )
        )

        val toolCall = message.getFirstToolCall(testResponseMetadata)
        assertNotNull(toolCall)
        assertEquals("get_weather", toolCall.tool)
        assertTrue(toolCall.id!!.startsWith("ollama_tool_call_"))
        assertTrue(toolCall.id!!.length > "ollama_tool_call_".length)
        assertTrue(toolCall.content.contains("New York"))
        assertTrue(toolCall.content.contains("celsius"))
    }

    @Test
    fun testGetToolCallsWithMultipleToolCalls() {
        val weatherArgs = JsonObject(
            mapOf(
                "city" to JsonPrimitive("Paris")
            )
        )

        val calculatorArgs = JsonObject(
            mapOf(
                "expression" to JsonPrimitive("2 + 2")
            )
        )

        val message = OllamaChatMessageDTO(
            role = "assistant",
            content = "",
            toolCalls = listOf(
                OllamaToolCallDTO(
                    function = OllamaToolCallDTO.Call(
                        name = "get_weather",
                        arguments = weatherArgs
                    )
                ),
                OllamaToolCallDTO(
                    function = OllamaToolCallDTO.Call(
                        name = "calculator",
                        arguments = calculatorArgs
                    )
                )
            )
        )

        val toolCalls = message.getToolCalls(testResponseMetadata)
        assertEquals(2, toolCalls.size)

        val weatherCall = toolCalls[0]
        assertEquals("get_weather", weatherCall.tool)
        assertTrue(weatherCall.id!!.startsWith("ollama_tool_call_"))
        assertTrue(weatherCall.content.contains("Paris"))

        val calculatorCall = toolCalls[1]
        assertEquals("calculator", calculatorCall.tool)
        assertTrue(calculatorCall.id!!.startsWith("ollama_tool_call_"))
        assertTrue(calculatorCall.content.contains("2 + 2"))

        // IDs should be different for different tool calls
        assertTrue(weatherCall.id != calculatorCall.id)
    }

    @Test
    fun testToolCallIdDeterministic() {
        val arguments = JsonObject(
            mapOf(
                "query" to JsonPrimitive("test query")
            )
        )

        val message1 = OllamaChatMessageDTO(
            role = "assistant",
            content = "",
            toolCalls = listOf(
                OllamaToolCallDTO(
                    function = OllamaToolCallDTO.Call(
                        name = "search",
                        arguments = arguments
                    )
                )
            )
        )

        val message2 = OllamaChatMessageDTO(
            role = "assistant",
            content = "",
            toolCalls = listOf(
                OllamaToolCallDTO(
                    function = OllamaToolCallDTO.Call(
                        name = "search",
                        arguments = arguments
                    )
                )
            )
        )

        val toolCall1 = message1.getFirstToolCall(testResponseMetadata)
        val toolCall2 = message2.getFirstToolCall(testResponseMetadata)

        assertNotNull(toolCall1)
        assertNotNull(toolCall2)

        // Same tool name and arguments should generate the same ID
        assertEquals(toolCall1.id, toolCall2.id)
    }

    @Test
    fun testToolCallIdDifferentForDifferentContent() {
        val arguments1 = JsonObject(
            mapOf(
                "city" to JsonPrimitive("London")
            )
        )

        val arguments2 = JsonObject(
            mapOf(
                "city" to JsonPrimitive("Tokyo")
            )
        )

        val message1 = OllamaChatMessageDTO(
            role = "assistant",
            content = "",
            toolCalls = listOf(
                OllamaToolCallDTO(
                    function = OllamaToolCallDTO.Call(
                        name = "get_weather",
                        arguments = arguments1
                    )
                )
            )
        )

        val message2 = OllamaChatMessageDTO(
            role = "assistant",
            content = "",
            toolCalls = listOf(
                OllamaToolCallDTO(
                    function = OllamaToolCallDTO.Call(
                        name = "get_weather",
                        arguments = arguments2
                    )
                )
            )
        )

        val toolCall1 = message1.getFirstToolCall(testResponseMetadata)
        val toolCall2 = message2.getFirstToolCall(testResponseMetadata)

        assertNotNull(toolCall1)
        assertNotNull(toolCall2)

        // Different arguments should generate different IDs
        assertTrue(toolCall1.id != toolCall2.id)
    }

    @Test
    fun testToolCallIdFormat() {
        val arguments = JsonObject(
            mapOf(
                "param" to JsonPrimitive("value")
            )
        )

        val message = OllamaChatMessageDTO(
            role = "assistant",
            content = "",
            toolCalls = listOf(
                OllamaToolCallDTO(
                    function = OllamaToolCallDTO.Call(
                        name = "test_tool",
                        arguments = arguments
                    )
                )
            )
        )

        val toolCall = message.getFirstToolCall(testResponseMetadata)
        assertNotNull(toolCall)

        // Verify ID format: ollama_tool_call_ followed by numbers
        val idPattern = Regex("^ollama_tool_call_\\d+$")
        assertTrue(idPattern.matches(toolCall.id!!), "ID '${toolCall.id}' doesn't match expected pattern")
    }
}
