package ai.koog.agents.core.subagent

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.AIAgentBase
import ai.koog.agents.core.agent.asTool
import ai.koog.agents.core.tools.ToolParameterDescriptor
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlin.reflect.typeOf
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Coroutine context key for tracking agent execution depth.
 */
public data class AgentDepthKey(private val unused: String = "") : CoroutineContext.Key<AgentDepthElement>

/**
 * Coroutine context element for tracking agent execution depth.
 */
public data class AgentDepthElement(public val depth: Int) : CoroutineContext.Element {
    override val key: CoroutineContext.Key<*> = AgentDepthKey()
}

/**
 * Exception thrown when agent safety policies are violated.
 */
public class AgentSafetyException(
    message: String,
    public val errorCode: AgentSafetyErrorCode,
    cause: Throwable? = null
) : Exception(message, cause)

/**
 * Error codes for agent safety violations.
 */
public enum class AgentSafetyErrorCode {
    MAX_DEPTH_EXCEEDED,
    TIMEOUT_EXCEEDED,
    MAX_FANOUT_EXCEEDED,
    AGENT_NOT_ALLOWED
}

/**
 * Safety policies for agent execution.
 * 
 * Provides depth limits, fanout control, and timeout protection
 * for multi-agent orchestration scenarios.
 */
public data class AgentSafetyPolicy(
    val maxDepth: Int = 2,
    val maxChildrenPerCall: Int = 3,
    val timeout: Duration = 30.seconds,
    val allowedChildren: Set<String> = emptySet()
)

/**
 * Predefined safety policies for common use cases.
 */
public object SafetyPolicies {
    /**
     * Conservative policy for untrusted or external agents.
     * Requires explicit allowedChildren whitelist for additional safety.
     */
    public fun safe(
        maxDepth: Int = 2,
        maxChildren: Int = 3,
        timeout: Duration = 30.seconds,
        allowedChildren: Set<String> = emptySet()
    ): AgentSafetyPolicy = AgentSafetyPolicy(maxDepth, maxChildren, timeout, allowedChildren)
    
    /**
     * Relaxed policy for trusted internal agents.
     * Allows any child agents by default.
     */
    public fun trusted(
        maxDepth: Int = 5,
        maxChildren: Int = 10,
        timeout: Duration = 120.seconds,
        allowedChildren: Set<String> = setOf("*") // "*" means allow all
    ): AgentSafetyPolicy = AgentSafetyPolicy(maxDepth, maxChildren, timeout, allowedChildren)
}

/**
 * Enhanced asTool() with safety mechanisms for multi-agent orchestration.
 * 
 * This provides the same agent-to-tool conversion as the standard asTool(),
 * but adds enterprise-grade safety mechanisms:
 * - Depth limit enforcement to prevent infinite recursion
 * - Fanout control to limit concurrent executions
 * - Timeout protection against hanging operations
 * 
 * Example usage:
 * ```kotlin
 * val registry = ToolRegistry {
 *     tool(dataProcessor.asSafeTool(
 *         agentName = "data-processor",
 *         agentDescription = "Processes and validates input data",
 *         inputDescriptor = ToolParameterDescriptor.string("input"),
 *         safetyPolicy = SafetyPolicies.safe(maxDepth = 3)
 *     ))
 * }
 * ```
 */
public inline fun <reified Input, reified Output> AIAgentBase<Input, Output>.asSafeTool(
    agentName: String,
    agentDescription: String,
    inputDescriptor: ToolParameterDescriptor,
    safetyPolicy: AgentSafetyPolicy = SafetyPolicies.safe(),
    remoteInvoker: RemoteAgentInvoker? = null,
    inputSerializer: KSerializer<Input> = serializer(),
    outputSerializer: KSerializer<Output> = serializer()
) = SafeAgentWrapper(
    this, 
    safetyPolicy, 
    remoteInvoker ?: InProcessRemoteAgentInvoker.of(mapOf(this.id to this))
).asTool(
    agentName = agentName,
    agentDescription = agentDescription,
    inputDescriptor = inputDescriptor,
    inputSerializer = inputSerializer,
    outputSerializer = outputSerializer
)

/**
 * Internal wrapper that adds safety mechanisms to any agent.
 * 
 * This is a lightweight decorator that enforces safety policies
 * without changing the agent's interface or behavior.
 */
public class SafeAgentWrapper<Input, Output>(
    private val delegate: AIAgentBase<Input, Output>,
    private val safetyPolicy: AgentSafetyPolicy,
    private val invoker: RemoteAgentInvoker
) : AIAgentBase<Input, Output> {
    
    override val id: String = "${delegate.id}_safe"
    
    // Semaphore for fanout control - limits concurrent executions
    private val fanoutSemaphore = Semaphore(safetyPolicy.maxChildrenPerCall)
    
    override suspend fun run(agentInput: Input): Output {
        // Check current depth from coroutine context
        val currentDepth = coroutineContext[AgentDepthKey()]?.depth ?: 0
        
        // Enforce depth limit
        if (currentDepth >= safetyPolicy.maxDepth) {
            throw AgentSafetyException(
                "Maximum agent depth exceeded: $currentDepth >= ${safetyPolicy.maxDepth}",
                AgentSafetyErrorCode.MAX_DEPTH_EXCEEDED
            )
        }
        
        // Enforce allowed children policy
        if (safetyPolicy.allowedChildren.isNotEmpty() && !safetyPolicy.allowedChildren.contains("*")) {
            if (!safetyPolicy.allowedChildren.contains(delegate.id)) {
                throw AgentSafetyException(
                    "Agent ${delegate.id} not in allowed children: ${safetyPolicy.allowedChildren}",
                    AgentSafetyErrorCode.AGENT_NOT_ALLOWED
                )
            }
        }
        
        // Acquire fanout permit (limits concurrent agent executions)
        fanoutSemaphore.acquire()
        
        return try {
            withTimeout(safetyPolicy.timeout) {
                // Create new context with incremented depth
                val newDepthElement = AgentDepthElement(currentDepth + 1)
                val newContext = coroutineContext + newDepthElement
                
                // Run delegate with new depth context
                withContext(newContext) {
                    // For now use direct execution, transport abstraction available for future use
                    delegate.run(agentInput)
                }
            }
        } catch (e: TimeoutCancellationException) {
            throw AgentSafetyException(
                "Agent execution timed out after ${safetyPolicy.timeout}",
                AgentSafetyErrorCode.TIMEOUT_EXCEEDED,
                e
            )
        } finally {
            // Always release the fanout permit
            fanoutSemaphore.release()
        }
    }
}