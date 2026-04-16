package ai.koog.agents.features.persistence.jdbc

import ai.koog.agents.features.sql.providers.SQLPersistenceSchemaMigrator
import ai.koog.agents.features.sql.providers.SQLPersistenceStorageProvider
import ai.koog.agents.snapshot.feature.AgentCheckpointData
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.sql.Types
import javax.sql.DataSource
import kotlin.time.Clock

/**
 * Abstract pure JDBC implementation of [SQLPersistenceStorageProvider] for managing agent checkpoint
 * persistence in SQL databases.
 *
 * Subclasses provide the database-specific upsert SQL via [upsertSql].
 *
 * @param dataSource The JDBC DataSource for obtaining database connections.
 *   The caller is responsible for managing the DataSource lifecycle
 *   (e.g., closing a connection pool). This provider does not close or otherwise manage the DataSource.
 * @param migrator Schema migrator for creating/updating the table
 * @param tableName Name of the table to store checkpoints (default: "agent_checkpoints")
 * @param ttlSeconds Optional TTL for checkpoint entries in seconds (null = no expiration)
 * @param ioDispatcher Coroutine dispatcher for I/O operations (default: [Dispatchers.IO])
 */
public abstract class JdbcPersistenceStorageProvider @JvmOverloads constructor(
    protected val dataSource: DataSource,
    migrator: SQLPersistenceSchemaMigrator,
    ttlSeconds: Long? = null,
    tableName: String = "agent_checkpoints",
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : SQLPersistenceStorageProvider<JdbcPersistenceFilter>(
    tableName = tableName,
    ttlSeconds = ttlSeconds,
    migrator = migrator
) {
    protected open fun serializeCheckpoint(checkpoint: AgentCheckpointData): String =
        defaultJson.encodeToString(AgentCheckpointData.serializer(), checkpoint)

    protected open fun deserializeCheckpoint(json: String): AgentCheckpointData =
        defaultJson.decodeFromString(AgentCheckpointData.serializer(), json)

    /**
     * Database-specific SQL for upsert operations.
     * The SQL must accept 6 positional parameters: persistence_id, checkpoint_id, created_at,
     * checkpoint_json, ttl_timestamp, version.
     */
    protected abstract val upsertSql: String

    /**
     * SQL for loading all checkpoints by session ID, filtering out expired entries.
     * Accepts 2 positional parameters: persistence_id, current_timestamp.
     */
    protected open val loadAllSql: String
        get() = """
            SELECT checkpoint_json FROM $tableName
            WHERE persistence_id = ? AND (ttl_timestamp IS NULL OR ttl_timestamp >= ?)
            ORDER BY created_at ASC
        """.trimIndent()

    /**
     * SQL for loading the latest checkpoint by session ID, filtering out expired entries.
     * Accepts 2 positional parameters: persistence_id, current_timestamp.
     */
    protected open val loadLatestSql: String
        get() = """
            SELECT checkpoint_json FROM $tableName
            WHERE persistence_id = ? AND (ttl_timestamp IS NULL OR ttl_timestamp >= ?)
            ORDER BY version DESC
            LIMIT 1
        """.trimIndent()

    /**
     * SQL for deleting expired entries.
     * Accepts 1 positional parameter: current_timestamp.
     */
    protected open val cleanupExpiredSql: String
        get() = "DELETE FROM $tableName WHERE ttl_timestamp IS NOT NULL AND ttl_timestamp < ?"

    /**
     * SQL for deleting a specific checkpoint.
     * Accepts 2 positional parameters: persistence_id, checkpoint_id.
     */
    protected open val deleteCheckpointSql: String
        get() = "DELETE FROM $tableName WHERE persistence_id = ? AND checkpoint_id = ?"

    /**
     * SQL for deleting all checkpoints for a session.
     * Accepts 1 positional parameter: persistence_id.
     */
    protected open val deleteAllCheckpointsSql: String
        get() = "DELETE FROM $tableName WHERE persistence_id = ?"

    /**
     * SQL for counting non-expired checkpoints for a session.
     * Accepts 2 positional parameters: persistence_id, current_timestamp.
     */
    protected open val getCheckpointCountSql: String
        get() = """
            SELECT COUNT(*) FROM $tableName
            WHERE persistence_id = ? AND (ttl_timestamp IS NULL OR ttl_timestamp >= ?)
        """.trimIndent()

    override suspend fun getCheckpoints(
        sessionId: String,
        filter: JdbcPersistenceFilter?
    ): List<AgentCheckpointData> {
        val now = Clock.System.now().toEpochMilliseconds()

        val checkpoints = withContext(ioDispatcher) {
            dataSource.connection.use { connection ->
                connection.prepareStatement(loadAllSql).use { stmt ->
                    stmt.setString(1, sessionId)
                    stmt.setLong(2, now)
                    stmt.executeQuery().use { rs ->
                        val result = mutableListOf<AgentCheckpointData>()
                        while (rs.next()) {
                            result.add(deserializeCheckpoint(rs.getString("checkpoint_json")))
                        }
                        result
                    }
                }
            }
        }

        return if (filter != null) {
            checkpoints.filter { filter.check(it) }
        } else {
            checkpoints
        }
    }

    override suspend fun saveCheckpoint(sessionId: String, agentCheckpointData: AgentCheckpointData) {
        val checkpointJson = serializeCheckpoint(agentCheckpointData)
        val now = Clock.System.now()
        val ttlTimestamp = calculateTtlTimestamp(now)

        withContext(ioDispatcher) {
            dataSource.connection.use { connection ->
                connection.prepareStatement(upsertSql).use { stmt ->
                    stmt.setString(1, sessionId)
                    stmt.setString(2, agentCheckpointData.checkpointId)
                    stmt.setLong(3, agentCheckpointData.createdAt.toEpochMilliseconds())
                    stmt.setString(4, checkpointJson)
                    if (ttlTimestamp != null) {
                        stmt.setLong(5, ttlTimestamp)
                    } else {
                        stmt.setNull(5, Types.BIGINT)
                    }
                    stmt.setLong(6, agentCheckpointData.version)
                    stmt.executeUpdate()
                }
            }
        }
    }

    override suspend fun getLatestCheckpoint(
        sessionId: String,
        filter: JdbcPersistenceFilter?
    ): AgentCheckpointData? {
        if (filter != null) {
            // When filter is provided, load all and filter in-memory
            return getCheckpoints(sessionId, filter).maxByOrNull { it.version }
        }

        val now = Clock.System.now().toEpochMilliseconds()

        return withContext(ioDispatcher) {
            dataSource.connection.use { connection ->
                connection.prepareStatement(loadLatestSql).use { stmt ->
                    stmt.setString(1, sessionId)
                    stmt.setLong(2, now)
                    stmt.executeQuery().use { rs ->
                        if (rs.next()) {
                            deserializeCheckpoint(rs.getString("checkpoint_json"))
                        } else {
                            null
                        }
                    }
                }
            }
        }
    }

    override suspend fun <T> transaction(block: suspend () -> T): T {
        return block()
    }

    override suspend fun cleanupExpired() {
        if (ttlSeconds == null) return

        val now = Clock.System.now().toEpochMilliseconds()

        withContext(ioDispatcher) {
            dataSource.connection.use { connection ->
                connection.prepareStatement(cleanupExpiredSql).use { stmt ->
                    stmt.setLong(1, now)
                    stmt.executeUpdate()
                }
            }
        }
    }

    override suspend fun deleteCheckpoint(agentId: String, checkpointId: String) {
        withContext(ioDispatcher) {
            dataSource.connection.use { connection ->
                connection.prepareStatement(deleteCheckpointSql).use { stmt ->
                    stmt.setString(1, agentId)
                    stmt.setString(2, checkpointId)
                    stmt.executeUpdate()
                }
            }
        }
    }

    override suspend fun deleteAllCheckpoints(agentId: String) {
        withContext(ioDispatcher) {
            dataSource.connection.use { connection ->
                connection.prepareStatement(deleteAllCheckpointsSql).use { stmt ->
                    stmt.setString(1, agentId)
                    stmt.executeUpdate()
                }
            }
        }
    }

    override suspend fun getCheckpointCount(agentId: String): Long {
        val now = Clock.System.now().toEpochMilliseconds()

        return withContext(ioDispatcher) {
            dataSource.connection.use { connection ->
                connection.prepareStatement(getCheckpointCountSql).use { stmt ->
                    stmt.setString(1, agentId)
                    stmt.setLong(2, now)
                    stmt.executeQuery().use { rs ->
                        rs.next()
                        rs.getLong(1)
                    }
                }
            }
        }
    }

    // ---- Blocking variants for Java callers ----

    /**
     * Blocking variant of [migrate] for Java callers.
     *
     * Runs the schema migration synchronously, blocking the calling thread
     * until completion. Prefer the suspend [migrate] when calling from Kotlin coroutines.
     */
    public fun migrateBlocking() {
        runBlocking { migrate() }
    }

    /**
     * Blocking variant of [saveCheckpoint] for Java callers.
     */
    public fun saveCheckpointBlocking(sessionId: String, agentCheckpointData: AgentCheckpointData) {
        runBlocking { saveCheckpoint(sessionId, agentCheckpointData) }
    }

    /**
     * Blocking variant of [getCheckpoints] for Java callers.
     */
    public fun getCheckpointsBlocking(sessionId: String): List<AgentCheckpointData> {
        return runBlocking { getCheckpoints(sessionId) }
    }

    /**
     * Blocking variant of [getLatestCheckpoint] for Java callers.
     */
    public fun getLatestCheckpointBlocking(sessionId: String): AgentCheckpointData? {
        return runBlocking { getLatestCheckpoint(sessionId) }
    }

    /**
     * Blocking variant of [getCheckpointCount] for Java callers.
     */
    public fun getCheckpointCountBlocking(sessionId: String): Long {
        return runBlocking { getCheckpointCount(sessionId) }
    }

    /**
     * Blocking variant of [deleteCheckpoint] for Java callers.
     */
    public fun deleteCheckpointBlocking(agentId: String, checkpointId: String) {
        runBlocking { deleteCheckpoint(agentId, checkpointId) }
    }

    /**
     * Blocking variant of [deleteAllCheckpoints] for Java callers.
     */
    public fun deleteAllCheckpointsBlocking(agentId: String) {
        runBlocking { deleteAllCheckpoints(agentId) }
    }

    /**
     * Blocking variant of [cleanupExpired] for Java callers.
     */
    public fun cleanupExpiredBlocking() {
        runBlocking { cleanupExpired() }
    }

    @Suppress("MissingKDocForPublicAPI")
    public companion object {
        /**
         * Default JSON configuration for checkpoint serialization.
         * Uses compact format (no pretty printing) since checkpoint data can be large.
         */
        public val defaultJson: Json = Json {
            prettyPrint = false
            ignoreUnknownKeys = true
            explicitNulls = false
        }
    }
}
