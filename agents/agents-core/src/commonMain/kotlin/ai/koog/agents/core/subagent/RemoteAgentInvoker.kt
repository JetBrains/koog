package ai.koog.agents.core.subagent

import kotlin.reflect.KType
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Transport-agnostic interface for invoking remote agents.
 * 
 * This interface abstracts the mechanism of invoking agents across different
 * transports: HTTP, gRPC, message queues, etc. The safety mechanisms are
 * handled at the agent level via SafeAgentWrapper, not at the transport level.
 */
public interface RemoteAgentInvoker {
    /**
     * Invokes a remote agent with the specified parameters.
     * 
     * @param spec The specification of the remote agent to invoke
     * @param input The input data to pass to the agent
     * @return The result of the agent execution
     */
    public suspend fun <I : Any, O : Any> invoke(
        spec: RemoteAgentSpec<I, O>,
        input: I
    ): SubagentResult<O>
}

/**
 * Simplified specification for remote agent invocation.
 * 
 * @param agentId Unique identifier for the target agent
 * @param inputType The expected input type (for serialization)
 * @param outputType The expected output type (for deserialization)
 */
public data class RemoteAgentSpec<I : Any, O : Any>(
    val agentId: String,
    val inputType: KType,
    val outputType: KType
)

// Safety policies moved to SafeAgentExecution.kt as AgentSafetyPolicy
// Context tracking will be handled via agent-level mechanisms in the future

/**
 * Result of a subagent invocation.
 */
public sealed class SubagentResult<out O> {
    /**
     * Successful execution with output.
     */
    public data class Success<O>(
        val output: O,
        val metadata: Map<String, String> = emptyMap()
    ) : SubagentResult<O>()
    
    /**
     * Failed execution with error details.
     */
    public data class Failed(
        val error: String,
        val cause: Throwable? = null,
        val errorCode: SubagentErrorCode = SubagentErrorCode.EXECUTION_FAILED
    ) : SubagentResult<Nothing>()
}

/**
 * Standard error codes for subagent failures.
 */
public enum class SubagentErrorCode {
    AGENT_NOT_FOUND,
    MAX_DEPTH_EXCEEDED,
    FANOUT_EXCEEDED,
    TIMEOUT_EXCEEDED,
    EXECUTION_FAILED,
    VALIDATION_FAILED
}

/**
 * Exception thrown when subagent execution fails.
 */
public class SubagentExecutionException(
    message: String,
    public val errorCode: SubagentErrorCode,
    cause: Throwable? = null
) : RuntimeException(message, cause)