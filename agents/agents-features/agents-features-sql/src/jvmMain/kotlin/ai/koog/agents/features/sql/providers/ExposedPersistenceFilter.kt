package ai.koog.agents.features.sql.providers

import org.jetbrains.exposed.sql.Query

/**
 * A filter contract for SQL-backed persistence based on JetBrains Exposed.
 *
 * Implementations translate high-level checkpoint filtering intent into an Exposed [Query]
 * that will be executed by [ExposedPersistenceStorageProvider].
 */
public interface ExposedPersistenceFilter {
    /**
     * Build an Exposed DSL [Query] that selects rows from the given [table]
     * matching the desired conditions.
     *
     * The returned query is expected to be a complete selection (e.g. `table.select { ... }`),
     * which the storage provider may further refine (ordering, limiting) when fetching either
     * the latest checkpoint or a list of checkpoints.
     *
     * Requirements and tips for implementors:
     * - Do not perform side effects; just construct and return a query expression.
     * - Use the provided [CheckpointsTable] columns (e.g. `agentId`, `checkpointId`, `createdAt`, etc.).
     * - Keep the query portable across different SQL dialects supported by Exposed.
     *
     * @param table The Exposed [CheckpointsTable] to select from.
     * @return An Exposed [Query] that yields rows conforming to this filter.
     */
    public fun query(table: CheckpointsTable): Query
}
