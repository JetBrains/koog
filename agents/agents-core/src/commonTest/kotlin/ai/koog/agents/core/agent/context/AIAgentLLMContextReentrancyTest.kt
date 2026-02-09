@file:OptIn(InternalAgentsApi::class)

package ai.koog.agents.core.agent.context

import ai.koog.agents.core.CalculatorChatExecutor.testClock
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.llm.OllamaModels
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Tests for session-level re-entrancy detection in AIAgentLLMContext.
 * 
 * These tests verify that:
 * 1. Nested writeSession calls reuse the same session (no deadlock)
 * 2. Nested readSession calls reuse the same session (no deadlock)
 * 3. readSession inside writeSession works (write implies read)
 * 4. writeSession inside readSession throws IllegalStateException (upgrade not supported)
 * 5. State changes in nested sessions are properly reflected
 */
class AIAgentLLMContextReentrancyTest : AgentTestBase() {

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

    // ==================== Nested Write Session Tests ====================

    @Test
    fun testNestedWriteSessionReusesSession() = runTest {
        val context = createTestLLMContext()
        var outerSessionId: Int? = null
        var innerSessionId: Int? = null

        context.writeSession {
            outerSessionId = this.hashCode()

            // Nested writeSession should reuse the same session
            context.writeSession {
                innerSessionId = this.hashCode()
            }
        }

        assertEquals(outerSessionId, innerSessionId, "Nested writeSession should reuse the same session object")
    }

    @Test
    fun testDeeplyNestedWriteSessions() = runTest {
        val context = createTestLLMContext()
        val sessionIds = mutableListOf<Int>()

        context.writeSession {
            sessionIds.add(this.hashCode())

            context.writeSession {
                sessionIds.add(this.hashCode())

                context.writeSession {
                    sessionIds.add(this.hashCode())

                    context.writeSession {
                        sessionIds.add(this.hashCode())
                    }
                }
            }
        }

        assertEquals(4, sessionIds.size)
        assertTrue(sessionIds.all { it == sessionIds[0] }, "All nested sessions should be the same object")
    }

    @Test
    fun testNestedWriteSessionStateChangesAreVisible() = runTest {
        val context = createTestLLMContext()
        val newModel = OllamaModels.Meta.LLAMA_4

        context.writeSession {
            // Change model in outer session
            this.model = newModel

            // Nested session should see the change
            context.writeSession {
                assertEquals(newModel.id, this.model.id, "Nested session should see model change from outer session")
            }
        }

        // Verify the change persisted
        context.readSession {
            assertEquals(newModel.id, model.id, "Model change should persist after session")
        }
    }

    @Test
    fun testNestedWriteSessionInnerChangesAreVisible() = runTest {
        val context = createTestLLMContext()
        val newPrompt = prompt("inner-prompt") {}

        context.writeSession {
            val originalPromptId = this.prompt.id

            // Change prompt in nested session
            context.writeSession {
                this.prompt = newPrompt
            }

            // Outer session should see the change (same session object)
            assertEquals(newPrompt.id, this.prompt.id, "Outer session should see prompt change from inner session")
        }

        // Verify the change persisted
        context.readSession {
            assertEquals(newPrompt.id, prompt.id, "Prompt change from inner session should persist")
        }
    }

    @Test
    fun testNestedWriteSessionReturnValue() = runTest {
        val context = createTestLLMContext()

        val result = context.writeSession {
            val innerResult = context.writeSession {
                "inner-result"
            }
            "outer-$innerResult"
        }

        assertEquals("outer-inner-result", result)
    }

    // ==================== Nested Read Session Tests ====================

    @Test
    fun testNestedReadSessionReusesSession() = runTest {
        val context = createTestLLMContext()
        var outerSessionId: Int? = null
        var innerSessionId: Int? = null

        context.readSession {
            outerSessionId = this.hashCode()

            // Nested readSession should reuse the same session
            context.readSession {
                innerSessionId = this.hashCode()
            }
        }

        assertEquals(outerSessionId, innerSessionId, "Nested readSession should reuse the same session object")
    }

