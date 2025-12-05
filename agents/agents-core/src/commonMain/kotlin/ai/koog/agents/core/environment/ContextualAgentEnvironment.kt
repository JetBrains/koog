package ai.koog.agents.core.environment

import ai.koog.agents.core.agent.context.AIAgentContext
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
        logger.trace { "Executing tool call (run id: ${context.runId}, tool call id: ${toolCall.id}, tool: ${toolCall.tool}, args: ${toolCall.contentJson})" }

        context.pipeline.onToolCallStarting(
            runId = context.runId,
            toolCallId = toolCall.id,
            toolName = toolCall.tool,
            toolArgs = toolCall.contentJson
        )

        val toolResult = environment.executeTool(toolCall)
        processToolResult(toolResult)

        logger.trace { "Tool call completed (run id: ${context.runId}, tool call id: ${toolCall.id}, tool: ${toolCall.tool}, args: ${toolCall.contentJson}) with result: $toolResult" }
        return toolResult
    }

    override suspend fun reportProblem(exception: Throwable) {
        environment.reportProblem(exception)
    }

    //region Private Methods

    private suspend fun processToolResult(
        toolResult: ReceivedToolResult
    ) {
        when (val toolResultKind = toolResult.resultKind) {
            is ToolResultKind.Success -> {
                context.pipeline.onToolCallCompleted(
                    runId = context.runId,
                    toolCallId = toolResult.id,
                    toolName = toolResult.tool,
                    toolArgs = toolResult.toolArgs,
                    toolDescription = toolResult.toolDescription,
                    toolResult = toolResult.result
                )
            }

            is ToolResultKind.Failure -> {
                context.pipeline.onToolCallFailed(
                    runId = context.runId,
                    toolCallId = toolResult.id,
                    toolName = toolResult.tool,
                    toolArgs = toolResult.toolArgs,
                    toolDescription = toolResult.toolDescription,
                    message = toolResult.content,
                    error = toolResultKind.error
                )
            }

            is ToolResultKind.ValidationError -> {
                context.pipeline.onToolValidationFailed(
                    runId = context.runId,
                    toolCallId = toolResult.id,
                    toolName = toolResult.tool,
                    toolArgs = toolResult.toolArgs,
                    toolDescription = toolResult.toolDescription,
                    message = toolResult.content,
                    error = toolResultKind.error
                )
            }
        }
    }

    //endregion Private Methods
}
