package ai.koog.agents.memory

import ai.koog.agents.core.agent.context.AIAgentLLMContext
import ai.koog.agents.core.agent.session.AIAgentLLMWriteSession
import ai.koog.agents.memory.config.MemoryScopeType
import ai.koog.agents.memory.config.MemoryScopesProfile
import ai.koog.agents.memory.feature.AgentMemory
import ai.koog.agents.memory.feature.similarity.EmbeddingProvider
import ai.koog.agents.memory.model.Concept
import ai.koog.agents.memory.model.FactType
import ai.koog.agents.memory.model.MemoryScope
import ai.koog.agents.memory.model.MemorySubject
import ai.koog.agents.memory.model.MultipleFacts
import ai.koog.agents.memory.model.SingleFact
import ai.koog.agents.memory.model.TokenBudget
import ai.koog.agents.memory.providers.AgentMemoryProvider
import ai.koog.prompt.dsl.PromptBuilder
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AgentMemoryEnhancementsTest {
    @kotlinx.serialization.Serializable
    object TestUserSubject : MemorySubject() {
        override val name: String = "user"
        override val promptDescription: String = "Test user subject"
        override val priorityLevel: Int = 0
    }

    private val testScopesProfile = MemoryScopesProfile(MemoryScopeType.AGENT to "agent-test")

    @Test
    fun loadAllFactsToAgent_respectsTokenBudget() = runTest {
        val conceptA = Concept("alpha", "First concept", FactType.MULTIPLE)
        val conceptB = Concept("beta", "Second concept", FactType.MULTIPLE)
        val factA = MultipleFacts(conceptA, timestamp = 1L, values = listOf("short"))
        val factB = MultipleFacts(conceptB, timestamp = 2L, values = listOf("another"))

        val memoryProvider = mockk<AgentMemoryProvider> {
            coEvery {
                loadAll(TestUserSubject, MemoryScope.Agent("agent-test"))
            } returns listOf(factA, factB)
        }

        val promptUpdate = slot<PromptBuilder.() -> Unit>()
        val messageSlot = slot<String>()
        val llmContext = mockk<AIAgentLLMContext> {
            coEvery {
                writeSession(
                    match<suspend AIAgentLLMWriteSession.() -> Any?> { block ->
                        val session = mockk<AIAgentLLMWriteSession> {
                            every { updatePrompt(capture(promptUpdate)) } returns Unit
                        }
                        runBlocking { block.invoke(session) }
                        true
                    }
                )
            } returns Unit
        }

        val memory = AgentMemory(
            agentMemory = memoryProvider,
            llm = llmContext,
            scopesProfile = testScopesProfile
        )

        memory.loadAllFactsToAgent(
            scopes = listOf(MemoryScopeType.AGENT),
            subjects = listOf(TestUserSubject),
            budget = TokenBudget(maxTokens = 2, maxFacts = 1)
        )

        val promptBuilder = mockk<PromptBuilder> {
            every { user(capture(messageSlot)) } returns mockk()
        }
        promptUpdate.captured.invoke(promptBuilder)

        val message = messageSlot.captured
        assertTrue(message.contains("alpha"), "Expected alpha fact to be present")
        assertFalse(message.contains("beta"), "Token budget should drop beta fact")
    }

    @Test
    fun loadAllFactsToAgent_ranksByEmbeddingWhenQueryProvided() = runTest {
        val conceptLow = Concept("generic", "Generic concept", FactType.SINGLE)
        val conceptHigh = Concept("priority", "Priority concept", FactType.SINGLE)
        val factLow = SingleFact(conceptLow, timestamp = 1L, value = "generic info")
        val factHigh = SingleFact(conceptHigh, timestamp = 2L, value = "priority data")

        val memoryProvider = mockk<AgentMemoryProvider> {
            coEvery {
                loadAll(TestUserSubject, MemoryScope.Agent("agent-test"))
            } returns listOf(factLow, factHigh)
        }

        val embeddingProvider = object : EmbeddingProvider {
            override suspend fun embed(text: String): FloatArray =
                if (text.contains("priority", ignoreCase = true)) floatArrayOf(1f) else floatArrayOf(0f)
        }

        val promptUpdate = slot<PromptBuilder.() -> Unit>()
        val messageSlot = slot<String>()
        val llmContext = mockk<AIAgentLLMContext> {
            coEvery {
                writeSession(
                    match<suspend AIAgentLLMWriteSession.() -> Any?> { block ->
                        val session = mockk<AIAgentLLMWriteSession> {
                            every { updatePrompt(capture(promptUpdate)) } returns Unit
                        }
                        runBlocking { block.invoke(session) }
                        true
                    }
                )
            } returns Unit
        }

        val memory = AgentMemory(
            agentMemory = memoryProvider,
            llm = llmContext,
            scopesProfile = testScopesProfile,
            embeddingProvider = embeddingProvider
        )

        memory.loadAllFactsToAgent(
            scopes = listOf(MemoryScopeType.AGENT),
            subjects = listOf(TestUserSubject),
            budget = TokenBudget(maxTokens = 10, maxFacts = 1),
            query = "priority"
        )

        val promptBuilder = mockk<PromptBuilder> {
            every { user(capture(messageSlot)) } returns mockk()
        }
        promptUpdate.captured.invoke(promptBuilder)

        val message = messageSlot.captured
        assertTrue(message.contains("priority data"), "Expected priority fact to be selected by ranking")
        assertFalse(message.contains("generic info"), "Budget should only allow the top ranked fact")
    }
}
