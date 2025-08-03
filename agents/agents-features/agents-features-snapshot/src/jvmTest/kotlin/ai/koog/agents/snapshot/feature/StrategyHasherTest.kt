package ai.koog.agents.snapshot.feature

import ai.koog.agents.core.agent.entity.AIAgentStrategy
import ai.koog.agents.core.agent.entity.SubgraphMetadata
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.builder.forwardTo
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertIs

class StrategyHasherTest {

    @Test
    fun testDefaultStrategyHasherProducesConsistentHashes() = runTest {
        val hasher = DefaultStrategyHasher()
        val strategy = createTestStrategy("test-strategy")
        
        val result1 = hasher.computeHash(strategy)
        val result2 = hasher.computeHash(strategy)
        
        assertIs<HashComputationResult.Success>(result1)
        assertIs<HashComputationResult.Success>(result2)
        assertEquals(result1.hash, result2.hash, "Hash should be consistent for the same strategy")
        assertTrue(result1.hash.startsWith("sha256:"), "Hash should have SHA-256 prefix")
    }

    @Test
    fun testDifferentStrategiesProduceDifferentHashes() = runTest {
        val hasher = DefaultStrategyHasher()
        val strategy1 = createTestStrategy("strategy-1")
        val strategy2 = createTestStrategy("strategy-2").apply {
            // Give strategy2 different metadata to ensure different hash
            metadata = SubgraphMetadata(
                nodesMap = mapOf(
                    "strategy-2:start" to this.nodeStart,
                    "strategy-2:finish" to this.nodeFinish,
                    "strategy-2:extra-node" to this.nodeStart // Add extra node
                ),
                uniqueNames = false // Different uniqueness setting
            )
        }
        
        val result1 = hasher.computeHash(strategy1)
        val result2 = hasher.computeHash(strategy2)
        
        assertIs<HashComputationResult.Success>(result1)
        assertIs<HashComputationResult.Success>(result2)
        assertNotEquals(result1.hash, result2.hash, "Different strategies should produce different hashes")
    }

    @Test
    fun testStrategyWithUninitializedMetadata() = runTest {
        val hasher = DefaultStrategyHasher()
        val strategy = createStrategyWithoutMetadata("test")
        
        val result = hasher.computeHash(strategy)
        
        assertIs<HashComputationResult.Success>(result)
        assertTrue(result.hash.startsWith("sha256:"), "Should still produce a hash even without metadata")
    }

    @Test
    fun testNoOpStrategyHasher() = runTest {
        val hasher = NoOpStrategyHasher
        val strategy = createTestStrategy("test")
        
        val result = hasher.computeHash(strategy)
        
        assertIs<HashComputationResult.Unavailable>(result)
    }

    @Test
    fun testFailingStrategyHasher() = runTest {
        val errorMessage = "Test failure"
        val hasher = FailingStrategyHasher(errorMessage)
        val strategy = createTestStrategy("test")
        
        val result = hasher.computeHash(strategy)
        
        assertIs<HashComputationResult.Failed>(result)
        assertEquals(errorMessage, result.reason)
    }

    @Test
    fun testConfigComputeAndSetHash() = runTest {
        val config = PersistencyFeatureConfig().apply {
            strategyHasher = DefaultStrategyHasher()
        }
        val strategy = createTestStrategy("test-config")
        
        val hash = config.computeAndSetHash(strategy)
        
        assertNotNull(hash)
        assertEquals(hash, config.graphHash, "graphHash should be set to computed hash")
        assertTrue(hash.startsWith("sha256:"))
    }

    @Test
    fun testConfigWithNoHasher() = runTest {
        val config = PersistencyFeatureConfig()
        val strategy = createTestStrategy("test")
        
        val hash = config.computeAndSetHash(strategy)
        
        assertNull(hash, "Should return null when no hasher is configured")
        assertNull(config.graphHash, "graphHash should remain null")
    }

    @Test
    fun testConfigWithFailingHasher() = runTest {
        val config = PersistencyFeatureConfig().apply {
            strategyHasher = FailingStrategyHasher("Test error")
        }
        val strategy = createTestStrategy("test-failing")
        
        val hash = config.computeAndSetHash(strategy)
        
        assertNull(hash, "Should return null when hash computation fails")
        assertNull(config.graphHash, "graphHash should remain null on failure")
    }

    @Test
    fun testConfigValidation() {
        val config = PersistencyFeatureConfig()
        
        // Test validation with warnings - should not throw
        config.validate()
        
        // Test with potentially problematic settings
        config.apply {
            strategyHasher = DefaultStrategyHasher()
            graphVersion = 1
            autoComputeHash = true
            migrators += SimpleTestMigrator()
        }
        
        // Should log warnings but not throw
        config.validate()
    }

    @Test
    fun testHashIncludesNodeTypes() = runTest {
        val hasher = DefaultStrategyHasher()
        
        // Create strategies with same names but different metadata
        val strategy1 = createTestStrategy("test")
        val strategy2 = createTestStrategy("test").apply {
            metadata = SubgraphMetadata(
                nodesMap = mapOf(
                    "test:start" to this.nodeStart,
                    "test:finish" to this.nodeFinish,
                    "test:extra" to this.nodeStart // Different structure
                ),
                uniqueNames = true
            )
        }
        
        val result1 = hasher.computeHash(strategy1)
        val result2 = hasher.computeHash(strategy2)
        
        assertIs<HashComputationResult.Success>(result1)
        assertIs<HashComputationResult.Success>(result2)
        assertNotEquals(result1.hash, result2.hash, "Different node structures should produce different hashes")
    }

    private fun createTestStrategy(name: String): AIAgentStrategy<String, String> {
        val strategy = strategy<String, String>(name) {
            // Need at least one edge for a valid strategy
            edge(nodeStart forwardTo nodeFinish)
        }
        
        // Initialize metadata (this is normally done by the builder)
        strategy.metadata = SubgraphMetadata(
            nodesMap = mapOf(
                "$name:start" to strategy.nodeStart,
                "$name:finish" to strategy.nodeFinish
            ),
            uniqueNames = true
        )
        
        return strategy
    }

    private fun createStrategyWithoutMetadata(name: String): AIAgentStrategy<String, String> {
        return strategy<String, String>(name) {
            // Need at least one edge for a valid strategy
            edge(nodeStart forwardTo nodeFinish)
        }
        // Don't initialize metadata - this will test the uninitialized case
    }
}

/**
 * Simple test migrator for validation testing.
 */
private class SimpleTestMigrator : CheckpointMigrator {
    override fun canMigrate(strategyId: String?, from: Int, to: Int): Boolean = true
    override suspend fun migrate(data: AgentCheckpointData, toVersion: Int): AgentCheckpointData = data
}