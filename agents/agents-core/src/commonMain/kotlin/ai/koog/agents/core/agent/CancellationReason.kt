package ai.koog.agents.core.agent

/**
 * Represents the reason why an agent execution was cancelled.
 *
 * This enum provides semantic context for cancellation events, enabling proper
 * handling of different cancellation scenarios in logging, telemetry, and error handling.
 */
public enum class CancellationReason {
    /**
     * The agent was cancelled due to a user-initiated request.
     * This is the most common cancellation reason, typically triggered by user interaction.
     */
    UserRequested,

    /**
     * The agent was cancelled due to a timeout.
     * This occurs when the agent execution exceeds predefined time limits.
     */
    Timeout,

    /**
     * The agent was cancelled due to policy enforcement.
     * This includes safety mechanisms, resource limits, or other automated policies.
     */
    Policy,

    /**
     * The agent was cancelled due to a system-level event.
     * This includes infrastructure issues, service shutdowns, or other system events.
     */
    System
}
