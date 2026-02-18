package ai.koog.prompt.executor.router

/**
 * Defines how requests are distributed across multiple clients.
 */
public enum class RoutingStrategy {
    /**
     * Rotates through clients in sequential order, distributing load evenly.
     */
    ROUND_ROBIN
}
