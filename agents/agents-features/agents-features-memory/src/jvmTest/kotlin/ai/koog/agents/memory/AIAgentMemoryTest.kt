package ai.koog.agents.memory

import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.context.AIAgentLLMContext
import ai.koog.agents.core.agent.session.AIAgentLLMWriteSession
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.memory.config.MemoryScopeType
import ai.koog.agents.memory.config.MemoryScopesProfile
import ai.koog.agents.memory.feature.AgentMemory
import ai.koog.agents.memory.model.Concept
import ai.koog.agents.memory.model.Fact
import ai.koog.agents.memory.model.FactType
import ai.koog.agents.memory.model.MemoryScope
import ai.koog.agents.memory.model.MemorySubject
import ai.koog.agents.memory.model.MultipleFacts
import ai.koog.agents.memory.model.SingleFact
import ai.koog.agents.memory.providers.AgentMemoryProvider
import ai.koog.agents.memory.providers.NoMemory
import ai.koog.agents.testing.tools.MockEnvironment
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.dsl.PromptBuilder
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(InternalAgentsApi::class)
class AIAgentMemoryTest {
    object MemorySubjects {
        /**
         * Information specific to the current user
         * Examples: Preferences, settings, authentication tokens
         */
        @Serializable
        data object User : MemorySubject() {
            override val name: String = "user"
            override val promptDescription: String =
                "User's preferences, settings, and behavior patterns, expectations from the agent, preferred messaging style, etc."
            override val priorityLevel: Int = 2
        }
    }

    private val testModel = mockk<LLModel> {
        every { id } returns "test-model"
        every { provider } returns mockk<LLMProvider>()
    }

    private val testClock: Clock = object : Clock {
        override fun now(): Instant = Instant.parse("2023-01-01T00:00:00Z")
    }

    @Test
    fun testNoMemoryLogging() = runTest {
        val concept = Concept("test", "test description", FactType.SINGLE)
        val subject = MemorySubjects.User
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
        val memoryProvider = mockk<AgentMemoryProvider>()
        val promptExecutor = mockk<PromptExecutor>()

        val response = mockk<Message.Response>()
        every { response.content } returns "Test fact"

        coEvery {
            promptExecutor.execute(any(), any(), any())
        } returns listOf(response)

        coEvery {
            memoryProvider.save(any(), any(), any())
        } returns Unit

        val llm = AIAgentLLMContext(
            tools = emptyList(),
            prompt = prompt("test") { },
            model = testModel,
            responseProcessor = null,
            promptExecutor = promptExecutor,
            environment = MockEnvironment(toolRegistry = ToolRegistry.EMPTY, promptExecutor),
            config = AIAgentConfig(Prompt.Empty, testModel, 100),
            clock = testClock
        )

        val memory = AgentMemory(
            agentMemory = memoryProvider,
            scopesProfile = MemoryScopesProfile()
        )
        val concept = Concept("test", "test description", FactType.SINGLE)

        memory.saveFactsFromHistory(
            llm = llm,
            concept = concept,
            subject = MemorySubjects.User,
            scope = MemoryScope.Agent("test")
        )

        coVerify {
            memoryProvider.save(
                match {
                    it is SingleFact &&
                        it.concept == concept &&
                        it.timestamp > 0 // Verify timestamp is set
                },
                MemorySubjects.User,
                MemoryScope.Agent("test")
            )
        }
    }

