package ai.koog.agents.core.environment

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.ToolException
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.message.Message.Tool
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.serialization.kotlinx.KotlinxSerializer
import ai.koog.serialization.typeToken
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GenericAgentEnvironmentTest {
    private val serializer = KotlinxSerializer()

    @Serializable
    private data class RequiredArgs(val required: String)

    private class RequiredArgsTool : SimpleTool<RequiredArgs>(
        argsType = typeToken<RequiredArgs>(),
        name = "required_args",
        description = "Tool that requires a single argument.",
    ) {
        override suspend fun execute(args: RequiredArgs): String = "Ok"
    }

    private class ValidationTool : SimpleTool<RequiredArgs>(
        argsType = typeToken<RequiredArgs>(),
        name = "validation_tool",
        description = "Tool that fails with validation error.",
    ) {
        override suspend fun execute(args: RequiredArgs): String {
            throw ToolException.ValidationFailure("Invalid arguments")
        }
    }

    private class FailingTool : SimpleTool<RequiredArgs>(
        argsType = typeToken<RequiredArgs>(),
        name = "failing_tool",
        description = "Tool that fails with runtime exception.",
    ) {
        override suspend fun execute(args: RequiredArgs): String {
            error("boom")
        }
    }

    private class SuccessTool : SimpleTool<RequiredArgs>(
        argsType = typeToken<RequiredArgs>(),
        name = "success_tool",
        description = "Tool that succeeds.",
    ) {
        override suspend fun execute(args: RequiredArgs): String = "ok:${args.required}"
    }

    private class CancellableTool : SimpleTool<RequiredArgs>(
        argsType = typeToken<RequiredArgs>(),
        name = "cancellable_tool",
        description = "Tool that throws cancellation.",
    ) {
        override suspend fun execute(args: RequiredArgs): String {
            throw CancellationException("cancelled")
        }
    }

    @Test
    fun testInvalidJsonArgsReturnsFailure() = runTest {
        val environment = GenericAgentEnvironment(
            agentId = "test_agent",
            logger = KotlinLogging.logger { },
            toolRegistry = ToolRegistry { tool(RequiredArgsTool()) },
            serializer = serializer,
        )

        val toolCall = Tool.Call(
            id = "1",
            tool = "required_args",
            content = "not-json",
            metaInfo = ResponseMetaInfo.Empty,
        )

        val result = environment.executeTool(toolCall)
        assertEquals("required_args", result.tool)
        assertTrue(result.resultKind is ToolResultKind.Failure)
    }

    @Test
    fun testMissingFieldReturnsFailure() = runTest {
        val environment = GenericAgentEnvironment(
            agentId = "test_agent",
            logger = KotlinLogging.logger { },
            toolRegistry = ToolRegistry { tool(RequiredArgsTool()) },
            serializer = serializer,
        )

        val toolCall = Tool.Call(
            id = "1",
            tool = "required_args",
            content = "{}",
            metaInfo = ResponseMetaInfo.Empty,
        )

        val result = environment.executeTool(toolCall)
        assertEquals("required_args", result.tool)
        assertTrue(result.resultKind is ToolResultKind.Failure)
    }

    @Test
    fun testUnknownToolReturnsFailure() = runTest {
        val environment = GenericAgentEnvironment(
            agentId = "test_agent",
            logger = KotlinLogging.logger { },
            toolRegistry = ToolRegistry {},
            serializer = serializer,
        )

        val result = environment.executeTool(
            Tool.Call(
                id = "1",
                tool = "missing_tool",
                content = """{"required":"value"}""",
                metaInfo = ResponseMetaInfo.Empty,
            )
        )

        assertEquals("missing_tool", result.tool)
        assertTrue(result.resultKind is ToolResultKind.Failure)
        assertTrue(result.content.contains("not found in the tool registry"))
    }

    @Test
    fun testToolExceptionReturnsValidationError() = runTest {
        val environment = GenericAgentEnvironment(
            agentId = "test_agent",
            logger = KotlinLogging.logger { },
            toolRegistry = ToolRegistry { tool(ValidationTool()) },
            serializer = serializer,
        )

        val result = environment.executeTool(
            Tool.Call(
                id = "1",
                tool = "validation_tool",
                content = """{"required":"value"}""",
                metaInfo = ResponseMetaInfo.Empty,
            )
        )

        assertTrue(result.resultKind is ToolResultKind.ValidationError)
        assertEquals("Invalid arguments", result.content)
    }

    @Test
    fun testRuntimeFailureReturnsFailure() = runTest {
        val environment = GenericAgentEnvironment(
            agentId = "test_agent",
            logger = KotlinLogging.logger { },
            toolRegistry = ToolRegistry { tool(FailingTool()) },
            serializer = serializer,
        )

        val result = environment.executeTool(
            Tool.Call(
                id = "1",
                tool = "failing_tool",
                content = """{"required":"value"}""",
                metaInfo = ResponseMetaInfo.Empty,
            )
        )

        assertTrue(result.resultKind is ToolResultKind.Failure)
        assertTrue(result.content.contains("failed to execute"))
    }

    @Test
    fun testSuccessfulExecutionReturnsSuccess() = runTest {
        val environment = GenericAgentEnvironment(
            agentId = "test_agent",
            logger = KotlinLogging.logger { },
            toolRegistry = ToolRegistry { tool(SuccessTool()) },
            serializer = serializer,
        )

        val result = environment.executeTool(
            Tool.Call(
                id = "1",
                tool = "success_tool",
                content = """{"required":"value"}""",
                metaInfo = ResponseMetaInfo.Empty,
            )
        )

        assertEquals(ToolResultKind.Success, result.resultKind)
        assertEquals("ok:value", result.content)
    }

    @Test
    fun testCancellationIsRethrown() = runTest {
        val environment = GenericAgentEnvironment(
            agentId = "test_agent",
            logger = KotlinLogging.logger { },
            toolRegistry = ToolRegistry { tool(CancellableTool()) },
            serializer = serializer,
        )

        assertFailsWith<CancellationException> {
            environment.executeTool(
                Tool.Call(
                    id = "1",
                    tool = "cancellable_tool",
                    content = """{"required":"value"}""",
                    metaInfo = ResponseMetaInfo.Empty,
                )
            )
        }
    }
}
