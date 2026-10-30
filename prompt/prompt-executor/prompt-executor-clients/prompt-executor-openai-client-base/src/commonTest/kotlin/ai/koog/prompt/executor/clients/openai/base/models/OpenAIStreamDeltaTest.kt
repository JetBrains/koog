package ai.koog.prompt.executor.clients.openai.base.models

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import kotlin.test.Test
import kotlin.test.assertEquals

class OpenAIStreamDeltaTest {
    private val json = Json {
        ignoreUnknownKeys = true
        namingStrategy = JsonNamingStrategy.SnakeCase
    }

    @Test
    fun testCanonicalReasoningField() {
        val delta = json.decodeFromString<OpenAIStreamDelta>("""{"reasoning_content":"Let me think."}""")
        assertEquals("Let me think.", delta.reasoningContent)
    }

    @Test
    fun testReasoningAliasPreservesWhitespace() {
        val delta = json.decodeFromString<OpenAIStreamDelta>("""{"reasoning":" \n\t"}""")
        assertEquals(" \n\t", delta.reasoningContent)
    }

    @Test
    fun testBothReasoningNamesDecodeToOneValue() {
        val delta = json.decodeFromString<OpenAIStreamDelta>(
            """{"reasoning_content":"Let me think.","reasoning":"Let me think."}"""
        )
        assertEquals("Let me think.", delta.reasoningContent)
    }
}
