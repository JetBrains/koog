package ai.koog.agents.core.subagent

import kotlin.time.Duration.Companion.seconds

/**
 * Extension functions for streamlined agent safety and remote execution.
 * 
 * This file provides convenience functions for the simplified subagent architecture
 * that focuses on safety mechanisms through agent wrappers and transport abstraction
 * for future remote execution capabilities.
 */

// Re-export the safety policies from SafeAgentExecution for convenience
public typealias SafetyPolicy = AgentSafetyPolicy

/**
 * Convenience function for creating safe policies.
 * 
 * @see SafetyPolicies.safe
 */
public fun safePolicy(
    maxDepth: Int = 2,
    maxChildren: Int = 3,
    timeout: kotlin.time.Duration = 30.seconds
): AgentSafetyPolicy = SafetyPolicies.safe(maxDepth, maxChildren, timeout)

/**
 * Convenience function for creating trusted policies.
 * 
 * @see SafetyPolicies.trusted
 */
public fun trustedPolicy(
    maxDepth: Int = 5,
    maxChildren: Int = 10,
    timeout: kotlin.time.Duration = 120.seconds
): AgentSafetyPolicy = SafetyPolicies.trusted(maxDepth, maxChildren, timeout)

/**
 * DSL for creating InProcessRemoteAgentInvoker with agent registrations.
 * This is primarily for testing and development of remote agent patterns.
 */
public fun inProcessRemoteInvoker(
    vararg agents: Pair<String, ai.koog.agents.core.agent.AIAgentBase<*, *>>
): InProcessRemoteAgentInvoker {
    return InProcessRemoteAgentInvoker.of(agents.toMap())
}