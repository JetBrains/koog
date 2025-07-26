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
    fun testDynamicStrategyProviderSelection() = runTest {
        // Given
        val ephemeralProvider = InMemoryPersistencyStorageProvider("ephemeral")
        val durableProvider = InMemoryPersistencyStorageProvider("durable")
        
        val providers = mapOf(
            "ephemeral" to ephemeralProvider,
            "durable" to durableProvider
        )
        
        var selectorCallCount = 0
        val selector: suspend (PersistencyStrategy.Dynamic.AgentContext) -> String = { context ->
            selectorCallCount++
            // Agent-level routing based on agent ID or context characteristics
            if (context.agentContext.id.contains("fast")) "ephemeral" else "durable"
        }
        
        val strategy = PersistencyStrategy.Dynamic(providers, selector)
        val strategyProvider = PersistencyStrategyProvider(strategy, mockContext)
        
        // When - save multiple checkpoints (all should use same cached provider)
        val checkpoint1 = testCheckpoint.copy(nodeId = "first-checkpoint")
        val checkpoint2 = testCheckpoint.copy(nodeId = "second-checkpoint")
        
        strategyProvider.saveCheckpoint(checkpoint1)
        strategyProvider.saveCheckpoint(checkpoint2)
        
        // Then - selector called only once, all operations use the same provider
        assertEquals(1, selectorCallCount)
        
        // Since mockContext.id doesn't contain "fast", should use durable provider
        assertNull(ephemeralProvider.getLatestCheckpoint())
        assertNotNull(durableProvider.getLatestCheckpoint())
        
        // Verify both checkpoints went to the same (durable) provider
        val checkpoints = durableProvider.getCheckpoints()
        assertEquals(2, checkpoints.size)
    }
    
    
    
    @Test
    fun testDynamicStrategyWithInvalidProvider() = runTest {
        // Given
        val providers = mapOf(
            "valid" to InMemoryPersistencyStorageProvider("valid")
        )
        
        val selector: suspend (PersistencyStrategy.Dynamic.AgentContext) -> String = { _ ->
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
            "redis" to redisProvider,
            "postgres" to postgresProvider
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
        
        // Verify provider instances
        assertEquals(redisProvider, strategy.providers["redis"])
        assertEquals(postgresProvider, strategy.providers["postgres"])
    }
    
    


}