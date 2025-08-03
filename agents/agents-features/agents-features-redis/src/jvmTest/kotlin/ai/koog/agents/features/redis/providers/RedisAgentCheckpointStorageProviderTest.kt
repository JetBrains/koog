package ai.koog.agents.features.redis.providers

import ai.koog.agents.snapshot.feature.AgentCheckpointData
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonPrimitive
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Test class for Redis-based agent checkpoint storage provider.
 * Uses Testcontainers to spin up a Redis instance for testing.
 */
@OptIn(ExperimentalLettuceCoroutinesApi::class)
class RedisAgentCheckpointStorageProviderTest {
    companion object {
        private const val REDIS_PORT = 6379
    }

    private lateinit var redis: GenericContainer<*>
    private lateinit var provider: JVMRedisPersistencyStorageProvider
    private lateinit var redisClient: RedisClient

    @BeforeTest
    fun setup() {
        // Start Redis container
        redis = GenericContainer(DockerImageName.parse("redis:latest"))
            .withExposedPorts(REDIS_PORT)
        redis.start()

        // Create Redis client and provider
        val redisUri = RedisURI.builder()
            .withHost(redis.host)
            .withPort(redis.getMappedPort(REDIS_PORT))
            .build()
        
        redisClient = RedisClient.create(redisUri)
        provider = JVMRedisPersistencyStorageProvider(
            persistenceId = "testAgentId",
            redisClient = redisClient,
            keyPrefix = "test:agent",
            ttlSeconds = null // No TTL for tests
        )
    }

    @AfterTest
    fun cleanup() {
        // Close Redis connection
        provider.close()
        
        // Stop Redis container
        redis.stop()
    }

    @Test
    fun testSaveAndRetrieveCheckpoint() = runTest {
        // Create a test checkpoint
        val checkpointId = "test-checkpoint"
        val createdAt = Clock.System.now()
        val nodeId = "test-node"
        val lastInput = JsonPrimitive("test-input")
        val time = Clock.System.now()
        val messageHistory = listOf(
            Message.User("Hello", metaInfo = RequestMetaInfo(time)),
            Message.Assistant("Hi there!", metaInfo = ResponseMetaInfo(time))
        )

        val checkpoint = AgentCheckpointData(
            checkpointId = checkpointId,
            createdAt = createdAt,
            nodeId = nodeId,
            lastInput = lastInput,
            messageHistory = messageHistory
        )

        // Save the checkpoint
        provider.saveCheckpoint(checkpoint)

        // Retrieve all checkpoints for the agent
        val checkpoints = provider.getCheckpoints()
        assertEquals(1, checkpoints.size, "Should have one checkpoint")

        // Verify the retrieved checkpoint
        val retrievedCheckpoint = checkpoints.first()
        assertEquals(checkpointId, retrievedCheckpoint.checkpointId)
        assertEquals(createdAt, retrievedCheckpoint.createdAt)
        assertEquals(nodeId, retrievedCheckpoint.nodeId)
        assertEquals(lastInput, retrievedCheckpoint.lastInput)
        assertEquals(messageHistory.size, retrievedCheckpoint.messageHistory.size)
        
        // Check first message (User)
        val originalUserMsg = messageHistory[0] as Message.User
        val retrievedUserMsg = retrievedCheckpoint.messageHistory[0] as Message.User
        assertEquals(originalUserMsg.content, retrievedUserMsg.content)
        
        // Check second message (Assistant)
        val originalAssistantMsg = messageHistory[1] as Message.Assistant
        val retrievedAssistantMsg = retrievedCheckpoint.messageHistory[1] as Message.Assistant
        assertEquals(originalAssistantMsg.content, retrievedAssistantMsg.content)

        // Test getLatestCheckpoint
        val latestCheckpoint = provider.getLatestCheckpoint()
        assertNotNull(latestCheckpoint, "Latest checkpoint should not be null")
        assertEquals(checkpointId, latestCheckpoint.checkpointId)

        // Create a second checkpoint with a later timestamp
        val laterCheckpointId = "later-checkpoint"
        val laterCreatedAt = Clock.System.now()
        val laterCheckpoint = AgentCheckpointData(
            checkpointId = laterCheckpointId,
            createdAt = laterCreatedAt,
            nodeId = nodeId,
            lastInput = lastInput,
            messageHistory = messageHistory
        )

        // Save the later checkpoint
        provider.saveCheckpoint(laterCheckpoint)

        // Verify that getLatestCheckpoint returns the later checkpoint
        val newLatestCheckpoint = provider.getLatestCheckpoint()
        assertNotNull(newLatestCheckpoint, "New latest checkpoint should not be null")
        assertEquals(laterCheckpointId, newLatestCheckpoint.checkpointId)

        // Verify that getCheckpoints returns both checkpoints in order
        val allCheckpoints = provider.getCheckpoints()
        assertEquals(2, allCheckpoints.size, "Should have two checkpoints")
        assertTrue(allCheckpoints.any { it.checkpointId == checkpointId })
        assertTrue(allCheckpoints.any { it.checkpointId == laterCheckpointId })
        
        // Verify ordering (newer checkpoint should be last)
        assertEquals(laterCheckpointId, allCheckpoints.last().checkpointId)
    }

