package ai.grazie.code.agents.local.features.tracing.writer

import ai.grazie.code.agents.core.tools.ToolRegistry
import ai.grazie.code.agents.core.agent.AIAgent
import ai.grazie.code.agents.core.agent.config.AIAgentConfig
import ai.grazie.code.agents.core.agent.entity.AIAgentStrategy
import ai.jetbrains.code.prompt.dsl.prompt
import ai.jetbrains.code.prompt.executor.clients.openai.OpenAIModels
import kotlinx.coroutines.CoroutineScope

fun createAgent(
    strategy: AIAgentStrategy,
    scope: CoroutineScope,
    installFeatures: suspend AIAgent.FeatureContext.() -> Unit = { }
): AIAgent {
    val agentConfig = AIAgentConfig(
        prompt = prompt("test") {
            system("Test system message")
            user("Test user message")
            assistant("Test assistant response")
        },
        model = OpenAIModels.Chat.GPT4o,
        maxAgentIterations = 10
    )

    return AIAgent(
        promptExecutor = TestLLMExecutor(),
        strategy = strategy,
        cs = scope,
        agentConfig = agentConfig,
        toolRegistry = ToolRegistry {
            stage("default") {
                tool(DummyTool())
            }
        },
        installFeatures = installFeatures,
    )
}
