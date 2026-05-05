package ai.koog.agents.testing.tools

import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.environment.AIAgentEnvironment
import ai.koog.agents.core.environment.GenericAgentEnvironment
import ai.koog.agents.core.environment.ReceivedToolResult
import ai.koog.agents.core.environment.ToolResultKind
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.annotations.InternalAgentToolsApi
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.message.Message
import ai.koog.serialization.JSONElement
import ai.koog.serialization.JSONObject
import ai.koog.serialization.JSONSerializer
import ai.koog.serialization.kotlinx.toKoogJSONObject
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * A mock implementation of [AIAgentEnvironment] used for testing agent behavior.
 *
 * Intercepts mock-matched tool calls and returns the configured stub result. Non-matched calls
 * execute the actual tool from the registry. When wrapping a [GenericAgentEnvironment], pipeline
 * lifecycle events ([onToolCallStarting], [onToolCallCompleted]) are fired so event-listening
 * features (e.g. EventHandler) observe mocked tool calls. Tool execution exceptions propagate to
 * the caller — matching the historical behavior tests rely on for asserting agent error handling.
 *
 * @property toolRegistry The registry containing all available tools for the agent
 * @property promptExecutor The executor for handling prompts, typically a [MockPromptExecutor]
 * @property baseEnvironment Optional base environment, used to discover the surrounding
 *   [AIAgentContext] so pipeline events can fire.
 */
@OptIn(InternalAgentToolsApi::class, InternalAgentsApi::class)
public class MockEnvironment(
    internal val toolRegistry: ToolRegistry,
    internal val promptExecutor: PromptExecutor,
    internal val serializer: JSONSerializer,
    internal val baseEnvironment: AIAgentEnvironment? = null
) : AIAgentEnvironment {

    private val context: AIAgentContext? = (baseEnvironment as? GenericAgentEnvironment)?.context

    public override suspend fun executeTools(toolCalls: List<Message.Tool.Call>): List<ReceivedToolResult> {
        return toolCalls.map { executeTool(it) }
    }

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun executeTool(toolCall: Message.Tool.Call): ReceivedToolResult {
        if (promptExecutor is MockPromptExecutor) {
            val matchedAction = promptExecutor.toolActions.find { it.satisfies(toolCall) }
            if (matchedAction != null) {
                val tool: Tool<*, *> = toolRegistry.getTool(toolCall.tool)
                val toolArgs = toolCall.contentJson.toKoogJSONObject()
                val toolDescription = tool.descriptor.description
                val eventId = Uuid.random().toString()

                fireStarting(eventId, toolCall, toolArgs, toolDescription)

                val (result, content) = matchedAction.invokeAndSerialize(toolCall)
                val encodedResult = tool.encodeResultUnsafe(result, serializer)

                fireCompleted(eventId, toolCall, toolArgs, toolDescription, encodedResult)

                return ReceivedToolResult(
                    id = toolCall.id,
                    tool = toolCall.tool,
                    toolArgs = toolArgs,
                    toolDescription = toolDescription,
                    content = content,
                    resultKind = ToolResultKind.Success,
                    result = encodedResult,
                )
            }
        }

        // Non-mock path. Resolve the tool (throws if missing — matching prior behavior so callers
        // can observe the failure as a thrown exception) and execute it directly. Tool exceptions
        // propagate up to the caller. Pipeline events fire only on successful completion, again
        // matching prior behavior where ContextualAgentEnvironment never fired a failed event for
        // a propagating exception.
        val tool = toolRegistry.getTool(toolCall.tool)
        val toolArgs = toolCall.contentJson.toKoogJSONObject()
        val toolDescription = tool.descriptor.description
        val eventId = Uuid.random().toString()

        fireStarting(eventId, toolCall, toolArgs, toolDescription)

        val args = tool.decodeArgs(toolArgs, serializer)
        val result = tool.executeUnsafe(args)
        val encodedResult = tool.encodeResultUnsafe(result, serializer)

        fireCompleted(eventId, toolCall, toolArgs, toolDescription, encodedResult)

        return ReceivedToolResult(
            id = toolCall.id,
            tool = toolCall.tool,
            toolArgs = toolArgs,
            toolDescription = toolDescription,
            content = tool.encodeResultToStringUnsafe(result, serializer),
            resultKind = ToolResultKind.Success,
            result = encodedResult,
        )
    }

    /**
     * Reports a problem by throwing the exception.
     *
     * @param exception The exception to the report
     * @throws Throwable The same exception that was passed in
     */
    override suspend fun reportProblem(exception: Throwable) {
        throw exception
    }

    private suspend fun fireStarting(
        eventId: String,
        toolCall: Message.Tool.Call,
        toolArgs: JSONObject,
        toolDescription: String?,
    ) {
        val ctx = context ?: return
        ctx.pipeline.onToolCallStarting(
            eventId = eventId,
            executionInfo = ctx.executionInfo,
            runId = ctx.runId,
            toolCallId = toolCall.id,
            toolName = toolCall.tool,
            toolDescription = toolDescription,
            toolArgs = toolArgs,
            context = ctx,
        )
    }

    private suspend fun fireCompleted(
        eventId: String,
        toolCall: Message.Tool.Call,
        toolArgs: JSONObject,
        toolDescription: String?,
        encodedResult: JSONElement?,
    ) {
        val ctx = context ?: return
        ctx.pipeline.onToolCallCompleted(
            eventId = eventId,
            executionInfo = ctx.executionInfo,
            runId = ctx.runId,
            toolCallId = toolCall.id,
            toolName = toolCall.tool,
            toolDescription = toolDescription,
            toolArgs = toolArgs,
            toolResult = encodedResult,
            context = ctx,
        )
    }
}
