package ai.koog.agents.features.sql.providers

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.Database

/**
 * H2 Database-specific implementation of [ExposedPersistencyStorageProvider] for managing
 * agent checkpoints in H2 databases.
 *
 * H2 is a lightweight, embeddable Java SQL database that's perfect for:
 * - Development and testing environments
 * - Embedded applications
 * - In-memory caching with persistence
 * - Small to medium-scale production deployments
 *
 * ## H2 Modes:
 * 1. **In-Memory**: Fast, data lost on shutdown
 * 2. **File-Based**: Persistent storage in a single file
 * 3. **Server Mode**: Traditional client-server database
 * 4. **Mixed Mode**: Combination of embedded and server
 *
 * ## Features:
 * - Very fast, especially in-memory mode
 * - Small footprint (~2MB)
 * - Full SQL support with advanced features
 * - Compatible with PostgreSQL and MySQL modes
 * - Built-in web console for administration
 *
 * ## Example Usage:
 * ```kotlin
 * // In-memory database (for testing)
 * val inMemoryProvider = H2PersistencyStorageProvider.inMemory(
 *     persistenceId = "test-agent",
 *     databaseName = "test"
 * )
 *
 * // File-based database
 * val fileProvider = H2PersistencyStorageProvider.fileBased(
 *     persistenceId = "my-agent",
 *     filePath = "./data/agent-checkpoints",
 *     ttlSeconds = 3600
 * )
 *
 * // Server mode
 * val serverProvider = H2PersistencyStorageProvider(
 *     persistenceId = "my-agent",
 *     jdbcUrl = "jdbc:h2:tcp://localhost/~/test",
 *     username = "sa",
 *     password = ""
 * )
 * ```
 *
 * @constructor Initializes the H2 persistence provider with connection details.
 */
public class H2PersistencyStorageProvider : ExposedPersistencyStorageProvider {
    
    /**
     * Creates a provider with a JDBC URL and credentials.
     *
     * @param persistenceId Unique identifier for this agent's persistence data
     * @param jdbcUrl H2 JDBC connection URL
     * @param username Database username (default: "sa")
     * @param password Database password (default: empty)
     * @param tableName Name of the table to store checkpoints (default: "agent_checkpoints")
     * @param ttlSeconds Optional TTL for checkpoint entries in seconds
     */
    public constructor(
        persistenceId: String,
        jdbcUrl: String,
        username: String = "sa",
        password: String = "",
        tableName: String = "agent_checkpoints",
        ttlSeconds: Long? = null
    ) : super(
        persistenceId = persistenceId,
        database = Database.connect(
            url = jdbcUrl,
            driver = "org.h2.Driver",
            user = username,
            password = password
        ),
        tableName = tableName,
        ttlSeconds = ttlSeconds
    )
    
    /**
     * Creates a provider with HikariCP configuration for advanced pooling.
     *
     * @param persistenceId Unique identifier for this agent's persistence data
     * @param hikariConfig HikariCP configuration
     * @param tableName Name of the table to store checkpoints
     * @param ttlSeconds Optional TTL for checkpoint entries in seconds
     */
    public constructor(
        persistenceId: String,
        hikariConfig: HikariConfig,
        tableName: String = "agent_checkpoints",
        ttlSeconds: Long? = null
    ) : super(
        persistenceId = persistenceId,
        database = Database.connect(HikariDataSource(hikariConfig)),
        tableName = tableName,
        ttlSeconds = ttlSeconds
    ) {
        this.dataSource = HikariDataSource(hikariConfig)
    }
    
    /**
     * Creates a provider with an existing HikariDataSource.
     *
     * @param persistenceId Unique identifier for this agent's persistence data
     * @param dataSource Pre-configured HikariDataSource
     * @param tableName Name of the table to store checkpoints
     * @param ttlSeconds Optional TTL for checkpoint entries in seconds
     */
    public constructor(
        persistenceId: String,
        dataSource: HikariDataSource,
        tableName: String = "agent_checkpoints",
        ttlSeconds: Long? = null
    ) : super(
        persistenceId = persistenceId,
        database = Database.connect(dataSource),
        tableName = tableName,
        ttlSeconds = ttlSeconds
    ) {
        this.dataSource = dataSource
    }
    
    private var dataSource: HikariDataSource? = null
    
