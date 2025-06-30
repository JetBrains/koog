package ai.koog.agents.core.environment

import ai.koog.agents.core.CalculatorChatExecutor.testClock
import ai.koog.agents.core.tools.reflect.ToolFromCallable
import ai.koog.prompt.message.Message
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.assertThrows
import kotlin.reflect.typeOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SafeToolTest {

    companion object {
        private const val TEST_RESULT = "Test result"
        private const val TEST_ERROR = "Error: Test error"
    }

    private fun testFunction(param1: String, param2: Int): String {
        return "Result: $param1 - $param2"
    }

    private class MockEnvironment(
        private val shouldSucceed: Boolean = true,
        private val resultContent: String = "Success content",
    ) : AIAgentEnvironment {
        override suspend fun executeTools(toolCalls: List<Message.Tool.Call>): List<ReceivedToolResult> {
            return toolCalls.map { toolCall ->
                if (shouldSucceed) {
                    ReceivedToolResult(
                        id = toolCall.id,
                        tool = toolCall.tool,
                        content = resultContent,
                        result = ToolFromCallable.Result(
                            result = TEST_RESULT,
                            type = typeOf<String>(),
                            json = Json,
                        )
                    )
                } else {
                    ReceivedToolResult(
                        id = toolCall.id,
                        tool = toolCall.tool,
                        content = TEST_ERROR,
                        result = null,
                    )
                }
            }
        }

        override suspend fun reportProblem(exception: Throwable) {
            throw exception
        }

        override suspend fun sendTermination(result: String?) {
            // No-op for testing
        }
    }


    @Test
    fun testExecuteSuccess() = runTest {
        val mockEnvironment = MockEnvironment(shouldSucceed = true)
        val safeTool = SafeToolFromCallable(::testFunction, mockEnvironment, testClock)
        assertEquals(safeTool.toolFunction, ::testFunction)

        val result = safeTool.execute("test", 123)

        assertTrue(result.isSuccessful())
        assertEquals(TEST_RESULT, result.asSuccessful().result)
        assertEquals("Success content", result.content)
    }

    @Test
    fun testExecuteFailure() = runTest {
        val mockEnvironment = MockEnvironment(shouldSucceed = false)
        val safeTool = SafeToolFromCallable(::testFunction, mockEnvironment, testClock)
        assertEquals(safeTool.toolFunction, ::testFunction)

        val result = safeTool.execute("test", 123)

        assertTrue(result.isFailure())
        assertEquals(TEST_ERROR, result.content)
        assertEquals(TEST_ERROR, result.asFailure().message)
    }

    @Test
    fun testExecuteRaw() = runTest {
        val mockEnvironment = MockEnvironment(shouldSucceed = true, resultContent = "Raw result content")
        val safeTool = SafeToolFromCallable(::testFunction, mockEnvironment, testClock)
        assertEquals(safeTool.toolFunction, ::testFunction)

        val result = safeTool.executeRaw("test", 123)

        assertEquals("Raw result content", result)
    }

    @Test
    fun testResultSuccessHelpers() = runTest {
        val success = SafeToolFromCallable.Result.Success(TEST_RESULT, "Success content")

        assertTrue(success.isSuccessful())
        assertEquals(TEST_RESULT, success.asSuccessful().result)
        assertEquals("Success content", success.content)
    }

    @Test
    fun testResultFailureHelpers() = runTest {
        val failure = SafeToolFromCallable.Result.Failure<String>("Error message")

        assertTrue(failure.isFailure())
        assertEquals("Error message", failure.asFailure().message)
        assertEquals("Error message", failure.content)
    }

    @Test
    fun testInvalidArgumentCount() = runTest {
        val mockEnvironment = MockEnvironment(shouldSucceed = true)
        val safeTool = SafeToolFromCallable(::testFunction, mockEnvironment, testClock)
        assertEquals(safeTool.toolFunction, ::testFunction)

        assertThrows<IllegalStateException> {
            safeTool.execute("test")
        }
    }

    @Test
    fun testZeroArgumentCount() = runTest {
        val mockEnvironment = MockEnvironment(shouldSucceed = true)
        val safeTool = SafeToolFromCallable(::testFunction, mockEnvironment, testClock)
        assertEquals(safeTool.toolFunction, ::testFunction)

        assertThrows<IllegalStateException> {
            safeTool.execute()
        }
    }

    @Test
    fun testTooManyArguments() = runTest {
        val mockEnvironment = MockEnvironment(shouldSucceed = true)
        val safeTool = SafeToolFromCallable(::testFunction, mockEnvironment, testClock)
        assertEquals(safeTool.toolFunction, ::testFunction)

        assertThrows<IllegalStateException> {
            safeTool.execute("test", 123, "extra argument")
        }
    }

    @Test
    fun testWithNullArgumentInMockEnvironment() = runTest {
        val mockEnvironment = MockEnvironment(shouldSucceed = true)
        val safeTool = SafeToolFromCallable(::testFunction, mockEnvironment, testClock)
        assertEquals(safeTool.toolFunction, ::testFunction)

        val result = safeTool.execute("test", null)

        assertTrue(result.isSuccessful())
        assertEquals(TEST_RESULT, result.asSuccessful().result)
    }

    @Test
    fun testSafeToolParameters() = runTest {
        val mockEnvironment = MockEnvironment(shouldSucceed = true)
        val safeTool = SafeToolFromCallable(::testFunction, mockEnvironment, testClock)
        assertEquals(safeTool.toolFunction, ::testFunction)

        val safeToolParams = safeTool.toolFunction.parameters.joinToString(", ") { it.name.toString() }

        assertEquals("param1, param2", safeToolParams)
    }

    @Test
    fun testWithNullArgumentInDirectCallEnvironment() = runTest {
        val directCallEnvironment = object : AIAgentEnvironment {
            override suspend fun executeTools(toolCalls: List<Message.Tool.Call>): List<ReceivedToolResult> {
                return toolCalls.map { toolCall ->
                    try {
                        val result = testFunction("test", null as Int)

                        ReceivedToolResult(
                            id = toolCall.id,
                            tool = toolCall.tool,
                            content = "Success: $result",
                            result = ToolFromCallable.Result(
                                result = result,
                                type = typeOf<String>(),
                                json = Json,
                            )
                        )
                    } catch (e: Exception) {
                        ReceivedToolResult(
                            id = toolCall.id,
                            tool = toolCall.tool,
                            content = "Error: ${e.message}",
                            result = null
                        )
                    }
                }
            }

            override suspend fun reportProblem(exception: Throwable) {
                throw exception
            }

            override suspend fun sendTermination(result: String?) {
                // No-op for testing
            }
        }

        val safeTool = SafeToolFromCallable(::testFunction, directCallEnvironment, testClock)
        assertEquals(safeTool.toolFunction, ::testFunction)

        val result = safeTool.execute("test", null)

        assertTrue(result.isFailure())
        assertTrue(result.content.contains("null cannot be cast to non-null type kotlin.Int"))
    }
}