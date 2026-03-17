package ai.koog.prompt.executor.selection

import ai.koog.prompt.executor.selection.ModelFilterAPI.Decision
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ModelFilterTest {
    @Test
    fun testSpecificModelFilter() = runTest {
        // Given
        val target = model(id = "target")
        val other = model(id = "other")

        // And
        val filter = ModelFilters.specific(target)

        // When
        val targetDecision = filter.evaluate(target)
        val otherDecision = filter.evaluate(other)

        // Then
        assertEquals(Decision.ACCEPTED, targetDecision)
        assertEquals(Decision.REJECTED, otherDecision)
    }

    @Test
    fun testCapabilitiesFilter() = runTest {
        // Given
        val filter = ModelFilters.withCapabilities(
            LLMCapability.Tools,
            LLMCapability.Completion
        )

        // And
        val capable = model(id = "capable", capabilities = listOf(LLMCapability.Tools, LLMCapability.Completion))
        val missingOne = model(id = "missing-one", capabilities = listOf(LLMCapability.Tools))
        val missingAll = model(id = "missing-all", capabilities = null)

        // When
        val capableDecision = filter.evaluate(capable)
        val missingOneDecision = filter.evaluate(missingOne)
        val missingAllDecision = filter.evaluate(missingAll)

        // Then
        assertEquals(Decision.ACCEPTED, capableDecision)
        assertEquals(Decision.REJECTED, missingOneDecision)
        assertEquals(Decision.REJECTED, missingAllDecision)
    }

    @Test
    fun testMinContextWindowFilter() = runTest {
        // Given
        val filter = ModelFilters.withMinContextLength(minTokens = 8_192)

        // And
        val exact = model(id = "exact", contextLength = 8_192)
        val more = model(id = "more", contextLength = 16_384)
        val less = model(id = "less", contextLength = 4_096)
        val missing = model(id = "missing", contextLength = null)

        // When
        val exactDecision = filter.evaluate(exact)
        val moreDecision = filter.evaluate(more)
        val lessDecision = filter.evaluate(less)
        val missingDecision = filter.evaluate(missing)

        // Then
        assertEquals(Decision.ACCEPTED, exactDecision)
        assertEquals(Decision.ACCEPTED, moreDecision)
        assertEquals(Decision.REJECTED, lessDecision)
        assertEquals(Decision.REJECTED, missingDecision)
    }

    private fun model(
        id: String,
        capabilities: List<LLMCapability>? = null,
        contextLength: Long? = null,
    ): LLModel = LLModel(
        provider = LLMProvider.OpenAI,
        id = id,
        capabilities = capabilities,
        contextLength = contextLength,
    )
}
