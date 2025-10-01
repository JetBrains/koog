package ai.koog.agents.features.sql.providers

import ai.koog.agents.snapshot.providers.PersistenceStorageProvider
import kotlinx.datetime.Instant

/**
 * Abstract base class for SQL-based implementations of [PersistenceStorageProvider].
 *
 * This provider offers a generic SQL abstraction for persisting agent checkpoints
 * to relational databases. Concrete implementations should handle specific SQL
 * dialects and connection management.
 *
 * ## Storage Schema:
 * Implementations should create a table with the following structure:
 * - persistence_id: String (part of primary key)
 * - checkpoint_id: String (part of primary key)
 * - created_at: Long (epoch milliseconds)
 * - checkpoint_json: String (JSON-serialized checkpoint data)
 * - ttl_timestamp: Long? (optional expiration timestamp)
 *
 * ## Design Decisions:
 * - Uses JSON serialization for checkpoint storage (leveraging database JSON support where available)
 * - Composite key on (persistence_id, checkpoint_id) ensures uniqueness
 * - Timestamp stored as epoch milliseconds for cross-database compatibility
 * - TTL is implemented via a nullable ttl_timestamp column for query-based cleanup
 *
 * ## Thread Safety:
 * Implementations must ensure thread-safe database access, typically through connection pooling.
 *
 * @constructor Initializes the SQL persistence provider.
 * @param tableName Name of the table to store checkpoints (default: "agent_checkpoints")
 * @param ttlSeconds Optional TTL for checkpoint entries in seconds (null = no expiration)
 */
public abstract class SQLPersistenceStorageProvider(
    protected val tableName: String = "agent_checkpoints",
    protected val ttlSeconds: Long? = null,
    protected val migrator: SQLPersistenceSchemaMigrator
) : PersistenceStorageProvider {

    /**
     * Initializes the database schema if it doesn't exist.
     * This should be called once during provider initialization.
     */
    public open suspend fun migrate() {
        migrator.migrate()
    }

    /**
     * Executes a database transaction with the given operations.
     * Implementations should ensure proper transaction isolation and rollback on failure.
     */
    protected abstract suspend fun <T> transaction(block: suspend () -> T): T

    /**
     * Cleans up expired checkpoints based on TTL.
     * This should be called periodically or before operations to maintain database hygiene.
     */
    public abstract suspend fun cleanupExpired()

    /**
     * Calculates the TTL timestamp for a checkpoint if TTL is configured.
     */
    public fun calculateTtlTimestamp(timestamp: Instant): Long? {
        return ttlSeconds?.let {
            timestamp.toEpochMilliseconds() + (it * 1000)
        }
    }

    /**
     * Deletes a specific checkpoint by ID
     */
    public abstract suspend fun deleteCheckpoint(agentId: String, checkpointId: String)

    /**
     * Deletes all checkpoints for this agent ID
     */
    public abstract suspend fun deleteAllCheckpoints(agentId: String)

    /**
     * Gets the total number of checkpoints stored
     */
    public abstract suspend fun getCheckpointCount(agentId: String): Long
}
