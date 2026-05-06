package ai.koog.agents.core.environment

import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.annotation.InternalAgentsApi
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
    /**
     * Attaches the freshly built [AIAgentContext] to this environment so that [executeTool]
     * implementations can dispatch pipeline events and route context-aware tools.
     *
     * Implementations that don't carry context (mocks, anonymous test envs) may keep the
     * default no-op. The framework calls this exactly once per agent run, before the first
     * tool call.
     */
    @InternalAgentsApi
    public fun attachContext(context: AIAgentContext) {
        // default: ignore
    }

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
}
