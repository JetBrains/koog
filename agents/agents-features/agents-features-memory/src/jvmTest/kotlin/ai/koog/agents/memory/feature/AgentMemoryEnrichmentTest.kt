package ai.koog.agents.memory.feature

import ai.koog.agents.core.agent.context.AIAgentLLMContext
import ai.koog.agents.memory.config.MemoryScopesProfile
import ai.koog.agents.memory.feature.summarization.SummaryProvider
import ai.koog.agents.memory.feature.summarization.SummaryResult
import ai.koog.agents.memory.model.Concept
import ai.koog.agents.memory.model.Fact
import ai.koog.agents.memory.model.FactType
import ai.koog.agents.memory.model.SingleFact
import ai.koog.agents.memory.providers.AgentMemoryProvider
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class AgentMemoryEnrichmentTest {
    private val concept = Concept("concept", "description", FactType.SINGLE)

    @Test
    fun enrichFactAttachesSummaryAndKeywords() = runTest {
        val summaryProvider = object : SummaryProvider {
            override suspend fun summarize(fact: Fact): SummaryResult =
                SummaryResult(summary = "compressed", keywords = listOf("tag"))
        }

        val memory = AgentMemory(
            agentMemory = mockk<AgentMemoryProvider>(relaxed = true),
            llm = mockk<AIAgentLLMContext>(relaxed = true),
            scopesProfile = MemoryScopesProfile(),
            summaryProvider = summaryProvider
        )

        val originalFact = SingleFact(concept = concept, timestamp = 1L, value = "original")
        val enriched = memory.enrichFactIfNeeded(originalFact, enrich = true) as SingleFact

        assertEquals("compressed", enriched.summary)
        assertEquals(listOf("tag"), enriched.keywords)
    }

    @Test
    fun enrichmentNoOpWhenDisabled() = runTest {
        val memory = AgentMemory(
            agentMemory = mockk<AgentMemoryProvider>(relaxed = true),
            llm = mockk<AIAgentLLMContext>(relaxed = true),
            scopesProfile = MemoryScopesProfile(),
            summaryProvider = null
        )

        val originalFact = SingleFact(concept = concept, timestamp = 1L, value = "original")
        val enriched = memory.enrichFactIfNeeded(originalFact, enrich = false)

        assertSame(originalFact, enriched, "Fact should remain unchanged when enrichment disabled or unavailable")
    }
}
