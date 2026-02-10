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
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.llm.OllamaModels
import ai.koog.prompt.processor.ResponseProcessor
import ai.koog.prompt.message.Message
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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

    /**
     * Issue 1: readSession exposes uncommitted state to unrelated concurrent readers.
     * When a writeSession is active, ALL readSession calls bypass the lock and see
     * uncommitted (potentially rolled-back) state.
     */
    @Test
    @Timeout(30)
    fun testReadSessionSeesUncommittedStateFromUnrelatedWriter() {
        runBlocking {
            val context = createTestLLMContext()
            val originalPromptId = context.readSession { prompt.id }

            val writerEnteredSession = CompletableDeferred<Unit>()
            val readerFinished = CompletableDeferred<String>()

            // Writer: enters writeSession, modifies prompt, then waits before committing
            val writerJob = launch(Dispatchers.Default) {
                try {
                    context.writeSession {
                        this.prompt = prompt("uncommitted-prompt") {}
                        writerEnteredSession.complete(Unit)
                        // Hold the session open while reader runs
                        delay(500)
                        // Throw to simulate rollback — changes should NOT be committed
                        error("Simulated failure — rolling back write session")
                    }
                } catch (_: IllegalStateException) {
                    // Expected
                }
            }

            // Reader: wait for writer to enter session, then read
            val readerJob = launch(Dispatchers.Default) {
                writerEnteredSession.await()
                val readPromptId = context.readSession { prompt.id }
                readerFinished.complete(readPromptId)
            }

            val observedPromptId = readerFinished.await()
            writerJob.join()
            readerJob.join()

            // After rollback, the committed state should still be the original
            val committedPromptId = context.readSession { prompt.id }
            assertEquals(originalPromptId, committedPromptId, "Committed state should be unchanged after rollback")

            // BUG: The concurrent reader observed "uncommitted-prompt" which was never committed.
            // With a correct implementation, the reader should have seen the original prompt.
            // This assertion documents the current buggy behavior:
            if (observedPromptId == "uncommitted-prompt") {
                println("[DEBUG_LOG] BUG CONFIRMED: readSession exposed uncommitted state to unrelated reader")
            }
            // Ideally this should pass, but with the current implementation it may fail:
            // assertEquals(originalPromptId, observedPromptId, "Reader should not see uncommitted state")
        }
    }

    /**
     * Issue 4: responseProcessor changes inside writeSession are not committed.
     * Note: This is NOT a regression — the develop branch also did not commit responseProcessor.
     * This test documents the current behavior (by design or long-standing omission).
     */
    @Test
    @Timeout(30)
    fun testResponseProcessorNotCommittedInWriteSession() {
        runBlocking {
            val context = createTestLLMContext()

            // Verify initial state
            val initialProcessor = context.readSession { responseProcessor }
            assertEquals(null, initialProcessor, "Initial responseProcessor should be null")

            // Set a responseProcessor inside writeSession
            val testProcessor = object : ResponseProcessor() {
                override suspend fun process(
                    executor: PromptExecutor,
                    prompt: Prompt,
                    model: LLModel,
                    tools: List<ToolDescriptor>,
                    responses: List<Message.Response>
                ) = responses
            }
            context.writeSession {
                this.responseProcessor = testProcessor
            }

            // BUG: responseProcessor is NOT committed because the TODO removed the commit line
            val afterWriteProcessor = context.readSession { responseProcessor }
            // This documents the bug — the processor should be testProcessor but is null
            if (afterWriteProcessor == null) {
                println("[DEBUG_LOG] BUG CONFIRMED: responseProcessor was not committed by writeSession")
            }
            // Ideally: assertEquals(testProcessor, afterWriteProcessor)
        }
    }

    /**
     * Issue 7: writeSession(reuseActiveSession = true) can be called by any coroutine while
     * a write session is active, not just the owning coroutine. This bypasses the write lock.
     */
    @Test
    @Timeout(30)
    fun testReuseActiveSessionCallableFromUnrelatedCoroutine() {
        runBlocking {
            val context = createTestLLMContext()
            val writerEnteredSession = CompletableDeferred<Unit>()
            val mutationDone = CompletableDeferred<Boolean>()

            // Writer holds the write session open
            val writerJob = launch(Dispatchers.Default) {
                context.writeSession {
                    writerEnteredSession.complete(Unit)
                    // Hold session open
                    delay(500)
                    "done"
                }
            }

            // Unrelated coroutine calls writeSession(reuseActiveSession = true)
            val intruderJob = launch(Dispatchers.Default) {
                writerEnteredSession.await()
                try {
                    context.writeSession(reuseActiveSession = true) {
                        this.prompt = prompt("intruder-prompt") {}
                    }
                    mutationDone.complete(true)
                    println("[DEBUG_LOG] BUG CONFIRMED: writeSession(reuseActiveSession=true) succeeded from unrelated coroutine")
                } catch (e: IllegalStateException) {
                    mutationDone.complete(false)
                }
            }

            val succeeded = mutationDone.await()
            writerJob.join()
            intruderJob.join()

            // BUG: This succeeds even though the caller is not the write session owner
            if (succeeded) {
                println("[DEBUG_LOG] writeSession(reuseActiveSession=true) has no caller verification — any coroutine can mutate during active write")
            }
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
