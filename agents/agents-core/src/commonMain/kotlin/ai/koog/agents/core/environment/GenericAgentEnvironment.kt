package ai.koog.agents.core.environment

import ai.koog.agents.core.agent.AIAgentContextAwareTool
import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolException
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.annotations.InternalAgentToolsApi
import ai.koog.prompt.message.Message
import ai.koog.serialization.JSONElement
import ai.koog.serialization.JSONObject
import ai.koog.serialization.JSONSerializer
import ai.koog.serialization.kotlinx.toKoogJSONObject
import io.github.oshai.kotlinlogging.KLogger
import kotlin.coroutines.cancellation.CancellationException
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * The single production [AIAgentEnvironment] implementation.
 *
 * Once an [AIAgentContext] is attached via [attachContext], this environment also dispatches
 * pipeline tool-call events ([ai.koog.agents.core.feature.pipeline.AIAgentPipeline.onToolCallStarting] /
 * `onToolCallCompleted` / `onToolCallFailed` / `onToolValidationFailed`) and dispatches tool execution
 * to [AIAgentContextAwareTool] for tools that opt into receiving the agent context.
 *
 * If no context is attached (e.g. when the env is constructed directly in unit tests), the
 * environment is functionally equivalent to a thin wrapper around the tool registry — no events
 * fire and tools are invoked through plain [Tool.execute].
 */
