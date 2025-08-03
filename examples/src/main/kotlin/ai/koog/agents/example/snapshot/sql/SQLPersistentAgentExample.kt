package ai.koog.agents.example.snapshot.sql

import ai.koog.agents.snapshot.feature.AgentCheckpointData
import ai.koog.agents.features.sql.providers.*
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonPrimitive

/**
 * Examples demonstrating SQL-based persistence providers for agent checkpoints.
 * 
 * This example shows how to use different SQL databases (PostgreSQL, MySQL, H2, SQLite)
 * for persisting agent state across sessions.
 */
object SQLPersistentAgentExample {
    
    @JvmStatic
    fun main(args: Array<String>) = runBlocking {
        println("SQL Persistence Provider Examples")
        println("=================================\n")
        
        // Choose which example to run based on command line argument
        when (args.firstOrNull()) {
            "postgres" -> postgresqlExample()
            "mysql" -> mysqlExample()
            "h2" -> h2Example()
            "sqlite" -> sqliteExample()
            else -> {
                println("Usage: SQLPersistentAgentExample [postgres|mysql|h2|sqlite]")
                println("\nRunning SQLite example by default...\n")
                sqliteExample()
            }
        }
    }
    
    /**
     * PostgreSQL persistence example
     */
    private suspend fun postgresqlExample() {
        println("PostgreSQL Persistence Example")
        println("------------------------------")
        
        val provider = PostgresPersistencyStorageProvider(
            persistenceId = "postgres-agent",
            jdbcUrl = "jdbc:postgresql://localhost:5432/agents",
            username = "agent_user",
            password = "agent_pass",
            ttlSeconds = 3600 // 1 hour TTL
        )
        
        try {
            // Initialize schema
            provider.initializeSchema()
            
            // Create and save checkpoint
            val checkpoint = createSampleCheckpoint("postgres-checkpoint-1")
            provider.saveCheckpoint(checkpoint)
            println("Saved checkpoint: ${checkpoint.checkpointId}")
            
            // Retrieve checkpoint
            val retrieved = provider.getLatestCheckpoint()
            println("Retrieved latest checkpoint: ${retrieved?.checkpointId}")
            
            // Show pool stats if available
            provider.getPoolStats()?.let { stats ->
                println("\nConnection Pool Stats:")
                println("  Active: ${stats.activeConnections}")
                println("  Idle: ${stats.idleConnections}")
                println("  Utilization: ${stats.utilizationPercent}%")
            }
        } finally {
            provider.close()
        }
    }
    
    /**
     * MySQL persistence example
     */
    private suspend fun mysqlExample() {
        println("MySQL Persistence Example")
        println("-------------------------")
        
        val provider = MySQLPersistencyStorageProvider(
            persistenceId = "mysql-agent",
            jdbcUrl = "jdbc:mysql://localhost:3306/agents?useSSL=false&serverTimezone=UTC",
            username = "agent_user",
            password = "agent_pass",
            ttlSeconds = 7200 // 2 hours TTL
        )
        
        try {
            // Initialize schema
            provider.initializeSchema()
            
            // Save multiple checkpoints
            val checkpoints = listOf(
                createSampleCheckpoint("mysql-checkpoint-1"),
                createSampleCheckpoint("mysql-checkpoint-2"),
                createSampleCheckpoint("mysql-checkpoint-3")
            )
            
            checkpoints.forEach { checkpoint ->
                provider.saveCheckpoint(checkpoint)
                println("Saved: ${checkpoint.checkpointId}")
            }
            
            // Get all checkpoints
            val allCheckpoints = provider.getCheckpoints()
            println("\nTotal checkpoints: ${allCheckpoints.size}")
            
            // Get checkpoint count
            val count = provider.getCheckpointCount()
            println("Checkpoint count: $count")
        } finally {
            provider.close()
        }
    }
    
    /**
     * H2 persistence example (multiple modes)
     */
    private suspend fun h2Example() {
        println("H2 Database Persistence Examples")
        println("--------------------------------")
        
        // Example 1: In-memory database (for testing)
        println("\n1. In-Memory H2:")
        val inMemoryProvider = H2PersistencyStorageProvider.inMemory(
            persistenceId = "h2-test-agent",
            databaseName = "test_agents"
        )
        
        inMemoryProvider.initializeSchema()
        val testCheckpoint = createSampleCheckpoint("h2-memory-checkpoint")
        inMemoryProvider.saveCheckpoint(testCheckpoint)
        println("   Saved to in-memory: ${testCheckpoint.checkpointId}")
        
        // Example 2: File-based database (for persistence)
        println("\n2. File-Based H2:")
        val fileProvider = H2PersistencyStorageProvider.fileBased(
            persistenceId = "h2-file-agent",
            filePath = "./data/h2/agent_checkpoints",
            ttlSeconds = 86400 // 24 hours
        )
        
        fileProvider.initializeSchema()
        val fileCheckpoint = createSampleCheckpoint("h2-file-checkpoint")
        fileProvider.saveCheckpoint(fileCheckpoint)
        println("   Saved to file: ${fileCheckpoint.checkpointId}")
        
        // Example 3: PostgreSQL compatibility mode
        println("\n3. PostgreSQL Compatible Mode:")
        val pgCompatProvider = H2PersistencyStorageProvider.postgresCompatible(
            persistenceId = "h2-pgcompat-agent",
            databasePath = "file:./data/h2/pg_compat"
        )
        
        pgCompatProvider.initializeSchema()
        val pgCheckpoint = createSampleCheckpoint("h2-pgcompat-checkpoint")
        pgCompatProvider.saveCheckpoint(pgCheckpoint)
        println("   Saved with PG compatibility: ${pgCheckpoint.checkpointId}")
        
        // Clean up
        inMemoryProvider.close()
        fileProvider.close()
        pgCompatProvider.close()
    }
    
    /**
     * SQLite persistence example
     */
    private suspend fun sqliteExample() {
        println("SQLite Persistence Example")
        println("--------------------------")
        
        println("\nSQLite provider supports:")
        println("- In-memory databases for testing")
        println("- File-based persistence")
        println("- Temporary databases")
        println("- VACUUM and integrity check operations")
        
        // Just show instantiation works
        val provider = SQLitePersistencyStorageProvider.inMemory(
            persistenceId = "sqlite-demo",
            pragmas = emptyMap()
        )
        
        println("\nProvider created successfully!")
        println("Note: Due to SQLite transaction limitations with PRAGMAs,")
        println("full example requires careful configuration.")
    }
    
    /**
     * Creates a sample checkpoint for testing
     */
    private fun createSampleCheckpoint(checkpointId: String): AgentCheckpointData {
        return AgentCheckpointData(
            checkpointId = checkpointId,
            createdAt = Clock.System.now(),
            nodeId = "example-node",
            lastInput = JsonPrimitive("Sample input for $checkpointId"),
            messageHistory = listOf(
                Message.System("You are a helpful assistant", RequestMetaInfo.create(Clock.System)),
                Message.User("Hello, agent!", RequestMetaInfo.create(Clock.System)),
                Message.Assistant("Hello! How can I help you today?", ResponseMetaInfo.create(Clock.System))
            )
        )
    }
}