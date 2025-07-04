package ai.koog.agents.memory.feature

import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.context.AIAgentLLMContext
import ai.koog.agents.core.agent.session.AIAgentLLMWriteSession
import ai.koog.agents.memory.model.*
import ai.koog.agents.testing.tools.MockEnvironment
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.agents.testing.tools.mockLLMAnswer
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.agents.core.tools.ToolRegistry
import io.mockk.*
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue


class RetrieveFactsFromHistoryTest {

    private val testModel = mockk<LLModel> {
        every { id } returns "test-model"
    }

    private val testClock: Clock = object : Clock {
        override fun now(): Instant = Instant.parse("2023-01-01T00:00:00Z")
    }

    /**
     * Test that retrieveFactsFromHistory correctly extracts a single fact.
     */
    @Test
    fun testRetrieveFactsFromHistorySingleFact() = runTest {
        // Arrange
        val concept = Concept("test-concept", "Test concept description", FactType.SINGLE)
        val factText = "This is a test fact"
        val testTimestamp = 1234567890L
        
        // Mock DefaultTimeProvider to return a fixed timestamp
        mockkObject(DefaultTimeProvider)
        every { DefaultTimeProvider.getCurrentTimestamp() } returns testTimestamp
        
        // Create a mock prompt executor that returns a response with the fact
        val promptExecutor = getMockExecutor(clock = testClock) {
            mockLLMAnswer("""{"fact": "$factText"}""").asDefaultResponse
        }
        
        // Create a real AIAgentLLMContext and AIAgentLLMWriteSession
        val llmContext = AIAgentLLMContext(
            tools = emptyList(),
            prompt = prompt("test") {
                user("Hello")
                assistant("Hi there")
            },
            model = testModel,
            promptExecutor = promptExecutor,
            environment = MockEnvironment(toolRegistry = ToolRegistry.EMPTY, promptExecutor),
            config = AIAgentConfig(Prompt.Empty, testModel, 100),
            clock = testClock
        )
        
        // Use the writeSession method to create a session and call retrieveFactsFromHistory
        var result: Fact? = null
        llmContext.writeSession {
            result = retrieveFactsFromHistory(concept)
        }
        
        // Assert
        assertTrue(result is SingleFact)
        assertEquals(concept, result!!.concept)
        assertEquals(testTimestamp, result!!.timestamp)
        assertEquals(factText, (result as SingleFact).value)
    }
    
    /**
     * Test that retrieveFactsFromHistory correctly extracts multiple facts.
     */
    @Test
    fun testRetrieveFactsFromHistoryMultipleFacts() = runTest {
        // Arrange
        val concept = Concept("test-concept", "Test concept description", FactType.MULTIPLE)
        val factsList = listOf("Fact 1", "Fact 2", "Fact 3")
        val testTimestamp = 1234567890L
        
        // Mock DefaultTimeProvider to return a fixed timestamp
        mockkObject(DefaultTimeProvider)
        every { DefaultTimeProvider.getCurrentTimestamp() } returns testTimestamp
        
        // Create a mock prompt executor that returns a response with multiple facts
        val promptExecutor = getMockExecutor(clock = testClock) {
            mockLLMAnswer("""{"facts": [{"fact": "Fact 1"}, {"fact": "Fact 2"}, {"fact": "Fact 3"}]}""").asDefaultResponse
        }
        
        // Create a real AIAgentLLMContext and AIAgentLLMWriteSession
        val llmContext = AIAgentLLMContext(
            tools = emptyList(),
            prompt = prompt("test") {
                user("Hello")
                assistant("Hi there")
            },
            model = testModel,
            promptExecutor = promptExecutor,
            environment = MockEnvironment(toolRegistry = ToolRegistry.EMPTY, promptExecutor),
            config = AIAgentConfig(Prompt.Empty, testModel, 100),
            clock = testClock
        )
        
        // Use the writeSession method to create a session and call retrieveFactsFromHistory
        var result: Fact? = null
        llmContext.writeSession {
            result = retrieveFactsFromHistory(concept)
        }
        
        // Assert
        assertTrue(result is MultipleFacts)
        assertEquals(concept, result!!.concept)
        assertEquals(testTimestamp, result!!.timestamp)
        assertEquals(factsList, (result as MultipleFacts).values)
    }
    
    /**
     * Test that retrieveFactsFromHistory handles errors correctly for single facts.
     */
    @Test
    fun testRetrieveFactsFromHistorySingleFactError() = runTest {
        // Arrange
        val concept = Concept("test-concept", "Test concept description", FactType.SINGLE)
        val testTimestamp = 1234567890L
        
        // Mock DefaultTimeProvider to return a fixed timestamp
        mockkObject(DefaultTimeProvider)
        every { DefaultTimeProvider.getCurrentTimestamp() } returns testTimestamp
        
        // Create a mock prompt executor that returns an invalid JSON response
        val promptExecutor = getMockExecutor(clock = testClock) {
            mockLLMAnswer("""invalid json""").asDefaultResponse
        }
        
        // Create a real AIAgentLLMContext and AIAgentLLMWriteSession
        val llmContext = AIAgentLLMContext(
            tools = emptyList(),
            prompt = prompt("test") {
                user("Hello")
                assistant("Hi there")
            },
            model = testModel,
            promptExecutor = promptExecutor,
            environment = MockEnvironment(toolRegistry = ToolRegistry.EMPTY, promptExecutor),
            config = AIAgentConfig(Prompt.Empty, testModel, 100),
            clock = testClock
        )
        
        // Use the writeSession method to create a session and call retrieveFactsFromHistory
        var result: Fact? = null
        llmContext.writeSession {
            result = retrieveFactsFromHistory(concept)
        }
        
        // Assert
        assertTrue(result is SingleFact)
        assertEquals(concept, result!!.concept)
        assertEquals(testTimestamp, result!!.timestamp)
        assertEquals("No facts extracted", (result as SingleFact).value)
    }
    
    /**
     * Test that retrieveFactsFromHistory handles errors correctly for multiple facts.
     */
    @Test
    fun testRetrieveFactsFromHistoryMultipleFactsError() = runTest {
        // Arrange
        val concept = Concept("test-concept", "Test concept description", FactType.MULTIPLE)
        val testTimestamp = 1234567890L
        
        // Mock DefaultTimeProvider to return a fixed timestamp
        mockkObject(DefaultTimeProvider)
        every { DefaultTimeProvider.getCurrentTimestamp() } returns testTimestamp
        
        // Create a mock prompt executor that returns an invalid JSON response
        val promptExecutor = getMockExecutor(clock = testClock) {
            mockLLMAnswer("""invalid json""").asDefaultResponse
        }
        
        // Create a real AIAgentLLMContext and AIAgentLLMWriteSession
        val llmContext = AIAgentLLMContext(
            tools = emptyList(),
            prompt = prompt("test") {
                user("Hello")
                assistant("Hi there")
            },
            model = testModel,
            promptExecutor = promptExecutor,
            environment = MockEnvironment(toolRegistry = ToolRegistry.EMPTY, promptExecutor),
            config = AIAgentConfig(Prompt.Empty, testModel, 100),
            clock = testClock
        )
        
        // Use the writeSession method to create a session and call retrieveFactsFromHistory
        var result: Fact? = null
        llmContext.writeSession {
            result = retrieveFactsFromHistory(concept)
        }
        
        // Assert
        assertTrue(result is MultipleFacts)
        assertEquals(concept, result!!.concept)
        assertEquals(testTimestamp, result!!.timestamp)
        assertEquals(emptyList<String>(), (result as MultipleFacts).values)
    }
}