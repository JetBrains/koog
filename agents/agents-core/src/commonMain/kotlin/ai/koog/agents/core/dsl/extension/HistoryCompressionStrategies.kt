package ai.koog.agents.core.dsl.extension

import ai.koog.agents.core.agent.session.AIAgentLLMWriteSession
import ai.koog.agents.core.prompt.Prompts.summarizeInTLDR
import ai.koog.prompt.message.Message
import kotlinx.datetime.Instant

/**
 * Represents an abstract strategy for compressing the history of messages in a `AIAgentLLMWriteSession`.
 * Different implementations define specific approaches to reducing the context size while maintaining key information.
 *
 * Example implementations:
 * - [HistoryCompressionStrategy.WholeHistory]
 * - [HistoryCompressionStrategy.FromLastNMessages]
 * - [HistoryCompressionStrategy.FromTimestamp]
 * - [HistoryCompressionStrategy.Chunked]
 * - [ai.koog.agents.memory.feature.history.RetrieveFactsFromHistory]
 */
public abstract class HistoryCompressionStrategy {
    /**
     * Compresses a given collection of memory messages using a specified strategy.
     *
     * @param llmSession The current LLM session used for processing during compression.
     * @param memoryMessages A list of messages representing the memory to be compressed.
     */
    public abstract suspend fun compress(
        llmSession: AIAgentLLMWriteSession,
        memoryMessages: List<Message>
    )

    /**
     * Compresses the current conversation prompt into a concise "TL;DR" summary using the specified
     * AIAgentLLMWriteSession. The resulting summary will encapsulate the key details and context of the conversation
     * for further processing or continuation.
     *
     * @param llmSession The session used to interact with the language model, providing functionality to update the prompt
     *                   and request a response without utilizing external tools.
     * @return A list of language model responses containing the summarized "TL;DR" of the conversation.
     */
    protected suspend fun compressPromptIntoTLDR(llmSession: AIAgentLLMWriteSession): List<Message.Response> {
        return with(llmSession) {
            // If there are any tool calls left in a history, we are not allowed to send a user message back
            dropTrailingToolCalls()
            appendPrompt {
                user {
                    summarizeInTLDR()
                }
            }
            listOf(llmSession.requestLLMWithoutTools())
        }
    }

    /**
     * Composes a message history by combining specific message types and handling memory preservation.
     *
     * The compose method preserves all system messages as well as the first user message (if present), then adds
     * memory messages (if provided), then sorts all current messages by timestamp and then appends tldr messages.
     *
     * @param originalMessages The original list of messages from the conversation history.
     * @param tldrMessages A list of messages that represent summarized or compressed content to include in the prompt.
     * @param memoryMessages A list of memory messages that should be included in the prompt.
     */
    protected fun composeMessageHistory(
        originalMessages: List<Message>,
        tldrMessages: List<Message>,
        memoryMessages: List<Message>,
    ): List<Message> {
        val messages = mutableListOf<Message>()

        // Leave all the system messages
        val systemMessages = originalMessages.filterIsInstance<Message.System>()
        messages.addAll(systemMessages)

        // Leave the first user message if present
        val firstUserMessage = originalMessages.firstOrNull { it is Message.User }
        firstUserMessage?.let { messages.add(it) }

        // Add the memory messages
        messages.addAll(memoryMessages)

        // Sort the messages by timestamp
        messages.sortWith { a, b -> a.metaInfo.timestamp.compareTo(b.metaInfo.timestamp) }

        // Add the tldr messages
        messages.addAll(tldrMessages)

        val trailingToolCalls = originalMessages.takeLastWhile { it is Message.Tool.Call }
        messages.addAll(trailingToolCalls)

        return messages
    }

    /**
     * Splits the provided list of messages into blocks of messages related to the same system message.
     * [User, System, User, Assistant, ToolCall, ToolResult, System, User, Assistant, System] ->
     * [[User, System, User, Assistant, ToolCall, ToolResult], [System, User, Assistant, Tool], [System, ]]
     *
     * @param messages The list of messages to be split.
     * @return A list of message blocks, each containing a list of messages related to the same system message.
     */
    protected fun splitHistoryBySystemMessages(messages: List<Message>): List<List<Message>> {
        val result = mutableListOf<MutableList<Message>>()
        var currentBlock = mutableListOf<Message>()
        var beforeSystemMessage = true

        for (message in messages) {
            if (message is Message.System) {
                if (beforeSystemMessage) {
                    beforeSystemMessage = false
                } else {
                    result.add(currentBlock)
                    currentBlock = mutableListOf()
                }
            }
            currentBlock.add(message)
        }

        if (currentBlock.isNotEmpty()) {
            result.add(currentBlock)
        }

        return result
    }

