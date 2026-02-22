package ai.koog.agents.planner.goap

/**
 * Represents a terminal builder for [Action] instances.
 */
public interface ActionTerminalBuilder<State> {
    /**
     * Builds the action instance.
     */
    public fun build(): Action<State>
}
