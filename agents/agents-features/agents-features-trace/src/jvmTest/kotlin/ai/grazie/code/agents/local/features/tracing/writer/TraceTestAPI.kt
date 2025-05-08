package ai.grazie.code.agents.local.features.tracing.writer

import ai.grazie.code.agents.core.event.EventHandler
import ai.grazie.code.agents.core.tools.ToolRegistry
import ai.grazie.code.agents.core.agent.KotlinAIAgent
import ai.grazie.code.agents.core.agent.config.LocalAgentConfig
import ai.grazie.code.agents.core.agent.entity.LocalAgentStrategy
import ai.jetbrains.code.prompt.dsl.prompt
import ai.jetbrains.code.prompt.executor.clients.openai.OpenAIModels
import kotlinx.coroutines.CoroutineScope

fun createAgent(
    strategy: LocalAgentStrategy,
    scope: CoroutineScope,
    installFeatures: suspend KotlinAIAgent.FeatureContext.() -> Unit = { }
): KotlinAIAgent {
    val agentConfig = LocalAgentConfig(
        prompt = prompt(OpenAIModels.GPT4o, "test") {
            system("Test system message")
            user("Test user message")
            assistant("Test assistant response")
        },
        maxAgentIterations = 10
    )

    return KotlinAIAgent(
        toolRegistry = ToolRegistry {
            stage("default") {
                tool(DummyTool())
            }
        },
        strategy = strategy,
        eventHandler = EventHandler { },
        agentConfig = agentConfig,
        promptExecutor = TestLLMExecutor(),
        cs = scope,
        installFeatures = installFeatures,
    )
}
