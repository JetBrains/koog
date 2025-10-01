package ai.koog.agents.features.sql.providers

import ai.koog.agents.snapshot.feature.AgentCheckpointData
import ai.koog.agents.snapshot.providers.PersistenceUtils
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNotNull
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.upsert

/**
 * Configuration for TTL cleanup behavior
 *
 * @property enabled Whether TTL cleanup should be performed automatically
 * @property intervalMs Minimum interval between cleanup operations in milliseconds (default: 1 minute)
 */
public data class CleanupConfig(
    val enabled: Boolean = true,
    val intervalMs: Long = 60_000L
) {
    /**
     * Companion object for CleanupConfig providing factory methods for creating configuration instances.
     */
    public companion object {
        /**
         * Creates a default CleanupConfig instance with automatic TTL cleanup enabled
         * and a default interval of 1 minute between cleanup operations.
         *
         * @return A CleanupConfig instance with default settings.
         */
        public fun default(): CleanupConfig = CleanupConfig()

        /**
         * Creates a CleanupConfig instance with automatic TTL cleanup disabled.
         *
         * @return A CleanupConfig instance with the `enabled` property set to `false`.
         */
        public fun disabled(): CleanupConfig = CleanupConfig(enabled = false)
    }
}

/**
 * Exposed table definition for storing agent checkpoints.
 *
 * Schema:
 * - Composite primary key: (persistence_id, checkpoint_id)
 * - Timestamp for ordering and querying
 * - JSON column for flexible checkpoint data storage
 * - Optional TTL timestamp for expiration (indexed for efficient cleanup)
 */
@Suppress("MissingKDocForPublicAPI")
public open class CheckpointsTable(tableName: String) : Table(tableName) {
    public val persistenceId: Column<String> = varchar("persistence_id", 255)
    public val checkpointId: Column<String> = varchar("checkpoint_id", 255)
    public val createdAt: Column<Long> = long("created_at").index()
    public val checkpointJson: Column<String> = text("checkpoint_json")
    public val ttlTimestamp: Column<Long?> = long("ttl_timestamp").nullable().index()

    override val primaryKey: PrimaryKey = PrimaryKey(persistenceId, checkpointId)

    init {
        // Create composite index for efficient queries
        index(isUnique = false, persistenceId, createdAt)
    }
}

/**
 * An abstract Exposed-based implementation of [SQLPersistenceStorageProvider] for managing
 * agent checkpoints in SQL databases using JetBrains Exposed ORM.
 *
 * This class provides a generic SQL implementation that works with any database supported
 * by Exposed (PostgreSQL, MySQL, H2, SQLite, etc.). It handles the common operations
 * while allowing concrete implementations to provide database-specific configurations.
 *
 * ## Architecture:
 * - Uses Exposed's DSL for type-safe SQL operations
 * - Leverages Exposed's JSON column support for checkpoint serialization
 * - Implements automatic schema creation and migration
 * - Provides transaction management with proper isolation
 * - Configurable TTL cleanup to prevent excessive operations
 *
 * ## Database Compatibility:
 * - PostgreSQL: Full support including JSONB columns
 * - MySQL: JSON column support (5.7+)
 * - H2: JSON stored as TEXT with parsing
 * - SQLite: JSON stored as TEXT with parsing
 *
 * ## Performance Considerations:
 * - Uses database-specific JSON operations where available
 * - Implements efficient querying with proper indexing
 * - Supports connection pooling through HikariCP
 * - Batch operations for cleanup and multi-checkpoint retrieval
 * - Configurable cleanup intervals to avoid excessive TTL operations
 *
 * ## TTL Implementation Notes:
 * - TTL is implemented via a nullable ttl_timestamp column for query-based cleanup
 * - The ttl_timestamp column is indexed for efficient cleanup queries
 * - Cleanup can be disabled entirely for scenarios where TTL is not needed
 * - When TTL is not configured (ttlSeconds = null), no TTL processing occurs
 *
 * @constructor Initializes the Exposed persistence provider.
 * @param database The Exposed Database instance to use
 * @param tableName Name of the table to store checkpoints (default: "agent_checkpoints")
 * @param ttlSeconds Optional TTL for checkpoint entries in seconds (null = no expiration)
 */
