package ai.koog.agents.snapshot.strategy

import ai.koog.agents.core.agent.context.AIAgentContextBase
import ai.koog.agents.snapshot.feature.AgentCheckpointData
import ai.koog.agents.snapshot.providers.InMemoryPersistencyStorageProvider
import ai.koog.agents.snapshot.providers.NoPersistencyStorageProvider
import ai.koog.agents.snapshot.providers.PersistencyStorageProvider
import io.mockk.*
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonNull
import kotlin.test.*
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class PersistencyStrategyTest {
    
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
    fun testSingleStrategy() = runTest {
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
    fun testNoneStrategy() = runTest {
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
    fun testFailoverStrategy() = runTest {
        // Given
        val failingProvider = mockk<PersistencyStorageProvider>()
        val workingProvider = InMemoryPersistencyStorageProvider(testPersistenceId)
        
        coEvery { failingProvider.getCheckpoints() } throws RuntimeException("Provider failed")
        coEvery { failingProvider.saveCheckpoint(any()) } just Runs
        coEvery { failingProvider.getLatestCheckpoint() } returns null
        
        // For failover, we put working provider first
        val strategy = PersistencyStrategy.Failover(listOf(workingProvider, failingProvider))
        val strategyProvider = PersistencyStrategyProvider(strategy, mockContext)
        
        // When
        strategyProvider.saveCheckpoint(testCheckpoint)
        val retrieved = strategyProvider.getLatestCheckpoint()
        
        // Then
        assertEquals(testCheckpoint, retrieved)
        
        // Test that failover happens on read when first provider fails
        val failFirstStrategy = PersistencyStrategy.Failover(listOf(failingProvider, workingProvider))
        val failFirstProvider = PersistencyStrategyProvider(failFirstStrategy, mockContext)
        
        // Should failover to working provider
        val retrievedAfterFailover = failFirstProvider.getLatestCheckpoint()
        assertEquals(testCheckpoint, retrievedAfterFailover)
    }
    
    @Test
    fun testDynamicStrategy() = runTest {
        // Given
        val ephemeralProvider = InMemoryPersistencyStorageProvider(testPersistenceId)
        val durableProvider = InMemoryPersistencyStorageProvider(testPersistenceId)
        
        val providers = mapOf(
            "ephemeral" to ephemeralProvider,
            "durable" to durableProvider
        )
        
        val selector: suspend (PersistencyStrategy.Dynamic.OperationContext) -> String = { context ->
            when (context.operation) {
                is PersistencyStrategy.Dynamic.Operation.SaveCheckpoint -> "ephemeral"
                else -> "durable"
            }
        }
        
        val strategy = PersistencyStrategy.Dynamic(providers, selector)
        val strategyProvider = PersistencyStrategyProvider(strategy, mockContext)
        
        // When
        strategyProvider.saveCheckpoint(testCheckpoint)
        
        // Then
        assertNotNull(ephemeralProvider.getLatestCheckpoint())
        assertNull(durableProvider.getLatestCheckpoint())
    }
    
    @Test
    fun testHybridStrategyDefaultBehavior() = runTest {
        // Given
        val ephemeralProvider = InMemoryPersistencyStorageProvider(testPersistenceId)
        val durableProvider = InMemoryPersistencyStorageProvider(testPersistenceId)
        
        val strategy = PersistencyStrategy.Hybrid(
            ephemeralProvider = ephemeralProvider,
            durableProvider = durableProvider
        )
        val strategyProvider = PersistencyStrategyProvider(strategy, mockContext)
        
        // When - save checkpoint (default heuristic returns false, so uses durable)
        strategyProvider.saveCheckpoint(testCheckpoint)
        
        // Then - should use durable provider by default
        assertNull(ephemeralProvider.getLatestCheckpoint())
        assertNotNull(durableProvider.getLatestCheckpoint())
    }
    
    @Test
    fun testHybridStrategyCustomSelector() = runTest {
        // Given
        val ephemeralProvider = InMemoryPersistencyStorageProvider(testPersistenceId)
        val durableProvider = InMemoryPersistencyStorageProvider(testPersistenceId)
        val criticalProvider = InMemoryPersistencyStorageProvider(testPersistenceId)
        
        val customSelector: suspend (PersistencyStrategy.Dynamic.OperationContext) -> PersistencyStrategy.Hybrid.ProviderType = { context ->
            when {
                context.metadata["critical"] == true -> PersistencyStrategy.Hybrid.ProviderType.CRITICAL
                context.checkpoint?.nodeId?.contains("important") == true -> PersistencyStrategy.Hybrid.ProviderType.DURABLE
                else -> PersistencyStrategy.Hybrid.ProviderType.EPHEMERAL
            }
        }
        
        val strategy = PersistencyStrategy.Hybrid(
            ephemeralProvider = ephemeralProvider,
            durableProvider = durableProvider,
            criticalProvider = criticalProvider,
            selector = customSelector
        )
        val strategyProvider = PersistencyStrategyProvider(strategy, mockContext)
        
        // When - save critical checkpoint
        val criticalCheckpoint = testCheckpoint.copy(checkpointId = "critical", nodeId = "critical-node")
        // We need to use a different approach since we can't pass metadata through saveCheckpoint
        // In real usage, this would be handled by the context
        
        // Test important node
        val importantCheckpoint = testCheckpoint.copy(checkpointId = "important", nodeId = "important-node")
        strategyProvider.saveCheckpoint(importantCheckpoint)
        
        // Then
        assertNotNull(durableProvider.getLatestCheckpoint())
        assertEquals("important", durableProvider.getLatestCheckpoint()?.checkpointId)
    }
    
    @Test
    fun testFailoverAllProvidersFail() = runTest {
        // Given
        val provider1 = mockk<PersistencyStorageProvider>()
        val provider2 = mockk<PersistencyStorageProvider>()
        
        coEvery { provider1.getCheckpoints() } throws RuntimeException("Provider 1 failed")
        coEvery { provider2.getCheckpoints() } throws RuntimeException("Provider 2 failed")
        
        val strategy = PersistencyStrategy.Failover(listOf(provider1, provider2))
        val strategyProvider = PersistencyStrategyProvider(strategy, mockContext)
        
        // When/Then
        assertFailsWith<IllegalStateException> {
            strategyProvider.getCheckpoints()
        }
    }
}