    /**
     * WholeHistory is a concrete implementation of the HistoryCompressionStrategy
     * that encapsulates the logic for compressing entire conversation history into
     * a succinct summary (TL;DR) and composing the necessary messages to create a
     * streamlined prompt suitable for language model interactions.
     *
     * This strategy preserves all system messages as well as the first user message
     * (if presented) and memory messages (if provided) and then appends
     * tldr of the whole original history (except trailing tool calls).
     *
     * [System, User, Assistant, ToolCall1, ToolResult, ToolCall2]
     * ->
     * [System, User, Memory, TLDR(System, User, Assistant, ToolCall1, ToolResult)]
     */
    public object WholeHistory : HistoryCompressionStrategy() {
        /**
         * Compresses and adjusts the prompt for the agent's writing session by summarizing and incorporating
         * memory messages optionally.
         *
         * @param llmSession The current session of the agent which allows prompt manipulation and sending requests.
         * @param memoryMessages A list of memory messages to be optionally preserved and included in the prompt.
         */
        override suspend fun compress(
            llmSession: AIAgentLLMWriteSession,
            memoryMessages: List<Message>
        ) {
            val originalMessages = llmSession.prompt.messages
            val tldrMessages = compressPromptIntoTLDR(llmSession)
            val compressedMessages = composeMessageHistory(
                originalMessages,
                tldrMessages,
                memoryMessages,
            )
            llmSession.prompt = llmSession.prompt.withMessages { compressedMessages }
        }
    }

    /**
     * WholeHistoryMultipleSystemMessages is a concrete implementation of the HistoryCompressionStrategy
     * that handles scenarios where the conversation history contains multiple system messages.
     *
     * This strategy:
     * 1. Splits the history into blocks based on system message boundaries
     * 2. Processes each block separately to generate TL;DR summaries
     * 3. Maintains the chronological order of system messages while compressing the conversation
     * 4. Preserves memory messages only in the first block to maintain context
     *
     * [System1, User1, Assistant, ToolCall, ToolResult, System2, User2, Assistant, User3, System3, Assistant, System4 ]
     * ->
     * [System1, User1, Memory, TLDR(System1, User1, Assistant, ToolCall, ToolResult),
     * System2, User2, TLDR(System2, User2, Assistant, User3),
     * System3, Assistant, TLDR(System3, Assistant)
     * System4, TLDR(System4)]
     */
    public object WholeHistoryMultipleSystemMessages : HistoryCompressionStrategy() {
        /**
         * Compresses and adjusts the prompt for the agent's write session by summarizing and incorporating
         * memory messages optionally.
         *
         * @param llmSession The current session of the agent which allows prompt manipulation and sending requests.
         * @param memoryMessages A list of memory messages to be optionally preserved and included in the prompt.
         */
        override suspend fun compress(
            llmSession: AIAgentLLMWriteSession,
            memoryMessages: List<Message>
        ) {
            val compressedMessages = mutableListOf<Message>()

            // Split the messages into blocks by system messages
            val messageBlocks = splitHistoryBySystemMessages(llmSession.prompt.messages)

            messageBlocks.mapIndexed { index, messageBlock ->
                llmSession.prompt = llmSession.prompt.withMessages { messageBlock }

                // Compress the current block of messages
                val tldrMessageBlock = compressPromptIntoTLDR(llmSession)

                // Compos the messages for the current block
                val compressedMessageBlock = composeMessageHistory(
                    originalMessages = messageBlock,
                    tldrMessages = tldrMessageBlock,
                    // Add memories only to the first block
                    memoryMessages = if (index == 0) memoryMessages else emptyList(),
                )
                compressedMessages.addAll(compressedMessageBlock)
            }
            llmSession.prompt = llmSession.prompt.withMessages { compressedMessages }
        }
    }

