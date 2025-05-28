package ai.koog.prompt

import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.message.Message
import ai.koog.prompt.params.LLMParams
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PromptTest {
    val systemMessage = "You are a helpful assistant"
    val userMessage = "Hello"

    val originalPrompt = Prompt.build("test") {
        system(systemMessage)
        user(userMessage)
    }

    @Test
    fun testPromptBuilding() {
        val assistantMessage = "Hi! How can I help you?"
        val toolCallId = "tool_call_dummy_123"
        val toolName = "search"
        val toolContent = "Searching for information..."
        val toolResult = "Found some results"

        val prompt = Prompt.build("test") {
            system(systemMessage)
            user(userMessage)
            assistant(assistantMessage)
            tool {
                call(Message.Tool.Call(toolCallId, toolName, toolContent))
                result(Message.Tool.Result(toolCallId, toolName, toolResult))
            }
        }

        assertEquals(5, prompt.messages.size)
        assertTrue(prompt.messages[0] is Message.System)
        assertTrue(prompt.messages[1] is Message.User)
        assertTrue(prompt.messages[2] is Message.Assistant)
        assertTrue(prompt.messages[3] is Message.Tool.Call)
        assertTrue(prompt.messages[4] is Message.Tool.Result)

        assertEquals(systemMessage, prompt.messages[0].content)
        assertEquals(userMessage, prompt.messages[1].content)
        assertEquals(assistantMessage, prompt.messages[2].content)
        assertEquals(toolContent, prompt.messages[3].content)
        assertEquals(toolResult, prompt.messages[4].content)
        assertEquals(toolName, (prompt.messages[3] as Message.Tool.Call).tool)
        assertEquals(toolName, (prompt.messages[4] as Message.Tool.Result).tool)
    }

    @Test
    fun testSerialization() {
        val json = Json.encodeToString(originalPrompt)
        val decoded = Json.decodeFromString<Prompt>(json)

        assertEquals(originalPrompt, decoded)
        assertEquals(2, decoded.messages.size)
        assertTrue(decoded.messages[0] is Message.System)
        assertTrue(decoded.messages[1] is Message.User)
    }

    @Test
    fun testUpdatePromptWithNewMessages() {
        val systemMessage = "You are a coding assistant"
        val userMessage = "Help me with Kotlin"
        val assistantMessage = "I'll help you with Kotlin programming"

        val newMessages = listOf(
            Message.System(systemMessage),
            Message.User(userMessage),
            Message.Assistant(assistantMessage)
        )

        val updatedPrompt = originalPrompt.withMessages(newMessages)

        assertEquals(3, updatedPrompt.messages.size)
        assertEquals(systemMessage, updatedPrompt.messages[0].content)
        assertEquals(userMessage, updatedPrompt.messages[1].content)
        assertEquals(assistantMessage, updatedPrompt.messages[2].content)
    }

    @Test
    fun testUpdatePromptWithNewParams() {
        val speculation = "test speculation"
        val schemaName = "test-schema"
        val newParams = LLMParams(
            temperature = 0.7,
            speculation = speculation,
            schema = LLMParams.Schema.JSON.Simple(
                schemaName,
                buildJsonObject { put("type", "string") }
            ),
            toolChoice = LLMParams.ToolChoice.Auto
        )

        val updatedPrompt = originalPrompt.withParams(newParams)

        assertEquals(0.7, updatedPrompt.params.temperature)
        assertEquals(speculation, updatedPrompt.params.speculation)
        assertTrue(updatedPrompt.params.schema is LLMParams.Schema.JSON.Simple)
        assertEquals(schemaName, updatedPrompt.params.schema.name)
        assertTrue(updatedPrompt.params.toolChoice is LLMParams.ToolChoice.Auto)
    }

    @Test
    fun testUpdatePromptWithUpdatedMessages() {
        val assistantMessage = "Hi there! How can I assist you today?"
        val userMessage = "I need help with coding"

        val updatedPrompt = originalPrompt.withUpdatedMessages {
            add(Message.Assistant(assistantMessage))
            add(Message.User(userMessage))
        }

        assertEquals(4, updatedPrompt.messages.size)
        assertTrue(updatedPrompt.messages[0] is Message.System)
        assertTrue(updatedPrompt.messages[1] is Message.User)
        assertTrue(updatedPrompt.messages[2] is Message.Assistant)
        assertTrue(updatedPrompt.messages[3] is Message.User)
        assertEquals(assistantMessage, updatedPrompt.messages[2].content)
        assertEquals(userMessage, updatedPrompt.messages[3].content)
    }

    @Test
    fun testUpdatePromptWithUpdatedParams() {
        val newSpeculation = "improved speculation"
        val schemaName = "full-schema"
        val updatedPrompt = originalPrompt.withUpdatedParams {
            temperature = 0.8
            speculation = newSpeculation
            schema = LLMParams.Schema.JSON.Full(
                schemaName,
                buildJsonObject {
                    put("type", "object")
                    put("required", true)
                }
            )
            toolChoice = LLMParams.ToolChoice.Required
        }

        assertEquals(0.8, updatedPrompt.params.temperature)
        assertEquals(newSpeculation, updatedPrompt.params.speculation)
        assertTrue(updatedPrompt.params.schema is LLMParams.Schema.JSON.Full)
        assertEquals(schemaName, updatedPrompt.params.schema.name)
        assertTrue(updatedPrompt.params.toolChoice is LLMParams.ToolChoice.Required)
    }

    @Test
    fun testWithMessagesFunctions() {
        val originalPrompt = Prompt.build("test") {
            system("You are a helpful assistant")
            user("Hello")
        }

        // Test adding a message
        val updatedPrompt = originalPrompt.withMessages { messages ->
            messages + Message.Assistant("How can I help you?")
        }

        assertNotEquals(originalPrompt, updatedPrompt)
        assertEquals(3, updatedPrompt.messages.size)
        assertEquals(Message.Assistant("How can I help you?"), updatedPrompt.messages[2])

        // Test replacing messages
        val replacedPrompt = originalPrompt.withMessages {
            listOf(Message.System("You are a coding assistant"))
        }

        assertEquals(1, replacedPrompt.messages.size)
        assertEquals(Message.System("You are a coding assistant"), replacedPrompt.messages[0])
    }

    @Test
    fun testWithParamsFunction() {
        val originalPrompt = Prompt.build("test") {
            system("You are a helpful assistant")
        }

        val newParams = LLMParams(
            temperature = 0.7,
            speculation = "test speculation"
        )

        val updatedPrompt = originalPrompt.withParams(newParams)

        assertNotEquals(originalPrompt, updatedPrompt)
        assertEquals(newParams, updatedPrompt.params)
        assertEquals(0.7, updatedPrompt.params.temperature)
        assertEquals("test speculation", updatedPrompt.params.speculation)
    }

    @Test
    fun testWithUpdatedParamsFunction() {
        val originalPrompt = Prompt.build("test") {
            system("You are a helpful assistant")
        }

        // Test updating temperature only
        val tempUpdatedPrompt = originalPrompt.withUpdatedParams {
            temperature = 0.8
        }

        assertNotEquals(originalPrompt, tempUpdatedPrompt)
        assertEquals(0.8, tempUpdatedPrompt.params.temperature)

        // Test updating multiple parameters
        val multiUpdatedPrompt = originalPrompt.withUpdatedParams {
            temperature = 0.5
            speculation = "new speculation"
            toolChoice = LLMParams.ToolChoice.Auto
        }

        assertEquals(0.5, multiUpdatedPrompt.params.temperature)
        assertEquals("new speculation", multiUpdatedPrompt.params.speculation)
        assertEquals(LLMParams.ToolChoice.Auto, multiUpdatedPrompt.params.toolChoice)
    }
}
