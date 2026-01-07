package ai.koog.agents.core.environment

import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.agent.execution.AgentExecutionInfo
import ai.koog.prompt.message.Message
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Suppress("MissingKDocForPublicAPI")
public class ContextualAgentEnvironment(
    private val environment: AIAgentEnvironment,
    private val context: AIAgentContext,
) : AIAgentEnvironment {

    private companion object {
        private val logger = KotlinLogging.logger { }
    }

    override suspend fun executeTool(toolCall: Message.Tool.Call): ReceivedToolResult {
        @OptIn(ExperimentalUuidApi::class)
        val eventId = Uuid.random().toString()

        logger.trace {
            "Executing tool call (" +
                "event id: $eventId, " +
                "run id: ${context.runId}, " +
                "tool call id: ${toolCall.id}, " +
                "tool: ${toolCall.tool}, " +
                "args: ${toolCall.contentJson})"
        }

        context.pipeline.onToolCallStarting(
            eventId = eventId,
            executionInfo = context.executionInfo,
            runId = context.runId,
            toolCallId = toolCall.id,
            toolName = toolCall.tool,
            toolDescription = context.llm.toolRegistry.getToolOrNull(toolCall.tool)?.descriptor?.description,
            toolArgs = toolCall.contentJson,
            context = context
        )

        val toolResult = environment.executeTool(toolCall)
        processToolResult(eventId, context.executionInfo, toolResult)

        logger.trace {
            "Tool call completed (" +
                "event id: ${toolResult.id}, " +
                "execution info: ${context.executionInfo.path()}, " +
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
        eventId: String,
        executionInfo: AgentExecutionInfo,
        toolResult: ReceivedToolResult
    ) {
        when (val toolResultKind = toolResult.resultKind) {
            is ToolResultKind.Success -> {
                context.pipeline.onToolCallCompleted(
                    eventId = eventId,
                    executionInfo = executionInfo,
                    runId = context.runId,
                    toolCallId = toolResult.id,
                    toolName = toolResult.tool,
                    toolDescription = toolResult.toolDescription,
                    toolArgs = toolResult.toolArgs,
                    toolResult = toolResult.result,
                    context = context
                )
            }

            is ToolResultKind.Failure -> {
                context.pipeline.onToolCallFailed(
                    eventId = eventId,
                    executionInfo = executionInfo,
                    runId = context.runId,
                    toolCallId = toolResult.id,
                    toolName = toolResult.tool,
                    toolDescription = toolResult.toolDescription,
                    toolArgs = toolResult.toolArgs,
                    message = toolResult.content,
                    error = toolResultKind.error,
                    context = context
                )
            }

            is ToolResultKind.ValidationError -> {
                context.pipeline.onToolValidationFailed(
                    eventId = eventId,
                    executionInfo = executionInfo,
                    runId = context.runId,
                    toolCallId = toolResult.id,
                    toolName = toolResult.tool,
                    toolDescription = toolResult.toolDescription,
                    toolArgs = toolResult.toolArgs,
                    message = toolResult.content,
                    error = toolResultKind.error,
                    context = context
                )
            }
        }
    }

    //endregion Private Methods
}
