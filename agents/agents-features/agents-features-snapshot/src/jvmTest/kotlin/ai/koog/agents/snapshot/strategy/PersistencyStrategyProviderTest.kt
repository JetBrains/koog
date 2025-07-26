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
    private val testPersistenceId = "test-agent"
    
    @BeforeTest
    fun setUp() {
        mockContext = mockk(relaxed = true)
        testCheckpoint = AgentCheckpointData(
            checkpointId = Uuid.random().toString(),
            messageHistory = emptyList(),
            nodeId = "test-node",
            lastInput = JsonNull,
            createdAt = Clock.System.now()
        )
    }
    
    @AfterTest
    fun tearDown() {
        unmockkAll()
    }
    
    @Test
    fun testSingleStrategyProvider() = runTest {
        // Given
        val provider = InMemoryPersistencyStorageProvider(testPersistenceId)
        val strategy = PersistencyStrategy.Single(provider)
        val strategyProvider = PersistencyStrategyProvider(strategy, mockContext)
        
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
        val strategyProvider = PersistencyStrategyProvider(strategy, mockContext)
        
        // When
        strategyProvider.saveCheckpoint(testCheckpoint)
        val retrieved = strategyProvider.getLatestCheckpoint()
        
        // Then
        assertNull(retrieved)
    }
    
    @Test
    fun testFailoverStrategyWithWorkingProviders() = runTest {
        // Given
        val provider1 = InMemoryPersistencyStorageProvider("primary")
        val provider2 = InMemoryPersistencyStorageProvider("backup")
        
        val strategy = PersistencyStrategy.Failover(listOf(provider1, provider2))
        val strategyProvider = PersistencyStrategyProvider(strategy, mockContext)
        
        // When
        strategyProvider.saveCheckpoint(testCheckpoint)
        
        // Then - should use primary provider for writes
        assertNotNull(provider1.getLatestCheckpoint())
        assertNull(provider2.getLatestCheckpoint())
    }
    
    @Test
    fun testFailoverStrategyWithFailingPrimary() = runTest {
        // Given
        val failingProvider = mockk<PersistencyStorageProvider>()
        val workingProvider = InMemoryPersistencyStorageProvider("backup")
        
        // Make the first provider fail on health checks for write operations too
        coEvery { failingProvider.getCheckpoints() } throws RuntimeException("Primary failed")
        coEvery { failingProvider.getLatestCheckpoint() } throws RuntimeException("Primary failed") 
        coEvery { failingProvider.saveCheckpoint(any()) } throws RuntimeException("Primary failed")
        
        val strategy = PersistencyStrategy.Failover(listOf(failingProvider, workingProvider))
        val strategyProvider = PersistencyStrategyProvider(strategy, mockContext)
        
        // When - save should failover to working provider (health check fails on primary)
        strategyProvider.saveCheckpoint(testCheckpoint)
        
        // Then - should use the working provider
        assertEquals(testCheckpoint, workingProvider.getLatestCheckpoint())
        
        // Then - read should also use working provider  
        val retrieved = strategyProvider.getLatestCheckpoint()
        assertEquals(testCheckpoint, retrieved)
        
        // Verify failover happened on both operations
        coVerify(exactly = 2) { failingProvider.getCheckpoints() } // Once for save health check, once for read
    }
    
    @Test
    fun testDynamicStrategyProviderSelection() = runTest {
        // Given
        val ephemeralProvider = InMemoryPersistencyStorageProvider("ephemeral")
        val durableProvider = InMemoryPersistencyStorageProvider("durable")
        
        val providers = mapOf(
            "ephemeral" to ephemeralProvider,
            "durable" to durableProvider
        )
        
        var selectorCallCount = 0
        val selector: suspend (PersistencyStrategy.Dynamic.OperationContext) -> String = { context ->
            selectorCallCount++
            when (context.operation) {
                is PersistencyStrategy.Dynamic.Operation.SaveCheckpoint -> {
                    if (context.checkpoint?.nodeId?.contains("fast") == true) "ephemeral" else "durable"
                }
                else -> "durable"
            }
        }
        
        val strategy = PersistencyStrategy.Dynamic(providers, selector)
        val strategyProvider = PersistencyStrategyProvider(strategy, mockContext)
        
        // When - save with "fast" node
        val fastCheckpoint = testCheckpoint.copy(nodeId = "fast-processing")
        strategyProvider.saveCheckpoint(fastCheckpoint)
        
        // Then
        assertEquals(1, selectorCallCount)
        assertNotNull(ephemeralProvider.getLatestCheckpoint())
        assertNull(durableProvider.getLatestCheckpoint())
        
        // When - save with regular node
        val regularCheckpoint = testCheckpoint.copy(nodeId = "regular-processing")
        strategyProvider.saveCheckpoint(regularCheckpoint)
        
        // Then
        assertEquals(2, selectorCallCount)
        assertNotNull(durableProvider.getLatestCheckpoint())
    }
    
    @Test
    fun testHybridStrategyDefaultBehavior() = runTest {
        // Given
        val ephemeralProvider = InMemoryPersistencyStorageProvider("ephemeral")
        val durableProvider = InMemoryPersistencyStorageProvider("durable")
        
        val strategy = PersistencyStrategy.Hybrid(
            ephemeralProvider = ephemeralProvider,
            durableProvider = durableProvider
        )
        val strategyProvider = PersistencyStrategyProvider(strategy, mockContext)
        
        // When - save any checkpoint (default behavior: always use durable for saves)
        val checkpoint1 = testCheckpoint.copy(nodeId = "any-node")
        strategyProvider.saveCheckpoint(checkpoint1)
        
        // Then - should use durable provider for all saves by default
        assertNull(ephemeralProvider.getLatestCheckpoint())
        assertNotNull(durableProvider.getLatestCheckpoint())
        
        // When - save another checkpoint
        val checkpoint2 = testCheckpoint.copy(nodeId = "another-node")
        strategyProvider.saveCheckpoint(checkpoint2)
        
        // Then - should still use durable provider
        assertNull(ephemeralProvider.getLatestCheckpoint())
        assertNotNull(durableProvider.getLatestCheckpoint())
    }
    
    @Test
    fun testHybridStrategyCustomSelector() = runTest {
        // Given
        val ephemeralProvider = InMemoryPersistencyStorageProvider("ephemeral")
        val durableProvider = InMemoryPersistencyStorageProvider("durable")
        val criticalProvider = InMemoryPersistencyStorageProvider("critical")
        
        val selector: suspend (PersistencyStrategy.Dynamic.OperationContext) -> PersistencyStrategy.Hybrid.ProviderType = { context ->
            when {
                context.checkpoint?.nodeId == "critical" -> PersistencyStrategy.Hybrid.ProviderType.CRITICAL
                context.checkpoint?.nodeId?.startsWith("temp") == true -> PersistencyStrategy.Hybrid.ProviderType.EPHEMERAL
                else -> PersistencyStrategy.Hybrid.ProviderType.DURABLE
            }
        }
        
        val strategy = PersistencyStrategy.Hybrid(
            ephemeralProvider = ephemeralProvider,
            durableProvider = durableProvider,
            criticalProvider = criticalProvider,
            selector = selector
        )
        val strategyProvider = PersistencyStrategyProvider(strategy, mockContext)
        
        // Test ephemeral selection
        val tempCheckpoint = testCheckpoint.copy(nodeId = "temp-processing")
        strategyProvider.saveCheckpoint(tempCheckpoint)
        assertNotNull(ephemeralProvider.getLatestCheckpoint())
        
        // Test critical selection  
        val criticalCheckpoint = testCheckpoint.copy(nodeId = "critical")
        strategyProvider.saveCheckpoint(criticalCheckpoint)
        assertNotNull(criticalProvider.getLatestCheckpoint())
        
        // Test durable selection
        val regularCheckpoint = testCheckpoint.copy(nodeId = "regular")
        strategyProvider.saveCheckpoint(regularCheckpoint)
        assertNotNull(durableProvider.getLatestCheckpoint())
    }
    
    @Test
    fun testFailoverExhaustsAllProviders() = runTest {
        // Given
        val provider1 = mockk<PersistencyStorageProvider>()
        val provider2 = mockk<PersistencyStorageProvider>()
        val provider3 = mockk<PersistencyStorageProvider>()
        
        // All providers fail
        listOf(provider1, provider2, provider3).forEach { provider ->
            coEvery { provider.getCheckpoints() } throws RuntimeException("Provider failed")
            coEvery { provider.getLatestCheckpoint() } returns null
            coEvery { provider.saveCheckpoint(any()) } just Runs
        }
        
        val strategy = PersistencyStrategy.Failover(listOf(provider1, provider2, provider3))
        val strategyProvider = PersistencyStrategyProvider(strategy, mockContext)
        
        // When/Then
        assertFailsWith<IllegalStateException> {
            strategyProvider.getCheckpoints()
        }.also { exception ->
            assertTrue(exception.message?.contains("All 3 providers") == true)
        }
        
        // Verify all providers were tried
        coVerify(exactly = 1) { provider1.getCheckpoints() }
        coVerify(exactly = 1) { provider2.getCheckpoints() }
        coVerify(exactly = 1) { provider3.getCheckpoints() }
    }
    
    @Test
    fun testHybridStrategyReadOperations() = runTest {
        // Given
        val ephemeralProvider = InMemoryPersistencyStorageProvider("ephemeral")
        val durableProvider = InMemoryPersistencyStorageProvider("durable")
        
        // Add data to both providers
        val ephemeralCheckpoint = testCheckpoint.copy(checkpointId = "ephemeral-1")
        val durableCheckpoint = testCheckpoint.copy(checkpointId = "durable-1")
        
        ephemeralProvider.saveCheckpoint(ephemeralCheckpoint)
        durableProvider.saveCheckpoint(durableCheckpoint)
        
        val strategy = PersistencyStrategy.Hybrid(
            ephemeralProvider = ephemeralProvider,
            durableProvider = durableProvider
        )
        val strategyProvider = PersistencyStrategyProvider(strategy, mockContext)
        
        // When - getLatestCheckpoint (should try ephemeral first)
        val latest = strategyProvider.getLatestCheckpoint()
        
        // Then - should return ephemeral data since it has content
        assertEquals("ephemeral-1", latest?.checkpointId)
        
        // When - use empty ephemeral provider
        val emptyEphemeralProvider = InMemoryPersistencyStorageProvider("ephemeral-empty")
        val strategy2 = PersistencyStrategy.Hybrid(
            ephemeralProvider = emptyEphemeralProvider,
            durableProvider = durableProvider
        )
        val strategyProvider2 = PersistencyStrategyProvider(strategy2, mockContext)
        
        // Then - should fallback to durable
        val latest2 = strategyProvider2.getLatestCheckpoint()
        assertEquals("durable-1", latest2?.checkpointId)
    }
    
    @Test
    fun testDynamicStrategyWithInvalidProvider() = runTest {
        // Given
        val providers = mapOf(
            "valid" to InMemoryPersistencyStorageProvider("valid")
        )
        
        val selector: suspend (PersistencyStrategy.Dynamic.OperationContext) -> String = { _ ->
            "invalid" // Return non-existent provider
        }
        
        val strategy = PersistencyStrategy.Dynamic(providers, selector)
        val strategyProvider = PersistencyStrategyProvider(strategy, mockContext)
        
        // When/Then
        assertFailsWith<IllegalStateException> {
            strategyProvider.saveCheckpoint(testCheckpoint)
        }.also { exception ->
            assertTrue(exception.message?.contains("Provider 'invalid' not found") == true)
        }
    }
    
    @Test
    fun testFailoverWithEmptyProviderList() = runTest {
        // Given
        val strategy = PersistencyStrategy.Failover(emptyList())
        val strategyProvider = PersistencyStrategyProvider(strategy, mockContext)
        
        // When/Then
        assertFailsWith<IllegalStateException> {
            strategyProvider.getCheckpoints()
        }.also { exception ->
            assertTrue(exception.message?.contains("No providers configured") == true)
        }
    }
    
    @Test
    fun testConcurrentAccessToStrategyProvider() = runTest {
        // Given
        val provider = InMemoryPersistencyStorageProvider("concurrent")
        val strategy = PersistencyStrategy.Single(provider)
        val strategyProvider = PersistencyStrategyProvider(strategy, mockContext)
        
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
    fun testAutoSelectForTaskStructure() = runTest {
        // Given
        val redisProvider = InMemoryPersistencyStorageProvider("redis")
        val postgresProvider = InMemoryPersistencyStorageProvider("postgres")
        
        val providers = mapOf(
            "redis" to PersistencyStrategy.AutoSelectForTask.ProviderInfo(
                provider = redisProvider,
                description = "Fast in-memory cache with TTL support",
                capabilities = listOf("fast", "ephemeral")
            ),
            "postgres" to PersistencyStrategy.AutoSelectForTask.ProviderInfo(
                provider = postgresProvider,
                description = "Durable SQL database with ACID compliance",
                capabilities = listOf("durable", "queryable")
            )
        )
        
        val strategy = PersistencyStrategy.AutoSelectForTask(
            providers = providers,
            taskDescription = "High-frequency trading agent requiring fast operations",
            maxRetries = 3
        )
        
        // Then - verify structure (LLM interaction testing requires integration tests)
        assertEquals(2, strategy.providers.size)
        assertTrue(strategy.providers.containsKey("redis"))
        assertTrue(strategy.providers.containsKey("postgres"))
        assertEquals("High-frequency trading agent requiring fast operations", strategy.taskDescription)
        assertEquals(3, strategy.maxRetries)
        
        // Verify provider info
        val redisInfo = strategy.providers["redis"]!!
        assertEquals("Fast in-memory cache with TTL support", redisInfo.description)
        assertEquals(listOf("fast", "ephemeral"), redisInfo.capabilities)
        assertEquals(redisProvider, redisInfo.provider)
        
        val postgresInfo = strategy.providers["postgres"]!!
        assertEquals("Durable SQL database with ACID compliance", postgresInfo.description)
        assertEquals(listOf("durable", "queryable"), postgresInfo.capabilities)
        assertEquals(postgresProvider, postgresInfo.provider)
    }
    
    @Test
    fun testMultipleOperationTypes() = runTest {
        // Given
        val provider = InMemoryPersistencyStorageProvider(testPersistenceId)
        val strategy = PersistencyStrategy.Single(provider)
        val strategyProvider = PersistencyStrategyProvider(strategy, mockContext)
        
        // When - test multiple operations
        strategyProvider.saveCheckpoint(testCheckpoint)
        val checkpoints = strategyProvider.getCheckpoints()
        val latest = strategyProvider.getLatestCheckpoint()
        
        // Then
        assertEquals(1, checkpoints.size)
        assertEquals(testCheckpoint, checkpoints.first())
        assertEquals(testCheckpoint, latest)
    }
}