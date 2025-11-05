package ai.koog.agents.testing.client

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.model.LLMChoice
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.streaming.StreamFrame
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * A test double implementation of [LLMClient] that captures the last inputs provided to each API
 * and returns predefined responses. This is useful in unit and integration tests to assert that a
 * component under test interacts with an LLM client as expected without making real network calls.
 *
 * Constructor parameters allow you to predefine what each method should return.
 *
 * @property executeResponses The list of [Message.Response] to return from [execute].
 * @property streamingChunks The sequence of chunks to emit from [executeStreaming].
 * @property choices The list of [LLMChoice] to return from [executeMultipleChoices].
 * @property moderationResult The [ModerationResult] to return from [moderate].
 * @property llmProvider [LLMProvider] associated with the client or [LLMProvider.OpenAI], if not defined
 */
public class CapturingLLMClient(
    private val executeResponses: List<Message.Response> = emptyList(),
    private val streamingChunks: List<StreamFrame> = emptyList(),
    private val choices: List<LLMChoice> = emptyList(),
    private val moderationResult: ModerationResult = ModerationResult(isHarmful = false, categories = emptyMap()),
    private val llmProvider: LLMProvider = LLMProvider.OpenAI
) : LLMClient {

    /** The last [Prompt] passed to [execute], or null if it hasn't been called yet. */
    public var lastExecutedPrompt: Prompt? = null

    /** The last [LLModel] passed to [execute], or null if it hasn't been called yet. */
    public var lastExecutedModel: LLModel? = null

    /** The last list of tools passed to [execute], or null if it hasn't been called yet. */
    public var lastExecutedTools: List<ToolDescriptor>? = null

    /** The last [Prompt] passed to [executeStreaming], or null if it hasn't been called yet. */
    public var lastStreamingPrompt: Prompt? = null

    /** The last [LLModel] passed to [executeStreaming], or null if it hasn't been called yet. */
    public var lastStreamingModel: LLModel? = null

    /** The last [Prompt] passed to [executeMultipleChoices], or null if it hasn't been called yet. */
    public var lastChoicesPrompt: Prompt? = null

    /** The last [LLModel] passed to [executeMultipleChoices], or null if it hasn't been called yet. */
    public var lastChoicesModel: LLModel? = null

    /** The last list of tools passed to [executeMultipleChoices], or null if it hasn't been called yet. */
    public var lastChoicesTools: List<ToolDescriptor>? = null

    /** The last [Prompt] passed to [moderate], or null if it hasn't been called yet. */
    public var lastModerationPrompt: Prompt? = null

    /** The last [LLModel] passed to [moderate], or null if it hasn't been called yet. */
    public var lastModerationModel: LLModel? = null

    override fun llmProvider(): LLMProvider = llmProvider

    /**
     * Simulates a non-streaming LLM execution.
     * Captures input parameters and returns the predefined [executeResponses].
     */
    override suspend fun execute(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ): List<Message.Response> {
        lastExecutedPrompt = prompt
        lastExecutedModel = model
        lastExecutedTools = tools
        return executeResponses
    }

    /**
     * Simulates a streaming LLM execution.
     * Captures input parameters and emits the predefined [streamingChunks].
     */
    override fun executeStreaming(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ): Flow<StreamFrame> {
        lastStreamingPrompt = prompt
        lastStreamingModel = model
        return flowOf(*streamingChunks.toTypedArray())
    }

    /**
     * Simulates an LLM call that returns multiple choices.
     * Captures input parameters and returns the predefined [choices].
     */
    override suspend fun executeMultipleChoices(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ): List<LLMChoice> {
        lastChoicesPrompt = prompt
        lastChoicesModel = model
        lastChoicesTools = tools
        return choices
    }

    /**
     * Simulates a content moderation call.
     * Captures input parameters and returns the predefined [moderationResult].
     */
    override suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult {
        lastModerationPrompt = prompt
        lastModerationModel = model
        return moderationResult
    }

    override fun close() {
        // No resources to close
    }
}
