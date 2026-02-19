package ai.koog.agents.features.sql.providers

import ai.koog.agents.snapshot.feature.AgentCheckpointData
import ai.koog.agents.snapshot.providers.PersistenceUtils
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNotNull
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.upsert

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
public abstract class ExposedPersistenceStorageProvider @JvmOverloads constructor(
    protected val database: Database,
    tableName: String = "agent_checkpoints",
    ttlSeconds: Long? = null,
    migrator: SQLPersistenceSchemaMigrator,
    private val json: Json = PersistenceUtils.defaultCheckpointJson
) : SQLPersistenceStorageProvider<ExposedPersistenceFilter>(
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
    @JvmOverloads
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
                (checkpointsTable.ttlTimestamp less now) and (checkpointsTable.ttlTimestamp.isNotNull())
            }
            if (deletedCount > 0) {
                lastCleanupTime = now
            }
        }
    }

    @JvmOverloads
    override suspend fun getCheckpoints(sessionId: String, filter: ExposedPersistenceFilter?): List<AgentCheckpointData> {
        if (filter == null) {
            val now = Clock.System.now().toEpochMilliseconds()
            return transaction {
                checkpointsTable.select(checkpointsTable.checkpointJson).where {
                    (checkpointsTable.persistenceId eq sessionId) and
                        ((checkpointsTable.ttlTimestamp eq null) or (checkpointsTable.ttlTimestamp greaterEq now))
                }.mapNotNull { row ->
                    runCatching {
                        json.decodeFromString<AgentCheckpointData>(row[checkpointsTable.checkpointJson])
                    }.getOrNull()
                }
            }
        }

        return transaction {
            filter.query(checkpointsTable)
                .mapNotNull { row ->
                    runCatching {
                        json.decodeFromString<AgentCheckpointData>(row[checkpointsTable.checkpointJson])
                    }.getOrNull()
                }
        }
    }

    override suspend fun saveCheckpoint(sessionId: String, agentCheckpointData: AgentCheckpointData) {
        val checkpointJson = json.encodeToString(agentCheckpointData)
        val ttlTimestamp = calculateTtlTimestamp(agentCheckpointData.createdAt)

        transaction {
            checkpointsTable.upsert {
                it[checkpointsTable.persistenceId] = sessionId
                it[checkpointsTable.checkpointId] = agentCheckpointData.checkpointId
                it[checkpointsTable.createdAt] = agentCheckpointData.createdAt.toEpochMilliseconds()
                it[checkpointsTable.checkpointJson] = checkpointJson
                it[checkpointsTable.ttlTimestamp] = ttlTimestamp
                it[checkpointsTable.version] = agentCheckpointData.version
            }
        }
    }

    override suspend fun getLatestCheckpoint(sessionId: String, filter: ExposedPersistenceFilter?): AgentCheckpointData? {
        if (filter == null) {
            val now = Clock.System.now().toEpochMilliseconds()
            return transaction {
                checkpointsTable
                    .select(checkpointsTable.checkpointJson)
                    .where {
                        (checkpointsTable.persistenceId eq sessionId) and
                            ((checkpointsTable.ttlTimestamp eq null) or (checkpointsTable.ttlTimestamp greaterEq now))
                    }
                    .orderBy(checkpointsTable.version to SortOrder.DESC)
                    .limit(1)
                    .firstNotNullOfOrNull { row ->
                        runCatching {
                            json.decodeFromString<AgentCheckpointData>(row[checkpointsTable.checkpointJson])
                        }
                    }?.getOrNull()
            }
        }

        return transaction {
            filter
                .query(checkpointsTable)
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
                (checkpointsTable.persistenceId eq agentId) and (checkpointsTable.checkpointId eq checkpointId)
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
