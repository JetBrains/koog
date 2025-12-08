package ai.koog.agents.core.environment

import ai.koog.agents.core.agent.context.element.getAgentRunInfoElementOrThrow
import ai.koog.agents.core.feature.pipeline.AIAgentPipeline
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolException
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.annotations.InternalAgentToolsApi
import ai.koog.prompt.message.Message
import io.github.oshai.kotlinlogging.KLogger

internal class GenericAgentEnvironment(
    private val agentId: String,
    private val logger: KLogger,
    private val toolRegistry: ToolRegistry,
    private val pipeline: AIAgentPipeline
) : AIAgentEnvironment {

    override suspend fun executeTool(toolCall: Message.Tool.Call): ReceivedToolResult {
        val agentRunInfo = getAgentRunInfoElementOrThrow()
        logger.info {
            formatLog(agentRunInfo.runId, "Executing tool: ${toolCall.tool}")
        }

        val environmentToolResult = processToolCall(toolCall)

        logger.debug {
            formatLog(agentRunInfo.runId, "Received tool result (\ntool: ${toolCall.tool},\nresult: ${environmentToolResult.result},\ncontent: ${environmentToolResult.content}\n)")
        }

        return environmentToolResult
    }

    override suspend fun reportProblem(exception: Throwable) {
        val agentRunInfo = getAgentRunInfoElementOrThrow()

        logger.error(exception) {
            formatLog(agentRunInfo.runId, "Agent reports a problem: ${exception.message}")
        }
        throw exception
    }

    @OptIn(InternalAgentToolsApi::class)
    private suspend fun processToolCall(toolCall: Message.Tool.Call): ReceivedToolResult {
        logger.debug { "Handling tool call sent by server..." }

        val runId = getAgentRunInfoElementOrThrow().runId
        val id = toolCall.id
        val toolName = toolCall.tool

        // Tool
        val tool = toolRegistry.getToolOrNull(toolName)
            ?: run {
                logger.error { formatLog(runId, "Tool with name '$toolName' not found in the tool registry.") }
                return ReceivedToolResult(
                    id = id,
                    tool = toolName,
                    content = "Tool with name '$toolName' not found in the tool registry. Use one of the available tools.",
                    result = null,
                )
            }

        // Tool Args
        val toolArgsJson = toolCall.contentJson
        val toolArgs = try {
            tool.decodeArgs(toolArgsJson)
        } catch (e: Exception) {
            logger.error(e) { formatLog(runId, "Tool with name '$toolName' failed to parse arguments: $toolArgsJson") }
            return ReceivedToolResult(
                id = id,
                tool = toolName,
                content = "Tool with name '$toolName' failed to parse arguments due to the error: ${e.message}",
                result = null,
            )
        }

        pipeline.onToolCallStarting(runId, id, tool, toolArgs)

        val toolResult = try {
            @Suppress("UNCHECKED_CAST")
            (tool as Tool<Any?, Any?>).execute(toolArgs)
        } catch (e: ToolException) {
            pipeline.onToolValidationFailed(runId, id, tool, toolArgs, e.message)

            return ReceivedToolResult(
                id = id,
                tool = toolName,
                content = e.message,
                result = null,
            )
        } catch (e: Exception) {
            logger.error(e) { "Tool with name '$toolName' failed to execute with arguments: $toolArgs" }
            pipeline.onToolCallFailed(runId, id, tool, toolArgs, e)

            return ReceivedToolResult(
                id = id,
                tool = toolName,
                content = "Tool with name '$toolName' failed to execute due to the error: ${e.message}!",
                result = null
            )
        }

        logger.trace { "Completed execution of the tool '$toolName' with result: $toolResult" }
        pipeline.onToolCallCompleted(runId, id, tool, toolArgs, toolResult)

        return ReceivedToolResult(
            id = id,
            tool = toolName,
            content = tool.encodeResultToStringUnsafe(toolResult),
            result = tool.encodeResult(toolResult)
        )
    }

    private fun formatLog(runId: String, message: String): String =
        "[agent id: $agentId, run id: $runId] $message"
}
