package ai.koog.agents.features.sql.providers

import ai.koog.agents.snapshot.providers.PersistenceUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction

/**
 * PostgreSQL-specific implementation of [ExposedPersistenceStorageProvider] for managing
 * agent checkpoints in PostgreSQL databases.
 *
 * @constructor Initializes the PostgreSQL persistence provider with connection details.
 */
public class PostgresPersistenceStorageProvider @JvmOverloads constructor(
    database: Database,
    tableName: String = "agent_checkpoints",
    ttlSeconds: Long? = null,
    migrator: SQLPersistenceSchemaMigrator = PostgresPersistenceSchemaMigrator(database, tableName),
    json: Json = PersistenceUtils.defaultCheckpointJson
) : ExposedPersistenceStorageProvider(database, tableName, ttlSeconds, migrator, json) {

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

    /**
     * Companion object of [ai.koog.agents.features.sql.providers.PostgresPersistenceStorageProvider]
     * */
    public companion object {
        /**
         * Gets an instance of the [PostgresPersistenceStorageProvider.Builder]
         * */
        @JvmStatic
        public fun builder(): Builder = Builder()
    }

    /**
     * Helps constructing the [PostgresPersistenceStorageProvider]
     * */
    public class Builder internal constructor() {
        internal var database: Database? = null
        internal var tableName: String = "agent_checkpoints"
        internal var ttlSeconds: Long? = null
        internal var migrator: SQLPersistenceSchemaMigrator? = null
        internal var json: Json = PersistenceUtils.defaultCheckpointJson

        /**
         * Set the database connection
         * */
        public fun database(database: Database): Builder = apply { this.database = database }

        /**
         * Specify the pstgres table name to be used for saving agent checkpoints
         * */
        public fun tableName(tableName: String): Builder = apply { this.tableName = tableName }

        /**
         * Set TTL (in seconds) to be used by the database client
         * */
        public fun ttlSeconds(ttlSeconds: Long): Builder = apply { this.ttlSeconds = ttlSeconds }

        /**
         * Configure the [SQLPersistenceSchemaMigrator]
         * */
        public fun migrator(migration: SQLPersistenceSchemaMigrator): Builder = apply { this.migrator = migration }

        /**
         * Set custom [Json] instance to be used
         * */
        public fun json(json: Json): Builder = apply { this.json = json }

        /**
         * Create an instance of [PostgresPersistenceStorageProvider].
         * [database] must be set before calling this method
         * */
        public fun build(): PostgresPersistenceStorageProvider {
            assert(database != null) { "Database is required but was not set" }

            return PostgresPersistenceStorageProvider(
                database!!,
                tableName,
                ttlSeconds,
                migrator ?: PostgresPersistenceSchemaMigrator(database!!, tableName),
                json
            )
        }
    }
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
                version BIGINT NOT NULL,

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

            exec(
                """
            CREATE UNIQUE INDEX IF NOT EXISTS ${tableName}_persistence_id_version_idx ON $tableName (persistence_id, version);
                """.trimIndent()
            )
        }
    }
}
