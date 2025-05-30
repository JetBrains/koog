package ai.koog.prompt

import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.llm.OllamaModels
import ai.koog.prompt.message.Message
import ai.koog.prompt.params.LLMParams
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PromptTest {

    @Test
    fun testDSLBuilding() {
        val prompt = Prompt.build("test") {
            system("You are a helpful assistant")
            user("Hello")
            assistant("Hi! How can I help you?")
            tool {
                call(Message.Tool.Call("1", "search", "Searching for information..."))
                result(Message.Tool.Result("1", "search", "Found some results"))
            }
        }

        assertEquals(5, prompt.messages.size)
        assertTrue(prompt.messages[0] is Message.System)
        assertTrue(prompt.messages[1] is Message.User)
        assertTrue(prompt.messages[2] is Message.Assistant)
        assertTrue(prompt.messages[3] is Message.Tool.Call)
        assertTrue(prompt.messages[4] is Message.Tool.Result)

        assertEquals(Message.System("You are a helpful assistant"), prompt.messages[0])
        assertEquals(Message.User("Hello"), prompt.messages[1])
        assertEquals(Message.Assistant("Hi! How can I help you?"), prompt.messages[2])
        assertEquals(Message.Tool.Call("1", "search", "Searching for information..."), prompt.messages[3])
        assertEquals(Message.Tool.Result("1", "search", "Found some results"), prompt.messages[4])
    }

    @Test
    fun testSerialization() {
        val prompt = Prompt.build("test") {
            system("You are a helpful assistant")
            user("Hello")
        }

        val json = Json.encodeToString(prompt)
        val decoded = Json.decodeFromString<Prompt>(json)

        assertEquals(prompt, decoded)
        assertEquals(2, decoded.messages.size)
        assertTrue(decoded.messages[0] is Message.System)
        assertTrue(decoded.messages[1] is Message.User)
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
