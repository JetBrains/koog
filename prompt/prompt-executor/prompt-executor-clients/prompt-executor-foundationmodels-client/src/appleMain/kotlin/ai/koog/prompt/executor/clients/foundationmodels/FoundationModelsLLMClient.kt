package ai.koog.prompt.executor.clients.foundationmodels

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.Prompt
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message

/**
 * On-device [LLMClient] backed by Apple's Foundation Models framework (iOS 26+).
 *
 * Turnkey: the no-arg constructor wires the bundled `@objc`/cinterop session, so callers
 * write no Swift. Phase-1 supports single-shot [execute] with availability gating;
 * streaming, tools, structured output and moderation are not yet implemented.
 *
 * Construct with [AppleLLModels.SystemDefault] and register under [AppleLLMProvider]
 * in a `MultiLLMPromptExecutor` (ideally with a network fallback for when FM is
 * unavailable — [execute] throws [FoundationModelsException.Unavailable] then).
 */
public class FoundationModelsLLMClient internal constructor(
    private val session: FoundationModelsSession,
) : LLMClient() {

    /** Turnkey constructor: binds the bundled on-device Foundation Models session. */
    public constructor() : this(defaultFoundationModelsSession())

    override suspend fun execute(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
    ): Message.Assistant {
        session.availabilityReason()?.let { throw FoundationModelsException.Unavailable(it) }
        val input = prompt.toFoundationModelsInput()
        val content = session.respond(input.text, input.instructions)
        return foundationModelsAssistantMessage(content)
    }

    override suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult =
        throw UnsupportedOperationException("Moderation is not supported for Apple Foundation Models")

    override fun llmProvider(): LLMProvider = AppleLLMProvider

    override fun close() {
        // No-op: a fresh native session is created per call in this POC.
    }
}
