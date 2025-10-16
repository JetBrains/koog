package ai.koog.agents.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ModelInfoTest {

    @Test
    fun `test ModelInfo creation with all properties`() {
        val modelInfo = ModelInfo(
            provider = "openai",
            model = "gpt-4",
            displayName = "GPT-4",
            contextLength = 128000L,
            maxOutputTokens = 4096L
        )

        assertEquals("openai", modelInfo.provider)
        assertEquals("gpt-4", modelInfo.model)
        assertEquals("GPT-4", modelInfo.displayName)
        assertEquals(128000L, modelInfo.contextLength)
        assertEquals(4096L, modelInfo.maxOutputTokens)
        assertEquals("openai:gpt-4", modelInfo.eventString)
        assertEquals("GPT-4", modelInfo.modelIdentifierName)
    }

    @Test
    fun `test ModelInfo creation with minimal properties`() {
        val modelInfo = ModelInfo(
            provider = "anthropic",
            model = "claude-3"
        )

        assertEquals("anthropic", modelInfo.provider)
        assertEquals("claude-3", modelInfo.model)
        assertNull(modelInfo.displayName)
        assertNull(modelInfo.contextLength)
        assertNull(modelInfo.maxOutputTokens)
        assertEquals("anthropic:claude-3", modelInfo.eventString)
        assertEquals("anthropic/claude-3", modelInfo.modelIdentifierName)
    }

    @Test
    fun `test fromString with valid format`() {
        val modelInfo = ModelInfo.fromString("openai:gpt-4")

        assertEquals("openai", modelInfo.provider)
        assertEquals("gpt-4", modelInfo.model)
        assertNull(modelInfo.displayName)
        assertNull(modelInfo.contextLength)
        assertNull(modelInfo.maxOutputTokens)
    }

    @Test
    fun `test fromString with invalid format`() {
        val modelInfo = ModelInfo.fromString("invalid-format")

        assertEquals("unknown", modelInfo.provider)
        assertEquals("undefined", modelInfo.model)
        assertNull(modelInfo.displayName)
        assertNull(modelInfo.contextLength)
        assertNull(modelInfo.maxOutputTokens)
    }

    @Test
    fun `test fromString with multiple colons`() {
        val modelInfo = ModelInfo.fromString("openai:gpt-4:extra")

        assertEquals("openai", modelInfo.provider)
        assertEquals("gpt-4:extra", modelInfo.model)
    }

    @Test
    fun `test UNDEFINED constant`() {
        val undefined = ModelInfo.UNDEFINED

        assertEquals("unknown", undefined.provider)
        assertEquals("undefined", undefined.model)
        assertNull(undefined.displayName)
        assertNull(undefined.contextLength)
        assertNull(undefined.maxOutputTokens)
    }
}
