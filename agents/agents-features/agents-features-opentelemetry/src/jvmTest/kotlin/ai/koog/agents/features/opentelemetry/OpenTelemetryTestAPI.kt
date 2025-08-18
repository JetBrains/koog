package ai.koog.agents.features.opentelemetry

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.entity.AIAgentStrategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.params.LLMParams
import kotlinx.datetime.Clock
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal object OpenTelemetryTestAPI {

    internal fun createAgent(
        agentId: String = "test-agent-id",
        strategy: AIAgentStrategy<String, String>,
        promptId: String? = null,
        promptExecutor: PromptExecutor? = null,
        toolRegistry: ToolRegistry? = null,
        model: LLModel? = null,
        clock: Clock = Clock.System,
        temperature: Double? = 0.0,
        systemPrompt: String? = null,
        userPrompt: String? = null,
        assistantPrompt: String? = null,
        installFeatures: AIAgent.FeatureContext.() -> Unit = { }
    ): AIAgent<String, String> {
        val agentConfig = AIAgentConfig(
            prompt = prompt(promptId ?: "Test prompt", clock = clock, params = LLMParams(temperature = temperature)) {
                systemPrompt?.let { system(systemPrompt) }
                userPrompt?.let { user(userPrompt) }
                assistantPrompt?.let { assistant(assistantPrompt) }
            },
            model = model ?: OpenAIModels.Chat.GPT4o,
            maxAgentIterations = 10,
        )

        return AIAgent(
            id = agentId,
            promptExecutor = promptExecutor ?: getMockExecutor {},
            strategy = strategy,
            agentConfig = agentConfig,
            toolRegistry = toolRegistry ?: ToolRegistry { },
            clock = clock,
            installFeatures = installFeatures,
        )
    }

    fun assertMapsEqual(expected: Map<*, *>, actual: Map<*, *>, message: String = "") {
        assertEquals(expected.size, actual.size, "$message - Map sizes should be equal")

        expected.forEach { (key, value) ->
            assertTrue(actual.containsKey(key), "$message - Key '$key' should exist in actual map")
            assertEquals(value, actual[key], "$message - Value for key '$key' should match")
        }
    }
}
