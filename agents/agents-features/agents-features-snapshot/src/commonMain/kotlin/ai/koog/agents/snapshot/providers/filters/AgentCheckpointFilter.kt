package ai.koog.agents.snapshot.providers.filters

import ai.koog.agents.snapshot.feature.AgentCheckpointData

/**
 * Interface for filtering agent checkpoints.
 */
public interface AgentCheckpointFilter

/**
 * An interface that extends [AgentCheckpointFilter] to provide a mechanism for evaluating
 * whether a specific agent checkpoint meets the predicate-defined conditions.
 *
 * This filter is used to determine, based on certain criteria, whether an agent's checkpoint
 * should be acted upon, retained, or utilized in a specific context. The evaluation is
 * performed using the [check] function, which implements the predicate logic.
 */
public interface AgentCheckpointPredicateFilter : AgentCheckpointFilter {
    /**
     * Evaluates whether the provided agent checkpoint data meets the defined conditions.
     *
     * @param checkpointData The data associated with the agent's checkpoint, which includes
     *                       state information such as message history, node details, and properties.
     * @return `true` if the checkpoint data satisfies the conditions, `false` otherwise.
     */
    public fun check(checkpointData: AgentCheckpointData): Boolean
}
