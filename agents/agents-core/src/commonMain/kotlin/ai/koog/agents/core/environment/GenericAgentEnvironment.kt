package ai.koog.agents.core.environment

import ai.koog.agents.core.agent.AIAgentContextAwareTool
import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.feature.model.toAgentError
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolException
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.annotations.InternalAgentToolsApi
import ai.koog.prompt.message.Message
import ai.koog.serialization.JSONObject
import ai.koog.serialization.JSONSerializer
import ai.koog.serialization.kotlinx.toKoogJSONObject
import io.github.oshai.kotlinlogging.KLogger
import kotlin.coroutines.cancellation.CancellationException
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Default [AIAgentEnvironment] implementation.
 *
 * Decodes arguments, dispatches tools (including [AIAgentContextAwareTool]s), and emits
 * pipeline lifecycle events (`onToolCallStarting` / `onToolCallCompleted` / `onToolCallFailed` /
 * `onToolValidationFailed`) using the supplied [context].
 */
@OptIn(InternalAgentsApi::class)
public class GenericAgentEnvironment(
    private val logger: KLogger,
    private val toolRegistry: ToolRegistry,
    private val serializer: JSONSerializer,
    @property:InternalAgentsApi public val context: AIAgentContext,
) : AIAgentEnvironment {

    private val agentId: String get() = context.agentId

    override suspend fun executeTool(toolCall: Message.Tool.Call): ReceivedToolResult {
        logger.info {
            formatLog("Executing tool (name: ${toolCall.tool}, args: ${toolCall.contentJsonResult.getOrElse { "Failed to parse tool arguments: ${it.message}" }})")
        }

        val environmentToolResult = processToolCall(toolCall)

        logger.debug {
            formatLog("Received tool result (\ntool: ${toolCall.tool},\nresult: ${environmentToolResult.result},\ncontent: ${environmentToolResult.content}\n)")
        }

        return environmentToolResult
    }

    override suspend fun reportProblem(exception: Throwable) {
        logger.error(exception) {
            formatLog("Agent report a problem: ${exception.message}")
        }
        throw exception
    }

    @OptIn(InternalAgentToolsApi::class, InternalAgentsApi::class, ExperimentalUuidApi::class)
    private suspend fun processToolCall(toolCall: Message.Tool.Call): ReceivedToolResult {
        logger.debug { "Handling tool call sent by server..." }

        val eventId = Uuid.random().toString()
        val callId = toolCall.id
        val toolName = toolCall.tool

        // Step 1: parse raw JSON args.
        val toolArgsJson = try {
            toolCall.contentJson.toKoogJSONObject()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val message = "Failed to parse tool arguments: ${e.message}"
            context.pipeline.onToolValidationFailed(
                eventId = eventId,
                executionInfo = context.executionInfo,
                runId = context.runId,
                toolCallId = callId,
                toolName = toolName,
                toolDescription = null,
                toolArgs = JSONObject(emptyMap()),
                message = message,
                error = e.toAgentError(),
                context = context,
            )
            return ReceivedToolResult(
                id = callId,
                tool = toolName,
                toolArgs = JSONObject(emptyMap()),
                toolDescription = null,
                content = message,
                resultKind = ToolResultKind.Failure(e.toAgentError()),
                result = null,
            )
        }

        // Step 2: look up the tool.
        @Suppress("UNCHECKED_CAST")
        val tool = (toolRegistry.getToolOrNull(toolName) as? Tool<Any?, Any?>)
            ?: run {
                logger.error { formatLog("Tool with name '$toolName' not found in the tool registry.") }
                val message = "Tool with name '$toolName' not found in the tool registry. Use one of the available tools."
                context.pipeline.onToolCallStarting(
                    eventId = eventId,
                    executionInfo = context.executionInfo,
                    runId = context.runId,
                    toolCallId = callId,
                    toolName = toolName,
                    toolDescription = null,
                    toolArgs = toolArgsJson,
                    context = context,
                )
                context.pipeline.onToolCallFailed(
                    eventId = eventId,
                    executionInfo = context.executionInfo,
                    runId = context.runId,
                    toolCallId = callId,
                    toolName = toolName,
                    toolDescription = null,
                    toolArgs = toolArgsJson,
                    message = message,
                    error = null,
                    context = context,
                )
                return ReceivedToolResult(
                    id = callId,
                    tool = toolName,
                    toolArgs = toolArgsJson,
                    toolDescription = null,
                    content = message,
                    resultKind = ToolResultKind.Failure(null),
                    result = null,
                )
            }

        val toolDescription = tool.descriptor.description

        // Step 3: decode typed args.
        val toolArgs = try {
            tool.decodeArgs(toolArgsJson, serializer)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error(e) { formatLog("Tool with name '$toolName' failed to parse arguments: $toolArgsJson") }
            val message = "Tool with name '$toolName' failed to parse arguments due to the error: ${e.message}"
            context.pipeline.onToolCallStarting(
                eventId = eventId,
                executionInfo = context.executionInfo,
                runId = context.runId,
                toolCallId = callId,
                toolName = toolName,
                toolDescription = toolDescription,
                toolArgs = toolArgsJson,
                context = context,
            )
            context.pipeline.onToolCallFailed(
                eventId = eventId,
                executionInfo = context.executionInfo,
                runId = context.runId,
                toolCallId = callId,
                toolName = toolName,
                toolDescription = toolDescription,
                toolArgs = toolArgsJson,
                message = message,
                error = e.toAgentError(),
                context = context,
            )
            return ReceivedToolResult(
                id = callId,
                tool = toolName,
                toolArgs = toolArgsJson,
                toolDescription = toolDescription,
                content = message,
                resultKind = ToolResultKind.Failure(e.toAgentError()),
                result = null,
            )
        }

        // Step 4: fire start, dispatch tool (context-aware if applicable).
        context.pipeline.onToolCallStarting(
            eventId = eventId,
            executionInfo = context.executionInfo,
            runId = context.runId,
            toolCallId = callId,
            toolName = toolName,
            toolDescription = toolDescription,
            toolArgs = toolArgsJson,
            context = context,
        )

        val toolResult = try {
            if (tool is AIAgentContextAwareTool<*, *>) {
                @Suppress("UNCHECKED_CAST")
                (tool as AIAgentContextAwareTool<Any?, Any?>).execute(toolArgs, context)
            } else {
                tool.execute(toolArgs)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: ToolException) {
            context.pipeline.onToolValidationFailed(
                eventId = eventId,
                executionInfo = context.executionInfo,
                runId = context.runId,
                toolCallId = callId,
                toolName = toolName,
                toolDescription = toolDescription,
                toolArgs = toolArgsJson,
                message = e.message,
                error = e.toAgentError(),
                context = context,
            )
            return ReceivedToolResult(
                id = callId,
                tool = toolName,
                toolArgs = toolArgsJson,
                toolDescription = toolDescription,
                content = e.message,
                resultKind = ToolResultKind.ValidationError(e.toAgentError()),
                result = null,
            )
        } catch (e: Exception) {
            logger.error(e) { "Tool with name '$toolName' failed to execute with arguments: $toolArgs" }
            val message = "Tool with name '$toolName' failed to execute due to the error: ${e.message}!"
            context.pipeline.onToolCallFailed(
                eventId = eventId,
                executionInfo = context.executionInfo,
                runId = context.runId,
                toolCallId = callId,
                toolName = toolName,
                toolDescription = toolDescription,
                toolArgs = toolArgsJson,
                message = message,
                error = e.toAgentError(),
                context = context,
            )
            return ReceivedToolResult(
                id = callId,
                tool = toolName,
                toolArgs = toolArgsJson,
                toolDescription = toolDescription,
                content = message,
                resultKind = ToolResultKind.Failure(e.toAgentError()),
                result = null,
            )
        }

        logger.trace { "Completed execution of the tool '$toolName' with result: $toolResult" }

        // Step 5: encode result.
        val (content, result) = try {
            tool.encodeResultToStringUnsafe(toolResult, serializer) to
                tool.encodeResult(toolResult, serializer)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error(e) { "Tool with name '$toolName' failed to encode result: $toolResult" }
            val message = "Tool with name '$toolName' failed to serialize result due to the error: ${e.message}!"
            context.pipeline.onToolCallFailed(
                eventId = eventId,
                executionInfo = context.executionInfo,
                runId = context.runId,
                toolCallId = callId,
                toolName = toolName,
                toolDescription = toolDescription,
                toolArgs = toolArgsJson,
                message = message,
                error = e.toAgentError(),
                context = context,
            )
            return ReceivedToolResult(
                id = callId,
                tool = toolName,
                toolArgs = toolArgsJson,
                toolDescription = toolDescription,
                content = message,
                resultKind = ToolResultKind.Failure(e.toAgentError()),
                result = null,
            )
        }

        context.pipeline.onToolCallCompleted(
            eventId = eventId,
            executionInfo = context.executionInfo,
            runId = context.runId,
            toolCallId = callId,
            toolName = toolName,
            toolDescription = toolDescription,
            toolArgs = toolArgsJson,
            toolResult = result,
            context = context,
        )

        return ReceivedToolResult(
            id = callId,
            tool = toolName,
            toolArgs = toolArgsJson,
            toolDescription = toolDescription,
            content = content,
            resultKind = ToolResultKind.Success,
            result = result,
        )
    }

    private fun formatLog(message: String): String =
        "(agent id: $agentId) $message"
}
