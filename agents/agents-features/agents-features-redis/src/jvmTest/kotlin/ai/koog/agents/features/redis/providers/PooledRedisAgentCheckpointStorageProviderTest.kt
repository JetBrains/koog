package ai.koog.agents.features.redis.providers

import ai.koog.agents.snapshot.feature.AgentCheckpointData
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
 * Test class for the pooled Redis-based agent checkpoint storage provider.
 * Uses Testcontainers to spin up a Redis instance for testing.
 * 
 * Tests both basic functionality and connection pool-specific behavior.
 */
@OptIn(ExperimentalLettuceCoroutinesApi::class)
class PooledRedisAgentCheckpointStorageProviderTest {
    companion object {
        private const val REDIS_PORT = 6379
    }

    private lateinit var redis: GenericContainer<*>
    private lateinit var provider: PooledJVMRedisPersistencyStorageProvider
    private lateinit var redisClient: RedisClient

    @BeforeTest
    fun setup() {
        // Start Redis container
        redis = GenericContainer(DockerImageName.parse("redis:latest"))
            .withExposedPorts(REDIS_PORT)
        redis.start()

        // Create Redis client and provider with small pool for testing
        val redisUri = RedisURI.builder()
            .withHost(redis.host)
            .withPort(redis.getMappedPort(REDIS_PORT))
            .build()
        
        redisClient = RedisClient.create(redisUri)
        provider = PooledJVMRedisPersistencyStorageProvider(
            persistenceId = "testAgentId",
            redisUri = redisUri,
            keyPrefix = "test:agent",
            ttlSeconds = null,
            poolConfig = PooledJVMRedisPersistencyStorageProvider.PoolConfig(
                minIdle = 1,
                maxIdle = 3,
                maxTotal = 5
            )
        )
    }

    @AfterTest
    fun cleanup() {
        // Close provider and Redis connection
        provider.close()
        
        // Stop Redis container
        redis.stop()
    }

    @Test
    fun testBasicCheckpointOperations() = runTest {
        // Create a test checkpoint
        val checkpointId = "pooled-test-checkpoint"
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
    }

    @Test
    fun testConcurrentOperations() = runTest {
        // Test concurrent checkpoint operations to verify pool behavior
        // Using smaller number to avoid overwhelming the test container
        val numberOfOperations = 3
        val concurrentOps = (1..numberOfOperations).map { i ->
            async {
                val checkpoint = AgentCheckpointData(
                    checkpointId = "concurrent-checkpoint-$i",
                    createdAt = Clock.System.now(),
                    nodeId = "concurrent-node-$i",
                    lastInput = JsonPrimitive("concurrent-input-$i"),
                    messageHistory = emptyList()
                )
                provider.saveCheckpoint(checkpoint)
                
                // Also test concurrent retrieval operations
                provider.getCheckpoints()
            }
        }
        
        // Wait for all operations to complete
        concurrentOps.awaitAll()
        
        // Verify all checkpoints were saved
        val allCheckpoints = provider.getCheckpoints()
        assertEquals(numberOfOperations, allCheckpoints.size, "Should have all concurrent checkpoints")
        
        // Verify checkpoint IDs are unique and expected
        val checkpointIds = allCheckpoints.map { it.checkpointId }.toSet()
        assertEquals(numberOfOperations, checkpointIds.size, "All checkpoint IDs should be unique")
        
        for (i in 1..numberOfOperations) {
            assertTrue(
                checkpointIds.contains("concurrent-checkpoint-$i"),
                "Should contain checkpoint $i"
            )
        }
    }

    @Test
    fun testPoolStats() = runTest {
        // Get initial pool stats
        val initialStats = provider.getPoolStats()
        assertTrue(initialStats.numActive >= 0, "Active connections should be non-negative")
        assertTrue(initialStats.numIdle >= 0, "Idle connections should be non-negative")
        assertEquals(5, initialStats.maxTotal, "Max total should match configuration")
        assertEquals(3, initialStats.maxIdle, "Max idle should match configuration")
        assertEquals(1, initialStats.minIdle, "Min idle should match configuration")
        
        // Perform some operations and check stats again
        val checkpoint = AgentCheckpointData(
            checkpointId = "stats-test",
            createdAt = Clock.System.now(),
            nodeId = "stats-node",
            lastInput = JsonPrimitive("stats-input"),
            messageHistory = emptyList()
        )
        
        provider.saveCheckpoint(checkpoint)
        provider.getCheckpoints()
        
        val afterOpsStats = provider.getPoolStats()
        assertTrue(afterOpsStats.utilizationPercent >= 0.0, "Utilization should be non-negative")
        assertTrue(afterOpsStats.utilizationPercent <= 100.0, "Utilization should not exceed 100%")
    }

