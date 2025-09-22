package ai.koog.agents.features.sql.providers

import ai.koog.agents.snapshot.providers.PersistencyUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction

/**
 * PostgreSQL-specific implementation of [ExposedPersistencyStorageProvider] for managing
 * agent checkpoints in PostgreSQL databases.
 *
 * @constructor Initializes the PostgreSQL persistence provider with connection details.
 */
public class PostgresPersistencyStorageProvider(
    persistenceId: String,
    database: Database,
    tableName: String = "agent_checkpoints",
    ttlSeconds: Long? = null,
    migrator: SQLPersistenceSchemaMigrator = PostgresPersistenceSchemaMigrator(database, tableName),
    json: Json = PersistencyUtils.defaultCheckpointJson
) : ExposedPersistencyStorageProvider(persistenceId, database, tableName, ttlSeconds, migrator, json) {

    override suspend fun <T> transaction(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO, database) { block() }

    /**
     * PostgreSQL-optimized table with JSONB column.
     */
    override val checkpointsTable: PostgresCheckpointsTable = PostgresCheckpointsTable(tableName)

    /**
     * PostgreSQL-specific table definition.
     * Note: Currently uses TEXT for JSON storage. Future versions may use JSONB when Exposed adds better support.
     */
    public class PostgresCheckpointsTable(tableName: String) : CheckpointsTable(tableName)
}

/**
 * Implementation of [SQLPersistenceSchemaMigrator] for handling schema migrations in PostgreSQL
 * databases using the Exposed SQL library.
 *
 * This class focuses on PostgreSQL-specific schema migration requirements and provides
 * mechanisms to apply the necessary schema updates to ensure database compatibility.
 *
 * The [migrate] function, when implemented, will handle the execution of the schema
 * migrations asynchronously, allowing for seamless database updates as part of
 * an application's lifecycle.
 *
 * Designed to work with PostgreSQL, this migrator ensures that schema operations
 * respect PostgreSQL constraints, data types, and optimizations.
 */
public class PostgresPersistenceSchemaMigrator(private val database: Database, private val tableName: String) :
    SQLPersistenceSchemaMigrator {
    override suspend fun migrate() {
        transaction(database) {
            // Execute the raw PostgreSQL DDL
            exec(
                """
            -- Create the checkpoints table
            CREATE TABLE IF NOT EXISTS $tableName (
                persistence_id VARCHAR(255) NOT NULL,
                checkpoint_id VARCHAR(255) NOT NULL,
                created_at BIGINT NOT NULL,
                checkpoint_json TEXT NOT NULL,
                ttl_timestamp BIGINT NULL,
                
                -- Primary key constraint
                CONSTRAINT ${tableName}_pkey PRIMARY KEY (persistence_id, checkpoint_id)
            )
                """.trimIndent()
            )

            // Create indexes
            exec(
                """
            CREATE INDEX IF NOT EXISTS idx_${tableName}_created_at ON $tableName (created_at)
                """.trimIndent()
            )

            exec(
                """
            CREATE INDEX IF NOT EXISTS idx_${tableName}_ttl_timestamp ON $tableName (ttl_timestamp)
                """.trimIndent()
            )
        }
    }
}
