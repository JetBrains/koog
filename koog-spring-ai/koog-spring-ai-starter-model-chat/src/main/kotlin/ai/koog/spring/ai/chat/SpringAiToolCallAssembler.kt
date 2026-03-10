package ai.koog.spring.ai.chat

import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.streaming.StreamFrameFlowBuilder
import org.springframework.ai.chat.messages.AssistantMessage

/**
 * Handles provider-specific differences in how tool calls arrive during streaming.
 *
 * Different LLM providers stream tool calls differently:
 * - **Anthropic / Google**: Each streaming chunk contains a fully formed tool call with final `id`,
 *   `name`, and complete `arguments` JSON. The Spring AI adapters for these providers aggregate
 *   partial data (e.g. Anthropic's `INPUT_JSON_DELTA` content blocks) internally before surfacing
 *   the tool call, so Koog receives complete tool calls and can emit them immediately.
 * - **OpenAI**: Tool calls arrive incrementally — the first chunk carries the `id` and `name`,
 *   while subsequent chunks append partial `arguments` JSON fragments (with `null` `id` and `name`).
 *   These must be buffered and reassembled before emission.
 *
 * The assembler is created via [forProvider], which selects the appropriate [SpringAiToolStreamingMode]
 * based on the detected [LLMProvider]. After all streaming chunks have been processed, [flush] must
 * be called to emit any buffered tool calls.
 *
 * Tool calls are keyed by `(generationIndex, toolCallIndex)` to correctly handle multiple
 * concurrent tool calls within the same generation (e.g. OpenAI parallel tool calling).
 *
 * @see SpringAiToolStreamingMode
 * @see SpringAiLLMClient.executeStreaming
 */
internal class SpringAiToolCallAssembler private constructor(
    private val mode: SpringAiToolStreamingMode,
) {

    private data class PendingToolCall(
        val id: String?,
        val name: String?,
        val generationIndex: Int,
        val arguments: StringBuilder = StringBuilder(),
    )

    // Key is (generationIndex, toolCallIndex) to uniquely identify each tool call slot across chunks
    private val pendingByKey = linkedMapOf<Pair<Int, Int>, PendingToolCall>()

    /**
     * Processes tool calls from a single streaming chunk.
     *
     * In [SpringAiToolStreamingMode.EMIT_IMMEDIATELY] mode, each tool call is emitted immediately
     * via [StreamFrameFlowBuilder.emitToolCallDelta]. In [SpringAiToolStreamingMode.BUFFER_UNTIL_END]
     * mode, tool call fragments are accumulated internally and only emitted when [flush] is called.
     *
     * @param toolCalls the tool calls from the current chunk's assistant message
     * @param generationIndex the index of the generation within the [org.springframework.ai.chat.model.ChatResponse]
     * @param out the stream builder to emit frames into
     */
    suspend fun accept(
        toolCalls: List<AssistantMessage.ToolCall>,
        generationIndex: Int,
        out: StreamFrameFlowBuilder,
    ) {
        if (toolCalls.isEmpty()) return

        when (mode) {
            SpringAiToolStreamingMode.EMIT_IMMEDIATELY -> {
                for ((toolCallIndex, toolCall) in toolCalls.withIndex()) {
                    out.emitToolCallDelta(
                        id = toolCall.id(),
                        name = toolCall.name(),
                        args = toolCall.arguments(),
                        index = toolCallIndex
                    )
                }
            }

            SpringAiToolStreamingMode.BUFFER_UNTIL_END -> {
                for ((toolCallIndex, toolCall) in toolCalls.withIndex()) {
                    val key = Pair(generationIndex, toolCallIndex)
                    val pending = pendingByKey.getOrPut(key) {
                        PendingToolCall(
                            id = toolCall.id(),
                            name = toolCall.name(),
                            generationIndex = generationIndex,
                        )
                    }
                    pending.arguments.append(toolCall.arguments().orEmpty())
                }
            }
        }
    }

    /**
     * Emits all buffered tool calls accumulated in [SpringAiToolStreamingMode.BUFFER_UNTIL_END] mode.
     *
     * Must be called after all streaming chunks have been collected. For providers using
     * [SpringAiToolStreamingMode.EMIT_IMMEDIATELY], this is a no-op (nothing is buffered).
     *
     * @param out the stream builder to emit completed tool call frames into
     */
    suspend fun flush(out: StreamFrameFlowBuilder) {
        for ((key, pending) in pendingByKey) {
            val (_, toolCallIndex) = key
            out.emitToolCallDelta(
                id = pending.id,
                name = pending.name,
                args = pending.arguments.toString(),
                index = toolCallIndex
            )
        }
        pendingByKey.clear()
    }

    internal companion object {
        /**
         * Creates an assembler configured for the given [provider].
         *
         * The provider is typically auto-detected by [SpringAiChatModelProviderDetector] from the
         * Spring AI [org.springframework.ai.chat.model.ChatModel] class name (e.g. `OpenAiChatModel` resolves to [LLMProvider.OpenAI]).
         *
         * @param provider the detected LLM provider
         * @return a new assembler instance with the appropriate streaming mode
         */
        fun forProvider(provider: LLMProvider): SpringAiToolCallAssembler {
            val mode = when (provider) {
                LLMProvider.Anthropic,
                LLMProvider.Google -> SpringAiToolStreamingMode.EMIT_IMMEDIATELY

                LLMProvider.OpenAI -> SpringAiToolStreamingMode.BUFFER_UNTIL_END

                else -> SpringAiToolStreamingMode.BUFFER_UNTIL_END
            }
            return SpringAiToolCallAssembler(mode)
        }
    }
}

/**
 * Determines how [SpringAiToolCallAssembler] handles tool calls during streaming.
 */
internal enum class SpringAiToolStreamingMode {
    /**
     * The provider's Spring AI adapter delivers fully assembled tool calls in each chunk.
     * Tool calls are emitted immediately without buffering.
     *
     * Used for Anthropic and Google, whose Spring AI implementations aggregate partial
     * tool call data internally before exposing it.
     */
    EMIT_IMMEDIATELY,

    /**
     * The provider streams tool calls incrementally across multiple chunks.
     * Fragments are buffered and reassembled, then emitted together when [SpringAiToolCallAssembler.flush] is called.
     *
     * Used for OpenAI (and as the safe default for unknown providers), where the first chunk
     * carries `id` and `name` while subsequent chunks append partial `arguments` JSON.
     */
    BUFFER_UNTIL_END,
}
