package ai.grazie.code.agents.local.features.tracing.writer

import ai.grazie.code.agents.core.event.EventHandler
import ai.grazie.code.agents.core.tools.ToolRegistry
import ai.grazie.code.agents.local.KotlinAIAgent
import ai.grazie.code.agents.local.agent.LocalAgentConfig
import ai.grazie.code.agents.local.agent.LocalAgentStrategy
import ai.jetbrains.code.prompt.dsl.prompt
import ai.jetbrains.code.prompt.llm.OllamaModels
import kotlinx.coroutines.CoroutineScope

fun createAgent(
    strategy: LocalAgentStrategy,
    scope: CoroutineScope,
    installFeatures: suspend KotlinAIAgent.FeatureContext.() -> Unit = { }
): KotlinAIAgent {
    val agentConfig = LocalAgentConfig(
        prompt = prompt(OllamaModels.Meta.LLAMA_3_2, "test") {
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
