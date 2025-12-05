package ai.koog.agents.core.agent.context

import ai.koog.agents.core.CalculatorChatExecutor
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.config.MissingToolsConversionStrategy
import ai.koog.agents.core.agent.config.ToolCallDescriber
import ai.koog.agents.core.environment.AIAgentEnvironment
import ai.koog.agents.core.environment.ReceivedToolResult
import ai.koog.agents.core.environment.ToolResultKind
import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.llm.OllamaModels
import ai.koog.prompt.message.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Timeout
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AIAgentLLMContextConcurrencyTest {

    @Test
    @Timeout(30)
    fun testConcurrentReadWrite() {
        runBlocking {
            val context = createTestLLMContext()
            val readResults = CopyOnWriteArrayList<String>()
            val writeResults = CopyOnWriteArrayList<String>()
            val counter = AtomicInteger(0)

            // coroutines for read operations
            val readJobs = (1..5).map {
                async(Dispatchers.Default) {
                    val index = counter.getAndIncrement()
                    val result = context.readSession {
                        delay(1)
                        "result-$index"
                    }
                    readResults.add(result)
                }
            }

            // coroutines for write tools operations
            val writeToolsJobs = (1..3).map {
                async(Dispatchers.Default) {
                    val index = counter.getAndIncrement()
                    val result = context.writeSession {
                        delay(1)
                        this.tools = listOf(
                            ToolDescriptor(
                                name = "tool-$index",
                                description = "Tool $index",
                                requiredParameters = emptyList()
                            )
                        )
                        "write-tool-result-$index"
                    }
                    writeResults.add(result)
                }
            }

            // coroutines for write prompt operations
            val writePromptJobs = (1..3).map {
                async(Dispatchers.Default) {
                    val index = counter.getAndIncrement()
                    val result = context.writeSession {
                        delay(1)
                        this.prompt = prompt("prompt-$index") {}
                        "write-prompt-result-$index"
                    }
                    writeResults.add(result)
                }
            }

            (readJobs + writeToolsJobs + writePromptJobs).awaitAll()

            val promptId = context.readSession { prompt.id }
            val toolName = context.readSession { tools.firstOrNull()?.name }

            // verify state
            assertTrue(promptId.isNotEmpty(), "Prompt ID should not be empty")
            assertNotNull(toolName, "Tool name should not be null")
        }
    }

    @Test
    @Timeout(30)
    fun testVerifyState() {
        runBlocking {
            val context = createTestLLMContext()

            val jobs = listOf(
                // update tools
                async(Dispatchers.Default) {
                    context.writeSession {
                        this.tools = listOf(
                            ToolDescriptor(
                                name = "updated-tool",
                                description = "Updated Tool",
                                requiredParameters = emptyList()
                            )
                        )
                    }
                },

                // update prompt
                async(Dispatchers.Default) {
                    context.writeSession {
                        this.prompt = prompt("updated-prompt") {}
                    }
                },

                async(Dispatchers.Default) {
                    val promptId = context.readSession { prompt.id }
                    val toolName = context.readSession { tools.firstOrNull()?.name }
                }
            )

            jobs.awaitAll()

            val promptId = context.readSession { prompt.id }
            val toolName = context.readSession { tools.firstOrNull()?.name }

            assertTrue(promptId.isNotEmpty(), "Prompt ID should not be empty")
            assertNotNull(toolName, "Tool name should not be null")
        }
    }

    @Serializable
    private data class TestToolArgs(
        @property:LLMDescription("The input to process")
        val input: String
    )

    private class TestTool : SimpleTool<TestToolArgs>() {
        override val argsSerializer = TestToolArgs.serializer()

        override val name: String = "test-tool"
        override val description: String = "A test tool for testing"

        override suspend fun doExecute(args: TestToolArgs): String {
            return "Processed: ${args.input}"
        }
    }

    private fun createTestEnvironment(): AIAgentEnvironment {
        return object : AIAgentEnvironment {
            override suspend fun executeTool(toolCall: Message.Tool.Call): ReceivedToolResult {
                return ReceivedToolResult(
                    id = toolCall.id,
                    tool = toolCall.tool,
                    toolArgs = toolCall.contentJson,
                    toolDescription = null,
                    content = "",
                    resultKind = ToolResultKind.Success,
                    result = JsonPrimitive("")
                )
            }

            override suspend fun reportProblem(exception: Throwable) {
                // Do nothing
            }
        }
    }

    private fun createTestConfig(): AIAgentConfig {
        return AIAgentConfig(
            prompt = createTestPrompt(),
            model = OllamaModels.Meta.LLAMA_3_2,
            maxAgentIterations = 10,
            missingToolsConversionStrategy = MissingToolsConversionStrategy.All(ToolCallDescriber.JSON)
        )
    }

    private fun createTestPrompt(): Prompt {
        return prompt("test-prompt") {}
    }

    private fun createTestLLMContext(): AIAgentLLMContext {
        val testTool = TestTool()
        val tools = listOf(testTool.descriptor)

        val toolRegistry = ToolRegistry.Companion {
            tool(testTool)
        }

        val mockExecutor = getMockExecutor(clock = CalculatorChatExecutor.testClock) {
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
            clock = CalculatorChatExecutor.testClock
        )
    }
}
