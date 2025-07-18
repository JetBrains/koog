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
        val copiedContext = originalContext.copy()

        assertEquals(originalContext.toolRegistry, copiedContext.toolRegistry)
        assertEquals(originalContext.promptExecutor, copiedContext.promptExecutor)
    }

    @Test
    fun testReadSession() = runTest {
        val context = createTestLLMContext()

        val result = context.readSession {
            assertEquals(createTestPrompt().id, prompt.id)
            assertEquals(OllamaModels.Meta.LLAMA_3_2.id, model.id)

            // return a test value
            "test-result"
        }

        assertEquals("test-result", result)
    }

    @Test
    fun testWriteSession() = runTest {
        val context = createTestLLMContext()

        val result = context.writeSession {
            assertEquals(createTestPrompt().id, prompt.id)

            // return a test value
            "test-result"
        }

        assertEquals("test-result", result)
    }


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
                // Do nothing
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