    /**
     * A strategy for compressing history by retaining only the last `n` messages in a session.
     *
     * This class removes all but the last `n` messages from the current prompt history and then
     * compresses the retained messages into a summary (TL;DR). It also allows integration of
     * specific memory messages back into the prompt if needed.
     *
     * @property n The number of most recent messages to retain during compression.
     */
    public data class FromLastNMessages(val n: Int) : HistoryCompressionStrategy() {
        /**
         * Compresses the conversation history by retaining the last N messages, generating a summary,
         * and composing the resulting prompt with the necessary messages.
         *
         * @param llmSession the session in which the language model operates, providing functionalities
         *        to manage prompts and request responses.
         * @param memoryMessages a list of messages representing historical memory to be optionally retained
         *        if preserveMemory is true.
         */
        override suspend fun compress(
            llmSession: AIAgentLLMWriteSession,
            memoryMessages: List<Message>
        ) {
            val originalMessages = llmSession.prompt.messages
            llmSession.leaveLastNMessages(n)
            val tldrMessages = compressPromptIntoTLDR(llmSession)
            val compressedMessages = composeMessageHistory(
                originalMessages,
                tldrMessages,
                memoryMessages,
            )
            llmSession.prompt = llmSession.prompt.withMessages { compressedMessages }
        }
    }

    /**
     * A strategy for compressing message histories using a specified timestamp as a reference point.
     * This strategy removes messages that occurred before a given timestamp and creates a summarized
     * context for further interactions.
     *
     * This strategy preserves all system messages as well as the first user message
     * (if presented) and memory messages (if provided) and then appends
     * tldr of the subset of messages starting from the provided timestamp (except trailing tool calls).
     *
     * @param timestamp The timestamp indicating the earliest point to retain messages from.
     */
    public data class FromTimestamp(val timestamp: Instant) : HistoryCompressionStrategy() {
        /**
         * Compresses the conversation history by retaining the messages from the timestamp, generating a summary,
         * and composing the resulting prompt with the necessary messages.
         *
         * @param llmSession The session used for writing and managing the large language model's state.
         * @param memoryMessages The list of memory messages that should be used or referenced during compression.
         */
        override suspend fun compress(
            llmSession: AIAgentLLMWriteSession,
            memoryMessages: List<Message>
        ) {
            val originalMessages = llmSession.prompt.messages
            llmSession.leaveMessagesFromTimestamp(timestamp)
            val tldrMessages = compressPromptIntoTLDR(llmSession)
            val compressedMessages = composeMessageHistory(
                originalMessages,
                tldrMessages,
                memoryMessages,
            )
            llmSession.prompt = llmSession.prompt.withMessages { compressedMessages }
        }
    }

    /**
     * A concrete implementation of the `HistoryCompressionStrategy` that splits the session's prompt
     * into chunks of a predefined size and generates summaries (TL;DR) for each chunk.
     *
     * This strategy preserves all system messages as well as the first user message
     * (if presented) and memory messages (if provided) and then appends
     * tldr of each chuck of messages from initial history (except trailing tool calls for each chunk).
     *
     * @property chunkSize The size of chunks into which the prompt messages are divided.
     */
    public data class Chunked(val chunkSize: Int) : HistoryCompressionStrategy() {
        /**
         * Compresses the conversation history into a summarized form (TLDR) using chunked processing.
         *
         * @param llmSession The session used to interact with the LLM, which maintains the prompt and tool states.
         * @param memoryMessages A list of memory messages to be retained if preserveMemory is true.
         */
        override suspend fun compress(
            llmSession: AIAgentLLMWriteSession,
            memoryMessages: List<Message>
        ) {
            val originalMessages = llmSession.prompt.messages
            val tldrMessageChunks = llmSession.prompt.messages.chunked(chunkSize).flatMap { messageChunk ->
                llmSession.prompt = llmSession.prompt.withMessages { messageChunk }

                compressPromptIntoTLDR(llmSession)
            }

            val compressedMessages = composeMessageHistory(
                originalMessages,
                tldrMessageChunks,
                memoryMessages
            )

            llmSession.prompt = llmSession.prompt.withMessages { compressedMessages }
        }
    }
}
