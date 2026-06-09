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
 * Check [availability] before routing prompts here: `MultiLLMPromptExecutor` falls back
 * only for providers with **no registered client**, so register this client (and select
 * [AppleLLModels.SystemDefault]) only when [availability] reports
 * [FoundationModelsAvailability.Available], and point `FallbackPromptExecutorSettings`
 * at a cloud provider for the rest. Calling [execute] while unavailable throws
 * [FoundationModelsException.Unavailable] carrying the same typed reason.
 */
public class FoundationModelsLLMClient internal constructor(
    private val session: FoundationModelsSession,
) : LLMClient() {

    /** Turnkey constructor: binds the bundled on-device Foundation Models session. */
    public constructor() : this(defaultFoundationModelsSession())

    /**
     * Reports whether the on-device model can run right now, without executing anything.
     * Safe to call on any OS version: pre-26 systems report
     * [FoundationModelsAvailability.Unavailable.OSVersionTooOld].
     */
    public fun availability(): FoundationModelsAvailability =
        foundationModelsAvailabilityFromToken(session.availabilityToken())

    override suspend fun execute(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
    ): Message.Assistant {
        val availability = availability()
        if (availability is FoundationModelsAvailability.Unavailable) {
            throw FoundationModelsException.Unavailable(availability)
        }
        val input = prompt.toFoundationModelsInput()
        val content = session.respond(input.text, input.instructions)
        return foundationModelsAssistantMessage(content)
    }

    override suspend fun models(): List<LLModel> = AppleLLModels.models

    override suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult =
        throw UnsupportedOperationException("Moderation is not supported for Apple Foundation Models")

    override fun llmProvider(): LLMProvider = AppleLLMProvider

    override fun close() {
        // No-op: a fresh native session is created per call in this POC.
    }
}
