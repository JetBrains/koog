package ai.koog.agents.features.persistence.jdbc

import ai.koog.agents.features.sql.providers.SQLPersistenceSchemaMigrator
import kotlinx.serialization.json.Json
import javax.sql.DataSource

/**
 * H2-specific JDBC implementation of [JdbcPersistenceStorageProvider].
 *
 * Uses `MERGE INTO ... KEY (...) VALUES (...)` for upsert operations.
 *
 * @param dataSource The JDBC DataSource for H2 connections
 * @param tableName Name of the table to store checkpoints (default: "agent_checkpoints")
 * @param ttlSeconds Optional TTL for checkpoint entries in seconds (null = no expiration)
 * @param migrator Schema migrator for creating/updating the table
 * @param json JSON serializer instance for checkpoint serialization
 */
public class H2JdbcPersistenceStorageProvider @JvmOverloads constructor(
    dataSource: DataSource,
    tableName: String = "agent_checkpoints",
    ttlSeconds: Long? = null,
    migrator: SQLPersistenceSchemaMigrator = H2JdbcPersistenceSchemaMigrator(dataSource, tableName),
    json: Json = defaultJson
) : JdbcPersistenceStorageProvider(dataSource, migrator, ttlSeconds, tableName) {

    override val upsertSql: String = """
        MERGE INTO $tableName (persistence_id, checkpoint_id, created_at, checkpoint_json, ttl_timestamp, version)
        KEY (persistence_id, checkpoint_id)
        VALUES (?, ?, ?, ?, ?, ?)
    """.trimIndent()
}
