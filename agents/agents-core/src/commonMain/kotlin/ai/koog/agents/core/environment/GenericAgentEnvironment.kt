package ai.koog.agents.core.environment

import ai.koog.agents.core.agent.context.element.getAgentRunInfoElementOrThrow
import ai.koog.agents.core.environment.AIAgentEnvironmentUtils.mapToToolResult
import ai.koog.agents.core.feature.pipeline.AIAgentPipeline
import ai.koog.agents.core.model.message.AIAgentEnvironmentToolResultToAgentContent
import ai.koog.agents.core.model.message.AgentToolCallToEnvironmentContent
import ai.koog.agents.core.model.message.AgentToolCallsToEnvironmentMessage
import ai.koog.agents.core.model.message.EnvironmentToolResultMultipleToAgentMessage
import ai.koog.agents.core.model.message.EnvironmentToolResultToAgentContent
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolException
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.annotations.InternalAgentToolsApi
import ai.koog.prompt.message.Message
import io.github.oshai.kotlinlogging.KLogger
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.supervisorScope

internal class GenericAgentEnvironment(
    private val agentId: String,
    private val strategyId: String,
    private val logger: KLogger,
    private val toolRegistry: ToolRegistry,
    private val pipeline: AIAgentPipeline
) : AIAgentEnvironment {

    override suspend fun executeTools(toolCalls: List<Message.Tool.Call>): List<ReceivedToolResult> {
        val agentRunInfo = currentCoroutineContext().getAgentRunInfoElementOrThrow()
        logger.info {
            formatLog(
                agentRunInfo.agentId,
                agentRunInfo.runId,
                "Executing tools: [${toolCalls.joinToString(", ") { it.tool }}]"
            )
        }

        val message = AgentToolCallsToEnvironmentMessage(
            runId = agentRunInfo.runId,
            content = toolCalls.map { call ->
                AgentToolCallToEnvironmentContent(
                    agentId = agentId,
                    runId = agentRunInfo.runId,
                    toolCallId = call.id,
                    toolName = call.tool,
                    toolArgs = call.contentJson
                )
            }
        )

        val results = processToolCallMultiple(message).mapToToolResult()
        logger.debug {
            "Received results from tools call (" +
                "tools: [${toolCalls.joinToString(", ") { it.tool }}], " +
                "results: [${results.joinToString(", ") { it.resultString() }}])"
        }

        return results
    }

    private fun ReceivedToolResult.resultString(): String =
        toolRegistry.tools.firstOrNull { it.name == tool }?.encodeResultToStringUnsafe(result) ?: "null"

    override suspend fun reportProblem(exception: Throwable) {
        val agentRunInfo = currentCoroutineContext().getAgentRunInfoElementOrThrow()

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
        result: Any?
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
        val tool = toolRegistry.getTool(content.toolName)
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
            result = toolResult
        )
    }

    private suspend fun processToolCallMultiple(
        message: AgentToolCallsToEnvironmentMessage
    ): EnvironmentToolResultMultipleToAgentMessage {
        val results = supervisorScope {
            message.content
                .map { call -> async { processToolCall(call) } }
                .awaitAll()
        }

        return EnvironmentToolResultMultipleToAgentMessage(
            runId = message.runId,
            content = results
        )
    }

    private fun formatLog(agentId: String, runId: String, message: String): String =
        "[agent id: $agentId, run id: $runId] $message"
}
