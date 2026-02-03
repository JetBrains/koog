@file:OptIn(InternalAgentsApi::class)

package ai.koog.agents.core.agent.context

import ai.koog.agents.core.CalculatorChatExecutor
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.config.MissingToolsConversionStrategy
import ai.koog.agents.core.agent.config.ToolCallDescriber
import ai.koog.agents.core.annotation.InternalAgentsApi
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
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Timeout
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
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
    fun testWithPromptRaceCondition() {
        runBlocking {
            val context = createTestLLMContext()
            // Reset prompt to a known start state
            context.withPrompt { prompt("0") {} }

            val iterations = 100
            val jobs = (1..iterations).map {
                async(Dispatchers.Default) {
                    // Simulate some work and update prompt
                    context.withPrompt {
                        // Append "." to the ID
                        // We simulate a read-modify-write cycle here.
                        // If multiple threads read the same 'id' and append '.', they overwrite each other.
                        prompt(this.id + ".") {}
                    }
                }
            }

            jobs.awaitAll()

            val finalId = context.prompt.id
            // Expected length: 1 (initial "0") + 100 (dots) = 101.
            // With ReadLock, many updates will be lost, so length < 101.
            assertEquals(
                1 + iterations,
                finalId.length,
                "Lost updates detected! Race condition in withPrompt."
            )
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

    // ==================== Session Reentrancy Tests ====================

    /**
     * Verifies that nested writeSession calls with the same sessionId reuse the same session object.
     * This is the core reentrancy behavior enabled by activeWriteSession tracking.
     */
    @Test
    @Timeout(30)
    fun testNestedWriteSessionReusesSession() {
        runBlocking {
            val context = createTestLLMContext()
            var outerSessionId: Int? = null
            var innerSessionId: Int? = null

            context.writeSession { sessionId ->
                outerSessionId = this.hashCode()

                // Nested writeSession with same sessionId should reuse the same session
                context.writeSession(sessionId) { _ ->
                    innerSessionId = this.hashCode()
                }
            }

            assertEquals(outerSessionId, innerSessionId, "Nested writeSession should reuse the same session object")
        }
    }

    /**
     * Verifies that state changes in outer session are visible in nested session (same sessionId).
     */
    @Test
    @Timeout(30)
    fun testNestedWriteSessionStateChangesAreVisible() {
        runBlocking {
            val context = createTestLLMContext()
            val newModel = OllamaModels.Meta.LLAMA_4

            context.writeSession { sessionId ->
                // Change model in outer session
                this.model = newModel

                // Nested session with same sessionId should see the change
                context.writeSession(sessionId) { _ ->
                    assertEquals(
                        newModel.id,
                        this.model.id,
                        "Nested session should see model change from outer session"
                    )
                }
            }

            // Verify the change persisted
            context.readSession {
                assertEquals(newModel.id, model.id, "Model change should persist after session")
            }
        }
    }

    /**
     * Verifies that readSession inside writeSession sees uncommitted state.
     * This works because activeWriteSession is set, allowing reads without acquiring read lock.
     */
    @Test
    @Timeout(30)
    fun testReadSessionInsideWriteSessionSeesUncommittedState() {
        runBlocking {
            val context = createTestLLMContext()
            var readSessionExecuted = false

            context.writeSession { _ ->
                // Change something in write session
                this.model = OllamaModels.Meta.LLAMA_4

                // Read session inside write session should see the uncommitted change
                context.readSession {
                    readSessionExecuted = true
                    assertEquals(
                        OllamaModels.Meta.LLAMA_4.id,
                        model.id,
                        "Read session should see uncommitted model change"
                    )
                }
            }

            assertTrue(readSessionExecuted, "Read session inside write session should execute")
        }
    }

    /**
     * Verifies that writeSession inside readSession deadlocks (lock upgrade not supported).
     * This is expected behavior documented in AIAgentLLMContextImpl.
     */
    @Test
    @Timeout(30)
    fun testWriteSessionInsideReadSessionDeadlocks() {
        runBlocking {
            val context = createTestLLMContext()

            var writeExecuted = false
            val result = withTimeoutOrNull(100) {
                context.readSession {
                    context.writeSession { _ ->
                        writeExecuted = true
                    }
                }
            }

            // Result should be null (timeout) because of deadlock
            assertEquals(null, result, "writeSession inside readSession should deadlock (timeout)")
            assertEquals(false, writeExecuted, "writeSession should not have executed due to deadlock")
        }
    }

    /**
     * Verifies that concurrent write sessions from different coroutines are properly serialized.
     */
    @Test
    @Timeout(30)
    fun testConcurrentWriteSessionsAreSerialized() {
        runBlocking {
            val context = createTestLLMContext()
            val results = CopyOnWriteArrayList<String>()

            val job1 = async(Dispatchers.Default) {
                context.writeSession { _ ->
                    delay(10)
                    this.prompt = prompt("coroutine-1") {}
                    results.add("coroutine-1-start")
                    delay(20)
                    results.add("coroutine-1-end")
                }
            }

            val job2 = async(Dispatchers.Default) {
                delay(5) // Start slightly after job1
                context.writeSession { _ ->
                    results.add("coroutine-2-start")
                    this.prompt = prompt("coroutine-2") {}
                    results.add("coroutine-2-end")
                }
            }

            job1.await()
            job2.await()

            // Due to write lock, coroutine-2 should wait for coroutine-1 to complete
            assertEquals("coroutine-1-start", results[0])
            assertEquals("coroutine-1-end", results[1])
            assertEquals("coroutine-2-start", results[2])
            assertEquals("coroutine-2-end", results[3])
        }
    }

    /**
     * Verifies that multiple read sessions can run concurrently.
     */
    @Test
    @Timeout(30)
    fun testMultipleReadSessionsRunConcurrently() {
        runBlocking {
            val context = createTestLLMContext()
            val results = CopyOnWriteArrayList<String>()

            val job1 = async(Dispatchers.Default) {
                context.readSession {
                    results.add("reader-1-start")
                    delay(20)
                    results.add("reader-1-end")
                    prompt.id
                }
            }

            val job2 = async(Dispatchers.Default) {
                delay(5)
                context.readSession {
                    results.add("reader-2-start")
                    delay(10)
                    results.add("reader-2-end")
                    prompt.id
                }
            }

            job1.await()
            job2.await()

            // Multiple readers can run concurrently
            // reader-2 should start before reader-1 ends
            val reader1StartIndex = results.indexOf("reader-1-start")
            val reader1EndIndex = results.indexOf("reader-1-end")
            val reader2StartIndex = results.indexOf("reader-2-start")

            assertTrue(reader2StartIndex > reader1StartIndex, "Reader 2 should start after reader 1")
            assertTrue(reader2StartIndex < reader1EndIndex, "Reader 2 should start before reader 1 ends (concurrent)")
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
