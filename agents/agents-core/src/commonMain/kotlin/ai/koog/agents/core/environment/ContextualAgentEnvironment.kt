package ai.koog.agents.core.environment

import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.agent.execution.AgentExecutionInfo
import ai.koog.prompt.message.Message
import io.github.oshai.kotlinlogging.KotlinLogging

internal class ContextualAgentEnvironment(
    private val environment: AIAgentEnvironment,
    private val context: AIAgentContext,
) : AIAgentEnvironment {

    companion object {
        private val logger = KotlinLogging.logger { }
    }

    override suspend fun executeTool(toolCall: Message.Tool.Call): ReceivedToolResult {
        logger.trace {
            "Executing tool call (" +
                "run id: ${context.runId}, " +
                "tool call id: ${toolCall.id}, " +
                "tool: ${toolCall.tool}, " +
                "args: ${toolCall.contentJson})"
        }

        context.pipeline.onToolCallStarting(
            executionInfo = context.executionInfo,
            runId = context.runId,
            toolCallId = toolCall.id,
            toolName = toolCall.tool,
            toolDescription = context.llm.toolRegistry.getToolOrNull(toolCall.tool)?.descriptor?.description,
            toolArgs = toolCall.contentJson
        )

        val toolResult = environment.executeTool(toolCall)
        processToolResult(context.executionInfo, toolResult)

        logger.trace {
            "Tool call completed (" +
                "run id: ${context.runId}, " +
                "tool call id: ${toolCall.id}, " +
                "tool: ${toolCall.tool}, " +
                "tool description: ${toolResult.toolDescription}, " +
                "args: ${toolCall.contentJson}) " +
                "with result: $toolResult"
        }

        return toolResult
    }

    override suspend fun reportProblem(exception: Throwable) {
        environment.reportProblem(exception)
    }

    //region Private Methods

    private suspend fun processToolResult(
        executionInfo: AgentExecutionInfo,
        toolResult: ReceivedToolResult
    ) {
        when (val toolResultKind = toolResult.resultKind) {
            is ToolResultKind.Success -> {
                context.pipeline.onToolCallCompleted(
                    executionInfo = executionInfo,
                    runId = context.runId,
                    toolCallId = toolResult.id,
                    toolName = toolResult.tool,
                    toolDescription = toolResult.toolDescription,
                    toolArgs = toolResult.toolArgs,
                    toolResult = toolResult.result
                )
            }

            is ToolResultKind.Failure -> {
                context.pipeline.onToolCallFailed(
                    executionInfo = executionInfo,
                    runId = context.runId,
                    toolCallId = toolResult.id,
                    toolName = toolResult.tool,
                    toolDescription = toolResult.toolDescription,
                    toolArgs = toolResult.toolArgs,
                    message = toolResult.content,
                    error = toolResultKind.error
                )
            }

            is ToolResultKind.ValidationError -> {
                context.pipeline.onToolValidationFailed(
                    executionInfo = executionInfo,
                    runId = context.runId,
                    toolCallId = toolResult.id,
                    toolName = toolResult.tool,
                    toolDescription = toolResult.toolDescription,
                    toolArgs = toolResult.toolArgs,
                    message = toolResult.content,
                    error = toolResultKind.error
                )
            }
        }
    }

    //endregion Private Methods
}
