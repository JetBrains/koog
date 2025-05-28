package ai.koog.agents.features.eventHandler.feature

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.entity.AIAgentStrategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.ToolRegistry.Builder
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import kotlinx.datetime.Clock

fun createAgent(
    strategy: AIAgentStrategy,
    clock: Clock,
    configureTools: Builder.() -> Unit = { },
    installFeatures: AIAgent.FeatureContext.() -> Unit = { }
): AIAgent {
    val agentConfig = AIAgentConfig(
        prompt = prompt("test", clock = clock) {
            system("Test system message")
            user("Test user message")
            assistant("Test assistant response")
        },
        model = OpenAIModels.Chat.GPT4o,
        maxAgentIterations = 10
    )

    return AIAgent(
        promptExecutor = TestLLMExecutor(clock),
        strategy = strategy,
        agentConfig = agentConfig,
        toolRegistry = ToolRegistry { configureTools() },
        clock = clock,
        installFeatures = installFeatures,
    )
}