    @Test
    fun testLoadFactsWithScopeMatching() = runTest {
        val memoryProvider = mockk<AgentMemoryProvider>()
        val promptExecutor = mockk<PromptExecutor>()
        val concept = Concept("test", "test description", FactType.SINGLE)

        val testTimestamp = 1234567890L
        val agentFact = SingleFact(concept = concept, value = "agent fact", timestamp = testTimestamp)
        val featureFact = SingleFact(concept = concept, value = "feature fact", timestamp = testTimestamp)
        val productFact = SingleFact(concept = concept, value = "product fact", timestamp = testTimestamp)

        // Mock responses for User subject with specific scopes
        coEvery {
            memoryProvider.load(concept, MemorySubjects.User, MemoryScope.Agent("test-agent"))
        } returns listOf(agentFact)

        coEvery {
            memoryProvider.load(concept, MemorySubjects.User, MemoryScope.Feature("test-feature"))
        } returns listOf(featureFact)

        coEvery {
            memoryProvider.load(concept, MemorySubjects.User, MemoryScope.Product("test-product"))
        } returns listOf(productFact)

        coEvery {
            memoryProvider.load(concept, MemorySubjects.User, MemoryScope.CrossProduct)
        } returns emptyList()

        // All other requests
        coEvery {
            memoryProvider.load(any(), any(), any())
        } returns emptyList()

        val response = mockk<Message.Response>()
        every { response.content } returns "OK"

        coEvery {
            promptExecutor.execute(any(), any(), any())
        } returns listOf(response)

        val llm = AIAgentLLMContext(
            tools = emptyList(),
            prompt = prompt("test") { },
            model = testModel,
            responseProcessor = null,
            promptExecutor = promptExecutor,
            environment = MockEnvironment(toolRegistry = ToolRegistry.EMPTY, promptExecutor),
            config = AIAgentConfig(Prompt.Empty, testModel, 100),
            clock = testClock
        )

        val memory = AgentMemory(
            agentMemory = memoryProvider,
            scopesProfile = MemoryScopesProfile(
                MemoryScopeType.AGENT to "test-agent",
                MemoryScopeType.FEATURE to "test-feature",
                MemoryScopeType.PRODUCT to "test-product",
                MemoryScopeType.ORGANIZATION to "test-organization"
            )
        )

        memory.loadFactsToAgent(llm = llm, concept = concept, subjects = listOf(MemorySubjects.User))

        coVerify {
            memoryProvider.load(concept, MemorySubjects.User, MemoryScope.Agent("test-agent"))
            memoryProvider.load(
                concept,
                MemorySubjects.User,
                MemoryScope.Feature("test-feature")
            )
            memoryProvider.load(
                concept,
                MemorySubjects.User,
                MemoryScope.Product("test-product")
            )
            memoryProvider.load(concept, MemorySubjects.User, MemoryScope.CrossProduct)
        }
    }