    @Test
    fun testDeleteOperations() = runTest {
        // Create and save a checkpoint
        val checkpointId = "pooled-delete-test"
        val checkpoint = AgentCheckpointData(
            checkpointId = checkpointId,
            createdAt = Clock.System.now(),
            nodeId = "delete-node",
            lastInput = JsonPrimitive("delete-test"),
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
    fun testMultipleAgentsIsolation() = runTest {
        // Create a second Redis client to avoid conflicts
        val redisUri2 = RedisURI.builder()
            .withHost(redis.host)
            .withPort(redis.getMappedPort(REDIS_PORT))
            .build()
        val redisClient2 = RedisClient.create(redisUri2)
        
        // Create a second provider with different persistence ID and separate client
        val provider2 = PooledJVMRedisPersistencyStorageProvider(
            persistenceId = "differentPooledAgentId",
            redisUri = redisUri2,
            keyPrefix = "test:agent",
            ttlSeconds = null,
            poolConfig = PooledJVMRedisPersistencyStorageProvider.PoolConfig(
                minIdle = 1,
                maxIdle = 2,
                maxTotal = 3
            )
        )
        
        try {
            // Save checkpoint for first agent
            val checkpoint1 = AgentCheckpointData(
                checkpointId = "pooled-agent1-checkpoint",
                createdAt = Clock.System.now(),
                nodeId = "pooled-node1",
                lastInput = JsonPrimitive("pooled-agent1-input"),
                messageHistory = emptyList()
            )
            provider.saveCheckpoint(checkpoint1)
            
            // Save checkpoint for second agent
            val checkpoint2 = AgentCheckpointData(
                checkpointId = "pooled-agent2-checkpoint",
                createdAt = Clock.System.now(),
                nodeId = "pooled-node2",
                lastInput = JsonPrimitive("pooled-agent2-input"),
                messageHistory = emptyList()
            )
            provider2.saveCheckpoint(checkpoint2)
            
            // Verify each agent only sees its own checkpoints
            val agent1Checkpoints = provider.getCheckpoints()
            assertEquals(1, agent1Checkpoints.size, "Agent 1 should only see its own checkpoint")
            assertEquals("pooled-agent1-checkpoint", agent1Checkpoints.first().checkpointId)
            
            val agent2Checkpoints = provider2.getCheckpoints()
            assertEquals(1, agent2Checkpoints.size, "Agent 2 should only see its own checkpoint")
            assertEquals("pooled-agent2-checkpoint", agent2Checkpoints.first().checkpointId)
            
            // Verify checkpoint counts are isolated
            assertEquals(1, provider.getCheckpointCount())
            assertEquals(1, provider2.getCheckpointCount())
        } finally {
            provider2.close()
            redisClient2.shutdown()
        }
    }

    @Test
    fun testTTLFunctionality() = runTest {
        // Create a separate Redis client for TTL testing to avoid conflicts
        val redisUriTTL = RedisURI.builder()
            .withHost(redis.host)
            .withPort(redis.getMappedPort(REDIS_PORT))
            .build()
        val redisClientTTL = RedisClient.create(redisUriTTL)
        
        // Create provider with 2 second TTL
        val ttlProvider = PooledJVMRedisPersistencyStorageProvider(
            persistenceId = "ttl-pooled-test-agent",
            redisUri = redisUriTTL,
            keyPrefix = "test:ttl",
            ttlSeconds = 2,
            poolConfig = PooledJVMRedisPersistencyStorageProvider.PoolConfig(
                minIdle = 1,
                maxIdle = 2,
                maxTotal = 3
            )
        )
        
        try {
            // Save a checkpoint
            val checkpoint = AgentCheckpointData(
                checkpointId = "ttl-pooled-checkpoint",
                createdAt = Clock.System.now(),
                nodeId = "ttl-pooled-node",
                lastInput = JsonPrimitive("ttl-pooled-test"),
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
            redisClientTTL.shutdown()
        }
    }

    @Test
    fun testEmptyState() = runTest {
        // Test that getCheckpoints returns empty list when no checkpoints exist
        val checkpoints = provider.getCheckpoints()
        assertEquals(0, checkpoints.size, "Should return empty list when no checkpoints exist")
        
        // Test that getLatestCheckpoint returns null when no checkpoints exist
        val latest = provider.getLatestCheckpoint()
        assertNull(latest, "Should return null when no checkpoints exist")
        
        // Test checkpoint count
        assertEquals(0, provider.getCheckpointCount())
    }
}