    @Test
    fun testDeeplyNestedReadSessions() = runTest {
        val context = createTestLLMContext()
        val sessionIds = mutableListOf<Int>()

        context.readSession {
            sessionIds.add(this.hashCode())

            context.readSession {
                sessionIds.add(this.hashCode())

                context.readSession {
                    sessionIds.add(this.hashCode())
                }
            }
        }

        assertEquals(3, sessionIds.size)
        assertTrue(sessionIds.all { it == sessionIds[0] }, "All nested read sessions should be the same object")
    }

    @Test
    fun testNestedReadSessionReturnValue() = runTest {
        val context = createTestLLMContext()

        val result = context.readSession {
            val innerResult = context.readSession {
                "inner-${prompt.id}"
            }
            "outer-$innerResult"
        }

        assertEquals("outer-inner-test-prompt", result)
    }

    // ==================== Write -> Read (Downgrade) Tests ====================

    @Test
    fun testReadSessionInsideWriteSession() = runTest {
        val context = createTestLLMContext()
        var readSessionExecuted = false
        var writeSessionId: Int? = null
        var readSessionId: Int? = null

        context.writeSession {
            writeSessionId = this.hashCode()
            // Change something in write session
            this.model = OllamaModels.Meta.LLAMA_4

            // Read session inside write session should work (creates a new read session view)
            context.readSession {
                readSessionExecuted = true
                readSessionId = this.hashCode()
                // Should see the updated model from write session
                assertEquals(OllamaModels.Meta.LLAMA_4.id, model.id)
            }
        }

        assertTrue(readSessionExecuted, "Read session inside write session should execute")
        // Read session inside write session creates a new AIAgentLLMReadSession object
        // (different from nested writeSession which reuses the same session)
        assertTrue(
            writeSessionId != readSessionId,
            "Read session inside write session should be a different object (read view)"
        )
    }

    @Test
    fun testMultipleReadSessionsInsideWriteSession() = runTest {
        val context = createTestLLMContext()
        val readResults = mutableListOf<String>()

        context.writeSession {
            this.prompt = prompt("prompt-1") {}

            context.readSession {
                readResults.add(prompt.id)
            }

            this.prompt = prompt("prompt-2") {}

            context.readSession {
                readResults.add(prompt.id)
            }
        }

        assertEquals(listOf("prompt-1", "prompt-2"), readResults)
    }

    // ==================== Read -> Write (Upgrade) Tests ====================

    @Test
    fun testWriteSessionInsideReadSessionThrows() = runTest {
        val context = createTestLLMContext()

        assertFailsWith<IllegalStateException> {
            context.readSession {
                // Attempting to upgrade from read to write should throw
                context.writeSession {
                    // This should not execute
                }
            }
        }
    }

    @Test
    fun testWriteSessionInsideNestedReadSessionThrows() = runTest {
        val context = createTestLLMContext()

        assertFailsWith<IllegalStateException> {
            context.readSession {
                context.readSession {
                    // Attempting to upgrade from nested read to write should throw
                    context.writeSession {
                        // This should not execute
                    }
                }
            }
        }
    }

    // ==================== Exception Handling Tests ====================

    @Test
    fun testExceptionInNestedWriteSession() = runTest {
        val context = createTestLLMContext()
        val originalPrompt = context.prompt

        assertFailsWith<RuntimeException> {
            context.writeSession {
                this.prompt = prompt("temp-prompt") {}

                context.writeSession {
                    throw RuntimeException("Test exception in nested session")
                }
            }
        }

        // Context should remain unchanged due to exception
        context.readSession {
            assertEquals(originalPrompt.id, prompt.id, "Context should be unchanged after exception")
        }
    }

    @Test
    fun testExceptionInOuterWriteSessionAfterNestedSuccess() = runTest {
        val context = createTestLLMContext()
        val originalPrompt = context.prompt

        assertFailsWith<RuntimeException> {
            context.writeSession {
                context.writeSession {
                    this.prompt = prompt("nested-prompt") {}
                }

                throw RuntimeException("Test exception in outer session")
            }
        }

        // Context should remain unchanged due to exception in outer session
        context.readSession {
            assertEquals(originalPrompt.id, prompt.id, "Context should be unchanged after exception")
        }
    }

    // ==================== Concurrent Access Tests ====================

