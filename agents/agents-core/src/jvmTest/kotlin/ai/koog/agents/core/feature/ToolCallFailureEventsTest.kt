package ai.koog.agents.core.feature

import ai.koog.agents.core.agent.GraphAIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.entity.createStorageKey
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.ReceivedToolResults
import ai.koog.agents.core.dsl.extension.ToolCalls
import ai.koog.agents.core.dsl.extension.nodeExecuteSingleTool
import ai.koog.agents.core.dsl.extension.nodeExecuteTools
import ai.koog.agents.core.environment.ReceivedToolResult
import ai.koog.agents.core.feature.config.FeatureConfig
import ai.koog.agents.core.feature.handler.tool.ToolCallFailedContext
import ai.koog.agents.core.feature.handler.tool.ToolValidationFailedContext
import ai.koog.agents.core.feature.pipeline.AIAgentGraphPipeline
import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.message.MessagePart
import ai.koog.serialization.JSONSerializer
import ai.koog.serialization.kotlinx.KotlinxSerializer
import ai.koog.serialization.typeToken
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ToolCallFailureEventsTest {
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

    private class BadResultTool : SimpleTool<RequiredArgs>(
        argsType = typeToken<RequiredArgs>(),
        name = "bad_result",
        description = "Tool that fails on result serialization.",
    ) {
        override suspend fun execute(args: RequiredArgs): String = "Ok"
        override fun encodeResultToString(result: String, serializer: JSONSerializer): String {
            throw IllegalStateException("Serialization failed")
        }
    }

    private class ToolFailureCaptureConfig : FeatureConfig() {
        var onToolCallFailed: (ToolCallFailedContext) -> Unit = {}
        var onToolValidationFailed: (ToolValidationFailedContext) -> Unit = {}
    }

    private object ToolFailureCaptureFeature : AIAgentGraphFeature<ToolFailureCaptureConfig, Unit> {
        override val key = createStorageKey<Unit>("tool_failure_capture")

        override fun createInitialConfig(
            agentConfig: AIAgentConfig,
        ): ToolFailureCaptureConfig = ToolFailureCaptureConfig()

        override fun install(config: ToolFailureCaptureConfig, pipeline: AIAgentGraphPipeline) {
            pipeline.interceptToolCallFailed(this) { eventContext ->
                config.onToolCallFailed(eventContext)
            }
            pipeline.interceptToolValidationFailed(this) { eventContext ->
                config.onToolValidationFailed(eventContext)
            }
        }
    }

    @Test
    fun testInvalidJsonTriggersToolValidationFailedEvent() = runTest {
        var toolValidationFailed: ToolValidationFailedContext? = null

        val strategy = strategy<ToolCalls, ReceivedToolResults>("tool_failure_strategy") {
            val executeTool by nodeExecuteTools()
            edge(nodeStart forwardTo executeTool)
            edge(executeTool forwardTo nodeFinish)
        }

        val agent = GraphAIAgent(
            promptExecutor = getMockExecutor(serializer) { },
            agentConfig = AIAgentConfig.withSystemPrompt("test"),
            strategy = strategy,
            toolRegistry = ToolRegistry { tool(RequiredArgsTool()) },
            installFeatures = {
                install(ToolFailureCaptureFeature) {
                    onToolValidationFailed = { toolValidationFailed = it }
                }
            }
        )

        val toolCall = MessagePart.Tool.Call(
            id = "1",
            tool = "required_args",
            args = "not-json",
        )

        agent.run(ToolCalls(listOf(toolCall)))
        val capturedFailure = assertNotNull(toolValidationFailed)
        assertEquals("required_args", capturedFailure.toolName)
        assertTrue(capturedFailure.message.contains("Failed to parse tool arguments"))
    }

    @Test
    fun testMissingFieldTriggersToolCallFailedEvent() = runTest {
        var toolCallFailed: ToolCallFailedContext? = null

        val strategy = strategy<MessagePart.Tool.Call, ReceivedToolResult>("tool_failure_strategy") {
            val executeTool by nodeExecuteSingleTool()
            edge(nodeStart forwardTo executeTool)
            edge(executeTool forwardTo nodeFinish)
        }

        val agent = GraphAIAgent(
            promptExecutor = getMockExecutor(serializer) { },
            agentConfig = AIAgentConfig.withSystemPrompt("test"),
            strategy = strategy,
            toolRegistry = ToolRegistry { tool(RequiredArgsTool()) },
            installFeatures = {
                install(ToolFailureCaptureFeature) {
                    onToolCallFailed = { toolCallFailed = it }
                }
            }
        )

        val toolCall = MessagePart.Tool.Call(
            id = "1",
            tool = "required_args",
            args = "{}",
        )

        agent.run(toolCall)
        val captureFailure = assertNotNull(toolCallFailed)
        assertEquals("required_args", captureFailure.toolName)
    }

    @Test
    fun testResultSerializationFailureTriggersToolCallFailedEvent() = runTest {
        var toolCallFailed: ToolCallFailedContext? = null

        val strategy = strategy<MessagePart.Tool.Call, ReceivedToolResult>("tool_failure_strategy") {
            val executeTool by nodeExecuteSingleTool()
            edge(nodeStart forwardTo executeTool)
            edge(executeTool forwardTo nodeFinish)
        }

        val agent = GraphAIAgent(
            promptExecutor = getMockExecutor(serializer) { },
            agentConfig = AIAgentConfig.withSystemPrompt("test"),
            strategy = strategy,
            toolRegistry = ToolRegistry { tool(BadResultTool()) },
            installFeatures = {
                install(ToolFailureCaptureFeature) {
                    onToolCallFailed = { toolCallFailed = it }
                }
            }
        )

        val toolCall = MessagePart.Tool.Call(
            id = "1",
            tool = "bad_result",
            args = "{\"required\": \"value\"}",
        )

        agent.run(toolCall)
        val capturedFailure = assertNotNull(toolCallFailed)
        assertEquals("bad_result", capturedFailure.toolName)
    }
}
