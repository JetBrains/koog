package ai.koog.prompt.executor.clients.anthropic

import ai.koog.prompt.executor.clients.anthropic.models.AnthropicMessageRequest
import ai.koog.prompt.executor.clients.list
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import io.kotest.matchers.collections.shouldContain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AnthropicModelsTest {

    @Test
    fun `Anthropic models should have Anthropic provider`() {
        val models = AnthropicModels.list()

        models.forEach { model ->
            assertSame(
                expected = LLMProvider.Anthropic,
                actual = model.provider,
                message = "Anthropic model ${model.id} doesn't have Anthropic provider but ${model.provider}."
            )
        }
    }

    @Test
    fun `Claude 4_5 and newer models should support structured output`() {
        val modelsWithSchema = listOf(
            AnthropicModels.Haiku_4_5,
            AnthropicModels.Sonnet_4_5,
            AnthropicModels.Sonnet_4_6,
            AnthropicModels.Opus_4_5,
            AnthropicModels.Opus_4_6,
        )

        modelsWithSchema.forEach { model ->
            assertTrue(
                model.supports(LLMCapability.Schema.JSON.Standard),
                "Model ${model.id} should support Schema.JSON.Standard capability"
            )
        }
    }

    @Test
    fun `Pre-4_5 models should not support structured output`() {
        @Suppress("DEPRECATION")
        val modelsWithoutSchema = listOf(
            AnthropicModels.Haiku_3,
            AnthropicModels.Sonnet_4,
            AnthropicModels.Opus_4,
            AnthropicModels.Opus_4_1,
        )

        modelsWithoutSchema.forEach { model ->
            assertTrue(
                !model.supports(LLMCapability.Schema.JSON.Standard),
                "Model ${model.id} should NOT support Schema.JSON.Standard capability"
            )
        }
    }

    @Test
    fun `AnthropicMessageRequest should use custom maxTokens when provided`() {
        val customMaxTokens = 4000
        val request = AnthropicMessageRequest(
            model = AnthropicModels.Opus_4_6.id,
            messages = emptyList(),
            maxTokens = customMaxTokens
        )

        assertEquals(customMaxTokens, request.maxTokens)
    }

    @Test
    fun `AnthropicMessageRequest should use default maxTokens when not provided`() {
        val request = AnthropicMessageRequest(
            model = AnthropicModels.Opus_4_6.id,
            messages = emptyList()
        )

        assertEquals(AnthropicMessageRequest.MAX_TOKENS_DEFAULT, request.maxTokens)
    }

    @Test
    fun `AnthropicMessageRequest should reject zero maxTokens`() {
        val exception = assertFailsWith<IllegalArgumentException> {
            AnthropicMessageRequest(
                model = AnthropicModels.Opus_4_6.id,
                messages = emptyList(),
                maxTokens = 0
            )
        }
        assertEquals("maxTokens must be greater than 0, but was 0", exception.message)
    }

    @Test
    fun `AnthropicModels models should return all declared models`() {
        val reflectionModels = AnthropicModels.list().map { it.id }

        val models = AnthropicModels.models.map { it.id }

        assert(models.size == reflectionModels.size)

        reflectionModels.forEach { model ->
            models shouldContain model
        }
    }

    @Test
    fun `Anthropic extended thinking models should advertise thinking capability`() {
        assertNotNull(AnthropicModels.Haiku_4_5.capabilities) shouldContain LLMCapability.Thinking
        assertNotNull(AnthropicModels.Sonnet_4.capabilities) shouldContain LLMCapability.Thinking
        assertNotNull(AnthropicModels.Opus_4_6.capabilities) shouldContain LLMCapability.Thinking
    }
}
