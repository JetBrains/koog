package ai.koog.agents.snapshot.strategy

import ai.koog.agents.core.agent.context.AIAgentContextBase
import ai.koog.agents.snapshot.feature.AgentCheckpointData
import ai.koog.agents.snapshot.providers.InMemoryPersistencyStorageProvider
import ai.koog.agents.snapshot.providers.PersistencyStorageProvider
import io.mockk.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonNull
import kotlin.test.*
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class PersistencyStrategyProviderTest {
    
    private lateinit var mockContext: AIAgentContextBase
    private lateinit var testCheckpoint: AgentCheckpointData
    private lateinit var registry: ProviderRegistry
    
    @BeforeTest
    fun setUp() {
        mockContext = mockk(relaxed = true) {
            every { id } returns "test-agent"
        }
        testCheckpoint = AgentCheckpointData(
            checkpointId = Uuid.random().toString(),
            messageHistory = emptyList(),
            nodeId = "test-node",
            lastInput = JsonNull,
            createdAt = Clock.System.now()
        )
        registry = ProviderRegistry()
    }
    
    @AfterTest
    fun tearDown() {
        unmockkAll()
    }
    
    @Test
    fun testFixedSingleStrategyProvider() = runTest {
        // Given
        val provider = InMemoryPersistencyStorageProvider("test-persistence")
        val providerId = registry.register(provider, "test-provider")
        val strategy = PersistencyStrategy.Fixed(CoordinationStrategies.Single(providerId))
        val strategyProvider = PersistencyStrategyProvider(strategy, registry, mockContext)
        
        // When
        strategyProvider.saveCheckpoint(testCheckpoint)
        val retrieved = strategyProvider.getLatestCheckpoint()
        
        // Then
        assertEquals(testCheckpoint, retrieved)
    }
    
    @Test
    fun testNoneStrategyProvider() = runTest {
        // Given
        val strategy = PersistencyStrategy.None
        val strategyProvider = PersistencyStrategyProvider(strategy, registry, mockContext)
        
        // When
        strategyProvider.saveCheckpoint(testCheckpoint)
        val retrieved = strategyProvider.getLatestCheckpoint()
        
        // Then
        assertNull(retrieved)
    }
    
    @Test
    fun testDynamicStrategyProviderSelection() = runTest {
        // Given
        val ephemeralProvider = InMemoryPersistencyStorageProvider("ephemeral")
        val durableProvider = InMemoryPersistencyStorageProvider("durable")
        
        val ephemeralId = registry.register(ephemeralProvider, "ephemeral")
        val durableId = registry.register(durableProvider, "durable")
        
        var selectorCallCount = 0
        val selector: suspend (PersistencyStrategy.Dynamic.AgentContext, ProviderRegistry) -> CoordinationStrategy = { context, _ ->
            selectorCallCount++
            // Agent-level routing based on agent ID or context characteristics
            if (context.agentContext.id.contains("fast")) {
                CoordinationStrategies.Single(ephemeralId)
            } else {
                CoordinationStrategies.Single(durableId)
            }
        }
        
        val strategy = PersistencyStrategy.Dynamic(selector)
        val strategyProvider = PersistencyStrategyProvider(strategy, registry, mockContext)
        
        // When - save multiple checkpoints (all should use same cached provider)
        val checkpoint1 = testCheckpoint.copy(nodeId = "first-checkpoint")
        val checkpoint2 = testCheckpoint.copy(nodeId = "second-checkpoint")
        
        strategyProvider.saveCheckpoint(checkpoint1)
        strategyProvider.saveCheckpoint(checkpoint2)
        
        // Then - selector called only once, all operations use the same provider
        assertEquals(1, selectorCallCount)
        
        // Since mockContext.id is "test-agent" (doesn't contain "fast"), should use durable provider
        assertNull(ephemeralProvider.getLatestCheckpoint())
        assertNotNull(durableProvider.getLatestCheckpoint())
        
        // Verify both checkpoints went to the same (durable) provider
        val checkpoints = durableProvider.getCheckpoints()
        assertEquals(2, checkpoints.size)
    }
    
    @Test
    fun testConcurrentAccessToStrategyProvider() = runTest {
        // Given
        val provider = InMemoryPersistencyStorageProvider("concurrent")
        val providerId = registry.register(provider, "concurrent")
        val strategy = PersistencyStrategy.Fixed(CoordinationStrategies.Single(providerId))
        val strategyProvider = PersistencyStrategyProvider(strategy, registry, mockContext)
        
        val numConcurrentOperations = 20
        val checkpoints = mutableListOf<AgentCheckpointData>()
        
        // When - perform concurrent save operations
        repeat(numConcurrentOperations) { i ->
            launch {
                val checkpoint = testCheckpoint.copy(
                    checkpointId = "concurrent-$i",
                    nodeId = "node-$i"
                )
                strategyProvider.saveCheckpoint(checkpoint)
                synchronized(checkpoints) {
                    checkpoints.add(checkpoint)
                }
            }
        }
        
        // Allow some time for all operations to complete
        kotlinx.coroutines.delay(50)
        
        // Then - all checkpoints should be saved
        val savedCheckpoints = provider.getCheckpoints()
        assertEquals(numConcurrentOperations, savedCheckpoints.size)
        
        // Verify all expected checkpoints are present
        val savedIds = savedCheckpoints.map { it.checkpointId }.toSet()
        repeat(numConcurrentOperations) { i ->
            assertTrue(savedIds.contains("concurrent-$i"), "Missing checkpoint concurrent-$i")
        }
    }
    
    @Test
    fun testAutoSelectCoordinationStructure() = runTest {
        // Given
        val redisProvider = InMemoryPersistencyStorageProvider("redis")
        val postgresProvider = InMemoryPersistencyStorageProvider("postgres")
        
        val redisId = registry.register(redisProvider, "redis")
        val postgresId = registry.register(postgresProvider, "postgres")
        
        val options = listOf(
            CoordinationStrategies.Single(redisId),
            CoordinationStrategies.Single(postgresId),
            CoordinationStrategies.WriteToAll(listOf(redisId, postgresId))
        )
        
        val strategy = PersistencyStrategy.AutoSelectCoordination(
            taskDescription = "High-frequency trading agent requiring fast operations",
            options = options,
            registry = registry,
            maxRetries = 3
        )
        
        // Then - verify structure (LLM interaction testing requires integration tests)
        assertEquals(3, strategy.options.size)
        assertEquals("High-frequency trading agent requiring fast operations", strategy.taskDescription)
        assertEquals(3, strategy.maxRetries)
        assertEquals(registry, strategy.registry)
        
        // Verify coordination options
        assertTrue(strategy.options[0] is CoordinationStrategies.Single)
        assertTrue(strategy.options[1] is CoordinationStrategies.Single)
        assertTrue(strategy.options[2] is CoordinationStrategies.WriteToAll)
    }

    @Test
    fun testWriteToAllCoordinationStrategy() = runTest {
        // Given
        val provider1 = InMemoryPersistencyStorageProvider("provider1")
        val provider2 = InMemoryPersistencyStorageProvider("provider2")
        val provider1Id = registry.register(provider1, "p1")
        val provider2Id = registry.register(provider2, "p2")
        
        val strategy = PersistencyStrategy.Fixed(
            CoordinationStrategies.WriteToAll(listOf(provider1Id, provider2Id), readFrom = provider1Id)
        )
        
        val strategyProvider = PersistencyStrategyProvider(strategy, registry, mockContext)
        
        // When
        strategyProvider.saveCheckpoint(testCheckpoint)
        
        // Then - checkpoint should be saved to both providers
        assertNotNull(provider1.getLatestCheckpoint())
        assertNotNull(provider2.getLatestCheckpoint())
        assertEquals(testCheckpoint.checkpointId, provider1.getLatestCheckpoint()?.checkpointId)
        assertEquals(testCheckpoint.checkpointId, provider2.getLatestCheckpoint()?.checkpointId)
        
        // But reads should come from the readFrom provider (provider1)
        val retrieved = strategyProvider.getLatestCheckpoint()
        assertEquals(testCheckpoint.checkpointId, retrieved?.checkpointId)
    }

    @Test
    fun testWriteAllBestEffortCoordinationStrategy() = runTest {
        // Given
        val workingProvider = InMemoryPersistencyStorageProvider("working")
        val failingProvider = FailingPersistencyStorageProvider()
        val workingId = registry.register(workingProvider, "working")
        val failingId = registry.register(failingProvider, "failing")
        
        val strategy = PersistencyStrategy.Fixed(
            CoordinationStrategies.WriteAllBestEffort(listOf(workingId, failingId), readFrom = workingId)
        )
        
        val strategyProvider = PersistencyStrategyProvider(strategy, registry, mockContext)
        
        // When/Then - should succeed even if one provider fails
        strategyProvider.saveCheckpoint(testCheckpoint)
        
        // Verify the working provider received the checkpoint
        assertNotNull(workingProvider.getLatestCheckpoint())
        assertEquals(testCheckpoint.checkpointId, workingProvider.getLatestCheckpoint()?.checkpointId)
    }

    @Test
    fun testWriteWithBackupCoordinationStrategy() = runTest {
        // Given
        val primaryProvider = InMemoryPersistencyStorageProvider("primary")
        val backupProvider = InMemoryPersistencyStorageProvider("backup")
        val primaryId = registry.register(primaryProvider, "primary")
        val backupId = registry.register(backupProvider, "backup")
        
        val strategy = PersistencyStrategy.Fixed(
            CoordinationStrategies.WriteWithBackup(primaryId, listOf(backupId))
        )
        
        val strategyProvider = PersistencyStrategyProvider(strategy, registry, mockContext)
        
        // When
        strategyProvider.saveCheckpoint(testCheckpoint)
        
        // Then - checkpoint should be saved to both primary and backup
        assertNotNull(primaryProvider.getLatestCheckpoint())
        assertNotNull(backupProvider.getLatestCheckpoint())
        assertEquals(testCheckpoint.checkpointId, primaryProvider.getLatestCheckpoint()?.checkpointId)
        assertEquals(testCheckpoint.checkpointId, backupProvider.getLatestCheckpoint()?.checkpointId)
    }

    @Test
    fun testPrioritizedCoordinationStrategy() = runTest {
        // Given
        val emptyProvider = InMemoryPersistencyStorageProvider("empty")
        val filledProvider = InMemoryPersistencyStorageProvider("filled")
        filledProvider.saveCheckpoint(testCheckpoint)
        
        val emptyId = registry.register(emptyProvider, "empty")
        val filledId = registry.register(filledProvider, "filled")
        
        val strategy = PersistencyStrategy.Fixed(
            CoordinationStrategies.Prioritized(listOf(emptyId, filledId))
        )
        
        val strategyProvider = PersistencyStrategyProvider(strategy, registry, mockContext)
        
        // When
        val result = strategyProvider.getLatestCheckpoint()
        
        // Then - should return from the first provider that has data (filled)
        assertNotNull(result)
        assertEquals(testCheckpoint.checkpointId, result.checkpointId)
    }

    @Test
    fun testPrioritizedCoordinationStrategyWithFastFirst() = runTest {
        // Given - test prioritized strategy with fast provider first (replaces FastestFirst)
        val fastProvider = InMemoryPersistencyStorageProvider("fast")
        val fallbackProvider = InMemoryPersistencyStorageProvider("fallback")
        fallbackProvider.saveCheckpoint(testCheckpoint)
        
        val fastId = registry.register(fastProvider, "fast")
        val fallbackId = registry.register(fallbackProvider, "fallback")
        
        val strategy = PersistencyStrategy.Fixed(
            CoordinationStrategies.Prioritized(listOf(fastId, fallbackId))
        )
        
        val strategyProvider = PersistencyStrategyProvider(strategy, registry, mockContext)
        
        // When
        val result = strategyProvider.getLatestCheckpoint()
        
        // Then - should fallback to second provider since first is empty
        assertNotNull(result)
        assertEquals(testCheckpoint.checkpointId, result.checkpointId)
    }

    @Test
    fun testWriteAllBestEffortFailsIfAllProvidersFail() = runTest {
        // Given
        val failingProvider1 = FailingPersistencyStorageProvider()
        val failingProvider2 = FailingPersistencyStorageProvider()
        val failing1Id = registry.register(failingProvider1, "fail1")
        val failing2Id = registry.register(failingProvider2, "fail2")
        
        val strategy = PersistencyStrategy.Fixed(
            CoordinationStrategies.WriteAllBestEffort(listOf(failing1Id, failing2Id))
        )
        
        val strategyProvider = PersistencyStrategyProvider(strategy, registry, mockContext)
        
        // When/Then - should fail when all providers fail
        assertFailsWith<IllegalStateException> {
            strategyProvider.saveCheckpoint(testCheckpoint)
        }
    }

    @Test
    fun testProviderRegistryTypesafety() {
        // Given
        val provider = InMemoryPersistencyStorageProvider("test")
        
        // When
        val providerId = registry.register(provider, "test-provider")
        
        // Then
        assertEquals("test-provider", providerId.value)
        assertTrue(registry.contains(providerId))
        assertEquals(provider, registry.get(providerId))
        
        // Test invalid provider access
        val invalidId = ProviderId("non-existent")
        assertFalse(registry.contains(invalidId))
        assertFailsWith<IllegalStateException> {
            registry.get(invalidId)
        }
    }

    /**
     * Mock provider that always fails operations for testing error handling
     */
    private class FailingPersistencyStorageProvider : PersistencyStorageProvider {
        override suspend fun saveCheckpoint(agentCheckpointData: AgentCheckpointData) {
            throw RuntimeException("Simulated provider failure")
        }

        override suspend fun getCheckpoints(): List<AgentCheckpointData> {
            throw RuntimeException("Simulated provider failure")
        }

        override suspend fun getLatestCheckpoint(): AgentCheckpointData? {
            throw RuntimeException("Simulated provider failure")
        }
    }
}