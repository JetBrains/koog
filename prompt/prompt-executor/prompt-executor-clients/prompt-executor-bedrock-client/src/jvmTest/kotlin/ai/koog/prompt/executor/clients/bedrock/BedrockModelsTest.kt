package ai.koog.prompt.executor.clients.bedrock

import ai.koog.prompt.executor.clients.list
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import io.kotest.matchers.collections.shouldContain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class BedrockModelsTest {

    @Test
    fun `BedrockModels models should have Bedrock provider`() {
        val models = BedrockModels.list()

        models.forEach { model ->
            assertSame(
                expected = LLMProvider.Bedrock,
                actual = model.provider,
                message = "Bedrock model ${model.id} doesn't have Bedrock provider but ${model.provider}."
            )
        }
    }

    @Test
    fun `BedrockModels models should return all declared models`() {
        val reflectionModels = BedrockModels.list().map { it.id }

        val models = BedrockModels.models.map { it.id }

        assert(models.size == reflectionModels.size)

        reflectionModels.forEach { model ->
            models shouldContain model
        }
    }

    @Test
    fun `Claude Fable 5 Bedrock model should expose documented model profile`() {
        val model = BedrockModels.AnthropicClaudeFable5

        assertEquals(LLMProvider.Bedrock, model.provider)
        assertEquals("us.anthropic.claude-fable-5", model.id)
        assertEquals(1_000_000, model.contextLength)
        assertEquals(128_000, model.maxOutputTokens)
        assertTrue(model.supports(LLMCapability.Vision.Image))
        assertTrue(model.supports(LLMCapability.Tools))
    }
}
