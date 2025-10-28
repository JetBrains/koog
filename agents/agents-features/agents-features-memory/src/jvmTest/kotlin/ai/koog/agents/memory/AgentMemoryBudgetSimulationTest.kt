package ai.koog.agents.memory

import ai.koog.agents.core.agent.context.AIAgentLLMContext
import ai.koog.agents.core.agent.session.AIAgentLLMWriteSession
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.memory.config.MemoryScopeType
import ai.koog.agents.memory.config.MemoryScopesProfile
import ai.koog.agents.memory.feature.AgentMemory
import ai.koog.agents.memory.model.Concept
import ai.koog.agents.memory.model.Fact
import ai.koog.agents.memory.model.FactType
import ai.koog.agents.memory.model.MemoryScope
import ai.koog.agents.memory.model.MemorySubject
import ai.koog.agents.memory.model.MultipleFacts
import ai.koog.agents.memory.model.TokenBudget
import ai.koog.agents.memory.providers.AgentMemoryProvider
import ai.koog.prompt.dsl.PromptBuilder
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlin.math.ceil
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class InMemoryAgentMemoryProvider : AgentMemoryProvider {
    internal val store = mutableMapOf<Pair<String, MemoryScope>, MutableList<Fact>>()

    override suspend fun save(fact: Fact, subject: MemorySubject, scope: MemoryScope) {
        store.getOrPut(subject.name to scope) { mutableListOf() }.add(fact)
    }

    override suspend fun load(concept: Concept, subject: MemorySubject, scope: MemoryScope): List<Fact> =
        store[subject.name to scope].orEmpty().filter { it.concept == concept }

    override suspend fun loadAll(subject: MemorySubject, scope: MemoryScope): List<Fact> =
        store[subject.name to scope].orEmpty()

    override suspend fun loadByDescription(
        description: String,
        subject: MemorySubject,
        scope: MemoryScope
    ): List<Fact> = store[subject.name to scope].orEmpty().filter {
        it.concept.description.contains(description, ignoreCase = true)
    }

    internal fun mapFacts(
        subject: MemorySubject,
        scope: MemoryScope,
        mapper: (Fact, Int) -> Fact
    ) {
        val key = subject.name to scope
        store[key] = store[key].orEmpty()
            .mapIndexed { index, fact -> mapper(fact, index) }
            .sortedByDescending { fact ->
                when (fact) {
                    is MultipleFacts -> fact.timestamp
                    else -> Long.MIN_VALUE
                }
            }
            .toMutableList()
    }
}

@OptIn(InternalAgentsApi::class)
class AgentMemoryBudgetSimulationTest {
    @Serializable
    object TicketSubject : MemorySubject() {
        override val name: String = "ticket-history"
        override val promptDescription: String = "Historic support tickets including diagnostics and resolutions."
        override val priorityLevel: Int = 1
    }

    private val concept = Concept(
        keyword = "support-ticket-history",
        description = "Detailed ticket transcripts and resolutions for a customer.",
        factType = FactType.MULTIPLE
    )

    private val scope = MemoryScope.Product("support-suite")

    @Test
    fun `growing memory without budgets floods the prompt`() = runTest {
        val provider = InMemoryAgentMemoryProvider()

        repeat(12) { index ->
            provider.save(
                MultipleFacts(
                    concept = concept,
                    timestamp = index.toLong(),
                    values = listOf(
                        "Ticket $index detail: ${"A".repeat(120)}",
                        "Ticket $index resolution: ${"B".repeat(120)}",
                        "Ticket $index follow-up: ${"C".repeat(120)}"
                    )
                ),
                TicketSubject,
                scope
            )
        }

        val initialMessage = captureLoadMessage(
            provider = provider,
            defaultBudget = null,
            explicitBudget = null
        )

        val initialFactCount = Regex("- \\[").findAll(initialMessage).count()
        assertEquals(12, initialFactCount, "All stored facts should be replayed without a budget")
        assertTrue(initialMessage.contains("Ticket 11 detail"), "Most recent raw fact must be present")
        val initialApproxTokens = approximateTokens(initialMessage)

        provider.mapFacts(TicketSubject, scope) { fact, idx ->
            if (fact is MultipleFacts) {
                fact.copy(summary = "Ticket $idx summary", keywords = listOf("ticket-$idx"))
            } else {
                fact
            }
        }

        val constrainedMessage = captureLoadMessage(
            provider = provider,
            defaultBudget = TokenBudget(maxTokens = 120, maxFacts = 3),
            explicitBudget = TokenBudget(maxTokens = 120, maxFacts = 3)
        )

        val constrainedFactCount = Regex("- \\[").findAll(constrainedMessage).count()
        assertEquals(3, constrainedFactCount, "Budget should cap the number of facts injected")
        assertFalse(constrainedMessage.contains("Ticket 0 detail"), "Summaries should replace verbose values")
        assertTrue(constrainedMessage.contains("Ticket 11 summary"), "Most recent summary should be present")
        val constrainedApproxTokens = approximateTokens(constrainedMessage)

        assertTrue(
            constrainedApproxTokens < initialApproxTokens,
            "Token budgeting should reduce injected tokens ($constrainedApproxTokens vs $initialApproxTokens)"
        )
    }

    private suspend fun captureLoadMessage(
        provider: AgentMemoryProvider,
        defaultBudget: TokenBudget?,
        explicitBudget: TokenBudget?
    ): String {
        val promptSlot = slot<PromptBuilder.() -> Unit>()
        val sessionBlock = slot<suspend AIAgentLLMWriteSession.() -> Any?>()
        val llmContext = mockk<AIAgentLLMContext>()
        coEvery { llmContext.writeSession(capture(sessionBlock)) } returns Unit

        val agentMemory = AgentMemory(
            agentMemory = provider,
            scopesProfile = MemoryScopesProfile(MemoryScopeType.PRODUCT to scope.name),
            defaultTokenBudget = defaultBudget
        )

        agentMemory.loadFactsToAgent(
            llm = llmContext,
            concept = concept,
            scopes = listOf(MemoryScopeType.PRODUCT),
            subjects = listOf(TicketSubject),
            budget = explicitBudget
        )

        assertTrue(sessionBlock.isCaptured, "AgentMemory should invoke writeSession")
        val session = mockk<AIAgentLLMWriteSession> {
            every { appendPrompt(capture(promptSlot)) } returns Unit
        }
        sessionBlock.captured.invoke(session)

        assertTrue(promptSlot.isCaptured, "AgentMemory should update the prompt during load")
        val messageSlot = slot<String>()
        val builder = mockk<PromptBuilder> {
            every { user(capture(messageSlot)) } returns mockk(relaxed = true)
        }
        promptSlot.captured.invoke(builder)
        return messageSlot.captured
    }

    private fun approximateTokens(text: String): Int = ceil(text.length / 4.0).toInt()
}
