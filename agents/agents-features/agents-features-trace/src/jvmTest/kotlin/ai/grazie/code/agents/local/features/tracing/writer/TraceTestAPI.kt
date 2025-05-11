package ai.grazie.code.agents.local.features.tracing.writer

import ai.grazie.code.agents.core.tools.ToolRegistry
import ai.grazie.code.agents.core.agent.AIAgentBase
import ai.grazie.code.agents.core.agent.config.LocalAgentConfig
import ai.grazie.code.agents.core.agent.entity.LocalAgentStrategy
import ai.jetbrains.code.prompt.dsl.prompt
import ai.jetbrains.code.prompt.executor.clients.openai.OpenAIModels
import kotlinx.coroutines.CoroutineScope

fun createAgent(
    strategy: LocalAgentStrategy,
    scope: CoroutineScope,
    installFeatures: suspend AIAgentBase.FeatureContext.() -> Unit = { }
): AIAgentBase {
    val agentConfig = LocalAgentConfig(
        prompt = prompt("test") {
            system("Test system message")
            user("Test user message")
            assistant("Test assistant response")
        },
        model = OpenAIModels.Chat.GPT4o,
        maxAgentIterations = 10
    )

    return AIAgentBase(
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
