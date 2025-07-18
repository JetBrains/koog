package ai.koog.agents.core.agent.context

import ai.koog.agents.core.CalculatorChatExecutor.testClock
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.config.AIAgentConfigBase
import ai.koog.agents.core.agent.config.MissingToolsConversionStrategy
import ai.koog.agents.core.agent.config.ToolCallDescriber
import ai.koog.agents.core.agent.entity.AIAgentStateManager
import ai.koog.agents.core.agent.entity.AIAgentStorage
import ai.koog.agents.core.agent.entity.AIAgentStorageKey
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.environment.AIAgentEnvironment
import ai.koog.agents.core.environment.ReceivedToolResult
import ai.koog.agents.core.feature.AIAgentPipeline
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.agents.testing.tools.mockLLMAnswer
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.llm.OllamaModels
import ai.koog.prompt.message.Message
import kotlinx.coroutines.test.runTest
import kotlin.reflect.typeOf
import kotlin.test.*

class AIAgentContextTest {

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

    private fun createTestEnvironment(id: String = "test-environment"): AIAgentEnvironment {
        return object : AIAgentEnvironment {
            override suspend fun executeTools(toolCalls: List<Message.Tool.Call>): List<ReceivedToolResult> {
                return emptyList()
            }

            override suspend fun reportProblem(exception: Throwable) {
                // Do nothing
            }

            override fun toString(): String = "TestEnvironment($id)"
        }
    }

    private fun createTestConfig(id: String = "test-config"): AIAgentConfigBase {
        return AIAgentConfig(
            prompt = createTestPrompt(),
            model = OllamaModels.Meta.LLAMA_3_2,
            maxAgentIterations = 10,
            missingToolsConversionStrategy = MissingToolsConversionStrategy.All(ToolCallDescriber.JSON)
        )
    }

    private fun createTestPrompt(): Prompt {
        return prompt("test-prompt") {}
    }

    private fun createTestLLMContext(id: String = "test-llm"): AIAgentLLMContext {
        val mockExecutor = getMockExecutor(clock = testClock) {
            mockLLMAnswer("Test response").asDefaultResponse
        }

        return AIAgentLLMContext(
            tools = emptyList(),
            prompt = createTestPrompt(),
            model = OllamaModels.Meta.LLAMA_3_2,
            promptExecutor = mockExecutor,
            environment = createTestEnvironment(),
            config = createTestConfig(),
            clock = testClock
        )
    }

    private fun createTestStateManager(): AIAgentStateManager {
        return AIAgentStateManager()
    }

    private fun createTestStorage(): AIAgentStorage {
        return AIAgentStorage()
    }

    @OptIn(InternalAgentsApi::class)
    private fun createTestContext(
        environment: AIAgentEnvironment = createTestEnvironment(),
        config: AIAgentConfigBase = createTestConfig(),
        llmContext: AIAgentLLMContext = createTestLLMContext(),
        stateManager: AIAgentStateManager = createTestStateManager(),
        storage: AIAgentStorage = createTestStorage(),
        runId: String = "test-run-id",
        strategyName: String = "test-strategy",
        pipeline: AIAgentPipeline = AIAgentPipeline()
    ): AIAgentContext {
        return AIAgentContext(
            environment = environment,
            agentInputType = typeOf<String>(),
            agentInput = "test-input",
            config = config,
            llm = llmContext,
            stateManager = stateManager,
            storage = storage,
            runId = runId,
            strategyName = strategyName,
            pipeline = pipeline,
            id = "test-context-id",
        )
    }
}