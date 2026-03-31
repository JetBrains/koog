package ai.koog.agents.features.chathistory.aws

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AgentcoreConversationIdParserTest {

    private val parser = AgentcoreConversationIdParser()

    @Test
    fun testParseActorOnly() {
        val (actor, session) = parser.parse("myActor")
        assertEquals("myActor", actor)
        assertEquals("default-session", session)
    }

    @Test
    fun testParseActorAndSession() {
        val (actor, session) = parser.parse("myActor:mySession")
        assertEquals("myActor", actor)
        assertEquals("mySession", session)
    }

    @Test
    fun testParseColonInSession() {
        val (actor, session) = parser.parse("myActor:session:with:colons")
        assertEquals("myActor", actor)
        assertEquals("session:with:colons", session)
    }

    @Test
    fun testParseBlankThrows() {
        assertFailsWith<IllegalArgumentException> {
            parser.parse("")
        }
        assertFailsWith<IllegalArgumentException> {
            parser.parse("   ")
        }
    }

    @Test
    fun testParseBlankActorThrows() {
        assertFailsWith<IllegalArgumentException> {
            parser.parse(":session")
        }
    }

    @Test
    fun testParseBlankSessionThrows() {
        assertFailsWith<IllegalArgumentException> {
            parser.parse("actor:")
        }
    }

    @Test
    fun testCustomDefaultSession() {
        val customParser = AgentcoreConversationIdParser(defaultSession = "custom-session")
        val (actor, session) = customParser.parse("myActor")
        assertEquals("myActor", actor)
        assertEquals("custom-session", session)
    }

    @Test
    fun testCustomDefaultSessionNotUsedWhenSessionProvided() {
        val customParser = AgentcoreConversationIdParser(defaultSession = "custom-session")
        val (actor, session) = customParser.parse("myActor:explicit")
        assertEquals("myActor", actor)
        assertEquals("explicit", session)
    }
}
