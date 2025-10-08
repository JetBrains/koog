package ai.koog.agents.snapshot.providers.filters

import ai.koog.agents.snapshot.feature.AgentCheckpointData

/**
 * Interface for defining predicates to filter agent checkpoint data.
 *
 * Implementations of this interface are utilized in storage providers to determine
 * whether specific agent checkpoints meet given conditions. This allows for selective
 * retrieval and processing of checkpoints based on custom logic, such as filtering by
 * node, time, or specific checkpoint properties.
 */
public interface AgentCheckpointPredicateFilter {
    /**
     * Evaluates whether the provided agent checkpoint data meets the defined conditions.
     *
     * @param checkpointData The data associated with the agent's checkpoint, which includes
     *                       state information such as message history, node details, and properties.
     * @return `true` if the checkpoint data satisfies the conditions, `false` otherwise.
     */
    public fun check(checkpointData: AgentCheckpointData): Boolean
}
