package ai.koog.agents.features.sql.providers

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.Database

/**
 * MySQL-specific implementation of [ExposedPersistencyStorageProvider] for managing
 * agent checkpoints in MySQL databases.
 *
 * This provider is optimized for MySQL 5.7+ and MariaDB 10.2+, leveraging their 
 * JSON column support for efficient checkpoint storage.
 *
 * ## Connection Options:
 * 1. JDBC URL: Direct connection string
 * 2. HikariCP: Advanced connection pooling with monitoring
 * 3. External DataSource: Integrate with existing connection pools
 *
 * ## MySQL Features:
 * - JSON column support for structured data
 * - Efficient indexing with composite keys
 * - Transaction support with proper isolation levels
 * - Compatible with MySQL replication for HA setups
 *
 * ## Example Usage:
 * ```kotlin
 * // Using JDBC URL
 * val provider = MySQLPersistencyStorageProvider(
 *     persistenceId = "my-agent",
 *     jdbcUrl = "jdbc:mysql://localhost:3306/mydb?useSSL=false&serverTimezone=UTC",
 *     username = "user",
 *     password = "pass",
 *     ttlSeconds = 3600
 * )
 *
 * // Using HikariCP configuration
 * val hikariConfig = HikariConfig().apply {
 *     jdbcUrl = "jdbc:mysql://localhost:3306/mydb"
 *     username = "user"
 *     password = "pass"
 *     maximumPoolSize = 10
 *     addDataSourceProperty("useSSL", "false")
 *     addDataSourceProperty("serverTimezone", "UTC")
 * }
 * val provider = MySQLPersistencyStorageProvider(
 *     persistenceId = "my-agent",
 *     hikariConfig = hikariConfig
 * )
 * ```
 *
 * @constructor Initializes the MySQL persistence provider with connection details.
 */
public class MySQLPersistencyStorageProvider : ExposedPersistencyStorageProvider {
    
    /**
     * Creates a provider with a JDBC URL and credentials.
     *
     * @param persistenceId Unique identifier for this agent's persistence data
     * @param jdbcUrl MySQL JDBC connection URL
     * @param username Database username
     * @param password Database password
     * @param tableName Name of the table to store checkpoints (default: "agent_checkpoints")
     * @param ttlSeconds Optional TTL for checkpoint entries in seconds
     */
    public constructor(
        persistenceId: String,
        jdbcUrl: String,
        username: String,
        password: String,
        tableName: String = "agent_checkpoints",
        ttlSeconds: Long? = null
    ) : super(
        persistenceId = persistenceId,
        database = Database.connect(
            url = jdbcUrl,
            driver = "com.mysql.cj.jdbc.Driver",
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