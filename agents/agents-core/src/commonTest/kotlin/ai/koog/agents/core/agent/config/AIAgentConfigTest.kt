package ai.koog.agents.core.agent.config

import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class AIAgentConfigTest {
    private companion object {
        private val testModel = OpenAIModels.Chat.GPT4o
        private val testPrompt = prompt("test-id") {
            system("Test system prompt")
        }

        private const val TEST_PROMPT_CONTENT = "Test system prompt"
        private const val MAX_ITERATIONS = 5
    }

    @Test
    fun testConstructorWithAllParameters() {
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
        val config = AIAgentConfig(
            prompt = testPrompt,
            model = testModel,
            maxAgentIterations = MAX_ITERATIONS,
        )

        assertEquals(testPrompt, config.prompt)
        assertEquals(testModel, config.model)
        assertEquals(MAX_ITERATIONS, config.maxAgentIterations)
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
        assertEquals(testId, config.prompt.id)
        assertEquals(testPromptContent, systemMessage?.content)
    }

    @Test
    fun testWithSystemPromptDefaultParameters() {
        val config = AIAgentConfig.withSystemPrompt(
            prompt = TEST_PROMPT_CONTENT
        )

        assertEquals(testModel, config.model)
        assertEquals(3, config.maxAgentIterations)

        assertEquals("koog-agents", config.prompt.id)
        val systemMessage = config.prompt.messages.firstOrNull()
        assertNotNull(systemMessage)
        assertEquals(TEST_PROMPT_CONTENT, systemMessage.content)
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