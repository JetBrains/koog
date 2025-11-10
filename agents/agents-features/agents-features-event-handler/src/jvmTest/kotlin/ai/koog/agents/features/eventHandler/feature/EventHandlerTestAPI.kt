package ai.koog.agents.features.eventHandler.feature

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.GraphAIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.params.LLMParams
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

val ts: Instant = Instant.parse("2023-01-01T00:00:00Z")

val testClock: Clock = object : Clock {
    override fun now(): Instant = ts
}

fun createAgent(
    strategy: AIAgentGraphStrategy<String, String>,
    agentId: String = "test-agent-id",
    executor: PromptExecutor? = null,
    promptId: String? = null,
    systemPrompt: String? = null,
    userPrompt: String? = null,
    assistantPrompt: String? = null,
    temperature: Double? = null,
    toolRegistry: ToolRegistry? = null,
    model: LLModel? = null,
    installFeatures: GraphAIAgent.FeatureContext.() -> Unit = { }
): AIAgent<String, String> {
    val agentConfig = AIAgentConfig(
        prompt = prompt(
            id = promptId ?: "Test prompt",
            clock = testClock,
            params = LLMParams(temperature = temperature)
        ) {
            system(systemPrompt ?: "Test system message")
            user(userPrompt ?: "Test user message")
            assistant(assistantPrompt ?: "Test assistant response")
        },
        model = model ?: OpenAIModels.Chat.GPT4o,
        maxAgentIterations = 10,
    )

    return AIAgent(
        id = agentId,
        promptExecutor = executor ?: TestLLMExecutor(testClock),
        strategy = strategy,
        agentConfig = agentConfig,
        toolRegistry = toolRegistry ?: ToolRegistry { },
        clock = testClock,
        installFeatures = installFeatures,
    )
}
