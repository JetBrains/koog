package ai.koog.agents.features.sql.providers

import ai.koog.agents.snapshot.providers.PersistenceUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction

/**
 * H2 Database-specific implementation of [ExposedPersistenceStorageProvider] for managing
 * agent checkpoints in H2 databases.
*/
public class H2PersistenceStorageProvider(
    database: Database,
    tableName: String = "agent_checkpoints",
    ttlSeconds: Long? = null,
    migrator: SQLPersistenceSchemaMigrator = H2PersistenceSchemaMigrator(database, tableName),
    json: Json = PersistenceUtils.defaultCheckpointJson
) : ExposedPersistenceStorageProvider(database, tableName, ttlSeconds, migrator, json) {

    public override suspend fun <T> transaction(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO, database) {
            block()
        }

    @Suppress("MissingKDocForPublicAPI")
    public companion object {
        /**
         * Creates an in-memory H2 provider.
         * Data is lost when the JVM shuts down.
         * Perfect for testing and temporary caching.
         *
         * @param databaseName Name of the in-memory database
         * @param options Additional H2 options (e.g., "DB_CLOSE_DELAY=-1")
         * @param tableName Name of the table to store checkpoints
         * @param ttlSeconds Optional TTL for checkpoint entries in seconds
         */
        public fun inMemory(
            databaseName: String = "test",
            options: String = "DB_CLOSE_DELAY=-1",
            tableName: String = "agent_checkpoints",
            ttlSeconds: Long? = null
        ): H2PersistenceStorageProvider = H2PersistenceStorageProvider(
            database = Database.connect("jdbc:h2:mem:$databaseName;$options"),
            tableName = tableName,
            ttlSeconds = ttlSeconds
        )

        /**
         * Creates a file-based H2 provider.
         * Data is persisted to a file on disk.
         * Good balance between performance and persistence.
         *
         * @param filePath Path to the database file (without .mv.db extension)
         * @param options Additional H2 options
         * @param tableName Name of the table to store checkpoints
         * @param ttlSeconds Optional TTL for checkpoint entries in seconds
         */
        public fun fileBased(
            filePath: String,
            options: String = "",
            tableName: String = "agent_checkpoints",
            ttlSeconds: Long? = null
        ): H2PersistenceStorageProvider = H2PersistenceStorageProvider(
            database = Database.connect(
                if (options.isNotEmpty()) {
                    "jdbc:h2:file:$filePath;$options"
                } else {
                    "jdbc:h2:file:$filePath"
                }
            ),
            tableName = tableName,
            ttlSeconds = ttlSeconds
        )

        /**
         * Creates an H2 provider with PostgreSQL compatibility mode.
         * Useful when migrating from PostgreSQL or for compatibility testing.
         *
         * @param databasePath Path to database (memory or file)
         * @param tableName Name of the table to store checkpoints
         * @param ttlSeconds Optional TTL for checkpoint entries in seconds
         */
        public fun postgresCompatible(
            databasePath: String = "mem:test",
            tableName: String = "agent_checkpoints",
            ttlSeconds: Long? = null
        ): H2PersistenceStorageProvider {
            return H2PersistenceStorageProvider(
                database = Database.connect("jdbc:h2:$databasePath;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE"),
                tableName = tableName,
                ttlSeconds = ttlSeconds
            )
        }
    }
}

/**
 * H2-specific implementation of the [SQLPersistenceSchemaMigrator] interface.
 *
 * This class is responsible for managing and executing schema migrations
 * for an H2 database using the Exposed SQL library. It allows for the creation
 * or modification of database tables and other schema components to ensure they
 * meet the required structure defined by the application's needs.
 *
 * The migration process is executed asynchronously as part of the [migrate] function,
 * providing seamless integration with coroutine-based applications.
 *
 * The implementation details of the migration process are specific to the H2 database and
 * may include adjustments for compatibility with its particular SQL dialect and features.
 *
 * Use this class when working with H2 as your database provider and schema migrations
 * are required during the application's lifecycle.
 */
public class H2PersistenceSchemaMigrator(private val database: Database, private val tableName: String) : SQLPersistenceSchemaMigrator {
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
