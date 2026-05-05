package ai.koog.agents.core.environment

import ai.koog.agents.core.agent.AIAgentContextAwareTool
import ai.koog.agents.core.agent.StubAIAgentContext
import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.serialization.kotlinx.KotlinxSerializer
import ai.koog.serialization.typeToken
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals

@Serializable
internal data class CtxAwareToolArgs(val value: String)

internal class ContextAwareRecordingTool : Tool<CtxAwareToolArgs, String>(
    argsType = typeToken<CtxAwareToolArgs>(),
    resultType = typeToken<String>(),
    name = "context_aware",
    description = "Records the agent id from context.",
), AIAgentContextAwareTool<CtxAwareToolArgs, String> {
    var capturedAgentId: String? = null
    var capturedRunId: String? = null
    var plainExecuteCalled: Boolean = false

    override suspend fun execute(args: CtxAwareToolArgs): String {
        plainExecuteCalled = true
        return "plain:${args.value}"
    }

    override suspend fun execute(args: CtxAwareToolArgs, context: AIAgentContext): String {
        capturedAgentId = context.agentId
        capturedRunId = context.runId
        return "ctx:${args.value}"
    }
}

internal class PlainCtxToolForDispatch : SimpleTool<CtxAwareToolArgs>(
    argsType = typeToken<CtxAwareToolArgs>(),
    name = "plain",
    description = "Plain tool with no context awareness.",
) {
    override suspend fun execute(args: CtxAwareToolArgs): String = "plain:${args.value}"
}

@OptIn(InternalAgentsApi::class)
class AIAgentContextAwareToolDispatchTest {
    private val serializer = KotlinxSerializer()
    private val logger = KotlinLogging.logger { }

    private fun toolCall(toolName: String): Message.Tool.Call =
        Message.Tool.Call(
            id = "1",
            tool = toolName,
            content = """{"value":"hello"}""",
            metaInfo = ResponseMetaInfo.Empty,
        )

    @Test
    fun testContextAwareToolReceivesContext() = runTest {
        val tool = ContextAwareRecordingTool()
        val environment = GenericAgentEnvironment(
            logger = logger,
            toolRegistry = ToolRegistry { tool(tool) },
            serializer = serializer,
            context = StubAIAgentContext(agentId = "parent-id-42", runId = "run-id-7"),
        )

        val result = environment.executeTool(toolCall("context_aware"))

        assertEquals(ToolResultKind.Success, result.resultKind)
        assertEquals("\"ctx:hello\"", result.content)
        assertEquals("parent-id-42", tool.capturedAgentId)
        assertEquals("run-id-7", tool.capturedRunId)
        assertEquals(false, tool.plainExecuteCalled)
    }

    @Test
    fun testPlainToolStillRuns() = runTest {
        val tool = PlainCtxToolForDispatch()
        val environment = GenericAgentEnvironment(
            logger = logger,
            toolRegistry = ToolRegistry { tool(tool) },
            serializer = serializer,
            context = StubAIAgentContext(agentId = "parent-id-99", runId = "run-id-99"),
        )

        val result = environment.executeTool(toolCall("plain"))

        assertEquals(ToolResultKind.Success, result.resultKind)
        assertEquals("plain:hello", result.content)
    }
}
