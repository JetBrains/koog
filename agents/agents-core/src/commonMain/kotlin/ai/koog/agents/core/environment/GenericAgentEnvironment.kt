package ai.koog.agents.core.environment

import ai.koog.agents.core.tools.ToolBase
import ai.koog.agents.core.tools.ToolCallMetadata
import ai.koog.agents.core.tools.ToolException
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.annotations.InternalAgentToolsApi
import ai.koog.prompt.message.MessagePart
import ai.koog.serialization.JSONObject
import ai.koog.serialization.JSONSerializer
import ai.koog.serialization.kotlinx.toKoogJSONObject
import io.github.oshai.kotlinlogging.KLogger
import kotlin.coroutines.cancellation.CancellationException

/**
 * Represents base agent environment with generic abstractions.
 */
public class GenericAgentEnvironment(
    private val agentId: String,
    private val logger: KLogger,
    private val toolRegistry: ToolRegistry,
    private val serializer: JSONSerializer,
) : AIAgentEnvironment {

    override suspend fun executeTool(toolCall: MessagePart.Tool.Call): ReceivedToolResult =
        executeTool(toolCall, ToolCallMetadata.EMPTY)

    override suspend fun executeTool(
        toolCall: MessagePart.Tool.Call,
        metadata: ToolCallMetadata,
    ): ReceivedToolResult {
        logger.info {
            formatLog("Executing tool (name: ${toolCall.tool}, args: ${toolCall.args}")
        }

        val environmentToolResult = processToolCall(toolCall, metadata)

        logger.debug {
            formatLog("Received tool result (\ntool: ${toolCall.tool},\nresult: ${environmentToolResult.result},\ncontent: ${environmentToolResult.output}\n)")
        }

        return environmentToolResult
    }

    override suspend fun reportProblem(exception: Throwable) {
        logger.error(exception) {
            formatLog("Agent report a problem: ${exception.message}")
        }
        throw exception
    }

    @OptIn(InternalAgentToolsApi::class)
    private suspend fun processToolCall(
        toolCall: MessagePart.Tool.Call,
        metadata: ToolCallMetadata,
    ): ReceivedToolResult {
        logger.debug { "Handling tool call sent by server..." }

        // Tool
        val id = toolCall.id
        val toolName = toolCall.tool
        val toolArgsJson = try {
            toolCall.argsJson.toKoogJSONObject()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return ReceivedToolResult(
                id = id,
                tool = toolName,
                toolArgs = JSONObject(emptyMap()),
                toolDescription = null,
                output = "Tool with name '$toolName' failed to parse arguments due to the error: ${e.message}",
                resultKind = ToolResultKind.Failure(e),
                result = null,
                resultObject = null
            )
        }

        val tool = toolRegistry.getToolOrNull(toolName)
            ?: run {
                logger.error { formatLog("Tool with name '$toolName' not found in the tool registry.") }
                return ReceivedToolResult(
                    id = id,
                    tool = toolName,
                    toolArgs = toolArgsJson,
                    toolDescription = null,
                    output = "Tool with name '$toolName' not found in the tool registry. Use one of the available tools.",
                    resultKind = ToolResultKind.Failure(null),
                    result = null,
                    resultObject = null
                )
            }

        val toolDescription = tool.descriptor.description

        // Tool Args
        val toolArgs = try {
            tool.decodeArgs(toolArgsJson, serializer)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error(e) { formatLog("Tool with name '$toolName' failed to parse arguments: $toolArgsJson") }
            return ReceivedToolResult(
                id = id,
                tool = toolName,
                toolArgs = toolArgsJson,
                toolDescription = toolDescription,
                output = "Tool with name '$toolName' failed to parse arguments due to the error: ${e.message}",
                resultKind = ToolResultKind.Failure(e),
                result = null,
                resultObject = null
            )
        }

        val toolResult = try {
            @Suppress("UNCHECKED_CAST")
            (tool as ToolBase<Any?, Any?>).execute(toolArgs, metadata)
        } catch (e: CancellationException) {
            throw e
        } catch (e: ToolException) {
            return ReceivedToolResult(
                id = id,
                tool = toolName,
                toolArgs = toolArgsJson,
                toolDescription = toolDescription,
                output = e.message,
                resultKind = ToolResultKind.ValidationError(e),
                result = null,
                resultObject = null
            )
        } catch (e: Exception) {
            logger.error(e) { "Tool with name '$toolName' failed to execute with arguments: $toolArgs" }

            return ReceivedToolResult(
                id = id,
                tool = toolName,
                toolArgs = toolArgsJson,
                toolDescription = toolDescription,
                output = "Tool with name '$toolName' failed to execute due to the error: ${e.message}!",
                resultKind = ToolResultKind.Failure(e),
                result = null,
                resultObject = null
            )
        }

        logger.trace { "Completed execution of the tool '$toolName' with result: $toolResult" }

        val (content, result, parts) = try {
            val content = tool.encodeResultToStringUnsafe(toolResult, serializer)
            val result = tool.encodeResult(toolResult, serializer)
            val parts = tool.encodeResultToPartsUnsafe(toolResult, serializer)
            Triple(content, result, parts)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error(e) { "Tool with name '$toolName' failed to encode result: $toolResult" }
            return ReceivedToolResult(
                id = id,
                tool = toolName,
                toolArgs = toolArgsJson,
                toolDescription = toolDescription,
                output = "Tool with name '$toolName' failed to serialize result due to the error: ${e.message}!",
                resultKind = ToolResultKind.Failure(e),
                result = null,
                resultObject = null
            )
        }

        return ReceivedToolResult(
            id = id,
            tool = toolName,
            toolArgs = toolArgsJson,
            toolDescription = toolDescription,
            output = content,
            resultKind = ToolResultKind.Success,
            result = result,
            resultObject = toolResult,
            parts = parts,
        )
    }

    private fun formatLog(message: String): String =
        "(agent id: $agentId) $message"
}
