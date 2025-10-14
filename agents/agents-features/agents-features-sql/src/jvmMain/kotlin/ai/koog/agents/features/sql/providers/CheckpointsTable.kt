package ai.koog.agents.features.sql.providers

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table

/**
 * Exposed table definition for storing agent checkpoints.
 *
 * Schema:
 * - Composite primary key: (persistence_id, checkpoint_id)
 * - Timestamp for ordering and querying
 * - JSON column for flexible checkpoint data storage
 * - Optional TTL timestamp for expiration (indexed for efficient cleanup)
 */
public open class CheckpointsTable(tableName: String) : Table(tableName) {
    /**
     * Represents the column "persistence_id" in the CheckpointsTable.
     *
     * This column is part of the composite primary key (persistence_id, checkpoint_id) used to uniquely
     * identify checkpoint records in the table. It is a string column with a maximum length of 255
     * characters, and its value indicates the persistence identifier associated with a specific
     * checkpoint.
     */
    public val persistenceId: Column<String> = varchar("persistence_id", 255)

    /**
     * Represents the column "checkpoint_id" in the CheckpointsTable.
     *
     * This column is part of the composite primary key (persistence_id, checkpoint_id) used to uniquely
     * identify checkpoint records in the table. It is a string column with a maximum length of 255
     * characters, and its value specifies the unique identifier for a checkpoint within the context of
     * a persistence ID.
     */
    public val checkpointId: Column<String> = varchar("checkpoint_id", 255)

    /**
     * Represents the "created_at" column in the CheckpointsTable.
     *
     * This column stores the creation timestamp of a checkpoint.
     * It is indexed to enable efficient ordering and querying of checkpoints by their creation time.
     */
    public val createdAt: Column<Long> = long("created_at").index()

    /**
     * Represents the `checkpoint_json` column in the `CheckpointsTable`.
     *
     * This column stores serialized checkpoint data as a JSON string. It is a required field,
     * ensuring the persistence of critical state information for agent checkpoints.
     */
    public val checkpointJson: Column<String> = text("checkpoint_json")

    /**
     * Represents the TTL (Time-To-Live) timestamp for a checkpoint.
     *
     * This column stores an optional expiration timestamp, in milliseconds since the epoch,
     * indicating when the checkpoint is considered expired and eligible for removal or archival.
     *
     * If null, the checkpoint does not have a TTL and is retained indefinitely.
     *
     * Indexed to allow for efficient queries based on TTL.
     */
    public val ttlTimestamp: Column<Long?> = long("ttl_timestamp").nullable().index()

    /**
     * Represents the version of the checkpoint.
     *
     * This column stores a long integer indicating the version of the checkpoint.
     */
    public val version: Column<Long> = long("version")

    override val primaryKey: PrimaryKey = PrimaryKey(persistenceId, checkpointId)

    init {
        // Create composite index for efficient queries
        index(isUnique = false, persistenceId, createdAt)
        index(isUnique = true, persistenceId, version)
    }
}
