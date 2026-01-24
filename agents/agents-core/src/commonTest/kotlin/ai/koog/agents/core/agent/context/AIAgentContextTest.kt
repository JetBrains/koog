package ai.koog.agents.core.agent.context

import ai.koog.agents.core.agent.entity.AIAgentStorageKey
import ai.koog.agents.core.agent.execution.AgentExecutionInfo
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull

class AIAgentContextTest : AgentTestBase() {

    @Test
    fun testContextCreation() = runTest {
        val context = createTestContext()

        assertEquals("test-run-id", context.runId)
        assertEquals("test-strategy", context.strategyName)
        assertNotNull(context.environment)
        assertNotNull(context.config)
        assertNotNull(context.llm)
        assertNotNull(context.stateManager)
        assertNotNull(context.storage)
    }

    @Test
    fun testFeatureRetrieval() = runTest {
        val featureKey = AIAgentStorageKey<String>("test-feature")
        val featureValue = "test-feature-value"

        val context = createTestContext()

        context.storage.set(featureKey, featureValue)

        val retrievedFromStorage = context.storage.get(featureKey)
        assertEquals(featureValue, retrievedFromStorage)
    }

    @Test
    fun testFeatureRetrievalNotFound() = runTest {
        val featureKey = AIAgentStorageKey<String>("non-existent-feature")
        val context = createTestContext()

        val retrievedFromStorage = context.storage.get(featureKey)
        assertNull(retrievedFromStorage)
    }

    @Test
    fun testFeatureOverwrite() = runTest {
        val featureKey = AIAgentStorageKey<String>("test-feature")
        val initialValue = "initial-value"
        val updatedValue = "updated-value"
        val context = createTestContext()

        // initial feature value
        context.storage.set(featureKey, initialValue)
        val initialRetrieved = context.storage.get(featureKey)
        assertEquals(initialValue, initialRetrieved)

        // overwritten feature value
        context.storage.set(featureKey, updatedValue)
        val updatedRetrieved = context.storage.get(featureKey)
        assertEquals(updatedValue, updatedRetrieved)
    }

    @Test
    fun testContextCopy() = runTest {
        val originalContext = createTestContext()

        val newEnvironment = createTestEnvironment("new-environment")

        val copiedContext = originalContext.copy(
            environment = newEnvironment,
            runId = "new-run-id",
            strategyName = "new-strategy"
        )

        // check overriden properties
        assertEquals("new-run-id", copiedContext.runId)
        assertEquals("new-strategy", copiedContext.strategyName)
        assertEquals(newEnvironment, copiedContext.environment)

        // check that other properties remain the same
        assertEquals(originalContext.config, copiedContext.config)
        assertEquals(originalContext.llm, copiedContext.llm)
        assertEquals(originalContext.stateManager, copiedContext.stateManager)
        assertEquals(originalContext.storage, copiedContext.storage)
    }

    @Test
    fun testContextFork() = runTest {
        val originalContext = createTestContext()
        val forkedContext = originalContext.fork()

        assertEquals(originalContext.runId, forkedContext.runId)
        assertEquals(originalContext.strategyName, forkedContext.strategyName)
        assertEquals(originalContext.environment, forkedContext.environment)
        assertEquals(originalContext.config, forkedContext.config)

        assertNotSame(originalContext.llm, forkedContext.llm)
        assertNotSame(originalContext.stateManager, forkedContext.stateManager)
        assertNotSame(originalContext.storage, forkedContext.storage)
    }

    @Test
    fun testContextReplace() = runTest {
        val originalContext = createTestContext()

        val newLlm = createTestLLMContext("new-llm")
        val newStateManager = createTestStateManager()
        val newStorage = createTestStorage()

        val newContext = createTestContext(
            llmContext = newLlm,
            stateManager = newStateManager,
            storage = newStorage
        )

        originalContext.replace(newContext)

        assertEquals(newLlm, originalContext.llm)
        assertEquals(newStateManager, originalContext.stateManager)
        assertEquals(newStorage, originalContext.storage)
    }

