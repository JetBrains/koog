package ai.koog.agents.core.environment

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.message.Message.Tool
import ai.koog.prompt.message.ResponseMetaInfo
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GenericAgentEnvironmentTest {
    @Serializable
    private data class RequiredArgs(val required: String)

    private class RequiredArgsTool : SimpleTool<RequiredArgs>(
        argsSerializer = RequiredArgs.serializer(),
        name = "required_args",
        description = "Tool that requires a single argument.",
    ) {
        override suspend fun execute(args: RequiredArgs): String = "Ok"
    }

    @Test
    fun testInvalidJsonArgsReturnsFailure() = runTest {
        val environment = GenericAgentEnvironment(
            agentId = "test_agent",
            logger = KotlinLogging.logger { },
            toolRegistry = ToolRegistry { tool(RequiredArgsTool()) },
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
}
