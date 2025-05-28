package ai.koog.agents.features.tracing.writer

import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.entity.AIAgentStrategy
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.OpenAIModels

fun createAgent(
    strategy: AIAgentStrategy,
    promptId: String? = null,
    systemPrompt: String? = null,
    userPrompt: String? = null,
    assistantPrompt: String? = null,
    installFeatures: AIAgent.FeatureContext.() -> Unit = { }
): AIAgent {
    val agentConfig = AIAgentConfig(
        prompt = prompt(promptId ?: "Test prompt") {
            system(systemPrompt ?: "Test system message")
            user(userPrompt ?: "Test user message")
            assistant(assistantPrompt ?: "Test assistant response")
        },
        model = OpenAIModels.Chat.GPT4o,
        maxAgentIterations = 10
    )

    return AIAgent(
        promptExecutor = TestLLMExecutor(),
        strategy = strategy,
        agentConfig = agentConfig,
        toolRegistry = ToolRegistry {
            tool(DummyTool())
        },
        installFeatures = installFeatures,
    )
}
