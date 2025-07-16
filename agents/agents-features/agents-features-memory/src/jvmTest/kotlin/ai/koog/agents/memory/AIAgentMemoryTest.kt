package ai.koog.agents.memory

import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.context.AIAgentLLMContext
import ai.koog.agents.core.agent.session.AIAgentLLMWriteSession
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.memory.config.MemoryScopeType
import ai.koog.agents.memory.config.MemoryScopesProfile
import ai.koog.agents.memory.feature.AgentMemory
import ai.koog.agents.memory.model.*
import ai.koog.agents.memory.providers.AgentMemoryProvider
import ai.koog.agents.memory.providers.NoMemory
import ai.koog.agents.testing.tools.MockEnvironment
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.dsl.PromptBuilder
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import io.mockk.*
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
         * Information specific to the local machine environment
         * Examples: Installed tools, SDKs, OS configuration, available commands
         */
        @Serializable
        data object Machine : MemorySubject() {
            override val name: String = "machine"
            override val promptDescription: String = "Technical environment (installed tools, package managers, packages, SDKs, OS, etc.)"
            override val priorityLevel: Int = 1
        }

        /**
         * Information specific to the current user
         * Examples: Preferences, settings, authentication tokens
         */
        @Serializable
        data object User : MemorySubject() {
            override val name: String = "user"
            override val promptDescription: String = "User's preferences, settings, and behavior patterns, expectations from the agent, preferred messaging style, etc."
            override val priorityLevel: Int = 2
        }

        /**
         * Information specific to the current project
         * Examples: Build configuration, dependencies, code style rules
         */
        @Serializable
        data object Project : MemorySubject() {
            override val name: String = "project"
            override val promptDescription: String = "Project details, requirements, and constraints, dependencies, folders, technologies, modules, documentation, etc."
            override val priorityLevel: Int = 3
        }

        /**
         * Information shared across an organization
         * Examples: Coding standards, shared configurations, team practices
         */
        @Serializable
        data object Organization : MemorySubject() {
            override val name: String = "organization"
            override val promptDescription: String = "Organization structure and policies"
            override val priorityLevel: Int = 4
        }
    }

    private val testModel = mockk<LLModel> {
        every { id } returns "test-model"
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
        val responseSlot = slot<Message.Response>()

        val response = mockk<Message.Response>()
        every { response.content } returns "Test fact"

        coEvery {
            promptExecutor.execute(any(), any(), any())
        } returns listOf(response)

        coEvery {
            memoryProvider.save(any(), any(), any())
        } returns Unit

        val llmContext = AIAgentLLMContext(
            tools = emptyList(),
            prompt = prompt("test") { },
            model = testModel,
            promptExecutor = promptExecutor,
            environment = MockEnvironment(toolRegistry = ToolRegistry.EMPTY, promptExecutor),
            config = AIAgentConfig(Prompt.Empty, testModel, 100),
            clock = testClock
        )

        val memory = AgentMemory(
            agentMemory = memoryProvider,
            llm = llmContext,
            scopesProfile = MemoryScopesProfile()
        )
        val concept = Concept("test", "test description", FactType.SINGLE)

        memory.saveFactsFromHistory(
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

        // Mock responses for all subjects for Agent scope
        MemorySubject.registeredSubjects.forEach { subject ->
            coEvery {
                memoryProvider.load(concept, subject, MemoryScope.Agent("test-agent"))
            } returns when (subject) {
                MemorySubjects.User -> listOf(agentFact)
                else -> emptyList()
            }
        }

        // Mock responses for all subjects for Feature scope
        MemorySubject.registeredSubjects.forEach { subject ->
            coEvery {
                memoryProvider.load(concept, subject, MemoryScope.Feature("test-feature"))
            } returns when (subject) {
                MemorySubjects.User -> listOf(featureFact)
                else -> emptyList()
            }
        }

        // Mock responses for all subjects for Product scope
        MemorySubject.registeredSubjects.forEach { subject ->
            coEvery {
                memoryProvider.load(concept, subject, MemoryScope.Product("test-product"))
            } returns when (subject) {
                MemorySubjects.User -> listOf(productFact)
                else -> emptyList()
            }
        }

        // Mock responses for CrossProduct scope
        MemorySubject.registeredSubjects.forEach { subject ->
            coEvery {
                memoryProvider.load(concept, subject, MemoryScope.CrossProduct)
            } returns emptyList()
        }

        val response = mockk<Message.Response>()
        every { response.content } returns "OK"

        coEvery {
            promptExecutor.execute(any(), any(), any())
        } returns listOf(response)

        val llmContext = AIAgentLLMContext(
            tools = emptyList(),
            prompt = prompt("test") { },
            model = testModel,
            promptExecutor = promptExecutor,
            environment = MockEnvironment(toolRegistry = ToolRegistry.EMPTY, promptExecutor),
            config = AIAgentConfig(Prompt.Empty, testModel, 100),
            clock = testClock
        )

        val memory = AgentMemory(
            agentMemory = memoryProvider,
            llm = llmContext,
            scopesProfile = MemoryScopesProfile(
                MemoryScopeType.AGENT to "test-agent",
                MemoryScopeType.FEATURE to "test-feature",
                MemoryScopeType.PRODUCT to "test-product",
                MemoryScopeType.ORGANIZATION to "test-organization"
            )
        )

        memory.loadFactsToAgent(concept, subjects = listOf(MemorySubjects.User))

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
            println("[DEBUG_LOG] Loading facts for subject: ${secondArg<MemorySubject>()}, scope: ${thirdArg<MemoryScope>()}")
            listOf(machineFact)
        }

        // Create a slot to capture the prompt update
        val promptUpdateSlot = slot<PromptBuilder.() -> Unit>()

        // Mock LLM context to capture prompt updates
        mockkConstructor(AIAgentLLMWriteSession::class)

        val llmContext = mockk<AIAgentLLMContext>() {
            coEvery {
                writeSession<Any?>(any<suspend AIAgentLLMWriteSession.() -> Any?>())
            } coAnswers {
                val block = firstArg<suspend AIAgentLLMWriteSession.() -> Any?>()
                val writeSession = mockk<AIAgentLLMWriteSession> {
                    every { updatePrompt(capture(promptUpdateSlot)) } answers {
                        println("[DEBUG_LOG] Updating prompt with message containing facts")
                    }
                }
                block.invoke(writeSession)
            }
        }

        val memory = AgentMemory(
            agentMemory = memoryProvider,
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
        val memoryProvider = mockk<AgentMemoryProvider>()
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
            memoryProvider.save(capture(savedFacts), any(), any())
        } returns Unit

        val llmContext = AIAgentLLMContext(
            tools = emptyList(),
            prompt = prompt("test") { },
            model = testModel,
            promptExecutor = promptExecutor,
            environment = MockEnvironment(toolRegistry = ToolRegistry.EMPTY, promptExecutor),
            config = AIAgentConfig(Prompt.Empty, testModel, 100),
            clock = testClock
        )

        val memory = AgentMemory(
            agentMemory = memoryProvider,
            llm = llmContext,
            scopesProfile = MemoryScopesProfile()
        )

        val concept = Concept("test", "test description", FactType.SINGLE)
        val subject = MemorySubjects.User
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

        val llmContext = AIAgentLLMContext(
            tools = emptyList(),
            prompt = prompt("test") { },
            model = testModel,
            promptExecutor = promptExecutor,
            environment = MockEnvironment(toolRegistry = ToolRegistry.EMPTY, promptExecutor),
            config = AIAgentConfig(Prompt.Empty, testModel, 100),
            clock = testClock
        )

        val memory = AgentMemory(
            agentMemory = memoryProvider,
            llm = llmContext,
            scopesProfile = MemoryScopesProfile(
                MemoryScopeType.AGENT to "test-agent",
            )
        )

        memory.loadFactsToAgent(concept)

        coVerify {
            memoryProvider.load(concept, any(), any())
        }
    }
}