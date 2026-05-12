package ai.koog.agents.core.environment

import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.agent.execution.AgentExecutionInfo
import ai.koog.agents.core.agent.tools.AgentContextAwareTool
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.tools.ToolCallMetadata
import ai.koog.prompt.message.Message
import ai.koog.serialization.JSONObject
import ai.koog.serialization.kotlinx.toKoogJSONObject
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.coroutines.cancellation.CancellationException
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Represents an AI agent environment that operates within the context of a specific agent framework.
 *
 * This class acts as a decorator over an existing [AIAgentEnvironment], augmenting operations with contextual
 * processing using the provided [AIAgentContext].
 *
 * Metadata handling: when [executeTool] is called with a [ToolCallMetadata] argument, this environment
 * collects metadata contributions from every feature that registered a handler via
 * [ai.koog.agents.core.feature.pipeline.AIAgentPipelineAPI.provideToolCallMetadata] and merges them with
 * the caller-supplied metadata. On key collision, the caller's value wins, so an explicit call-site
 * override is never silently replaced by a feature contribution. After the merge, the framework injects
 * the live [AIAgentContext] under [AgentContextAwareTool.AgentContextKey]; the framework's value always
 * wins over caller and feature entries so a tool always observes the real context driving the current
 * call. The merged metadata is then passed to the wrapped environment, which threads it into
 * [ai.koog.agents.core.tools.ToolBase.execute].
 *
 * @constructor Constructs a new instance of [ContextualAgentEnvironment] with a decorated [environment] and a
 * contextual [context].
 *
 * @param environment The underlying agent environment responsible for managing tool execution.
 * @param context The context that augments the environment with additional behavioral and execution information.
 */
@InternalAgentsApi
public class ContextualAgentEnvironment(
    private val environment: AIAgentEnvironment,
    private val context: AIAgentContext,
) : AIAgentEnvironment {

    private companion object {
        private val logger = KotlinLogging.logger { }
    }

    override suspend fun executeTool(toolCall: Message.Tool.Call): ReceivedToolResult =
        executeTool(toolCall, ToolCallMetadata.EMPTY)

    override suspend fun executeTool(
        toolCall: Message.Tool.Call,
        metadata: ToolCallMetadata,
    ): ReceivedToolResult {
        @OptIn(ExperimentalUuidApi::class)
        val eventId = Uuid.random().toString()
        val toolDescription = context.llm.toolRegistry.getToolOrNull(toolCall.tool)?.descriptor?.description

        val toolArgs = try {
            toolCall.contentJson.toKoogJSONObject()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error { "Failed to execute tool call with id '${toolCall.id}' while parsing args: ${e.message}" }

            val tool = toolCall.tool
            val toolArgs = JSONObject(emptyMap())
            val message = "Failed to parse tool arguments: ${e.message}"
            context.pipeline.onToolValidationFailed(
                eventId = eventId,
                executionInfo = context.executionInfo,
                context = context,
                runId = context.runId,
                toolCallId = toolCall.id,
                toolName = tool,
                toolDescription = toolDescription,
                toolArgs = toolArgs,
                message = message,
                error = e,
            )
            return ReceivedToolResult(
                id = toolCall.id,
                tool = tool,
                toolArgs = toolArgs,
                toolDescription = null,
                content = message,
                resultKind = ToolResultKind.ValidationError(e),
                result = null
            )
        }

        logger.trace {
            "Executing tool call (" +
                "event id: $eventId, " +
                "run id: ${context.runId}, " +
                "tool call id: ${toolCall.id}, " +
                "tool: ${toolCall.tool}, " +
                "args: $toolArgs)"
        }

        context.pipeline.onToolCallStarting(
            eventId = eventId,
            executionInfo = context.executionInfo,
            context = context,
            runId = context.runId,
            toolCallId = toolCall.id,
            toolName = toolCall.tool,
            toolDescription = toolDescription,
            toolArgs = toolArgs,
        )

        val featureMetadata = context.pipeline.collectToolCallMetadata(
            eventId = eventId,
            executionInfo = context.executionInfo,
            runId = context.runId,
            toolCallId = toolCall.id,
            toolName = toolCall.tool,
            toolDescription = toolDescription,
            toolArgs = toolArgs,
            context = context
        )

        // Caller-supplied metadata wins on key collision, so an explicit call-site override is never
        // silently replaced by a feature contribution. The framework's live AIAgentContext is then injected
        // under the reserved key so that tools always see the real context driving the current call.
        val mergedMetadata = featureMetadata + metadata +
            ToolCallMetadata.of(AgentContextAwareTool.AgentContextKey to context)

        val toolResult = environment.executeTool(toolCall, mergedMetadata)
        processToolResult(eventId, context.executionInfo, toolResult)

        logger.trace {
            "Tool call completed (" +
                "event id: ${toolResult.id}, " +
                "execution info: ${context.executionInfo.path()}, " +
                "run id: ${context.runId}, " +
                "tool call id: ${toolCall.id}, " +
                "tool: ${toolCall.tool}, " +
                "tool description: ${toolResult.toolDescription}, " +
                "args: $toolArgs) " +
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
                    context = context,
                    runId = context.runId,
                    toolCallId = toolResult.id,
                    toolName = toolResult.tool,
                    toolDescription = toolResult.toolDescription,
                    toolArgs = toolResult.toolArgs,
                    toolResult = toolResult.result,
                )
            }

            is ToolResultKind.Failure -> {
                context.pipeline.onToolCallFailed(
                    eventId = eventId,
                    executionInfo = executionInfo,
                    context = context,
                    runId = context.runId,
                    toolCallId = toolResult.id,
                    toolName = toolResult.tool,
                    toolDescription = toolResult.toolDescription,
                    toolArgs = toolResult.toolArgs,
                    message = toolResult.content,
                    error = toolResultKind.error,
                )
            }

            is ToolResultKind.ValidationError -> {
                context.pipeline.onToolValidationFailed(
                    eventId = eventId,
                    executionInfo = executionInfo,
                    context = context,
                    runId = context.runId,
                    toolCallId = toolResult.id,
                    toolName = toolResult.tool,
                    toolDescription = toolResult.toolDescription,
                    toolArgs = toolResult.toolArgs,
                    message = toolResult.content,
                    error = toolResultKind.error,
                )
            }
        }
    }

    //endregion Private Methods
}
