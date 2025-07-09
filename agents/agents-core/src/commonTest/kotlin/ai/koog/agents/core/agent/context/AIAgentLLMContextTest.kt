package ai.koog.agents.core.agent.context

import ai.koog.agents.core.CalculatorChatExecutor.testClock
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.config.AIAgentConfigBase
import ai.koog.agents.core.agent.config.MissingToolsConversionStrategy
import ai.koog.agents.core.agent.config.ToolCallDescriber
import ai.koog.agents.core.environment.AIAgentEnvironment
import ai.koog.agents.core.environment.ReceivedToolResult
import ai.koog.agents.core.tools.*
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.agents.testing.tools.mockLLMAnswer
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.llm.OllamaModels
import ai.koog.prompt.message.Message
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame

class AIAgentLLMContextTest {

    @Test
    fun testContextCreation() = runTest {
        val context = createTestLLMContext()

        assertNotNull(context.toolRegistry)
        assertNotNull(context.promptExecutor)
    }

    @Test
    fun testContextCopy() = runTest {
        val originalContext = createTestLLMContext()

        // Create a copy with the same parameters
        val copiedContext = originalContext.copy()

        // Verify that the copy has the same properties
        assertEquals(originalContext.toolRegistry, copiedContext.toolRegistry)
        assertEquals(originalContext.promptExecutor, copiedContext.promptExecutor)

        // Verify that it's a deep copy
        assertNotSame(originalContext, copiedContext)
    }

    @Test
    fun testReadSession() = runTest {
        val context = createTestLLMContext()

        // Execute a read session
        val result = context.readSession {
            // Access prompt and model in read session
            assertEquals(createTestPrompt().id, prompt.id)
            assertEquals(OllamaModels.Meta.LLAMA_3_2.id, model.id)

            // Return a test value
            "test-result"
        }

        // Verify the result
        assertEquals("test-result", result)
    }

    @Test
    fun testWriteSession() = runTest {
        val context = createTestLLMContext()

        // Execute a write session
        val result = context.writeSession {
            // Access and modify prompt in write session
            assertEquals(createTestPrompt().id, prompt.id)

            // Return a test value
            "test-result"
        }

        // Verify the result
        assertEquals("test-result", result)
    }

    // Helper methods and classes

    @Serializable
    private data class TestToolArgs(val input: String) : ToolArgs

    private class TestTool : SimpleTool<TestToolArgs>() {
        override val argsSerializer = TestToolArgs.serializer()

        override val descriptor = ToolDescriptor(
            name = "test-tool",
            description = "A test tool for testing",
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "input",
                    description = "The input to process",
                    type = ToolParameterType.String
                )
            )
        )

        override suspend fun doExecute(args: TestToolArgs): String {
            return "Processed: ${args.input}"
        }
    }

    private fun createTestEnvironment(): AIAgentEnvironment {
        return object : AIAgentEnvironment {
            override suspend fun executeTools(toolCalls: List<Message.Tool.Call>): List<ReceivedToolResult> {
                return emptyList()
            }

            override suspend fun reportProblem(exception: Throwable) {
                // Do nothing in test
            }
        }
    }

    private fun createTestConfig(): AIAgentConfigBase {
        return AIAgentConfig(
            prompt = createTestPrompt(),
            model = OllamaModels.Meta.LLAMA_3_2,
            maxAgentIterations = 10,
            missingToolsConversionStrategy = MissingToolsConversionStrategy.All(ToolCallDescriber.JSON)
        )
    }

    private fun createTestPrompt(): Prompt {
        // Create an empty prompt for testing
        return prompt("test-prompt") {}
    }

    private fun createTestLLMContext(): AIAgentLLMContext {
        val testTool = TestTool()
        val tools = listOf(testTool.descriptor)

        val toolRegistry = ToolRegistry {
            tool(testTool)
        }

        val mockExecutor = getMockExecutor(clock = testClock) {
            mockLLMAnswer("Test response").asDefaultResponse
        }

        return AIAgentLLMContext(
            tools = tools,
            toolRegistry = toolRegistry,
            prompt = createTestPrompt(),
            model = OllamaModels.Meta.LLAMA_3_2,
            promptExecutor = mockExecutor,
            environment = createTestEnvironment(),
            config = createTestConfig(),
            clock = testClock
        )
    }
}