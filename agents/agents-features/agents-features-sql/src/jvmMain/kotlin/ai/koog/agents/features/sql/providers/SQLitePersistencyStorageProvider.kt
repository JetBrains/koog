package ai.koog.agents.features.sql.providers

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File
import java.sql.ResultSet

/**
 * SQLite-specific implementation of [ExposedPersistencyStorageProvider] for managing
 * agent checkpoints in SQLite databases.
 *
 * SQLite is a self-contained, serverless, zero-configuration database engine that's 
 * ideal for:
 * - Embedded applications
 * - Local data storage
 * - Single-user applications
 * - Mobile and desktop apps
 * - Prototyping and development
 *
 * ## Features:
 * - Zero configuration required
 * - Single file database
 * - ACID transactions
 * - Very small footprint (~600KB)
 * - No separate server process
 * - Cross-platform
 *
 * ## Limitations:
 * - Single writer at a time (multiple readers OK)
 * - Limited concurrent access
 * - No user management
 * - Basic data types (JSON stored as TEXT)
 *
 * ## Example Usage:
 * ```kotlin
 * // In-memory database
 * val inMemoryProvider = SQLitePersistencyStorageProvider.inMemory(
 *     persistenceId = "test-agent"
 * )
 *
 * // File-based database
 * val fileProvider = SQLitePersistencyStorageProvider(
 *     persistenceId = "my-agent",
 *     databasePath = "./data/agent-checkpoints.db",
 *     ttlSeconds = 3600
 * )
 *
 * // Temporary database
 * val tempProvider = SQLitePersistencyStorageProvider.temporary(
 *     persistenceId = "temp-agent"
 * )
 * ```
 *
 * @constructor Initializes the SQLite persistence provider.
 */
public class SQLitePersistencyStorageProvider : ExposedPersistencyStorageProvider {
    
    /**
     * Creates a provider with a file-based SQLite database.
     *
     * @param persistenceId Unique identifier for this agent's persistence data
     * @param databasePath Path to the SQLite database file
     * @param tableName Name of the table to store checkpoints (default: "agent_checkpoints")
     * @param ttlSeconds Optional TTL for checkpoint entries in seconds
     * @param pragmas Optional PRAGMA statements to configure SQLite
     */
    public constructor(
        persistenceId: String,
        databasePath: String,
        tableName: String = "agent_checkpoints",
        ttlSeconds: Long? = null,
        pragmas: Map<String, String> = defaultPragmas()
    ) : super(
        persistenceId = persistenceId,
        database = createDatabase(databasePath, pragmas),
        tableName = tableName,
        ttlSeconds = ttlSeconds
    ) {
        this.databasePath = databasePath
        this.pragmas = pragmas
    }
    
    private var databasePath: String? = null
    private var pragmas: Map<String, String> = emptyMap()
    private var pragmasApplied = false
    
    public companion object {
        /**
         * Default PRAGMA settings for optimal performance and safety.
         */
        public fun defaultPragmas(): Map<String, String> = mapOf(
            "journal_mode" to "WAL", // Write-Ahead Logging for better concurrency
            "synchronous" to "NORMAL", // Good balance of safety and performance
            "foreign_keys" to "ON", // Enable foreign key constraints
            "busy_timeout" to "5000" // Wait up to 5 seconds for locks
        )
        
        /**
         * Creates an in-memory SQLite provider.
         * Data is lost when the connection is closed.
         * Perfect for testing and temporary storage.
         *
         * @param persistenceId Unique identifier for this agent's persistence data
         * @param tableName Name of the table to store checkpoints
         * @param pragmas Optional PRAGMA statements
         */
        public fun inMemory(
            persistenceId: String,
            tableName: String = "agent_checkpoints",
            pragmas: Map<String, String> = defaultPragmas()
        ): SQLitePersistencyStorageProvider {
            return SQLitePersistencyStorageProvider(
                persistenceId = persistenceId,
                databasePath = ":memory:",
                tableName = tableName,
                pragmas = pragmas
            )
        }
        
        /**
         * Creates a temporary SQLite provider.
         * Database file is created in the system temp directory.
         * Useful for short-lived operations.
         *
         * @param persistenceId Unique identifier for this agent's persistence data
         * @param prefix Prefix for the temporary file
         * @param tableName Name of the table to store checkpoints
         * @param ttlSeconds Optional TTL for checkpoint entries in seconds
         * @param pragmas Optional PRAGMA statements
         */
        public fun temporary(
            persistenceId: String,
            prefix: String = "agent-checkpoints-",
            tableName: String = "agent_checkpoints",
            ttlSeconds: Long? = null,
            pragmas: Map<String, String> = defaultPragmas()
        ): SQLitePersistencyStorageProvider {
            val tempFile = File.createTempFile(prefix, ".db")
            tempFile.deleteOnExit()
            
            return SQLitePersistencyStorageProvider(
                persistenceId = persistenceId,
                databasePath = tempFile.absolutePath,
                tableName = tableName,
                ttlSeconds = ttlSeconds,
                pragmas = pragmas
            )
        }
        
        /**
         * Creates a SQLite database with the specified PRAGMA settings.
         */
        private fun createDatabase(databasePath: String, pragmas: Map<String, String>): Database {
            val jdbcUrl = "jdbc:sqlite:$databasePath"
            return Database.connect(
                url = jdbcUrl,
                driver = "org.sqlite.JDBC"
            )
        }
    }
    
    /**
     * Applies PRAGMA settings to the database connection.
     * This is done separately from database creation to avoid transaction issues.
     */
    private suspend fun applyPragmas() {
        if (!pragmasApplied && pragmas.isNotEmpty()) {
            transaction {
                pragmas.forEach { (key, value) ->
                    TransactionManager.current().exec("PRAGMA $key = $value")
                }
            }
            pragmasApplied = true
        }
    }
    
    public override suspend fun initializeSchema() {
        // Apply PRAGMAs first
        applyPragmas()
        // Then create schema
        super.initializeSchema()
    }
    
    /**
     * Optimizes the database by running VACUUM.
     * This reclaims unused space and can improve performance.
     * Should be called during maintenance windows.
     */
    public suspend fun vacuum() {
        transaction {
            TransactionManager.current().exec("VACUUM")
        }
    }
    
    /**
     * Runs an integrity check on the database.
     * Returns true if the database passes all checks.
     */
    public suspend fun integrityCheck(): Boolean {
        return transaction {
            val result = TransactionManager.current().exec("PRAGMA integrity_check") { rs: ResultSet ->
                if (rs.next()) rs.getString(1) else null
            }
            result == "ok"
        }
    }
    
    /**
     * Gets the current size of the database file in bytes.
     * Returns null for in-memory databases.
     */
    public fun getDatabaseSize(): Long? {
        return databasePath?.let { path ->
            if (path != ":memory:") {
                File(path).length()
            } else {
                null
            }
        }
    }
    
    /**
     * Enables or disables foreign key constraints.
     * Useful for bulk operations or migrations.
     */
    public suspend fun setForeignKeysEnabled(enabled: Boolean) {
        transaction {
            TransactionManager.current().exec("PRAGMA foreign_keys = ${if (enabled) "ON" else "OFF"}")
        }
    }
    
    /**
     * Sets the busy timeout in milliseconds.
     * Determines how long SQLite waits for locks.
     */
    public suspend fun setBusyTimeout(milliseconds: Int) {
        transaction {
            TransactionManager.current().exec("PRAGMA busy_timeout = $milliseconds")
        }
    }
}