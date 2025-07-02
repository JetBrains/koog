package ai.koog.agents.core.agent.config

import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import kotlin.test.*

class AIAgentConfigTest {
    private companion object {
        private val testModel = OpenAIModels.Chat.GPT4o
        private const val MAX_ITERATIONS = 5
    }

    @Test
    fun testConstructorWithAllParameters() {
        val testPrompt = prompt("test-id") {
            system("Test system prompt")
        }
        val testStrategy = MissingToolsConversionStrategy.All(ToolCallDescriber.JSON)

        val config = AIAgentConfig(
            prompt = testPrompt,
            model = testModel,
            maxAgentIterations = MAX_ITERATIONS,
            missingToolsConversionStrategy = testStrategy,
        )

        assertEquals(testPrompt, config.prompt)
        assertEquals(testModel, config.model)
        assertEquals(MAX_ITERATIONS, config.maxAgentIterations)
        assertEquals(testStrategy, config.missingToolsConversionStrategy)
    }

    @Test
    fun testConstructorWithDefaultStrategy() {
        val testPrompt = prompt("test-id") {
            system("Test system prompt")
        }
        val config = AIAgentConfig(
            prompt = testPrompt,
            model = testModel,
            maxAgentIterations = MAX_ITERATIONS,
        )

        assertEquals(testPrompt, config.prompt)
        assertEquals(testModel, config.model)
        assertEquals(MAX_ITERATIONS, config.maxAgentIterations)
        assertTrue(config.missingToolsConversionStrategy is MissingToolsConversionStrategy.Missing)
    }

    @Test
    fun testWithSystemPromptAllParameters() {
        val testPromptContent = "Test system prompt"
        val testId = "custom-id"

        val config = AIAgentConfig.withSystemPrompt(
            prompt = testPromptContent,
            llm = testModel,
            id = testId,
            maxAgentIterations = MAX_ITERATIONS,
        )
        val systemMessage = config.prompt.messages.firstOrNull()

        assertEquals(testModel, config.model)
        assertEquals(MAX_ITERATIONS, config.maxAgentIterations)
        assertTrue(config.missingToolsConversionStrategy is MissingToolsConversionStrategy.Missing)
        assertEquals(testId, config.prompt.id)
        assertEquals(testPromptContent, systemMessage?.content)
    }

    @Test
    fun testWithSystemPromptDefaultParameters() {
        val testPromptContent = "Test system prompt"

        val config = AIAgentConfig.withSystemPrompt(
            prompt = testPromptContent
        )

        assertEquals(OpenAIModels.Chat.GPT4o, config.model)
        assertEquals(3, config.maxAgentIterations)
        assertTrue(config.missingToolsConversionStrategy is MissingToolsConversionStrategy.Missing)

        assertEquals("code-engine-agents", config.prompt.id)
        val systemMessage = config.prompt.messages.firstOrNull()
        assertNotNull(systemMessage)
        assertEquals(testPromptContent, systemMessage.content)
    }

    @Test
    fun testEmptyPrompt() {
        val config = AIAgentConfig.withSystemPrompt("")
        val systemMessage = config.prompt.messages.firstOrNull()
        assertNotNull(systemMessage)
        assertEquals("", systemMessage.content)
    }

    @Test
    fun testZeroMaxIterations() {
        assertFailsWith<IllegalArgumentException> {
            AIAgentConfig.withSystemPrompt(
                prompt = "Test prompt",
                maxAgentIterations = 0,
            )
        }
    }

    @Test
    fun testNegativeMaxIterations() {
        assertFailsWith<IllegalArgumentException> {
            AIAgentConfig.withSystemPrompt(
                prompt = "Test prompt",
                maxAgentIterations = -1,
            )
        }
    }
}