    @Test
    fun testConcurrentWriteSessionsFromDifferentCoroutines() = runTest {
        val context = createTestLLMContext()
        val results = mutableListOf<String>()

        val job1 = async {
            context.writeSession {
                delay(10)
                this.prompt = prompt("coroutine-1") {}
                results.add("coroutine-1-start")
                delay(20)
                results.add("coroutine-1-end")
            }
        }

        val job2 = async {
            delay(5) // Start slightly after job1
            context.writeSession {
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

    @Test
    fun testMultipleReadSessionsConcurrently() = runTest {
        val context = createTestLLMContext()
        val results = mutableListOf<String>()

        val job1 = async {
            context.readSession {
                results.add("reader-1-start")
                delay(20)
                results.add("reader-1-end")
                prompt.id
            }
        }

        val job2 = async {
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

    // ==================== Independent Context Tests ====================

    @Test
    fun testIndependentContextsHaveIndependentSessions() = runTest {
        val context1 = createTestLLMContext()
        val context2 = createTestLLMContext()

        var context1SessionId: Int? = null
        var context2SessionId: Int? = null

        context1.writeSession {
            context1SessionId = this.hashCode()

            // Nested call to different context should create new session
            context2.writeSession {
                context2SessionId = this.hashCode()
            }
        }

        assertTrue(context1SessionId != context2SessionId, "Different contexts should have different sessions")
    }

    @Test
    fun testNestedCallToDifferentContextDoesNotAffectOriginal() = runTest {
        val context1 = createTestLLMContext()
        val context2 = createTestLLMContext()
        val newModel = OllamaModels.Meta.LLAMA_4

        context1.writeSession {
            val originalModel = this.model

            // Modify context2 inside context1's session
            context2.writeSession {
                this.model = newModel
            }

            // context1's model should be unchanged
            assertEquals(originalModel.id, this.model.id, "Context1's model should be unchanged")
        }

        // Verify context2 was modified
        context2.readSession {
            assertEquals(newModel.id, model.id, "Context2's model should be changed")
        }
    }

    // ==================== Copy Method Re-entrancy Tests ====================

    @Test
    fun testCopyInsideWriteSessionDoesNotDeadlock() = runTest {
        val context = createTestLLMContext()
        val newModel = OllamaModels.Meta.LLAMA_4

        val copiedContext = context.writeSession {
            // Modify the session state
            this.model = newModel
            this.prompt = prompt("modified-prompt") {}

            // Call copy() from within writeSession - should not deadlock
            context.copy(
                model = this.model,
                prompt = this.prompt
            )
        }

        // Verify the copied context has the modified values
        copiedContext.readSession {
            assertEquals(newModel.id, model.id, "Copied context should have the new model")
            assertEquals("modified-prompt", prompt.id, "Copied context should have the modified prompt")
        }
    }

    @Test
    fun testCopyInsideReadSessionDoesNotDeadlock() = runTest {
        val context = createTestLLMContext()

        val copiedContext = context.readSession {
            // Call copy() from within readSession - should not deadlock
            context.copy(
                prompt = prompt("copied-prompt") {}
            )
        }

        // Verify the copied context was created successfully
        copiedContext.readSession {
            assertEquals("copied-prompt", prompt.id, "Copied context should have the specified prompt")
        }
    }

    @Test
    fun testCopyInsideNestedWriteSessionDoesNotDeadlock() = runTest {
        val context = createTestLLMContext()

        val copiedContext = context.writeSession {
            context.writeSession {
                context.writeSession {
                    // Deeply nested - call copy()
                    context.copy(
                        prompt = prompt("deeply-nested-copy") {}
                    )
                }
            }
        }

        copiedContext.readSession {
            assertEquals("deeply-nested-copy", prompt.id)
        }
    }

    @Test
    fun testCopiedContextIsIndependent() = runTest {
        val context = createTestLLMContext()
        val newModel = OllamaModels.Meta.LLAMA_4

        val copiedContext = context.writeSession {
            context.copy()
        }

        // Modify original context
        context.writeSession {
            this.model = newModel
        }

        // Copied context should not be affected
        copiedContext.readSession {
            assertEquals(OllamaModels.Meta.LLAMA_3_2.id, model.id, "Copied context should retain original model")
        }

        // Original context should have new model
        context.readSession {
            assertEquals(newModel.id, model.id, "Original context should have new model")
        }
    }
}