public class GenericAgentEnvironment(
    private val agentId: String,
    private val logger: KLogger,
    private val toolRegistry: ToolRegistry,
    private val serializer: JSONSerializer,
) : AIAgentEnvironment {

    private var context: AIAgentContext? = null

    @InternalAgentsApi
    override fun attachContext(context: AIAgentContext) {
        check(this.context == null) { "AIAgentContext is already attached to this environment" }
        this.context = context
    }

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

    @OptIn(ExperimentalUuidApi::class)
    private suspend fun processToolCall(toolCall: Message.Tool.Call): ReceivedToolResult {
        logger.debug { "Handling tool call sent by server..." }

        val ctx = ToolCallScope(
            agentContext = this.context,
            eventId = if (this.context != null) Uuid.random().toString() else "",
            toolCallId = toolCall.id,
            toolName = toolCall.tool,
        )

        val toolArgsJson = when (val step = parseRawArgs(toolCall, ctx)) {
            is Step.Stop -> return step.result
            is Step.Continue -> step.value
        }

        val tool = toolRegistry.getToolOrNull(ctx.toolName)
        val toolDescription = tool?.descriptor?.description
        fireToolCallStarting(ctx, toolDescription, toolArgsJson)

        if (tool == null) {
            logger.error { formatLog("Tool with name '${ctx.toolName}' not found in the tool registry.") }
            return finish(
                ctx,
                failureResult(
                    ctx = ctx,
                    toolArgs = toolArgsJson,
                    toolDescription = null,
                    content = "Tool with name '${ctx.toolName}' not found in the tool registry. Use one of the available tools.",
                    error = null,
                ),
            )
        }

        val toolArgs = when (val step = decodeToolArgs(tool, toolArgsJson, ctx, toolDescription)) {
            is Step.Stop -> return finish(ctx, step.result)
            is Step.Continue -> step.value
        }

        val toolResult = when (val step = runTool(tool, toolArgs, ctx, toolArgsJson, toolDescription)) {
            is Step.Stop -> return finish(ctx, step.result)
            is Step.Continue -> step.value
        }

        logger.trace { "Completed execution of the tool '${ctx.toolName}' with result: $toolResult" }

        val (content, result) = when (val step = encodeToolOutput(tool, toolResult, ctx, toolArgsJson, toolDescription)) {
            is Step.Stop -> return finish(ctx, step.result)
            is Step.Continue -> step.value
        }

        return finish(ctx, successResult(ctx, toolArgsJson, toolDescription, content, result))
    }

    //region Phases

    private suspend fun parseRawArgs(toolCall: Message.Tool.Call, ctx: ToolCallScope): Step<JSONObject> = try {
        Step.Continue(toolCall.contentJson.toKoogJSONObject())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        logger.error { formatLog("Failed to parse arguments for tool '${ctx.toolName}': ${e.message}") }
        val message = "Failed to parse tool arguments: ${e.message}"
        // Validation failed before `Starting` fired — dispatch directly, not through `finish`.
        ctx.agentContext?.let { agentContext ->
            dispatchValidationFailed(
                agentContext = agentContext,
                ctx = ctx,
                toolDescription = agentContext.llm.toolRegistry.getToolOrNull(ctx.toolName)?.descriptor?.description,
                toolArgs = JSONObject(emptyMap()),
                message = message,
                error = e,
            )
        }
        Step.Stop(
            ReceivedToolResult(
                id = ctx.toolCallId,
                tool = ctx.toolName,
                toolArgs = JSONObject(emptyMap()),
                toolDescription = null,
                content = message,
                resultKind = ToolResultKind.ValidationError(e),
                result = null,
            )
        )
    }

    private fun decodeToolArgs(
        tool: Tool<*, *>,
        toolArgsJson: JSONObject,
        ctx: ToolCallScope,
        toolDescription: String?,
    ): Step<Any?> = try {
        @Suppress("UNCHECKED_CAST")
        Step.Continue((tool as Tool<Any?, *>).decodeArgs(toolArgsJson, serializer))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        logger.error(e) { formatLog("Tool with name '${ctx.toolName}' failed to parse arguments: $toolArgsJson") }
        Step.Stop(
            failureResult(
                ctx = ctx,
                toolArgs = toolArgsJson,
                toolDescription = toolDescription,
                content = "Tool with name '${ctx.toolName}' failed to parse arguments due to the error: ${e.message}",
                error = e,
            )
        )
    }

    private suspend fun runTool(
        tool: Tool<*, *>,
        toolArgs: Any?,
        ctx: ToolCallScope,
        toolArgsJson: JSONObject,
        toolDescription: String?,
    ): Step<Any?> = try {
        Step.Continue(invokeToolImpl(tool, toolArgs, ctx.agentContext))
    } catch (e: CancellationException) {
        throw e
    } catch (e: ToolException) {
        Step.Stop(
            ReceivedToolResult(
                id = ctx.toolCallId,
                tool = ctx.toolName,
                toolArgs = toolArgsJson,
                toolDescription = toolDescription,
                content = e.message,
                resultKind = ToolResultKind.ValidationError(e),
                result = null,
            )
        )
    } catch (e: Exception) {
        logger.error(e) { "Tool with name '${ctx.toolName}' failed to execute with arguments: $toolArgs" }
        Step.Stop(
            failureResult(
                ctx = ctx,
                toolArgs = toolArgsJson,
                toolDescription = toolDescription,
                content = "Tool with name '${ctx.toolName}' failed to execute due to the error: ${e.message}!",
                error = e,
            )
        )
    }

    @OptIn(InternalAgentToolsApi::class)
    private fun encodeToolOutput(
        tool: Tool<*, *>,
        toolResult: Any?,
        ctx: ToolCallScope,
        toolArgsJson: JSONObject,
        toolDescription: String?,
    ): Step<Pair<String, JSONElement>> = try {
        @Suppress("UNCHECKED_CAST")
        val unsafeTool = tool as Tool<Any?, Any?>
        Step.Continue(
            unsafeTool.encodeResultToStringUnsafe(toolResult, serializer) to
                unsafeTool.encodeResult(toolResult, serializer)
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        logger.error(e) { "Tool with name '${ctx.toolName}' failed to encode result: $toolResult" }
        Step.Stop(
            failureResult(
                ctx = ctx,
                toolArgs = toolArgsJson,
                toolDescription = toolDescription,
                content = "Tool with name '${ctx.toolName}' failed to serialize result due to the error: ${e.message}!",
                error = e,
            )
        )
    }

    //endregion Phases

    //region Tool dispatch

    /**
     * Routes execution to [AIAgentContextAwareTool.execute] when the tool opts in and a context is
     * attached, otherwise falls back to the plain [Tool.execute].
     */
    private suspend fun invokeToolImpl(tool: Tool<*, *>, toolArgs: Any?, context: AIAgentContext?): Any? =
        if (tool is AIAgentContextAwareTool<*, *> && context != null) {
            @Suppress("UNCHECKED_CAST")
            (tool as AIAgentContextAwareTool<Any?, Any?>).execute(toolArgs, context)
        } else {
            @Suppress("UNCHECKED_CAST")
            (tool as Tool<Any?, Any?>).execute(toolArgs)
        }

    //endregion Tool dispatch

    //region Result builders

    private fun failureResult(
        ctx: ToolCallScope,
        toolArgs: JSONObject,
        toolDescription: String?,
        content: String,
        error: Throwable?,
    ): ReceivedToolResult = ReceivedToolResult(
        id = ctx.toolCallId,
        tool = ctx.toolName,
        toolArgs = toolArgs,
        toolDescription = toolDescription,
        content = content,
        resultKind = ToolResultKind.Failure(error),
        result = null,
    )

    private fun successResult(
        ctx: ToolCallScope,
        toolArgs: JSONObject,
        toolDescription: String?,
        content: String,
        result: JSONElement,
    ): ReceivedToolResult = ReceivedToolResult(
        id = ctx.toolCallId,
        tool = ctx.toolName,
        toolArgs = toolArgs,
        toolDescription = toolDescription,
        content = content,
        resultKind = ToolResultKind.Success,
        result = result,
    )

    //endregion Result builders

    //region Pipeline events

    @OptIn(InternalAgentsApi::class)
    private suspend fun fireToolCallStarting(
        ctx: ToolCallScope,
        toolDescription: String?,
        toolArgs: JSONObject,
    ) {
        val agentContext = ctx.agentContext ?: return
        agentContext.pipeline.onToolCallStarting(
            eventId = ctx.eventId,
            executionInfo = agentContext.executionInfo,
            runId = agentContext.runId,
            toolCallId = ctx.toolCallId,
            toolName = ctx.toolName,
            toolDescription = toolDescription,
            toolArgs = toolArgs,
            context = agentContext,
        )
    }

    @OptIn(InternalAgentsApi::class)
    private suspend fun finish(ctx: ToolCallScope, result: ReceivedToolResult): ReceivedToolResult {
        val agentContext = ctx.agentContext ?: return result
        when (val kind = result.resultKind) {
            is ToolResultKind.Success -> agentContext.pipeline.onToolCallCompleted(
                eventId = ctx.eventId,
                executionInfo = agentContext.executionInfo,
                runId = agentContext.runId,
                toolCallId = result.id,
                toolName = result.tool,
                toolDescription = result.toolDescription,
                toolArgs = result.toolArgs,
                toolResult = result.result,
                context = agentContext,
            )

            is ToolResultKind.Failure -> agentContext.pipeline.onToolCallFailed(
                eventId = ctx.eventId,
                executionInfo = agentContext.executionInfo,
                runId = agentContext.runId,
                toolCallId = result.id,
                toolName = result.tool,
                toolDescription = result.toolDescription,
                toolArgs = result.toolArgs,
                message = result.content,
                error = kind.error,
                context = agentContext,
            )

            is ToolResultKind.ValidationError -> dispatchValidationFailed(
                agentContext = agentContext,
                ctx = ctx,
                toolDescription = result.toolDescription,
                toolArgs = result.toolArgs,
                message = result.content,
                error = kind.error,
            )
        }
        return result
    }

    @OptIn(InternalAgentsApi::class)
    private suspend fun dispatchValidationFailed(
        agentContext: AIAgentContext,
        ctx: ToolCallScope,
        toolDescription: String?,
        toolArgs: JSONObject,
        message: String,
        error: Throwable,
    ) {
        agentContext.pipeline.onToolValidationFailed(
            eventId = ctx.eventId,
            executionInfo = agentContext.executionInfo,
            runId = agentContext.runId,
            toolCallId = ctx.toolCallId,
            toolName = ctx.toolName,
            toolDescription = toolDescription,
            toolArgs = toolArgs,
            message = message,
            error = error,
            context = agentContext,
        )
    }

    //endregion Pipeline events

    private fun formatLog(message: String): String =
        "(agent id: $agentId) $message"

    /**
     * Bag of values that every phase of [processToolCall] needs. Keeping them in one struct keeps
     * helper signatures short.
     */
    private data class ToolCallScope(
        val agentContext: AIAgentContext?,
        val eventId: String,
        val toolCallId: String?,
        val toolName: String,
    )

    /**
     * Outcome of an individual phase: either a value to feed into the next phase, or a final
     * [ReceivedToolResult] that short-circuits the pipeline.
     */
    private sealed interface Step<out T> {
        data class Continue<T>(val value: T) : Step<T>
        data class Stop(val result: ReceivedToolResult) : Step<Nothing>
    }
}
