package ai.koog.agents.features.eventHandler.feature

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.entity.AIAgentStrategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.ToolRegistry.Builder
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

val ts: Instant = Instant.parse("2023-01-01T00:00:00Z")

val testClock: Clock = object : Clock {
    override fun now(): Instant = ts
}

fun createAgent(
    strategy: AIAgentStrategy<String, String>,
    agentId: String = "test-agent-id",
    configureTools: Builder.() -> Unit = { },
    installFeatures: AIAgent.FeatureContext.() -> Unit = { }
): AIAgent<String, String> {
    val agentConfig = AIAgentConfig(
        prompt = prompt("test", clock = testClock) {
            system("Test system message")
            user("Test user message")
            assistant("Test assistant response")
        },
        model = OpenAIModels.Chat.GPT4o,
        maxAgentIterations = 10
    )

    return AIAgent(
        id = agentId,
        promptExecutor = TestLLMExecutor(testClock),
        strategy = strategy,
        agentConfig = agentConfig,
        toolRegistry = ToolRegistry { configureTools() },
        clock = testClock,
        installFeatures = installFeatures,
    )
}
