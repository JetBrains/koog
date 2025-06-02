package ai.koog.agents.features.tracing.writer

import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.entity.AIAgentStrategy
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo
import kotlinx.datetime.Clock

val testClock: Clock = object : Clock {
    override fun now(): kotlinx.datetime.Instant = kotlinx.datetime.Instant.parse("2023-01-01T00:00:00Z")
}

fun userMessage(content: String): Message.User = Message.User(content, metaInfo = RequestMetaInfo.create(testClock))
fun assistantMessage(content: String): Message.Assistant = Message.Assistant(content, metaInfo = ResponseMetaInfo.create(testClock))
fun systemMessage(content: String): Message.System = Message.System(content, metaInfo = RequestMetaInfo.create(testClock))

fun createAgent(
    strategy: AIAgentStrategy,
    promptId: String? = null,
    systemPrompt: String? = null,
    userPrompt: String? = null,
    assistantPrompt: String? = null,
    installFeatures: AIAgent.FeatureContext.() -> Unit = { }
): AIAgent {
    val agentConfig = AIAgentConfig(
        prompt = prompt(promptId ?: "Test prompt", clock = testClock) {
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
        clock = testClock,
        installFeatures = installFeatures,
    )
}
