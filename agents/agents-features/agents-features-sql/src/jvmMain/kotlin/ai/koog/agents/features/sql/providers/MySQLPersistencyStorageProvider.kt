package ai.koog.agents.features.sql.providers

import ai.koog.agents.snapshot.providers.PersistencyUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction

/**
 * MySQL-specific implementation of [ExposedPersistencyStorageProvider] for managing
 * agent checkpoints in MySQL databases.
 *
 * This provider is optimized for MySQL 5.7+ and MariaDB 10.2+, leveraging their
 * JSON column support for efficient checkpoint storage.
 *
 * ## MySQL Features:
 * - JSON column support for structured data
 * - Efficient indexing with composite keys
 * - Transaction support with proper isolation levels
 * - Compatible with MySQL replication for HA setups
 *
 * ## Example Usage:
 * ```kotlin
 * val provider = MySQLPersistencyStorageProvider(
 *     persistenceId = "my-agent",
 *     database = Database.connect(
 *         url = "jdbc:mysql://localhost:3306/mydb?useSSL=false&serverTimezone=UTC",
 *         driver = "com.mysql.cj.jdbc.Driver",
 *         user = "user",
 *         password = "pass"
 *     ),
 *     ttlSeconds = 3600
 * )
 * ```
 *
 * @constructor Initializes the MySQL persistence provider with an Exposed Database instance.
 */
public class MySQLPersistencyStorageProvider(
    persistenceId: String,
    database: Database,
    tableName: String = "agent_checkpoints",
    ttlSeconds: Long? = null,
    migrator: SQLPersistenceSchemaMigrator = MySqlPersistenceSchemaMigrator(database, tableName),
    json: Json = PersistencyUtils.defaultCheckpointJson
) : ExposedPersistencyStorageProvider(persistenceId, database, tableName, ttlSeconds, migrator, json) {

    override suspend fun <T> transaction(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO, database) {
            block()
        }
}

/**
 * MySqlExposedMigrator is responsible for managing schema migrations for a MySQL table
 * using the Exposed SQL library. It ensures that the required table for storing checkpoint
 * data is created and configured appropriately.
 *
 * This class provides functionality to set up the database schema by creating the table with
 * appropriate indices, comments, and data types, including support for JSON fields and TTL
 * (Time-to-Live) timestamps.
 *
 * The migration process is executed asynchronously to integrate seamlessly into applications
 * that rely on coroutine-based concurrency.
 *
 * @param database The connection to the MySQL database where migrations will be applied
 * @param tableName The name of the table to be created or updated during migration
 */
public class MySqlPersistenceSchemaMigrator(private val database: Database, private val tableName: String) : SQLPersistenceSchemaMigrator {
    override suspend fun migrate() {
        transaction(database) {
            exec(
                """
            CREATE TABLE IF NOT EXISTS $tableName (
                persistence_id VARCHAR(255) NOT NULL,
                checkpoint_id VARCHAR(255) NOT NULL,
                created_at BIGINT NOT NULL,
                checkpoint_json JSON NOT NULL,
                ttl_timestamp BIGINT NULL,
                
                PRIMARY KEY (persistence_id, checkpoint_id),
                INDEX idx_${tableName}_created_at (created_at),
                INDEX idx_${tableName}_ttl_timestamp (ttl_timestamp)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """.trimIndent()
            )

            exec(
                """
            ALTER TABLE $tableName COMMENT = 'Stores checkpoint data with persistence and TTL support'
                """.trimIndent()
            )

            exec(
                """
            ALTER TABLE $tableName 
                MODIFY COLUMN persistence_id VARCHAR(255) NOT NULL COMMENT 'Identifier for the persistence context',
                MODIFY COLUMN checkpoint_id VARCHAR(255) NOT NULL COMMENT 'Unique identifier for the checkpoint',
                MODIFY COLUMN created_at BIGINT NOT NULL COMMENT 'Timestamp when the checkpoint was created (as BIGINT)',
                MODIFY COLUMN checkpoint_json JSON NOT NULL COMMENT 'JSON data of the checkpoint',
                MODIFY COLUMN ttl_timestamp BIGINT NULL COMMENT 'Time-to-live timestamp for automatic expiration (nullable)'
                """.trimIndent()
            )
        }
    }
}
