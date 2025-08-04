package ai.koog.agents.core.environment

import ai.koog.agents.core.agent.CancellationReason
import ai.koog.prompt.message.Message

/**
 * AIAgentEnvironment provides a mechanism for AI agents to interface with an external environment.
 * It offers methods for tool execution, error reporting, and sending termination messages.
 */
public interface AIAgentEnvironment {
    /**
     * Executes a list of tool calls and returns their corresponding results.
     *
     * @param toolCalls A list of tool call messages to be executed. Each message contains details about the tool,
     * its identifier, the request content, and associated metadata.
     * @return A list of results corresponding to the executed tool calls. Each result includes details such as
     * the tool name, identifier, response content, and associated metadata.
     */
    public suspend fun executeTools(toolCalls: List<Message.Tool.Call>): List<ReceivedToolResult>

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
     * Sends a termination notification for the specified agent run.
     *
     * This method provides a way to notify the environment that an agent execution
     * has been cancelled. Implementations can use this to send the appropriate
     * [EnvironmentToAgentTerminationMessage] or perform other cleanup operations.
     *
     * The default implementation is a no-op, allowing environments that don't need
     * termination notifications to work without modification.
     *
     * @param runId The unique identifier of the agent run being terminated
     * @param reason The reason for the termination
     * @param message Optional descriptive message about the termination
     */
    public suspend fun sendTermination(runId: String, reason: CancellationReason, message: String? = null) {
        // Default no-op implementation
        // Environments can override this to send EnvironmentToAgentTerminationMessage
        // or perform other termination-related operations
    }
}

/**
 * Executes a single tool call and retrieves the result.
 *
 * This method sends the specified tool call to the tool execution environment, processes it,
 * and returns the result of the tool call. It internally leverages `executeTools` to handle
 * the execution and retrieves the first result from the returned list of results.
 *
 * @param toolCall The tool call to be executed, represented as an instance of [Message.Tool.Call].
 * @return The result of the executed tool call, represented as [ReceivedToolResult].
 */
public suspend fun AIAgentEnvironment.executeTool(toolCall: Message.Tool.Call): ReceivedToolResult =
    executeTools(listOf(toolCall)).first()