    public companion object {
        /**
         * Creates an in-memory H2 provider.
         * Data is lost when the JVM shuts down.
         * Perfect for testing and temporary caching.
         *
         * @param persistenceId Unique identifier for this agent's persistence data
         * @param databaseName Name of the in-memory database
         * @param options Additional H2 options (e.g., "DB_CLOSE_DELAY=-1")
         * @param tableName Name of the table to store checkpoints
         * @param ttlSeconds Optional TTL for checkpoint entries in seconds
         */
        public fun inMemory(
            persistenceId: String,
            databaseName: String = "test",
            options: String = "DB_CLOSE_DELAY=-1",
            tableName: String = "agent_checkpoints",
            ttlSeconds: Long? = null
        ): H2PersistencyStorageProvider {
            val jdbcUrl = "jdbc:h2:mem:$databaseName;$options"
            return H2PersistencyStorageProvider(
                persistenceId = persistenceId,
                jdbcUrl = jdbcUrl,
                tableName = tableName,
                ttlSeconds = ttlSeconds
            )
        }
        
        /**
         * Creates a file-based H2 provider.
         * Data is persisted to a file on disk.
         * Good balance between performance and persistence.
         *
         * @param persistenceId Unique identifier for this agent's persistence data
         * @param filePath Path to the database file (without .mv.db extension)
         * @param options Additional H2 options
         * @param tableName Name of the table to store checkpoints
         * @param ttlSeconds Optional TTL for checkpoint entries in seconds
         */
        public fun fileBased(
            persistenceId: String,
            filePath: String,
            options: String = "",
            tableName: String = "agent_checkpoints",
            ttlSeconds: Long? = null
        ): H2PersistencyStorageProvider {
            val jdbcUrl = if (options.isNotEmpty()) {
                "jdbc:h2:file:$filePath;$options"
            } else {
                "jdbc:h2:file:$filePath"
            }
            return H2PersistencyStorageProvider(
                persistenceId = persistenceId,
                jdbcUrl = jdbcUrl,
                tableName = tableName,
                ttlSeconds = ttlSeconds
            )
        }
        
        /**
         * Creates an H2 provider with PostgreSQL compatibility mode.
         * Useful when migrating from PostgreSQL or for compatibility testing.
         *
         * @param persistenceId Unique identifier for this agent's persistence data
         * @param databasePath Path to database (memory or file)
         * @param tableName Name of the table to store checkpoints
         * @param ttlSeconds Optional TTL for checkpoint entries in seconds
         */
        public fun postgresCompatible(
            persistenceId: String,
            databasePath: String = "mem:test",
            tableName: String = "agent_checkpoints",
            ttlSeconds: Long? = null
        ): H2PersistencyStorageProvider {
            val jdbcUrl = "jdbc:h2:$databasePath;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE"
            return H2PersistencyStorageProvider(
                persistenceId = persistenceId,
                jdbcUrl = jdbcUrl,
                tableName = tableName,
                ttlSeconds = ttlSeconds
            )
        }
    }
    
    /**
     * Closes the data source if it was created by this provider.
     * Should be called when the provider is no longer needed.
     */
    override fun close() {
        dataSource?.close()
    }
    
    /**
     * Returns connection pool statistics if using HikariCP.
     * Useful for monitoring connection usage and performance.
     */
    public fun getPoolStats(): PoolStats? {
        return dataSource?.let { ds ->
            PoolStats(
                activeConnections = ds.hikariPoolMXBean?.activeConnections ?: 0,
                idleConnections = ds.hikariPoolMXBean?.idleConnections ?: 0,
                totalConnections = ds.hikariPoolMXBean?.totalConnections ?: 0,
                threadsAwaitingConnection = ds.hikariPoolMXBean?.threadsAwaitingConnection ?: 0,
                maxPoolSize = ds.maximumPoolSize
            )
        }
    }
    
    /**
     * Connection pool statistics for monitoring.
     */
    public data class PoolStats(
        val activeConnections: Int,
        val idleConnections: Int,
        val totalConnections: Int,
        val threadsAwaitingConnection: Int,
        val maxPoolSize: Int
    ) {
        val utilizationPercent: Double = (activeConnections.toDouble() / maxPoolSize) * 100
        val isHighUtilization: Boolean = utilizationPercent > 80.0
    }
}