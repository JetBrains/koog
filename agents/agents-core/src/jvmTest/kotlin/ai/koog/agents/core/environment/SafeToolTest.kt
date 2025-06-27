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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SafeToolTest {

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
                            result = "Test result",
                            type = typeOf<String>(),
                            json = Json,
                        )
                    )
                } else {
                    ReceivedToolResult(
                        id = toolCall.id,
                        tool = toolCall.tool,
                        content = "Error: Test error",
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

        val result = safeTool.execute("test", 123)

        assertTrue(result.isSuccessful())
        assertEquals("Test result", result.asSuccessful().result)
        assertEquals("Success content", result.content)
    }

    @Test
    fun testExecuteFailure() = runTest {
        val mockEnvironment = MockEnvironment(shouldSucceed = false)
        val safeTool = SafeToolFromCallable(::testFunction, mockEnvironment, testClock)

        val result = safeTool.execute("test", 123)

        assertTrue(result.isFailure())
        assertEquals("Error: Test error", result.content)
        assertEquals("Error: Test error", result.asFailure().message)
    }

    @Test
    fun testExecuteRaw() = runTest {
        val mockEnvironment = MockEnvironment(shouldSucceed = true, resultContent = "Raw result content")
        val safeTool = SafeToolFromCallable(::testFunction, mockEnvironment, testClock)

        val result = safeTool.executeRaw("test", 123)

        assertEquals("Raw result content", result)
    }

    @Test
    fun testResultSuccessHelpers() = runTest {
        val success = SafeToolFromCallable.Result.Success("Test result", "Success content")

        assertEquals("Test result", success.asSuccessful().result)
        assertEquals("Success content", success.content)
    }

    @Test
    fun testResultFailureHelpers() = runTest {
        val failure = SafeToolFromCallable.Result.Failure<String>("Error message")

        assertFalse(failure.isSuccessful())
        assertTrue(failure.isFailure())
        assertEquals("Error message", failure.asFailure().message)
        assertEquals("Error message", failure.content)
    }

    @Test
    fun testInvalidArgumentCount() = runTest {
        val mockEnvironment = MockEnvironment(shouldSucceed = true)
        val safeTool = SafeToolFromCallable(::testFunction, mockEnvironment, testClock)

        assertThrows<IllegalStateException> {
            safeTool.execute("test")
        }
    }

    @Test
    fun testWithNullArgument() = runTest {
        val mockEnvironment = MockEnvironment(shouldSucceed = true)
        val safeTool = SafeToolFromCallable(::testFunction, mockEnvironment, testClock)

        val result = safeTool.execute("test", null)

        assertTrue(result.isSuccessful())
        assertEquals("Test result", result.asSuccessful().result)
    }
}