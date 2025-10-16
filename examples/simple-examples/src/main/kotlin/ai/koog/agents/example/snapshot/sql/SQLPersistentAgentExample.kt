package ai.koog.agents.example.snapshot.sql

import ai.koog.agents.features.sql.providers.H2PersistenceStorageProvider
import ai.koog.agents.features.sql.providers.MySQLPersistenceStorageProvider
import ai.koog.agents.features.sql.providers.PostgresPersistenceStorageProvider
import ai.koog.agents.snapshot.feature.AgentCheckpointData
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.exposed.sql.Database

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
            else -> {
                println("Usage: SQLPersistentAgentExample [postgres|mysql|h2]")
            }
        }
    }

    /**
     * PostgreSQL persistence example
     */
    private suspend fun postgresqlExample() {
        println("PostgreSQL Persistence Example")
        println("------------------------------")
        val agentId = "postgres-agent"

        val provider = PostgresPersistenceStorageProvider(
            database = Database.connect(
                url = "jdbc:postgresql://localhost:5432/agents",
                driver = "org.postgresql.Driver",
                user = "agent_user",
                password = "agent_pass"
            ),
            ttlSeconds = 3600 // 1 hour TTL
        )

        // Initialize schema
        provider.migrate()

        // Create and save checkpoint
        val checkpoint = createSampleCheckpoint("postgres-checkpoint-1", version = 0)
        provider.saveCheckpoint(agentId = agentId, agentCheckpointData = checkpoint)
        println("Saved checkpoint: ${checkpoint.checkpointId}")

        // Retrieve checkpoint
        val retrieved = provider.getLatestCheckpoint(agentId)
        println("Retrieved latest checkpoint: ${retrieved?.checkpointId}")
    }

    /**
     * MySQL persistence example
     */
    private suspend fun mysqlExample() {
        println("MySQL Persistence Example")
        println("-------------------------")
        val agentId = "postgres-agent"

        val provider = MySQLPersistenceStorageProvider(
            database = Database.connect(
                url = "jdbc:mysql://localhost:3306/agents?useSSL=false&serverTimezone=UTC",
                driver = "com.mysql.cj.jdbc.Driver",
                user = "agent_user",
                password = "agent_pass"
            ),
            ttlSeconds = 7200 // 2 hours TTL
        )

        // Initialize schema
        provider.migrate()

        // Save multiple checkpoints
        val checkpoints = listOf(
            createSampleCheckpoint("mysql-checkpoint-1", version = 1),
            createSampleCheckpoint("mysql-checkpoint-2", version = 2),
            createSampleCheckpoint("mysql-checkpoint-3", version = 3)
        )

        checkpoints.forEach { checkpoint ->
            provider.saveCheckpoint(agentId, checkpoint)
            println("Saved: ${checkpoint.checkpointId}")
        }

        // Get all checkpoints
        val allCheckpoints = provider.getCheckpoints(agentId)
        println("\nTotal checkpoints: ${allCheckpoints.size}")

        // Get checkpoint count
        val count = provider.getCheckpointCount(agentId)
        println("Checkpoint count: $count")
    }

    /**
     * H2 persistence example (multiple modes)
     */
    private suspend fun h2Example() {
        println("H2 Database Persistence Examples")
        println("--------------------------------")
        val agentId = "h2-test-agent"
        // Example 1: In-memory database (for testing)
        println("\n1. In-Memory H2:")
        val inMemoryProvider = H2PersistenceStorageProvider.inMemory(
            databaseName = "test_agents"
        )

        inMemoryProvider.migrate()
        val testCheckpoint = createSampleCheckpoint("h2-memory-checkpoint", version = 1)
        inMemoryProvider.saveCheckpoint(agentId, testCheckpoint)
        println("   Saved to in-memory: ${testCheckpoint.checkpointId}")

        val h2AgentId = "h2-file-agent"

        // Example 2: File-based database (for persistence)
        println("\n2. File-Based H2:")
        val fileProvider = H2PersistenceStorageProvider.fileBased(
            filePath = "./data/h2/agent_checkpoints",
            ttlSeconds = 86400 // 24 hours
        )

        fileProvider.migrate()
        val fileCheckpoint = createSampleCheckpoint("h2-file-checkpoint", version = 1)
        fileProvider.saveCheckpoint(h2AgentId, fileCheckpoint)
        println("   Saved to file: ${fileCheckpoint.checkpointId}")

        // Example 3: PostgreSQL compatibility mode
        println("\n3. PostgreSQL Compatible Mode:")

        val postgresAgentId = "postgres-agent"

        val pgCompatProvider = H2PersistenceStorageProvider(
            database = Database.connect(
                url = "jdbc:postgresql://localhost:5432/agents",
                driver = "org.postgresql.Driver",
                user = "agent_user",
                password = "agent_pass"
            ),
            ttlSeconds = 3600,
            tableName = "agent_checkpoints",
        )

        pgCompatProvider.migrate()
        val pgCheckpoint = createSampleCheckpoint("h2-pgcompat-checkpoint", version = 2)
        pgCompatProvider.saveCheckpoint(postgresAgentId, pgCheckpoint)
        println("   Saved with PG compatibility: ${pgCheckpoint.checkpointId}")
    }

    /**
     * Creates a sample checkpoint for testing
     */
    private fun createSampleCheckpoint(checkpointId: String, version: Long): AgentCheckpointData {
        return AgentCheckpointData(
            checkpointId = checkpointId,
            createdAt = Clock.System.now(),
            nodeId = "example-node",
            lastInput = JsonPrimitive("Sample input for $checkpointId"),
            messageHistory = listOf(
                Message.System("You are a helpful assistant", RequestMetaInfo.create(Clock.System)),
                Message.User("Hello, agent!", RequestMetaInfo.create(Clock.System)),
                Message.Assistant("Hello! How can I help you today?", ResponseMetaInfo.create(Clock.System))
            ),
            version = version
        )
    }
}
