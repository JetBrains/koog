package ai.koog.agents.core.agent.config

import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AIAgentConfigTest {

    @Test
    fun testConstructorWithAllParameters() {
        val testPrompt = prompt("test-id") {
            system("Test system prompt")
        }
        val testModel = OpenAIModels.Chat.GPT4o
        val testMaxIterations = 5
        val testStrategy = MissingToolsConversionStrategy.All(ToolCallDescriber.JSON)

        val config = AIAgentConfig(
            prompt = testPrompt,
            model = testModel,
            maxAgentIterations = testMaxIterations,
            missingToolsConversionStrategy = testStrategy
        )

        assertEquals(testPrompt, config.prompt)
        assertEquals(testModel, config.model)
        assertEquals(testMaxIterations, config.maxAgentIterations)
        assertEquals(testStrategy, config.missingToolsConversionStrategy)
    }

    @Test
    fun testConstructorWithDefaultStrategy() {
        val testPrompt = prompt("test-id") {
            system("Test system prompt")
        }
        val testModel = OpenAIModels.Chat.GPT4o
        val testMaxIterations = 5

        val config = AIAgentConfig(
            prompt = testPrompt,
            model = testModel,
            maxAgentIterations = testMaxIterations
        )

        assertEquals(testPrompt, config.prompt)
        assertEquals(testModel, config.model)
        assertEquals(testMaxIterations, config.maxAgentIterations)
        assertTrue(config.missingToolsConversionStrategy is MissingToolsConversionStrategy.Missing)
    }

    @Test
    fun testWithSystemPromptAllParameters() {
        val testPromptContent = "Test system prompt"
        val testModel = OpenAIModels.Chat.GPT4o
        val testId = "custom-id"
        val testMaxIterations = 10

        val config = AIAgentConfig.withSystemPrompt(
            prompt = testPromptContent,
            llm = testModel,
            id = testId,
            maxAgentIterations = testMaxIterations
        )

        assertEquals(testModel, config.model)
        assertEquals(testMaxIterations, config.maxAgentIterations)
        assertTrue(config.missingToolsConversionStrategy is MissingToolsConversionStrategy.Missing)

        // Verify prompt properties
        assertEquals(testId, config.prompt.id)
        val systemMessage = config.prompt.messages.firstOrNull()
        assertNotNull(systemMessage)
        assertEquals(testPromptContent, systemMessage.content)
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

        // Verify prompt properties
        assertEquals("code-engine-agents", config.prompt.id)
        val systemMessage = config.prompt.messages.firstOrNull()
        assertNotNull(systemMessage)
        assertEquals(testPromptContent, systemMessage.content)
    }

    @Test
    fun testConfigImplementsInterface() {
        val config = AIAgentConfig.withSystemPrompt("Test prompt")
        assertTrue(config is AIAgentConfigBase)
    }

    @Test
    fun testEdgeCaseEmptyPrompt() {
        val config = AIAgentConfig.withSystemPrompt("")
        val systemMessage = config.prompt.messages.firstOrNull()
        assertNotNull(systemMessage)
        assertEquals("", systemMessage.content)
    }

    @Test
    fun testEdgeCaseZeroMaxIterations() {
        val config = AIAgentConfig.withSystemPrompt(
            prompt = "Test prompt",
            maxAgentIterations = 0
        )
        assertEquals(0, config.maxAgentIterations)
    }

    @Test
    fun testEdgeCaseNegativeMaxIterations() {
        val config = AIAgentConfig.withSystemPrompt(
            prompt = "Test prompt",
            maxAgentIterations = -1
        )
        assertEquals(-1, config.maxAgentIterations)
    }
}