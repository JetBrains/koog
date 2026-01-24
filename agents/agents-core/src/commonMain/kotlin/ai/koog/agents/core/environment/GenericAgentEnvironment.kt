package ai.koog.agents.core.environment

import ai.koog.agents.core.feature.model.toAgentError
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolException
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.annotations.InternalAgentToolsApi
import ai.koog.prompt.message.Message
import io.github.oshai.kotlinlogging.KLogger
import kotlinx.serialization.json.JsonObject
import kotlin.coroutines.cancellation.CancellationException

/**
 * Represents base agent environment with generic abstractions.
 */
public class GenericAgentEnvironment(
    private val agentId: String,
    private val logger: KLogger,
    private val toolRegistry: ToolRegistry,
) : AIAgentEnvironment {

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

    @OptIn(InternalAgentToolsApi::class)
    private suspend fun processToolCall(toolCall: Message.Tool.Call): ReceivedToolResult {
        logger.debug { "Handling tool call sent by server..." }

        // Tool
        val id = toolCall.id
        val toolName = toolCall.tool
        val toolArgsJson = try {
            toolCall.contentJson
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return ReceivedToolResult(
                id = id,
                tool = toolName,
                toolArgs = JsonObject(emptyMap()),
                toolDescription = null,
                content = "Tool with name '$toolName' failed to parse arguments due to the error: ${e.message}",
                resultKind = ToolResultKind.Failure(e.toAgentError()),
                result = null,
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
                    content = "Tool with name '$toolName' not found in the tool registry. Use one of the available tools.",
                    resultKind = ToolResultKind.Failure(null),
                    result = null,
                )
            }

        val toolDescription = tool.descriptor.description

        // Tool Args
        val toolArgs = try {
            tool.decodeArgs(toolArgsJson)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error(e) { formatLog("Tool with name '$toolName' failed to parse arguments: $toolArgsJson") }
            return ReceivedToolResult(
                id = id,
                tool = toolName,
                toolArgs = toolArgsJson,
                toolDescription = toolDescription,
                content = "Tool with name '$toolName' failed to parse arguments due to the error: ${e.message}",
                resultKind = ToolResultKind.Failure(e.toAgentError()),
                result = null,
            )
        }

        val toolResult = try {
            @Suppress("UNCHECKED_CAST")
            (tool as Tool<Any?, Any?>).execute(toolArgs)
        } catch (e: CancellationException) {
            throw e
        } catch (e: ToolException) {
            return ReceivedToolResult(
                id = id,
                tool = toolName,
                toolArgs = toolArgsJson,
                toolDescription = toolDescription,
                content = e.message,
                resultKind = ToolResultKind.ValidationError(e.toAgentError()),
                result = null,
            )
        } catch (e: Exception) {
            logger.error(e) { "Tool with name '$toolName' failed to execute with arguments: $toolArgs" }

            return ReceivedToolResult(
                id = id,
                tool = toolName,
                toolArgs = toolArgsJson,
                toolDescription = toolDescription,
                content = "Tool with name '$toolName' failed to execute due to the error: ${e.message}!",
                resultKind = ToolResultKind.Failure(e.toAgentError()),
                result = null
            )
        }

        logger.trace { "Completed execution of the tool '$toolName' with result: $toolResult" }

        val (content, result) = try {
            tool.encodeResultToStringUnsafe(toolResult) to tool.encodeResult(toolResult)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error(e) { "Tool with name '$toolName' failed to encode result: $toolResult" }
            return ReceivedToolResult(
                id = id,
                tool = toolName,
                toolArgs = toolArgsJson,
                toolDescription = toolDescription,
                content = "Tool with name '$toolName' failed to serialize result due to the error: ${e.message}!",
                resultKind = ToolResultKind.Failure(e.toAgentError()),
                result = null
            )
        }

        return ReceivedToolResult(
            id = id,
            tool = toolName,
            toolArgs = toolArgsJson,
            toolDescription = toolDescription,
            content = content,
            resultKind = ToolResultKind.Success,
            result = result
        )
    }

    private fun formatLog(message: String): String =
        "(agent id: $agentId) $message"
}
