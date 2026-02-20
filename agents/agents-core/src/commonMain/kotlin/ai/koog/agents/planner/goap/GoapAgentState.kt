package ai.koog.agents.planner.goap

/**
 * Represents the state of a Goal-Oriented Action Planning (GOAP) agent during its operation.
 *
 * This abstract class provides a base for GOAP (Goal-Oriented Action Planning) agent states,
 * capturing the initial input and defining how to produce the final output.
 *
 * @param Input The type of the input data for the agent.
 * @param Output The type of the output data produced by the agent.
 * @property agentInput The initial input provided to the agent.
 */
public abstract class GoapAgentState<Input, Output>(
    public val agentInput: Input
) {
    /**
     * Provides an output based on the current state of the implementing class.
     *
     * @return An instance of the `Output` type, representing the result of the operation.
     */
    public abstract fun provideOutput(): Output
}
