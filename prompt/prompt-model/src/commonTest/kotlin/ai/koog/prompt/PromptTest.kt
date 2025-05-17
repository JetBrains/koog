package ai.koog.prompt

import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.llm.OllamaModels
import ai.koog.prompt.message.Message
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
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

        assertEquals("You are a helpful assistant", prompt.messages[0].content)
        assertEquals("Hello", prompt.messages[1].content)
        assertEquals("Hi! How can I help you?", prompt.messages[2].content)
        assertEquals("Searching for information...", prompt.messages[3].content)
        assertEquals("Found some results", prompt.messages[4].content)
        assertEquals("search", (prompt.messages[3] as Message.Tool.Call).tool)
        assertEquals("search", (prompt.messages[4] as Message.Tool.Result).tool)
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
}
