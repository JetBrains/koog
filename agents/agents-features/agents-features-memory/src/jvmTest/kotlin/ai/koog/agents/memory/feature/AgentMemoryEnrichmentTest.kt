package ai.koog.agents.memory.feature

import ai.koog.agents.memory.feature.summarization.SummaryProvider
import ai.koog.agents.memory.feature.summarization.SummaryResult
import ai.koog.agents.memory.model.Concept
import ai.koog.agents.memory.model.Fact
import ai.koog.agents.memory.model.FactType
import ai.koog.agents.memory.model.MemoryScope
import ai.koog.agents.memory.model.MemorySubject
import ai.koog.agents.memory.model.SingleFact
import ai.koog.agents.memory.providers.AgentMemoryProvider
import ai.koog.agents.memory.providers.MemoryRequestOptions
import ai.koog.agents.memory.providers.SmartAgentMemoryProvider
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private object TestSubject : MemorySubject() {
    override val name: String = "test"
    override val promptDescription: String = "test subject"
    override val priorityLevel: Int = 0
}

private class CapturingProvider : AgentMemoryProvider {
    val savedFacts = mutableListOf<Fact>()

    override suspend fun save(fact: Fact, subject: MemorySubject, scope: MemoryScope) {
        savedFacts += fact
    }

    override suspend fun load(concept: Concept, subject: MemorySubject, scope: MemoryScope): List<Fact> = emptyList()

    override suspend fun loadAll(subject: MemorySubject, scope: MemoryScope): List<Fact> = emptyList()

    override suspend fun loadByDescription(
        description: String,
        subject: MemorySubject,
        scope: MemoryScope
    ): List<Fact> = emptyList()
}

class AgentMemoryEnrichmentTest {
    private val concept = Concept("concept", "description", FactType.SINGLE)

    @Test
    fun enrichFactAttachesSummaryAndKeywords() = runTest {
        val summaryProvider = object : SummaryProvider {
            override suspend fun summarize(fact: Fact): SummaryResult =
                SummaryResult(summary = "compressed", keywords = listOf("tag"))
        }

        val delegate = CapturingProvider()
        val provider = SmartAgentMemoryProvider(
            delegate = delegate,
            summaryProvider = summaryProvider,
            embedder = null,
            defaultBudget = null
        )

        val originalFact = SingleFact(concept = concept, timestamp = 1L, value = "original")
        provider.save(originalFact, TestSubject, MemoryScope.Agent("test"), MemoryRequestOptions(enrich = true))

        val saved = delegate.savedFacts.single() as SingleFact
        assertEquals("compressed", saved.summary)
        assertEquals(listOf("tag"), saved.keywords)
    }

    @Test
    fun enrichmentNoOpWhenDisabled() = runTest {
        val delegate = CapturingProvider()
        val provider = SmartAgentMemoryProvider(
            delegate = delegate,
            summaryProvider = object : SummaryProvider {
                override suspend fun summarize(fact: Fact): SummaryResult =
                    SummaryResult(summary = "compressed", keywords = listOf("tag"))
            },
            embedder = null,
            defaultBudget = null
        )

        val originalFact = SingleFact(concept = concept, timestamp = 1L, value = "original")
        provider.save(originalFact, TestSubject, MemoryScope.Agent("test"), MemoryRequestOptions(enrich = false))

        val saved = delegate.savedFacts.single() as SingleFact
        assertNull(saved.summary, "When enrichment disabled via context the provider should skip summarization")
        assertEquals(emptyList<String>(), saved.keywords)
    }
}