@Suppress("MissingKDocForPublicAPI")
public abstract class ExposedPersistenceStorageProvider(
    protected val database: Database,
    tableName: String = "agent_checkpoints",
    ttlSeconds: Long? = null,
    migrator: SQLPersistenceSchemaMigrator,
    private val json: Json = PersistenceUtils.defaultCheckpointJson
) : SQLPersistenceStorageProvider(
    tableName = tableName,
    ttlSeconds = ttlSeconds,
    migrator
) {
    /**
     * The Exposed table definition for checkpoints.
     * Uses a composite primary key and JSON column for checkpoint data.
     */
    protected open val checkpointsTable: CheckpointsTable = CheckpointsTable(tableName)

    /**
     * Track last cleanup time to avoid excessive cleanup operations
     */
    private var lastCleanupTime: Long = 0

    /**
     * Conditionally performs cleanup based on configuration and TTL settings.
     * Only runs cleanup if:
     * 1. Cleanup is enabled in config
     * 2. TTL is configured (ttlSeconds is not null)
     * 3. Enough time has passed since last cleanup
     */
    public suspend fun conditionalCleanup(cleanupConfig: CleanupConfig = CleanupConfig.default()) {
        // Skip cleanup entirely if disabled or no TTL configured
        if (!cleanupConfig.enabled || ttlSeconds == null) {
            return
        }

        val now = Clock.System.now().toEpochMilliseconds()

        // Skip cleanup if we've cleaned up recently
        if (now - lastCleanupTime < cleanupConfig.intervalMs) {
            return
        }

        cleanupExpired()
    }

    override suspend fun cleanupExpired() {
        // Only perform cleanup if TTL is configured
        if (ttlSeconds == null) {
            return
        }

        val now = Clock.System.now().toEpochMilliseconds()

        transaction {
            val deletedCount = checkpointsTable.deleteWhere {
                (checkpointsTable.ttlTimestamp less now) and
                    (checkpointsTable.ttlTimestamp.isNotNull())
            }
            if (deletedCount > 0) {
                lastCleanupTime = now
            }
        }
    }

    override suspend fun getCheckpoints(agentId: String): List<AgentCheckpointData> {
        return transaction {
            checkpointsTable
                .select(checkpointsTable.checkpointJson)
                .where {
                    checkpointsTable.persistenceId eq agentId
                }
                .orderBy(checkpointsTable.createdAt to SortOrder.ASC)
                .mapNotNull { row ->
                    runCatching {
                        json.decodeFromString<AgentCheckpointData>(row[checkpointsTable.checkpointJson])
                    }.getOrNull()
                }
        }
    }

    override suspend fun saveCheckpoint(agentId: String, agentCheckpointData: AgentCheckpointData) {
        val checkpointJson = json.encodeToString(agentCheckpointData)
        val ttlTimestamp = calculateTtlTimestamp(agentCheckpointData.createdAt)

        transaction {
            // Use upsert for idempotent saves
            checkpointsTable.upsert {
                it[checkpointsTable.persistenceId] = agentId
                it[checkpointsTable.checkpointId] = agentCheckpointData.checkpointId
                it[checkpointsTable.createdAt] = agentCheckpointData.createdAt.toEpochMilliseconds()
                it[checkpointsTable.checkpointJson] = checkpointJson
                it[checkpointsTable.ttlTimestamp] = ttlTimestamp
            }
        }
    }

    override suspend fun getLatestCheckpoint(agentId: String): AgentCheckpointData? {
        return transaction {
            checkpointsTable
                .select(checkpointsTable.checkpointJson)
                .where {
                    checkpointsTable.persistenceId eq agentId
                }
                .orderBy(checkpointsTable.createdAt to SortOrder.DESC)
                .limit(1)
                .firstOrNull()?.let { row ->
                    runCatching {
                        json.decodeFromString<AgentCheckpointData>(row[checkpointsTable.checkpointJson])
                    }.getOrNull()
                }
        }
    }

    override suspend fun deleteCheckpoint(agentId: String, checkpointId: String) {
        transaction {
            checkpointsTable.deleteWhere {
                (checkpointsTable.persistenceId eq agentId) and
                    (checkpointsTable.checkpointId eq checkpointId)
            }
        }
    }

    override suspend fun deleteAllCheckpoints(agentId: String) {
        transaction {
            checkpointsTable.deleteWhere {
                checkpointsTable.persistenceId eq agentId
            }
        }
    }

    override suspend fun getCheckpointCount(agentId: String): Long {
        return transaction {
            checkpointsTable.selectAll().where {
                checkpointsTable.persistenceId eq agentId
            }.count()
        }
    }
}
