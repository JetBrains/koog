package ai.koog.prompt.executor.clients.anthropic

import ai.koog.prompt.executor.clients.anthropic.models.AnthropicMessageRequest
import ai.koog.prompt.executor.clients.list
import ai.koog.prompt.llm.LLMProvider
import io.kotest.matchers.collections.shouldContain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

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
    fun `AnthropicMessageRequest should use custom maxTokens when provided`() {
        val customMaxTokens = 4000
        val request = AnthropicMessageRequest(
            model = AnthropicModels.Opus_3.id,
            messages = emptyList(),
            maxTokens = customMaxTokens
        )

        assertEquals(customMaxTokens, request.maxTokens)
    }

    @Test
    fun `AnthropicMessageRequest should use default maxTokens when not provided`() {
        val request = AnthropicMessageRequest(
            model = AnthropicModels.Opus_3.id,
            messages = emptyList()
        )

        assertEquals(AnthropicMessageRequest.MAX_TOKENS_DEFAULT, request.maxTokens)
    }

    @Test
    fun `AnthropicMessageRequest should reject zero maxTokens`() {
        val exception = assertFailsWith<IllegalArgumentException> {
            AnthropicMessageRequest(
                model = AnthropicModels.Opus_3.id,
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
}
