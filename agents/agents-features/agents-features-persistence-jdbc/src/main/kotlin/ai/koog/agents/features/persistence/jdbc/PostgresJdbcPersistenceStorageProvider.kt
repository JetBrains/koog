package ai.koog.agents.features.persistence.jdbc

import ai.koog.agents.features.sql.providers.SQLPersistenceSchemaMigrator
import kotlinx.serialization.json.Json
import javax.sql.DataSource

/**
 * PostgreSQL-specific JDBC implementation of [JdbcPersistenceStorageProvider].
 *
 * Uses `INSERT ... ON CONFLICT (...) DO UPDATE` for upsert operations.
 *
 * @param dataSource The JDBC DataSource for PostgreSQL connections
 * @param tableName Name of the table to store checkpoints (default: "agent_checkpoints")
 * @param ttlSeconds Optional TTL for checkpoint entries in seconds (null = no expiration)
 * @param migrator Schema migrator for creating/updating the table
 * @param json JSON serializer instance for checkpoint serialization
 */
public class PostgresJdbcPersistenceStorageProvider @JvmOverloads constructor(
    dataSource: DataSource,
    tableName: String = "agent_checkpoints",
    ttlSeconds: Long? = null,
    migrator: SQLPersistenceSchemaMigrator = PostgresJdbcPersistenceSchemaMigrator(dataSource, tableName),
    json: Json = defaultJson
) : JdbcPersistenceStorageProvider(dataSource, migrator, ttlSeconds, tableName) {

    override val upsertSql: String = """
        INSERT INTO $tableName (persistence_id, checkpoint_id, created_at, checkpoint_json, ttl_timestamp, version)
        VALUES (?, ?, ?, ?, ?, ?)
        ON CONFLICT (persistence_id, checkpoint_id) DO UPDATE SET
            created_at = EXCLUDED.created_at,
            checkpoint_json = EXCLUDED.checkpoint_json,
            ttl_timestamp = EXCLUDED.ttl_timestamp,
            version = EXCLUDED.version
    """.trimIndent()
}
