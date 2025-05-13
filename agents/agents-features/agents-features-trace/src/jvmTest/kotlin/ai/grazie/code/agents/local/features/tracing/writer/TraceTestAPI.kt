package ai.grazie.code.agents.local.features.tracing.writer

import ai.grazie.code.agents.core.event.EventHandler
import ai.grazie.code.agents.core.tools.ToolRegistry
import ai.grazie.code.agents.core.agent.Agent
import ai.grazie.code.agents.core.agent.config.AgentConfig
import ai.grazie.code.agents.core.agent.entity.AgentStrategy
import ai.jetbrains.code.prompt.dsl.prompt
import ai.jetbrains.code.prompt.executor.clients.openai.OpenAIModels
import kotlinx.coroutines.CoroutineScope

fun createAgent(
    strategy: AgentStrategy,
    scope: CoroutineScope,
    installFeatures: suspend Agent.FeatureContext.() -> Unit = { }
): Agent {
    val agentConfig = AgentConfig(
        prompt = prompt("test") {
            system("Test system message")
            user("Test user message")
            assistant("Test assistant response")
        },
        model = OpenAIModels.Chat.GPT4o,
        maxAgentIterations = 10
    )

    return Agent(
        promptExecutor = TestLLMExecutor(),
        strategy = strategy,
        cs = scope,
        agentConfig = agentConfig,
        toolRegistry = ToolRegistry {
            stage("default") {
                tool(DummyTool())
            }
        },
        eventHandler = EventHandler { },
        installFeatures = installFeatures,
    )
}
