package ai.koog.prompt.executor.clients.foundationmodels

import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.message.MessagePart
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FoundationModelsMessageConvertersTest {

    @Test
    fun testSystemMessagesBecomeInstructionsAndRestBecomeText() {
        val p = prompt("t") {
            system("You are helpful.")
            user("Hello")
        }
        val input = p.toFoundationModelsInput()
        assertEquals("You are helpful.", input.instructions)
        assertEquals("Hello", input.text)
    }

    @Test
    fun testNoSystemMessageYieldsNullInstructions() {
        val p = prompt("t") { user("Just this") }
        val input = p.toFoundationModelsInput()
        assertNull(input.instructions)
        assertEquals("Just this", input.text)
    }

    @Test
    fun testMultipleNonSystemMessagesAreJoinedNewline() {
        val p = prompt("t") {
            user("first")
            assistant("second")
            user("third")
        }
        assertEquals("first\nsecond\nthird", p.toFoundationModelsInput().text)
    }

    @Test
    fun testResponseTextBecomesSingleAssistantTextPart() {
        val msg = foundationModelsAssistantMessage("the answer")
        assertEquals(1, msg.parts.size)
        assertEquals("the answer", (msg.parts.single() as MessagePart.Text).text)
    }

    @Test
    fun testEmptyResponseStillYieldsOneTextPart() {
        val msg = foundationModelsAssistantMessage("")
        assertEquals(1, msg.parts.size)
        assertEquals("", (msg.parts.single() as MessagePart.Text).text)
    }
}
