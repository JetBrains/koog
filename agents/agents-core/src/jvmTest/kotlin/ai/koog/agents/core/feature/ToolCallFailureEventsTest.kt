package ai.koog.agents.core.feature

import ai.koog.agents.core.agent.GraphAIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.entity.AIAgentStorageKey
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeExecuteTool
import ai.koog.agents.core.environment.ReceivedToolResult
import ai.koog.agents.core.feature.config.FeatureConfig
import ai.koog.agents.core.feature.handler.tool.ToolCallFailedContext
import ai.koog.agents.core.feature.handler.tool.ToolValidationFailedContext
import ai.koog.agents.core.feature.pipeline.AIAgentGraphPipeline
import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlin.reflect.typeOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ToolCallFailureEventsTest {
    @Serializable
    private data class RequiredArgs(val required: String)

    private class RequiredArgsTool : SimpleTool<RequiredArgs>(
        argsSerializer = RequiredArgs.serializer(),
        name = "required_args",
        description = "Tool that requires a single argument.",
    ) {
        override suspend fun execute(args: RequiredArgs): String = "Ok"
    }

    private class BadResultTool : SimpleTool<RequiredArgs>(
        argsSerializer = RequiredArgs.serializer(),
        name = "bad_result",
        description = "Tool that fails on result serialization.",
    ) {
        override suspend fun execute(args: RequiredArgs): String = "Ok"
        override fun encodeResultToString(result: String): String {
            throw IllegalStateException("Serialization failed")
        }
    }

    private class ToolFailureCaptureConfig : FeatureConfig() {
        var onToolCallFailed: (ToolCallFailedContext) -> Unit = {}
        var onToolValidationFailed: (ToolValidationFailedContext) -> Unit = {}
    }

    private object ToolFailureCaptureFeature : AIAgentGraphFeature<ToolFailureCaptureConfig, Unit> {
        override val key = AIAgentStorageKey<Unit>("tool_failure_capture")
        override fun createInitialConfig(): ToolFailureCaptureConfig = ToolFailureCaptureConfig()
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

        val strategy = strategy<Message.Tool.Call, ReceivedToolResult>("tool_failure_strategy") {
            val executeTool by nodeExecuteTool()
            edge(nodeStart forwardTo executeTool)
            edge(executeTool forwardTo nodeFinish)
        }

        val agent = GraphAIAgent(
            inputType = typeOf<Message.Tool.Call>(),
            outputType = typeOf<ReceivedToolResult>(),
            promptExecutor = getMockExecutor { },
            agentConfig = AIAgentConfig.withSystemPrompt("test"),
            strategy = strategy,
            toolRegistry = ToolRegistry { tool(RequiredArgsTool()) },
            installFeatures = {
                install(ToolFailureCaptureFeature) {
                    onToolValidationFailed = { toolValidationFailed = it }
                }
            }
        )

        val toolCall = Message.Tool.Call(
            id = "1",
            tool = "required_args",
            content = "not-json",
            metaInfo = ResponseMetaInfo.Empty,
        )

        agent.run(toolCall)
        val capturedFailure = assertNotNull(toolValidationFailed)
        assertEquals("required_args", capturedFailure.toolName)
        assertTrue(capturedFailure.message.contains("Failed to parse tool arguments"))
    }

    @Test
    fun testMissingFieldTriggersToolCallFailedEvent() = runTest {
        var toolCallFailed: ToolCallFailedContext? = null

        val strategy = strategy<Message.Tool.Call, ReceivedToolResult>("tool_failure_strategy") {
            val executeTool by nodeExecuteTool()
            edge(nodeStart forwardTo executeTool)
            edge(executeTool forwardTo nodeFinish)
        }

        val agent = GraphAIAgent(
            inputType = typeOf<Message.Tool.Call>(),
            outputType = typeOf<ReceivedToolResult>(),
            promptExecutor = getMockExecutor { },
            agentConfig = AIAgentConfig.withSystemPrompt("test"),
            strategy = strategy,
            toolRegistry = ToolRegistry { tool(RequiredArgsTool()) },
            installFeatures = {
                install(ToolFailureCaptureFeature) {
                    onToolCallFailed = { toolCallFailed = it }
                }
            }
        )

        val toolCall = Message.Tool.Call(
            id = "1",
            tool = "required_args",
            content = "{}",
            metaInfo = ResponseMetaInfo.Empty,
        )

        agent.run(toolCall)
        val captureFailure = assertNotNull(toolCallFailed)
        assertEquals("required_args", captureFailure.toolName)
    }

    @Test
    fun testResultSerializationFailureTriggersToolCallFailedEvent() = runTest {
        var toolCallFailed: ToolCallFailedContext? = null

        val strategy = strategy<Message.Tool.Call, ReceivedToolResult>("tool_failure_strategy") {
            val executeTool by nodeExecuteTool()
            edge(nodeStart forwardTo executeTool)
            edge(executeTool forwardTo nodeFinish)
        }

        val agent = GraphAIAgent(
            inputType = typeOf<Message.Tool.Call>(),
            outputType = typeOf<ReceivedToolResult>(),
            promptExecutor = getMockExecutor { },
            agentConfig = AIAgentConfig.withSystemPrompt("test"),
            strategy = strategy,
            toolRegistry = ToolRegistry { tool(BadResultTool()) },
            installFeatures = {
                install(ToolFailureCaptureFeature) {
                    onToolCallFailed = { toolCallFailed = it }
                }
            }
        )

        val toolCall = Message.Tool.Call(
            id = "1",
            tool = "bad_result",
            content = "{\"required\": \"value\"}",
            metaInfo = ResponseMetaInfo.Empty,
        )

        agent.run(toolCall)
        val capturedFailure = assertNotNull(toolCallFailed)
        assertEquals("bad_result", capturedFailure.toolName)
    }
}
