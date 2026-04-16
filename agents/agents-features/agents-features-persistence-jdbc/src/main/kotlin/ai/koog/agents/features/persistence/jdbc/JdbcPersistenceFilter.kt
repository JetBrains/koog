package ai.koog.agents.features.persistence.jdbc

import ai.koog.agents.snapshot.feature.AgentCheckpointData

/**
 * Filter interface for JDBC-based persistence storage providers.
 *
 * Since pure JDBC does not support building dynamic SQL queries like Exposed ORM,
 * filtering is performed in-memory after loading checkpoints from the database.
 *
 * Implement this interface to create custom filters for querying checkpoints.
 */
public fun interface JdbcPersistenceFilter {
    /**
     * Checks whether the given checkpoint data matches this filter's criteria.
     *
     * @param checkpointData The checkpoint data to evaluate.
     * @return `true` if the checkpoint matches the filter criteria, `false` otherwise.
     */
    public fun check(checkpointData: AgentCheckpointData): Boolean
}
