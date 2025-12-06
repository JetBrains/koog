package ai.koog.agents.core.agent.context

import ai.koog.agents.core.CalculatorChatExecutor.testClock
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.config.MissingToolsConversionStrategy
import ai.koog.agents.core.agent.config.ToolCallDescriber
import ai.koog.agents.core.agent.entity.AIAgentStateManager
import ai.koog.agents.core.agent.entity.AIAgentStorage
import ai.koog.agents.core.environment.AIAgentEnvironment
import ai.koog.agents.core.environment.ReceivedToolResult
import ai.koog.agents.core.feature.pipeline.AIAgentGraphPipeline
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.llm.OllamaModels
import ai.koog.prompt.message.Message
import kotlin.reflect.typeOf

open class AgentTestBase {
    protected val testAgentId = "test-agent"
    protected val testRunId = "test-run"
    protected val strategyName = "test-strategy"

    protected fun createTestEnvironment(id: String = "test-environment"): AIAgentEnvironment {
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

    protected fun createTestConfig(id: String = "test-config"): AIAgentConfig {
        return AIAgentConfig(
            prompt = createTestPrompt(),
            model = OllamaModels.Meta.LLAMA_3_2,
            maxAgentIterations = 10,
            missingToolsConversionStrategy = MissingToolsConversionStrategy.All(ToolCallDescriber.JSON)
        )
    }

    protected fun createTestPrompt(id: String = "test-prompt"): Prompt {
        return prompt(id) {}
    }

    protected fun createTestLLMContext(id: String = "test-llm"): AIAgentLLMContext {
        val mockExecutor = getMockExecutor(clock = testClock) {
            mockLLMAnswer("Test response").asDefaultResponse
        }

        return AIAgentLLMContext(
            tools = emptyList(),
            prompt = createTestPrompt(),
            model = OllamaModels.Meta.LLAMA_3_2,
            responseProcessor = null,
            promptExecutor = mockExecutor,
            environment = createTestEnvironment(),
            config = createTestConfig(),
            clock = testClock
        )
    }

    protected fun createTestStateManager(): AIAgentStateManager {
        return AIAgentStateManager()
    }

    protected fun createTestStorage(): AIAgentStorage {
        return AIAgentStorage()
    }

    protected open fun createTestContext(
        environment: AIAgentEnvironment = createTestEnvironment(),
        config: AIAgentConfig = createTestConfig(),
        llmContext: AIAgentLLMContext = createTestLLMContext(),
        stateManager: AIAgentStateManager = createTestStateManager(),
        storage: AIAgentStorage = createTestStorage(),
        runId: String = "test-run-id",
        strategyName: String = "test-strategy",
        pipeline: AIAgentGraphPipeline = AIAgentGraphPipeline(testClock),
        agentInput: String = "test-input"
    ): AIAgentGraphContext {
        return AIAgentGraphContext(
            environment = environment,
            agentId = testAgentId,
            agentInputType = typeOf<String>(),
            agentInput = agentInput,
            config = config,
            llm = llmContext,
            stateManager = stateManager,
            storage = storage,
            runId = runId,
            strategyName = strategyName,
            pipeline = pipeline,
        )
    }
}
