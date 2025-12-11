package ai.koog.agents.core.agent.context

import ai.koog.agents.core.CalculatorChatExecutor.testClock
import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.llm.OllamaModels
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class AIAgentLLMContextTest : AgentTestBase() {

    @OptIn(DetachedPromptExecutorAPI::class)
    @Test
    fun testContextCreation() = runTest {
        val context = createTestLLMContext()

        assertNotNull(context.toolRegistry)
        assertNotNull(context.promptExecutor)
    }

    @OptIn(DetachedPromptExecutorAPI::class)
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

    @Test
    fun testWriteSessionUpdatesModel() = runTest {
        val context = createTestLLMContext()
        val newModel = OllamaModels.Meta.LLAMA_4
        val newPrompt = prompt("new-test-prompt") {}
        val newTools = listOf(
            ToolDescriptor(
                name = "new-test-tool",
                description = "A new test tool",
                requiredParameters = emptyList()
            )
        )

        context.writeSession {
            this.model = newModel
            this.prompt = newPrompt
            this.tools = newTools
        }

        context.readSession {
            assertEquals(newModel.id, model.id)
            assertEquals(newPrompt.id, prompt.id)
            assertEquals(1, tools.size)
            assertEquals("new-test-tool", tools[0].name)
        }
    }

    @Test
    fun testContextCopyWithCustomParameters() = runTest {
        val originalContext = createTestLLMContext()
        val newPrompt = prompt("new-test-prompt") {}
        val newModel = OllamaModels.Meta.LLAMA_4
        val newTools = listOf(
            ToolDescriptor(
                name = "new-test-tool",
                description = "A new test tool",
                requiredParameters = emptyList()
            )
        )
        val newToolRegistry = ToolRegistry {
            // Empty registry
        }
        val newEnvironment = createTestEnvironment()
        val newConfig = createTestConfig()

        val copiedContext = originalContext.copy(
            prompt = newPrompt,
            model = newModel,
            tools = newTools,
            toolRegistry = newToolRegistry,
            environment = newEnvironment,
            config = newConfig
        )

        copiedContext.readSession {
            assertEquals(newPrompt.id, prompt.id)
            assertEquals(newModel.id, model.id)
            assertEquals(1, tools.size)
            assertEquals("new-test-tool", tools[0].name)
        }
        assertEquals(newToolRegistry, copiedContext.toolRegistry)
    }

    @Test
    fun testWriteSessionExceptionHandling() = runTest {
        val context = createTestLLMContext()
        val originalPrompt = context.prompt

        assertFailsWith<IllegalStateException> {
            context.writeSession {
                this.prompt = prompt("temp-prompt") {}
                throw IllegalStateException("Test exception")
            }
        }

        // confirm the context is unchanged
        context.readSession {
            assertEquals(originalPrompt.id, prompt.id)
        }
    }

    @Test
    fun testMultipleConsecutiveWriteSessions() = runTest {
        val context = createTestLLMContext()
        val models = listOf(
            OllamaModels.Meta.LLAMA_3_2,
            OllamaModels.Meta.LLAMA_4,
            OllamaModels.Meta.LLAMA_3_2
        )

        models.forEachIndexed { index, model ->
            context.writeSession {
                this.model = model
                this.prompt = prompt("prompt-$index") {}
            }
        }

        context.readSession {
            assertEquals(models.last().id, model.id)
            assertEquals("prompt-2", prompt.id)
        }
    }

    @Serializable
    private data class TestToolArgs(
        @property:LLMDescription("The input to process")
        val input: String
    )

    private class TestTool : SimpleTool<TestToolArgs>(
        argsSerializer = TestToolArgs.serializer(),
        name = "test-tool",
        description = "A test tool for testing"
    ) {
        override suspend fun execute(args: TestToolArgs): String {
            return "Processed: ${args.input}"
        }
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
            responseProcessor = null,
            promptExecutor = mockExecutor,
            environment = createTestEnvironment(),
            config = createTestConfig(),
            clock = testClock
        )
    }
}