    @Test
    fun testDeleteCheckpoint() = runTest {
        // Create and save a checkpoint
        val checkpointId = "delete-test-checkpoint"
        val checkpoint = AgentCheckpointData(
            checkpointId = checkpointId,
            createdAt = Clock.System.now(),
            nodeId = "test-node",
            lastInput = JsonPrimitive("test"),
            messageHistory = emptyList()
        )
        
        provider.saveCheckpoint(checkpoint)
        
        // Verify it exists
        val checkpoints = provider.getCheckpoints()
        assertEquals(1, checkpoints.size)
        
        // Delete the checkpoint
        provider.deleteCheckpoint(checkpointId)
        
        // Verify it's gone
        val afterDelete = provider.getCheckpoints()
        assertEquals(0, afterDelete.size)
        
        // Verify getLatestCheckpoint returns null
        val latest = provider.getLatestCheckpoint()
        assertNull(latest)
    }

    @Test
    fun testGetCheckpointCount() = runTest {
        // Initially should be 0
        assertEquals(0, provider.getCheckpointCount())
        
        // Add first checkpoint
        provider.saveCheckpoint(
            AgentCheckpointData(
                checkpointId = "checkpoint-1",
                createdAt = Clock.System.now(),
                nodeId = "node-1",
                lastInput = JsonPrimitive("input-1"),
                messageHistory = emptyList()
            )
        )
        assertEquals(1, provider.getCheckpointCount())
        
        // Add second checkpoint
        provider.saveCheckpoint(
            AgentCheckpointData(
                checkpointId = "checkpoint-2",
                createdAt = Clock.System.now(),
                nodeId = "node-2",
                lastInput = JsonPrimitive("input-2"),
                messageHistory = emptyList()
            )
        )
        assertEquals(2, provider.getCheckpointCount())
        
        // Delete one checkpoint
        provider.deleteCheckpoint("checkpoint-1")
        assertEquals(1, provider.getCheckpointCount())
    }

    @Test
    fun testMultipleAgentsIsolation() = runTest {
        // Create a second provider with different persistence ID
        val provider2 = JVMRedisPersistencyStorageProvider(
            persistenceId = "differentAgentId",
            redisClient = redisClient,
            keyPrefix = "test:agent",
            ttlSeconds = null
        )
        
        try {
            // Save checkpoint for first agent
            val checkpoint1 = AgentCheckpointData(
                checkpointId = "agent1-checkpoint",
                createdAt = Clock.System.now(),
                nodeId = "node1",
                lastInput = JsonPrimitive("agent1-input"),
                messageHistory = emptyList()
            )
            provider.saveCheckpoint(checkpoint1)
            
            // Save checkpoint for second agent
            val checkpoint2 = AgentCheckpointData(
                checkpointId = "agent2-checkpoint",
                createdAt = Clock.System.now(),
                nodeId = "node2",
                lastInput = JsonPrimitive("agent2-input"),
                messageHistory = emptyList()
            )
            provider2.saveCheckpoint(checkpoint2)
            
            // Verify each agent only sees its own checkpoints
            val agent1Checkpoints = provider.getCheckpoints()
            assertEquals(1, agent1Checkpoints.size, "Agent 1 should only see its own checkpoint")
            assertEquals("agent1-checkpoint", agent1Checkpoints.first().checkpointId)
            
            val agent2Checkpoints = provider2.getCheckpoints()
            assertEquals(1, agent2Checkpoints.size, "Agent 2 should only see its own checkpoint")
            assertEquals("agent2-checkpoint", agent2Checkpoints.first().checkpointId)
            
            // Verify checkpoint counts are isolated
            assertEquals(1, provider.getCheckpointCount())
            assertEquals(1, provider2.getCheckpointCount())
        } finally {
            provider2.close()
        }
    }
    
    @Test
    fun testEmptyCheckpointsReturnsEmptyList() = runTest {
        // Test that getCheckpoints returns empty list when no checkpoints exist
        val checkpoints = provider.getCheckpoints()
        assertEquals(0, checkpoints.size, "Should return empty list when no checkpoints exist")
        
        // Test that getLatestCheckpoint returns null when no checkpoints exist
        val latest = provider.getLatestCheckpoint()
        assertNull(latest, "Should return null when no checkpoints exist")
    }
    
    @Test
    fun testTTLFunctionality() = runTest {
        // Create provider with 2 second TTL
        val ttlProvider = JVMRedisPersistencyStorageProvider(
            persistenceId = "ttl-test-agent",
            redisClient = redisClient,
            keyPrefix = "test:ttl",
            ttlSeconds = 2
        )
        
        try {
            // Save a checkpoint
            val checkpoint = AgentCheckpointData(
                checkpointId = "ttl-checkpoint",
                createdAt = Clock.System.now(),
                nodeId = "ttl-node",
                lastInput = JsonPrimitive("ttl-test"),
                messageHistory = emptyList()
            )
            
            ttlProvider.saveCheckpoint(checkpoint)
            
            // Verify it exists
            val checkpoints = ttlProvider.getCheckpoints()
            assertEquals(1, checkpoints.size)
            
            // Also verify the sorted set has the entry
            val checkpointCount = ttlProvider.getCheckpointCount()
            assertEquals(1, checkpointCount)
            
            // Switch to real time for Redis operations
            withContext(Dispatchers.IO) {
                // Wait for TTL to expire with some buffer
                delay(3000) // 2 second TTL + 1 second buffer
                
                // Verify checkpoint has expired (getCheckpoints will clean up the sorted set)
                val afterTTL = ttlProvider.getCheckpoints()
                assertEquals(0, afterTTL.size, "Checkpoint should have expired after TTL")
            }
        } finally {
            ttlProvider.close()
        }
    }
}