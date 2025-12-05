package ai.koog.agents.core.environment

import ai.koog.agents.core.agent.context.element.getAgentRunInfoElementOrThrow
import ai.koog.agents.core.feature.pipeline.AIAgentPipeline
import ai.koog.agents.core.model.message.AIAgentEnvironmentToolResultToAgentContent
import ai.koog.agents.core.model.message.AgentToolCallToEnvironmentContent
import ai.koog.agents.core.model.message.AgentToolCallsToEnvironmentMessage
import ai.koog.agents.core.model.message.EnvironmentToolResultToAgentContent
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolException
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.annotations.InternalAgentToolsApi
import ai.koog.prompt.message.Message
import io.github.oshai.kotlinlogging.KLogger
import kotlinx.serialization.json.JsonElement

internal class GenericAgentEnvironment(
    private val agentId: String,
    private val strategyId: String,
    private val logger: KLogger,
    private val toolRegistry: ToolRegistry,
    private val pipeline: AIAgentPipeline
) : AIAgentEnvironment {

    override suspend fun executeTool(toolCall: Message.Tool.Call): ReceivedToolResult {
        val agentRunInfo = getAgentRunInfoElementOrThrow()
        logger.info {
            formatLog(agentRunInfo.agentId, agentRunInfo.runId, "Executing tool: ${toolCall.tool}")
        }

        val message = AgentToolCallsToEnvironmentMessage(
            runId = agentRunInfo.runId,
            content = AgentToolCallToEnvironmentContent(
                agentId = agentId,
                runId = agentRunInfo.runId,
                toolCallId = toolCall.id,
                toolName = toolCall.tool,
                toolArgs = toolCall.contentJson
            )
        )

        val environmentToolResult = processToolCall(message.content)

        logger.debug {
            "Tool (${toolCall.tool}) received result: ${environmentToolResult.message})"
        }

        return environmentToolResult.toResult()
    }

    override suspend fun reportProblem(exception: Throwable) {
        val agentRunInfo = getAgentRunInfoElementOrThrow()

        logger.error(exception) {
            formatLog(agentRunInfo.agentId, agentRunInfo.runId, "Reporting problem: ${exception.message}")
        }
        throw exception
    }

    private fun toolResult(
        toolCallId: String?,
        toolName: String,
        agentId: String,
        message: String,
        result: JsonElement?
    ): EnvironmentToolResultToAgentContent = AIAgentEnvironmentToolResultToAgentContent(
        toolCallId = toolCallId,
        toolName = toolName,
        agentId = agentId,
        message = message,
        toolResult = result
    )

    @OptIn(InternalAgentToolsApi::class)
    private suspend fun processToolCall(
        content: AgentToolCallToEnvironmentContent
    ): EnvironmentToolResultToAgentContent {
        logger.debug { "Handling tool call sent by server..." }

        val tool = toolRegistry.getToolOrNull(content.toolName)
            ?: run {
                logger.error { "Tool \"${content.toolName}\" not found." }
                return toolResult(
                    message = "Tool \"${content.toolName}\" not found. Use one of the available tools.",
                    toolCallId = content.toolCallId,
                    toolName = content.toolName,
                    agentId = agentId,
                    result = null
                )
            }

        val toolArgs = try {
            tool.decodeArgs(content.toolArgs)
        } catch (e: Exception) {
            logger.error(e) { "Tool \"${tool.name}\" failed to parse arguments: ${content.toolArgs}" }
            return toolResult(
                message = "Tool \"${tool.name}\" failed to parse arguments because of ${e.message}!",
                toolCallId = content.toolCallId,
                toolName = content.toolName,
                agentId = agentId,
                result = null
            )
        }

        pipeline.onToolCallStarting(content.runId, content.toolCallId, tool, toolArgs)

        val toolResult = try {
            @Suppress("UNCHECKED_CAST")
            (tool as Tool<Any?, Any?>).execute(toolArgs)
        } catch (e: ToolException) {
            pipeline.onToolValidationFailed(content.runId, content.toolCallId, tool, toolArgs, e.message)

            return toolResult(
                message = e.message,
                toolCallId = content.toolCallId,
                toolName = content.toolName,
                agentId = strategyId,
                result = null
            )
        } catch (e: Exception) {
            logger.error(e) { "Tool \"${tool.name}\" failed to execute with arguments: ${content.toolArgs}" }

            pipeline.onToolCallFailed(content.runId, content.toolCallId, tool, toolArgs, e)

            return toolResult(
                message = "Tool \"${tool.name}\" failed to execute because of ${e.message}!",
                toolCallId = content.toolCallId,
                toolName = content.toolName,
                agentId = strategyId,
                result = null
            )
        }

        pipeline.onToolCallCompleted(content.runId, content.toolCallId, tool, toolArgs, toolResult)

        logger.trace { "Completed execution of ${content.toolName} with result: $toolResult" }

        return toolResult(
            toolCallId = content.toolCallId,
            toolName = content.toolName,
            agentId = strategyId,
            message = tool.encodeResultToStringUnsafe(toolResult),
            result = tool.encodeResult(toolResult)
        )
    }

    private fun formatLog(agentId: String, runId: String, message: String): String =
        "[agent id: $agentId, run id: $runId] $message"
}
