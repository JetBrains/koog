package ai.koog.agents.features.tracing.writer

import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.entity.AIAgentStrategy
import ai.koog.prompt.dsl.AttachmentBuilder
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo
import kotlinx.datetime.Clock

internal val testClock: Clock = object : Clock {
    override fun now(): kotlinx.datetime.Instant = kotlinx.datetime.Instant.parse("2023-01-01T00:00:00Z")
}

/**
 * Creates a user message with optional media attachments.
 *
 * The method constructs a user message using the provided text content and any additional
 * media content defined via the `attachmentsBlock`. This allows the user to include
 * various types of media attachments, such as images, audio files, or documents, alongside
 * the message content.
 *
 * @param content The text content of the user message.
 * @param attachmentsBlock A lambda function used to configure the media attachments
 *                          for the message, using the `AttachmentBuilder` DSL.
 * @return A `Message.User` object containing the message content, metadata,
 *         and any associated media attachments.
 */
fun userMessage(content: String, attachmentsBlock: AttachmentBuilder.() -> Unit = {}): Message.User = Message.User(
    content,
    metaInfo = RequestMetaInfo.create(testClock),
    attachments = AttachmentBuilder().apply(attachmentsBlock).build()
)

/**
 * Creates an instance of [Message.Assistant] with the provided content and generated metadata.
 *
 * @param content The textual content of the assistant's message.
 * @return A new instance of [Message.Assistant] containing the given content and metadata generated using the test clock.
 */
fun assistantMessage(content: String): Message.Assistant =
    Message.Assistant(content, metaInfo = ResponseMetaInfo.create(testClock))

/**
 * Creates a system-generated message encapsulated in a [Message.System] instance.
 *
 * @param content The textual content of the system message.
 * @return A [Message.System] object containing the provided content and autogenerated metadata.
 */
fun systemMessage(content: String): Message.System =
    Message.System(content, metaInfo = RequestMetaInfo.create(testClock))

/**
 * Creates an AI agent with the specified configuration, strategy, and optional prompts.
 *
 * @param strategy The strategy used to define the workflow and execution pattern for the AI agent.
 * @param promptId The identifier for the prompt configuration. If null, a default prompt ID will be used.
 * @param systemPrompt Optional system-level message to include in the prompt. If null, a default message will be used.
 * @param userPrompt Optional user-level message to include in the prompt. If null, a default message will be used.
 * @param assistantPrompt Optional assistant response to include in the prompt. If null, a default response will be used.
 * @param installFeatures Lambda function allowing additional features to be installed on the agent.
 * @return A configured instance of the AIAgent class ready for execution.
 */
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