    @Test
    fun testLoadFactsWithOverriding() = runTest {
        val memoryProvider = mockk<AgentMemoryProvider>()
        val concept = Concept("test", "test description", FactType.SINGLE)
        val machineFact = SingleFact(concept = concept, value = "machine fact", timestamp = 1234567890L)

        // Mock memory feature to return only machine fact
        coEvery {
            memoryProvider.load(any(), any(), any())
        } answers {
            println(
                "[DEBUG_LOG] Loading facts for subject: ${secondArg<MemorySubject>()}, scope: ${thirdArg<MemoryScope>()}"
            )
            listOf(machineFact)
        }

        // Create a slot to capture the prompt update
        val promptUpdateSlot = slot<PromptBuilder.() -> Unit>()

        val llm = mockk<AIAgentLLMContext> {
            coEvery {
                writeSession<Any?>(any<suspend AIAgentLLMWriteSession.() -> Any?>())
            } coAnswers {
                val block = firstArg<suspend AIAgentLLMWriteSession.() -> Any?>()
                val writeSession = mockk<AIAgentLLMWriteSession> {
                    every { appendPrompt(capture(promptUpdateSlot)) } answers {
                        println("[DEBUG_LOG] Updating prompt with message containing facts")
                    }
                }
                block.invoke(writeSession)
            }
        }

        val memory = AgentMemory(
            agentMemory = memoryProvider,
            scopesProfile = MemoryScopesProfile(MemoryScopeType.AGENT to "test-agent")
        )

        memory.loadFactsToAgent(llm, concept, subjects = listOf(MemorySubjects.User))

        // Verify that writeSession was called and the prompt was updated with facts
        coVerify {
            llm.writeSession(any())
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
        val memoryProvider = mockk<AgentMemoryProvider>()
        val promptExecutor = mockk<PromptExecutor>()
        val savedFacts = mutableListOf<SingleFact>()

        // Mock LLM response
        val response = mockk<Message.Response>()
        every { response.content } returns "Test fact"
        coEvery {
            promptExecutor.execute(any(), any(), any())
        } returns listOf(response)

        // Mock memory feature to capture saved facts
        coEvery {
            memoryProvider.save(capture(savedFacts), any(), any())
        } returns Unit

        val llm = AIAgentLLMContext(
            tools = emptyList(),
            prompt = prompt("test") { },
            model = testModel,
            promptExecutor = promptExecutor,
            responseProcessor = null,
            environment = MockEnvironment(toolRegistry = ToolRegistry.EMPTY, promptExecutor),
            config = AIAgentConfig(Prompt.Empty, testModel, 100),
            clock = testClock
        )

        val memory = AgentMemory(
            agentMemory = memoryProvider,
            scopesProfile = MemoryScopesProfile()
        )

        val concept = Concept("test", "test description", FactType.SINGLE)
        val subject = MemorySubjects.User
        val scope = MemoryScope.Agent("test")

        // Save multiple facts
        repeat(3) { index ->
            memory.saveFactsFromHistory(
                llm = llm,
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
            memoryProvider.load(concept, subject, scope)
        } returns savedFacts

        val loadedFacts = memoryProvider.load(concept, subject, scope)
        assertEquals(savedFacts.size, loadedFacts.size, "Should load all saved facts")

        // Verify loaded facts maintain timestamp order
        previousTimestamp = 0L
        loadedFacts.forEach { fact ->
            assertTrue(fact.timestamp > previousTimestamp, "Loaded facts should maintain timestamp order")
            previousTimestamp = fact.timestamp
        }
    }

    @Test
    fun testSaveFactsFromHistoryWithCustomModel() = runTest {
        val customModel = OpenAIModels.Chat.O3Mini
        val originalModel = testModel

        val memoryProvider = mockk<AgentMemoryProvider>()
        val promptExecutor = mockk<PromptExecutor>()
        val savedFacts = mutableListOf<Fact>()
        val savedSubjects = mutableListOf<MemorySubject>()
        val savedScopes = mutableListOf<MemoryScope>()
        val fact = "Custom model extracted fact"

        val response = mockk<Message.Response>()
        every { response.content } returns """{"fact": "$fact"}"""

        val capturedModels = mutableListOf<LLModel>()
        coEvery {
            promptExecutor.execute(any(), capture(capturedModels), any())
        } returns listOf(response)

        coEvery {
            memoryProvider.save(capture(savedFacts), capture(savedSubjects), capture(savedScopes))
        } returns Unit

        val llm = AIAgentLLMContext(
            tools = emptyList(),
            prompt = prompt("test") {
                system("Test system message")
                user("I prefer using Java for enterprise applications")
                assistant("I'll remember your preference for Java in enterprise development")
            },
            model = originalModel,
            responseProcessor = null,
            promptExecutor = promptExecutor,
            environment = MockEnvironment(toolRegistry = ToolRegistry.EMPTY, promptExecutor),
            config = AIAgentConfig(Prompt.Empty, originalModel, 100),
            clock = testClock
        )

        val memory = AgentMemory(
            agentMemory = memoryProvider,
            scopesProfile = MemoryScopesProfile()
        )

        val concept = Concept("programming-preference", "User's programming language preference", FactType.SINGLE)
        val testSubject = MemorySubjects.User
        val testScope = MemoryScope.Agent("test")

        memory.saveFactsFromHistory(
            llm = llm,
            concept = concept,
            subject = testSubject,
            scope = testScope,
            retrievalModel = customModel
        )

        assertEquals(1, capturedModels.size, "PromptExecutor should be called exactly once")
        assertEquals(customModel, capturedModels.first(), "Custom model should be used for fact extraction")

        assertEquals(1, savedFacts.size, "Exactly one fact should be saved")
        val savedFact = savedFacts.first()
        assertTrue(savedFact is SingleFact, "Saved fact should be SingleFact type")
        assertEquals(concept, savedFact.concept, "Fact concept should match input")
        assertEquals(fact, savedFact.value, "Fact value should match response")
        assertTrue(savedFact.timestamp > 0, "Fact should have valid timestamp")
        assertTrue(savedFact.timestamp <= System.currentTimeMillis(), "Timestamp should not be in the future")

        assertEquals(1, savedSubjects.size, "Exactly one subject should be saved")
        assertEquals(testSubject, savedSubjects.first(), "Subject should match input")
        assertEquals(1, savedScopes.size, "Exactly one scope should be saved")
        assertEquals(testScope, savedScopes.first(), "Scope should match input")

        coVerify(exactly = 1) {
            memoryProvider.save(any(), testSubject, testScope)
        }

        coVerify(exactly = 1) {
            promptExecutor.execute(any(), customModel, any())
        }
    }

    @Test
    fun testSaveFactsFromHistoryMultipleFactsWithCustomModel() = runTest {
        val customModel = AnthropicModels.Sonnet_4
        val memoryProvider = mockk<AgentMemoryProvider>()
        val promptExecutor = mockk<PromptExecutor>()
        val savedFacts = mutableListOf<Fact>()
        val expectedFacts = listOf("Java for backend services", "Python for data analysis", "JavaScript for frontend")
        val testScopeName = "test"

        val response = mockk<Message.Response>()
        every {
            response.content
        } returns """
            {
                "facts": [
                    {"fact": "Java for backend services"},
                    {"fact": "Python for data analysis"},
                    {"fact": "JavaScript for frontend"}
                ]
            }
        """.trimIndent()

        val capturedModels = mutableListOf<LLModel>()
        coEvery {
            promptExecutor.execute(any(), capture(capturedModels), any())
        } returns listOf(response)

        coEvery {
            memoryProvider.save(capture(savedFacts), any(), any())
        } returns Unit

        val llm = AIAgentLLMContext(
            tools = emptyList(),
            prompt = prompt("test") {
                system("Test system message")
                user("I use Java for backend, Python for data analysis, and JavaScript for frontend")
                assistant("I'll remember your language preferences for different domains")
            },
            model = testModel,
            responseProcessor = null,
            promptExecutor = promptExecutor,
            environment = MockEnvironment(toolRegistry = ToolRegistry.EMPTY, promptExecutor),
            config = AIAgentConfig(Prompt.Empty, testModel, 100),
            clock = testClock
        )

        val memory = AgentMemory(
            agentMemory = memoryProvider,
            scopesProfile = MemoryScopesProfile()
        )

        val concept =
            Concept("language-preferences", "User's programming language preferences by domain", FactType.MULTIPLE)

        memory.saveFactsFromHistory(
            llm = llm,
            concept = concept,
            subject = MemorySubjects.User,
            scope = MemoryScope.Agent(testScopeName),
            retrievalModel = customModel
        )

        assertEquals(1, capturedModels.size, "PromptExecutor should be called exactly once")
        assertEquals(customModel, capturedModels.first(), "Custom model should be used")

        assertEquals(1, savedFacts.size, "Exactly one MultipleFacts should be saved")
        val savedFact = savedFacts.first()
        assertTrue(savedFact is MultipleFacts, "Saved fact should be MultipleFacts type")
        assertEquals(concept, savedFact.concept, "Fact concept should match input")
        assertEquals(FactType.MULTIPLE, savedFact.concept.factType, "Concept should be MULTIPLE type")
        assertTrue(savedFact.timestamp > 0, "Fact should have valid timestamp")

        assertEquals(expectedFacts.size, savedFact.values.size, "Should have exactly ${expectedFacts.size} facts")
        expectedFacts.forEach { expectedFact ->
            assertTrue(
                savedFact.values.contains(expectedFact),
                "Should contain fact: '$expectedFact'. Actual values: ${savedFact.values}"
            )
        }

        assertEquals(savedFact.values.size, savedFact.values.toSet().size, "Should not have duplicate facts")

        coVerify(exactly = 1) {
            memoryProvider.save(
                match {
                    it is MultipleFacts &&
                        it.concept == concept &&
                        it.timestamp > 0 &&
                        it.values.size == expectedFacts.size &&
                        expectedFacts.all { expected -> it.values.contains(expected) }
                },
                MemorySubjects.User,
                MemoryScope.Agent(testScopeName)
            )
        }
    }

    @Test
    fun testLoadFactsToAgent() = runTest {
        val memoryProvider = mockk<AgentMemoryProvider>()
        val promptExecutor = mockk<PromptExecutor>()
        val concept = Concept("test", "test description", FactType.SINGLE)

        coEvery {
            memoryProvider.load(any(), any(), any())
        } returns listOf(SingleFact(concept = concept, value = "test fact", timestamp = 1234567890L))

        val response = mockk<Message.Response>()
        every { response.content } returns "OK"

        coEvery {
            promptExecutor.execute(any(), any(), any())
        } returns listOf(response)

        val llm = AIAgentLLMContext(
            tools = emptyList(),
            prompt = prompt("test") { },
            model = testModel,
            responseProcessor = null,
            promptExecutor = promptExecutor,
            environment = MockEnvironment(toolRegistry = ToolRegistry.EMPTY, promptExecutor),
            config = AIAgentConfig(Prompt.Empty, testModel, 100),
            clock = testClock
        )

        val memory = AgentMemory(
            agentMemory = memoryProvider,
            scopesProfile = MemoryScopesProfile(
                MemoryScopeType.AGENT to "test-agent",
            )
        )

        memory.loadFactsToAgent(
            llm = llm,
            concept = concept,
            scopes = listOf(MemoryScopeType.AGENT),
            subjects = listOf(MemorySubjects.User)
        )

        coVerify {
            memoryProvider.load(concept, any(), any())
        }
    }
}
