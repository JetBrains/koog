package ai.koog.agents.core.environment

/**
 * Provides constants related to the termination tool functionality in an AI agent environment.
 *
 * This object contains constants used to signal or handle the termination of operations within
 * AI agent interactions.
 */
public object TerminationTool {
    /**
     * Represents the reserved name used to signify a termination action or signal within the application.
     *
     * The variable is intended for internal use, acting as a marker or identifier
     * for halting processes, sessions, or operations in an explicit manner.
     */
    public const val NAME: String = "__terminate__"

    /**
     * The key name used to represent the result of an operation or tool execution in various contexts.
     *
     * This constant is a simple string identifier, typically utilized
     * to store, retrieve, or refer to the output or resulting value produced
     * during a process.
     */
    public const val ARG: String = "result"
}
