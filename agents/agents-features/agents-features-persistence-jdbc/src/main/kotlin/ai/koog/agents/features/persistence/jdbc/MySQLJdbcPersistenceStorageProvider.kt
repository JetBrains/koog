package ai.koog.agents.features.persistence.jdbc

import ai.koog.agents.features.sql.providers.SQLPersistenceSchemaMigrator
import kotlinx.serialization.json.Json
import javax.sql.DataSource

/**
 * MySQL-specific JDBC implementation of [JdbcPersistenceStorageProvider].
 *
 * Uses `INSERT ... ON DUPLICATE KEY UPDATE` for upsert operations.
 * Compatible with MySQL 5.7+ and MariaDB 10.2+.
 *
 * @param dataSource The JDBC DataSource for MySQL connections
 * @param tableName Name of the table to store checkpoints (default: "agent_checkpoints")
 * @param ttlSeconds Optional TTL for checkpoint entries in seconds (null = no expiration)
 * @param migrator Schema migrator for creating/updating the table
 * @param json JSON serializer instance for checkpoint serialization
 */
public class MySQLJdbcPersistenceStorageProvider @JvmOverloads constructor(
    dataSource: DataSource,
    tableName: String = "agent_checkpoints",
    ttlSeconds: Long? = null,
    migrator: SQLPersistenceSchemaMigrator = MySQLJdbcPersistenceSchemaMigrator(dataSource, tableName),
    json: Json = defaultJson
) : JdbcPersistenceStorageProvider(dataSource, migrator, ttlSeconds, tableName) {

    override val upsertSql: String = """
        INSERT INTO $tableName (persistence_id, checkpoint_id, created_at, checkpoint_json, ttl_timestamp, version)
        VALUES (?, ?, ?, ?, ?, ?)
        ON DUPLICATE KEY UPDATE
            created_at = VALUES(created_at),
            checkpoint_json = VALUES(checkpoint_json),
            ttl_timestamp = VALUES(ttl_timestamp),
            version = VALUES(version)
    """.trimIndent()
}
