package ai.koog.agents.core.environment

import ai.koog.agents.core.CalculatorChatExecutor.testClock
import ai.koog.agents.core.feature.model.toAgentError
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.annotations.InternalAgentToolsApi
import ai.koog.agents.core.tools.reflect.ToolFromCallable
import ai.koog.agents.core.tools.reflect.asTool
import ai.koog.prompt.message.Message
import ai.koog.serialization.JSONObject
import ai.koog.serialization.JSONPrimitive
import ai.koog.serialization.kotlinx.KotlinxSerializer
import ai.koog.serialization.kotlinx.toKoogJSONElement
import ai.koog.serialization.kotlinx.toKoogJSONObject
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.serializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SafeToolTest {
    private val serializer = KotlinxSerializer()

    companion object {
        private const val TEST_RESULT = "Test result"
        private const val TEST_ERROR = "Error: Test error"
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
                    toolArgs = toolCall.contentJson.toKoogJSONObject(),
                    toolDescription = null,
                    content = resultContent,
                    resultKind = ToolResultKind.Success,
                    result = JSONPrimitive(TEST_RESULT)
                )
            } else {
                ReceivedToolResult(
                    id = toolCall.id,
                    tool = toolCall.tool,
                    toolArgs = toolCall.contentJson.toKoogJSONObject(),
                    toolDescription = null,
                    content = TEST_ERROR,
                    resultKind = ToolResultKind.Failure(Exception(TEST_ERROR).toAgentError()),
                    result = null,
                )
            }
        }

        override suspend fun reportProblem(exception: Throwable) {
            throw exception
        }
    }

    private object EchoTool : Tool<EchoTool.Echo, EchoTool.Echo>(
        argsSerializer = Echo.serializer(),
        resultSerializer = Echo.serializer(),
        name = "string_echo",
        description = "String echo tool"
    ) {
        @Serializable
        data class Echo(val value: String)

        override suspend fun execute(args: Echo): Echo = args
    }

    @Test
    fun testExecuteSuccess() = runTest {
        val mockEnvironment = MockEnvironment(shouldSucceed = true)
        val safeTool = SafeTool(::testFunction.asTool(), mockEnvironment, testClock)

        val result = safeTool.executeUnsafe(serializer, "test", 123)
        assertTrue(result.isSuccessful())
        assertEquals(TEST_RESULT, result.asSuccessful().result)
        assertEquals("Success content", result.content)
    }

    @Test
    fun testExecuteFailure() = runTest {
        val mockEnvironment = MockEnvironment(shouldSucceed = false)
        val safeTool = SafeTool(::testFunction.asTool(), mockEnvironment, testClock)

        val result = safeTool.executeUnsafe(serializer, "test", 123)

        assertTrue(result.isFailure())
        assertEquals(TEST_ERROR, result.content)
        assertEquals(TEST_ERROR, result.asFailure().message)
    }

    @Test
    fun testDecodeFailureReturnsFailure() {
        val badResult = buildJsonObject {
            put("not-a-value", "not-a-string-result")
        }

        val toolResult = ReceivedToolResult(
            id = "1",
            tool = EchoTool.name,
            toolArgs = JSONObject(emptyMap()),
            toolDescription = null,
            content = "Bad result",
            resultKind = ToolResultKind.Success,
            result = badResult.toKoogJSONElement(),
        )

        val safeResult = toolResult.toSafeResult(EchoTool, serializer)
        assertTrue(safeResult.isFailure())
    }

    @Test
    fun testSafeToolParameters() = runTest {
        val mockEnvironment = MockEnvironment(shouldSucceed = true)
        val safeTool = SafeTool(::testFunction.asTool(), mockEnvironment, testClock)

        val safeToolParams = (safeTool.tool as ToolFromCallable<String>)
            .callable.parameters
            .joinToString(", ") { it.name.toString() }

        assertEquals("param1, param2", safeToolParams)
    }

    @Test
    fun testWithDefaultParameter() = runTest {
        val mockEnvironment = MockEnvironment(shouldSucceed = true, resultContent = "Default param result")
        val safeTool = SafeTool(::testFunctionWithDefaultParam.asTool(), mockEnvironment, testClock)

        val result = safeTool.executeUnsafe(serializer, "test", 123)

        assertTrue(result.isSuccessful())
        assertEquals(TEST_RESULT, result.asSuccessful().result)
    }

    @Test
    fun testWithNullableArgument() = runTest {
        val mockEnvironment = MockEnvironment(shouldSucceed = true, resultContent = "Nullable arg result")
        val safeTool = SafeTool(::testFunctionWithNullableArg.asTool(), mockEnvironment, testClock)

        val resultWithValue = safeTool.executeUnsafe(serializer, "test", 123)
        assertTrue(resultWithValue.isSuccessful())
        assertEquals(TEST_RESULT, resultWithValue.asSuccessful().result)

        val resultWithNull = safeTool.executeUnsafe(serializer, "test", null)
        assertTrue(resultWithNull.isSuccessful())
        assertEquals(TEST_RESULT, resultWithNull.asSuccessful().result)
    }

    @Test
    fun testWithComplexArguments() = runTest {
        val mockEnvironment = MockEnvironment(shouldSucceed = true, resultContent = "Complex args result")
        val safeTool = SafeTool(::testFunctionWithComplexArgs.asTool(), mockEnvironment, testClock)

        val complexData = ComplexDataClass(
            id = "test-id",
            numbers = listOf(1, 2, 3),
            nested = SimpleDataClass(name = "nested-name", value = 42),
            enumValue = TestEnum.SECOND
        )

        val result = safeTool.executeUnsafe(serializer, "test", listOf(4, 5, 6), complexData)

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
                    val resultSerializer = serializer<String>()

                    ReceivedToolResult(
                        id = toolCall.id,
                        tool = toolCall.tool,
                        toolArgs = toolCall.contentJson.toKoogJSONObject(),
                        toolDescription = null,
                        content = "Success: $result",
                        resultKind = ToolResultKind.Success,
                        result = JSONPrimitive(result)
                    )
                } catch (e: Exception) {
                    ReceivedToolResult(
                        id = toolCall.id,
                        tool = toolCall.tool,
                        toolArgs = toolCall.contentJson.toKoogJSONObject(),
                        toolDescription = null,
                        content = "Error: ${e.message}",
                        resultKind = ToolResultKind.Failure(e.toAgentError()),
                        result = null
                    )
                }
            }

            override suspend fun reportProblem(exception: Throwable) {
                throw exception
            }
        }

        val safeTool = SafeTool(::testFunctionWithComplexArgs.asTool(), directCallEnvironment, testClock)

        val complexData = ComplexDataClass(
            id = "test-complex-id",
            numbers = listOf(1, 2, 3),
            nested = SimpleDataClass(name = "test-nested", value = 42),
            enumValue = TestEnum.FIRST
        )

        val result = safeTool.executeUnsafe(serializer, "test-param", listOf(4, 5, 6), complexData)

        assertTrue(result.isSuccessful())
        assertTrue(result.content.contains("direct-test"))
        assertTrue(result.content.contains("direct-call-id"))
    }
}
