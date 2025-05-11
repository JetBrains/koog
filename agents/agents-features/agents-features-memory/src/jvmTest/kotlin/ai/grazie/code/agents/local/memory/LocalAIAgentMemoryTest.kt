package ai.grazie.code.agents.local.memory

import ai.grazie.code.agents.core.agent.config.LocalAgentConfig
import ai.grazie.code.agents.core.agent.entity.stage.LocalAgentLLMContext
import ai.grazie.code.agents.core.agent.entity.stage.LocalAgentLLMWriteSession
import ai.grazie.code.agents.local.memory.config.MemoryScopeType
import ai.grazie.code.agents.local.memory.config.MemoryScopesProfile
import ai.grazie.code.agents.local.memory.feature.MemoryFeature
import ai.grazie.code.agents.local.memory.model.*
import ai.grazie.code.agents.local.memory.providers.AgentMemoryProvider
import ai.grazie.code.agents.local.memory.providers.NoMemory
import ai.jetbrains.code.prompt.dsl.Prompt
import ai.jetbrains.code.prompt.dsl.PromptBuilder
import ai.jetbrains.code.prompt.dsl.prompt
import ai.jetbrains.code.prompt.executor.model.PromptExecutor
import ai.jetbrains.code.prompt.llm.LLModel
import ai.jetbrains.code.prompt.message.Message
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocalAIAgentMemoryTest {
    private val testModel = mockk<LLModel> {
        every { id } returns "test-model"
    }

    @Test
    fun testNoMemoryLogging() = runTest {
        val concept = Concept("test", "test description", FactType.SINGLE)
        val subject = MemorySubject.USER
        val scope = MemoryScope.Agent("test")

        // Test save
        NoMemory.save(SingleFact(concept = concept, value = "test value", timestamp = 1234567890L), subject, scope)
        // Verify that save operation just logs and returns (no actual saving)

        // Test load
        val loadedFacts = NoMemory.load(concept, subject, scope)
        assertEquals<List<Fact>>(emptyList(), loadedFacts, "NoMemory should always return empty list")

        // Test loadByQuestion
        val questionFacts = NoMemory.loadByDescription("test question", subject, scope)
        assertEquals<List<Fact>>(emptyList(), questionFacts, "NoMemory should always return empty list")
    }

    @Test
    fun testSaveFactsFromHistory() = runTest {
        val memoryFeature = mockk<AgentMemoryProvider>()
        val promptExecutor = mockk<PromptExecutor>()
        val responseSlot = slot<Message.Response>()

        val response = mockk<Message.Response>()
        every { response.content } returns "Test fact"

        coEvery {
            promptExecutor.execute(any(), any(), any())
        } returns listOf(response)

        coEvery {
            memoryFeature.save(any(), any(), any())
        } returns Unit

        val llmContext = LocalAgentLLMContext(
            tools = emptyList(),
            prompt = prompt("test") { },
            model = testModel,
            promptExecutor = promptExecutor,
            environment = MockAgentEnvironment(),
            config = LocalAgentConfig(Prompt.Empty, testModel, 100),
        )

        val memory = MemoryFeature(
            agentMemory = memoryFeature,
            llm = llmContext,
            scopesProfile = MemoryScopesProfile()
        )
        val concept = Concept("test", "test description", FactType.SINGLE)

        memory.saveFactsFromHistory(
            concept = concept,
            subject = MemorySubject.USER,
            scope = MemoryScope.Agent("test")
        )

        coVerify {
            memoryFeature.save(
                match {
                    it is SingleFact &&
                            it.concept == concept &&
                            it.timestamp > 0 // Verify timestamp is set
                },
                MemorySubject.USER,
                MemoryScope.Agent("test")
            )
        }
    }

    @Test
    fun testLoadFactsWithScopeMatching() = runTest {
        val memoryFeature = mockk<AgentMemoryProvider>()
        val promptExecutor = mockk<PromptExecutor>()
        val concept = Concept("test", "test description", FactType.SINGLE)

        val testTimestamp = 1234567890L
        val agentFact = SingleFact(concept = concept, value = "agent fact", timestamp = testTimestamp)
        val featureFact = SingleFact(concept = concept, value = "feature fact", timestamp = testTimestamp)
        val productFact = SingleFact(concept = concept, value = "product fact", timestamp = testTimestamp)

        // Mock responses for all subjects for Agent scope
        MemorySubject.entries.forEach { subject ->
            coEvery {
                memoryFeature.load(concept, subject, MemoryScope.Agent("test-agent"))
            } returns when (subject) {
                MemorySubject.USER -> listOf(agentFact)
                else -> emptyList()
            }
        }

        // Mock responses for all subjects for Feature scope
        MemorySubject.entries.forEach { subject ->
            coEvery {
                memoryFeature.load(concept, subject, MemoryScope.Feature("test-feature"))
            } returns when (subject) {
                MemorySubject.USER -> listOf(featureFact)
                else -> emptyList()
            }
        }

        // Mock responses for all subjects for Product scope
        MemorySubject.entries.forEach { subject ->
            coEvery {
                memoryFeature.load(concept, subject, MemoryScope.Product("test-product"))
            } returns when (subject) {
                MemorySubject.USER -> listOf(productFact)
                else -> emptyList()
            }
        }

        // Mock responses for CrossProduct scope
        MemorySubject.values().forEach { subject ->
            coEvery {
                memoryFeature.load(concept, subject, MemoryScope.CrossProduct)
            } returns emptyList()
        }

        val response = mockk<Message.Response>()
        every { response.content } returns "OK"

        coEvery {
            promptExecutor.execute(any(), any(), any())
        } returns listOf(response)

        val llmContext = LocalAgentLLMContext(
            tools = emptyList(),
            prompt = prompt("test") { },
            model = testModel,
            promptExecutor = promptExecutor,
            environment = MockAgentEnvironment(),
            config = LocalAgentConfig(Prompt.Empty, testModel, 100),
        )

        val memory = MemoryFeature(
            agentMemory = memoryFeature,
            llm = llmContext,
            scopesProfile = MemoryScopesProfile(
                MemoryScopeType.AGENT to "test-agent",
                MemoryScopeType.FEATURE to "test-feature",
                MemoryScopeType.PRODUCT to "test-product",
                MemoryScopeType.ORGANIZATION to "test-organization"
            )
        )

        memory.loadFactsToAgent(concept, subjects = listOf(MemorySubject.USER))

        coVerify {
            memoryFeature.load(concept, MemorySubject.USER, MemoryScope.Agent("test-agent"))
            memoryFeature.load(
                concept,
                MemorySubject.USER,
                MemoryScope.Feature("test-feature")
            )
            memoryFeature.load(
                concept,
                MemorySubject.USER,
                MemoryScope.Product("test-product")
            )
            memoryFeature.load(concept, MemorySubject.USER, MemoryScope.CrossProduct)
        }
    }

    @Test
    fun testLoadFactsWithOverriding() = runTest {
        val memoryFeature = mockk<AgentMemoryProvider>()
        val concept = Concept("test", "test description", FactType.SINGLE)
        val machineFact = SingleFact(concept = concept, value = "machine fact", timestamp = 1234567890L)

        // Mock memory feature to return only machine fact
        coEvery {
            memoryFeature.load(any(), any(), any())
        } answers {
            println("[DEBUG_LOG] Loading facts for subject: ${secondArg<MemorySubject>()}, scope: ${thirdArg<MemoryScope>()}")
            listOf(machineFact)
        }

        // Create a slot to capture the prompt update
        val promptUpdateSlot = slot<PromptBuilder.() -> Unit>()

        // Mock LLM context to capture prompt updates
        mockkConstructor(LocalAgentLLMWriteSession::class)

        val llmContext = mockk<LocalAgentLLMContext>() {
            coEvery {
                writeSession<Any?>(any<suspend LocalAgentLLMWriteSession.() -> Any?>())
            } coAnswers {
                val block = firstArg<suspend LocalAgentLLMWriteSession.() -> Any?>()
                val writeSession = mockk<LocalAgentLLMWriteSession> {
                    every { updatePrompt(capture(promptUpdateSlot)) } answers {
                        println("[DEBUG_LOG] Updating prompt with message containing facts")
                    }
                }
                block.invoke(writeSession)
            }
        }

        val memory = MemoryFeature(
            agentMemory = memoryFeature,
            llm = llmContext,
            scopesProfile = MemoryScopesProfile(MemoryScopeType.AGENT to "test-agent")
        )

        memory.loadFactsToAgent(concept)

        // Verify that writeSession was called and the prompt was updated with facts
        coVerify {
            llmContext.writeSession(any())
        }
        assertTrue(promptUpdateSlot.isCaptured, "Prompt update should be captured")

        // Create a mock PromptBuilder to capture the actual message
        val messageSlot = slot<String>()
        val mockPromptBuilder = mockk<PromptBuilder> {
            every { user(capture(messageSlot)) } returns mockk()
        }
        promptUpdateSlot.captured.invoke(mockPromptBuilder)

        assertTrue(
            messageSlot.captured.contains("machine fact"),
            "Expected message to contain 'machine fact', but was: ${messageSlot.captured}"
        )
    }

    @Test
    fun testSequentialTimestamps() = runTest {
        val memoryFeature = mockk<AgentMemoryProvider>()
        val promptExecutor = mockk<PromptExecutor>()
        val savedFacts = mutableListOf<SingleFact>()

        // Mock DefaultTimeProvider to return sequential timestamps
        mockkObject(DefaultTimeProvider)
        var currentTime = 1000L
        every { DefaultTimeProvider.getCurrentTimestamp() } answers { currentTime++ }

        // Mock LLM response
        val response = mockk<Message.Response>()
        every { response.content } returns "Test fact"
        coEvery {
            promptExecutor.execute(any(), any(), any())
        } returns listOf(response)

        // Mock memory feature to capture saved facts
        coEvery {
            memoryFeature.save(capture(savedFacts), any(), any())
        } returns Unit

        val llmContext = LocalAgentLLMContext(
            tools = emptyList(),
            prompt = prompt("test") { },
            model = testModel,
            promptExecutor = promptExecutor,
            environment = MockAgentEnvironment(),
            config = LocalAgentConfig(Prompt.Empty, testModel, 100),
        )

        val memory = MemoryFeature(
            agentMemory = memoryFeature,
            llm = llmContext,
            scopesProfile = MemoryScopesProfile()
        )

        val concept = Concept("test", "test description", FactType.SINGLE)
        val subject = MemorySubject.USER
        val scope = MemoryScope.Agent("test")

        // Save multiple facts
        repeat(3) { index ->
            memory.saveFactsFromHistory(
                concept = concept,
                subject = subject,
                scope = scope
            )
        }

        // Verify facts were saved with sequential timestamps
        assertEquals(3, savedFacts.size, "Should have saved 3 facts")

        // Verify timestamps are sequential
        var previousTimestamp = 0L
        savedFacts.forEach { fact ->
            assertTrue(fact.timestamp > previousTimestamp, "Timestamps should be strictly increasing")
            previousTimestamp = fact.timestamp
        }

        // Load facts and verify they maintain order
        coEvery {
            memoryFeature.load(concept, subject, scope)
        } returns savedFacts

        val loadedFacts = memoryFeature.load(concept, subject, scope)
        assertEquals(savedFacts.size, loadedFacts.size, "Should load all saved facts")

        // Verify loaded facts maintain timestamp order
        previousTimestamp = 0L
        loadedFacts.forEach { fact ->
            assertTrue(fact.timestamp > previousTimestamp, "Loaded facts should maintain timestamp order")
            previousTimestamp = fact.timestamp
        }
    }

    fun testLoadFactsToAgent() = runTest {
        val memoryFeature = mockk<AgentMemoryProvider>()
        val promptExecutor = mockk<PromptExecutor>()
        val concept = Concept("test", "test description", FactType.SINGLE)

        coEvery {
            memoryFeature.load(any(), any(), any())
        } returns listOf(SingleFact(concept = concept, value = "test fact", timestamp = 1234567890L))

        val response = mockk<Message.Response>()
        every { response.content } returns "OK"

        coEvery {
            promptExecutor.execute(any(), any(), any())
        } returns listOf(response)

        val llmContext = LocalAgentLLMContext(
            tools = emptyList(),
            prompt = prompt("test") { },
            model = testModel,
            promptExecutor = promptExecutor,
            environment = MockAgentEnvironment(),
            config = LocalAgentConfig(Prompt.Empty, testModel, 100),
        )

        val memory = MemoryFeature(
            agentMemory = memoryFeature,
            llm = llmContext,
            scopesProfile = MemoryScopesProfile(
                MemoryScopeType.AGENT to "test-agent",
            )
        )

        memory.loadFactsToAgent(concept)

        coVerify {
            memoryFeature.load(concept, any(), any())
        }
    }
}