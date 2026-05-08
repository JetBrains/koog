package ai.koog.agents.core.environment

import ai.koog.agents.core.tools.ToolCallMetadata
import ai.koog.prompt.message.Message
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope

/**
 * AIAgentEnvironment provides a mechanism for AI agents to interface with an external environment.
 * It offers methods for tool execution, error reporting, and sending termination messages.
 */
public interface AIAgentEnvironment {

    /**
     * Executes a tool call and returns its result.
     *
     * @param toolCall A tool call messages to be executed. A message contains details about the tool,
     *        its identifier, the request content, and associated metadata.
     * @return A result corresponding to the executed tool call. The result includes details such as
     *         the tool name, identifier, response content, and associated metadata.
     */
    public suspend fun executeTool(toolCall: Message.Tool.Call): ReceivedToolResult

    /**
     * Executes a tool call with caller-supplied [metadata] and returns its result.
     *
     * [metadata] is an additive side channel that travels alongside the call (e.g. a trace span id,
     * a correlation id); it is not embedded in [Message.Tool.Call] and is not serialized to the LLM.
     *
     * The default implementation delegates to the single-argument [executeTool] overload and discards
     * [metadata], so existing environment implementations remain source-compatible. A custom environment
     * that overrides only that single-argument overload inherits this default and silently drops any
     * [metadata] supplied here; override this overload to propagate [metadata] to
     * [ai.koog.agents.core.tools.Tool.execute].
     *
     * @param toolCall The tool call to execute.
     * @param metadata Caller- and feature-contributed per-call context.
     * @return A result corresponding to the executed tool call.
     */
    public suspend fun executeTool(
        toolCall: Message.Tool.Call,
        metadata: ToolCallMetadata,
    ): ReceivedToolResult = executeTool(toolCall)

    /**
     * Reports a problem that occurred within the environment.
     *
     * This method is used to handle exceptions or other issues encountered during
     * the execution of operations within the AI agent environment. The provided exception
     * describes the nature of the problem.
     *
     * @param exception The exception representing the problem to report.
     */
    public suspend fun reportProblem(exception: Throwable)

    /**
     * Executes a batch of tool calls within the AI agent environment and processes their results.
     *
     * This method takes a list of tool call messages, processes them by sending appropriate requests
     * to the underlying environment, and returns a list of results corresponding to the tool calls.
     *
     * @param toolCalls A list of tool call messages to be executed. Each message contains details
     *        about the tool, its identifier, the request content, and associated metadata.
     * @return A list of results corresponding to the executed tool calls. Each result includes details
     *         such as the tool name, identifier, response content, and metadata.
     */
    public suspend fun executeTools(toolCalls: List<Message.Tool.Call>): List<ReceivedToolResult> {
        val results = supervisorScope {
            toolCalls
                .map { toolCall ->
                    async { executeTool(toolCall) }
                }
                .awaitAll()
        }

        return results
    }

    /**
     * Executes a batch of tool calls with shared caller-supplied [metadata] and returns their results.
     *
     * The same [metadata] is passed to every call in the batch.
     *
     * @param toolCalls A list of tool call messages to be executed.
     * @param metadata Caller- and feature-contributed per-call context.
     * @return A list of results corresponding to the executed tool calls.
     */
    public suspend fun executeTools(
        toolCalls: List<Message.Tool.Call>,
        metadata: ToolCallMetadata,
    ): List<ReceivedToolResult> {
        val results = supervisorScope {
            toolCalls
                .map { toolCall ->
                    async { executeTool(toolCall, metadata) }
                }
                .awaitAll()
        }

        return results
    }
}
