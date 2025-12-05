package ai.koog.agents.core.environment

import ai.koog.agents.core.CalculatorChatExecutor.testClock
import ai.koog.agents.core.tools.annotations.InternalAgentToolsApi
import ai.koog.agents.core.tools.asToolDescriptorSerializer
import ai.koog.prompt.message.Message
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.serializer
import org.junit.jupiter.api.assertThrows
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SafeToolTest {
    private fun testInvalidArguments(vararg args: Any?) = runTest {
        val mockEnvironment = MockEnvironment(shouldSucceed = true)
        val safeTool = SafeToolFromCallable(::testFunction, mockEnvironment, testClock)
        assertEquals(safeTool.toolFunction, ::testFunction)

        assertThrows<IllegalStateException> {
            safeTool.execute(*args)
        }
    }

    companion object {
        private const val TEST_RESULT = "Test result"
        private const val TEST_ERROR = "Error: Test error"

        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            explicitNulls = false
            decodeEnumsCaseInsensitive = true
        }
    }

    private fun testFunction(param1: String, param2: Int): String {
        return "Result: $param1 - $param2"
    }

    private fun testFunctionWithDefaultParam(param1: String, param2: Int = 42): String {
        return "Result with default: $param1 - $param2"
    }

    enum class TestEnum {
        FIRST,
        SECOND,
        THIRD
    }

    @Serializable
    data class SimpleDataClass(val name: String, val value: Int)

    @Serializable
    data class ComplexDataClass(
        val id: String,
        val numbers: List<Int>,
        val nested: SimpleDataClass,
        val enumValue: TestEnum
    )

    private fun testFunctionWithComplexArgs(
        param1: String,
        param2: List<Int>,
        param3: ComplexDataClass
    ): String {
        return "Complex result: $param1 - ${param2.size} items - ${param3.id}"
    }

    private fun testFunctionWithNullableArg(param1: String, param2: Int?): String {
        return "Nullable result: $param1 - ${param2 ?: "null"}"
    }

    private class MockEnvironment(
        private val shouldSucceed: Boolean = true,
        private val resultContent: String = "Success content",
    ) : AIAgentEnvironment {
        @OptIn(InternalAgentToolsApi::class)
        override suspend fun executeTool(toolCall: Message.Tool.Call): ReceivedToolResult {
            return if (shouldSucceed) {
                ReceivedToolResult(
                    id = toolCall.id,
                    tool = toolCall.tool,
                    content = resultContent,
                    result = json.encodeToJsonElement(serializer<String>().asToolDescriptorSerializer(), TEST_RESULT)
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

        override suspend fun reportProblem(exception: Throwable) {
            throw exception
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
    fun testInvalidArgumentCount() = testInvalidArguments("test")

    @Test
    fun testZeroArgumentCount() = testInvalidArguments()

    @Test
    fun testTooManyArguments() = testInvalidArguments("test", 123, "extra argument")

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
            override suspend fun executeTool(toolCall: Message.Tool.Call): ReceivedToolResult {
                return try {
                    val result = testFunction("test", null as Int)

                    ReceivedToolResult(
                        id = toolCall.id,
                        tool = toolCall.tool,
                        content = "Success: $result",
                        result = json.encodeToJsonElement(result).jsonObject
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

            override suspend fun reportProblem(exception: Throwable) {
                throw exception
            }
        }

        val safeTool = SafeToolFromCallable(::testFunction, directCallEnvironment, testClock)
        assertEquals(safeTool.toolFunction, ::testFunction)

        val result = safeTool.execute("test", null)

        assertTrue(result.isFailure())
        assertTrue(result.content.contains("null cannot be cast to non-null type kotlin.Int"))
    }

    @Test
    fun testWithDefaultParameter() = runTest {
        val mockEnvironment = MockEnvironment(shouldSucceed = true, resultContent = "Default param result")
        val safeTool = SafeToolFromCallable(::testFunctionWithDefaultParam, mockEnvironment, testClock)
        assertEquals(safeTool.toolFunction, ::testFunctionWithDefaultParam)

        val result = safeTool.execute("test", 123)

        assertTrue(result.isSuccessful())
        assertEquals(TEST_RESULT, result.asSuccessful().result)
    }

    @Test
    fun testWithNullableArgument() = runTest {
        val mockEnvironment = MockEnvironment(shouldSucceed = true, resultContent = "Nullable arg result")
        val safeTool = SafeToolFromCallable(::testFunctionWithNullableArg, mockEnvironment, testClock)
        assertEquals(safeTool.toolFunction, ::testFunctionWithNullableArg)

        val resultWithValue = safeTool.execute("test", 123)
        assertTrue(resultWithValue.isSuccessful())
        assertEquals(TEST_RESULT, resultWithValue.asSuccessful().result)

        val resultWithNull = safeTool.execute("test", null)
        assertTrue(resultWithNull.isSuccessful())
        assertEquals(TEST_RESULT, resultWithNull.asSuccessful().result)
    }

    @Test
    fun testWithComplexArguments() = runTest {
        val mockEnvironment = MockEnvironment(shouldSucceed = true, resultContent = "Complex args result")
        val safeTool = SafeToolFromCallable(::testFunctionWithComplexArgs, mockEnvironment, testClock)
        assertEquals(safeTool.toolFunction, ::testFunctionWithComplexArgs)

        val complexData = ComplexDataClass(
            id = "test-id",
            numbers = listOf(1, 2, 3),
            nested = SimpleDataClass(name = "nested-name", value = 42),
            enumValue = TestEnum.SECOND
        )

        val result = safeTool.execute("test", listOf(4, 5, 6), complexData)

        assertTrue(result.isSuccessful())
        assertEquals(TEST_RESULT, result.asSuccessful().result)
    }

    @OptIn(InternalAgentToolsApi::class)
    @Test
    fun testWithComplexArgumentsInDirectCallEnvironment() = runTest {
        val directCallEnvironment = object : AIAgentEnvironment {
            override suspend fun executeTool(toolCall: Message.Tool.Call): ReceivedToolResult {
                return try {
                    val complexData = ComplexDataClass(
                        id = "direct-call-id",
                        numbers = listOf(7, 8, 9),
                        nested = SimpleDataClass(name = "direct-nested", value = 100),
                        enumValue = TestEnum.THIRD
                    )

                    val result = testFunctionWithComplexArgs("direct-test", listOf(10, 11, 12), complexData)
                    val resultSerializer = serializer<String>().asToolDescriptorSerializer()

                    ReceivedToolResult(
                        id = toolCall.id,
                        tool = toolCall.tool,
                        content = "Success: $result",
                        result = json.encodeToJsonElement(resultSerializer, result)
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

            override suspend fun reportProblem(exception: Throwable) {
                throw exception
            }
        }

        val safeTool = SafeToolFromCallable(::testFunctionWithComplexArgs, directCallEnvironment, testClock)
        assertEquals(safeTool.toolFunction, ::testFunctionWithComplexArgs)

        val complexData = ComplexDataClass(
            id = "test-complex-id",
            numbers = listOf(1, 2, 3),
            nested = SimpleDataClass(name = "test-nested", value = 42),
            enumValue = TestEnum.FIRST
        )

        val result = safeTool.execute("test-param", listOf(4, 5, 6), complexData)

        assertTrue(result.isSuccessful())
        assertTrue(result.content.contains("direct-test"))
        assertTrue(result.content.contains("direct-call-id"))
    }
}