    @Test
    fun testCopyWithAllParameters() = runTest {
        val originalContext = createTestContext()

        val newEnvironment = createTestEnvironment("new-environment")
        val newConfig = createTestConfig("new-config")
        val newLlm = createTestLLMContext("new-llm")
        val newStateManager = createTestStateManager()
        val newStorage = createTestStorage()
        val newRunId = "new-run-id"
        val newStrategyName = "new-strategy"
        val newInput = "new-input"

        val copiedContext = originalContext.copy(
            environment = newEnvironment,
            agentInput = newInput,
            config = newConfig,
            llm = newLlm,
            stateManager = newStateManager,
            storage = newStorage,
            runId = newRunId,
            strategyName = newStrategyName,
        )

        assertEquals(newEnvironment, copiedContext.environment)
        assertEquals(newInput, copiedContext.agentInput)
        assertEquals(newConfig, copiedContext.config)
        assertEquals(newLlm, copiedContext.llm)
        assertEquals(newStateManager, copiedContext.stateManager)
        assertEquals(newStorage, copiedContext.storage)
        assertEquals(newRunId, copiedContext.runId)
        assertEquals(newStrategyName, copiedContext.strategyName)
    }

    @Test
    fun testCopyWithNullAgentInput() = runTest {
        val originalContext = createTestContext()

        val copiedContext = originalContext.copy(
            agentInput = null
        )

        assertNull(copiedContext.agentInput)
        assertEquals("test-input", originalContext.agentInput)
    }

    @Test
    fun testCopyWithDifferentAgentInputTypes() = runTest {
        val testInputs = listOf(
            "",
            "regular string",
            ComplexJsonInput("test-id", listOf(1, 2, 3), NestedObject("test-name", true)),
            TestEnum.FIRST,
            TestEnum.SECOND,
            1,
            3.14,
            true,
            false,
            listOf("item1", "item2", "item3"),
            emptyList<String>(),
            mapOf("key1" to "value1", "key2" to "value2"),
            emptyMap<String, String>(),
            listOf(listOf("nested1"), listOf("nested2")),
            mapOf("nestedMap" to mapOf("key" to "value")),
            mapOf("nestedList" to listOf(1, 2, 3))
        )

        val originalContext = createTestContext()

        testInputs.forEach { input ->
            val copiedContext = originalContext.copy(agentInput = input)

            assertEquals(input, copiedContext.agentInput)
            assertEquals("test-input", originalContext.agentInput)
        }
    }

    @Test
    fun testContextForkWithIsolatedStorage() = runTest {
        val storageKey = AIAgentStorageKey<String>("test-key")

        val originalContext = createTestContext()
        originalContext.storage.set(storageKey, "original-value")

        val forkedContext = originalContext.fork()
        forkedContext.storage.set(storageKey, "forked-value")

        assertEquals("original-value", originalContext.storage.get(storageKey))
        assertEquals("forked-value", forkedContext.storage.get(storageKey))
    }

    @Test
    fun testContextForkWithIsolatedStateManager() = runTest {
        val originalContext = createTestContext()
        val forkedContext = originalContext.fork()

        assertNotSame(originalContext.stateManager, forkedContext.stateManager)
        assertNotSame(originalContext.llm, forkedContext.llm)
    }

    //region Agent Execution Info

    @Test
    fun testAgentExecutionInfoDefaultState() {
        val parent1 = null
        val partName = "test-part"
        val executionInfo = AgentExecutionInfo(parent = parent1, partName = partName)
        val originalContext = createTestContext(executionInfo = executionInfo)

        assertNull(originalContext.executionInfo.parent)
        assertEquals(partName, originalContext.executionInfo.partName)
    }

    @Test
    fun testAgentExecutionInfoOverride() {
        val parent1 = null
        val partName1 = "test-part-1"
        val executionInfo1 = AgentExecutionInfo(parent = parent1, partName = partName1)

        val parent2 = executionInfo1
        val partName2 = "test-part-2"
        val executionInfo2 = AgentExecutionInfo(parent = parent2, partName = partName2)

        val originalContext = createTestContext(executionInfo = executionInfo1)

        assertEquals(parent1, originalContext.executionInfo.parent)
        assertEquals(partName1, originalContext.executionInfo.partName)

        originalContext.executionInfo = executionInfo2

        assertEquals(parent2, originalContext.executionInfo.parent)
        assertEquals(partName2, originalContext.executionInfo.partName)
    }

    //endregion Agent Execution Info

    private data class ComplexJsonInput(
        val id: String,
        val values: List<Int>,
        val nested: NestedObject
    )

    private data class NestedObject(
        val name: String,
        val active: Boolean
    )

    private enum class TestEnum {
        FIRST,
        SECOND
    }
}
