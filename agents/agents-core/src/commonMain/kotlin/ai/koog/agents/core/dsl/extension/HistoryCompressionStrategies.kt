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
     * @param preserveMemory A flag indicating whether parts of the memory should be preserved during compression.
     * @param memoryMessages A list of messages representing the memory to be compressed.
     */
    public abstract suspend fun compress(
        llmSession: AIAgentLLMWriteSession,
        preserveMemory: Boolean,
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
            dropTrailingToolCalls()
            updatePrompt {
                user {
                     summarizeInTLDR()
                }
            }
            listOf(llmSession.requestLLMWithoutTools())
        }
    }

    /**
     * Composes a new prompt by combining specific message types and handling memory preservation.
     *
     * @param llmSession The LLM write session used for prompt interaction and updates.
     * @param tldrMessages A list of messages that represent summarized or compressed content to include in the prompt.
     * @param preserveMemory A flag indicating whether to include memory messages in the prompt.
     * @param memoryMessages A list of memory messages that should be included in the prompt if `preserveMemory` is set to true.
     */
    protected fun composePromptWithRequiredMessages(
        llmSession: AIAgentLLMWriteSession,
        tldrMessages: List<Message>,
        preserveMemory: Boolean,
        memoryMessages: List<Message>
    ) {
        with(llmSession) {
            // Filter messages similar to MicroAgentBase
            val systemMessages = prompt.messages.filterIsInstance<Message.System>()
            val firstUserMessage = prompt.messages.firstOrNull { it is Message.User }

            prompt = prompt.withMessages { buildList {
                addAll(systemMessages)
                // Restore memory messages if needed
                if (preserveMemory && memoryMessages.isNotEmpty()) {
                    addAll(memoryMessages)
                }

                if (firstUserMessage != null) add(firstUserMessage)
                addAll(tldrMessages)
            }}
        }
    }

    /**
     * WholeHistory is a concrete implementation of the HistoryCompressionStrategy
     * that encapsulates the logic for compressing entire conversation history into
     * a succinct summary (TL;DR) and composing necessary messages to create a
     * streamlined prompt suitable for language model interactions.
     */
    public object WholeHistory : HistoryCompressionStrategy() {
        /**
         * Compresses and adjusts the prompt for the local agent's write session by summarizing and incorporating
         * memory messages optionally.
         *
         * @param llmSession The current session of the local agent which allows prompt manipulation and sending requests.
         * @param preserveMemory A flag indicating whether memory messages should be preserved during compression.
         * @param memoryMessages A list of memory messages to be optionally preserved and included in the prompt.
         */
        override suspend fun compress(
            llmSession: AIAgentLLMWriteSession,
            preserveMemory: Boolean,
            memoryMessages: List<Message>
        ) {
            val tldr = compressPromptIntoTLDR(llmSession)
            composePromptWithRequiredMessages(llmSession, tldr, preserveMemory, memoryMessages)
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
         * @param llmSession the session in which the local language model operates, providing functionalities
         *        to manage prompts and request responses.
         * @param preserveMemory a flag indicating whether memory messages should be preserved and included
         *        in the final composed prompt.
         * @param memoryMessages a list of messages representing historical memory to be optionally retained
         *        if preserveMemory is true.
         */
        override suspend fun compress(
            llmSession: AIAgentLLMWriteSession,
            preserveMemory: Boolean,
            memoryMessages: List<Message>
        ) {
            llmSession.leaveLastNMessages(n)
            val tldr = compressPromptIntoTLDR(llmSession)
            composePromptWithRequiredMessages(llmSession, tldr, preserveMemory, memoryMessages)
        }
    }

    /**
     * A strategy for compressing message histories using a specified timestamp as a reference point.
     * This strategy removes messages that occurred before a given timestamp and creates a summarized
     * context for further interactions.
     *
     * @param timestamp The timestamp indicating the earliest point to retain messages from.
     */
    public data class FromTimestamp(val timestamp: Instant) : HistoryCompressionStrategy() {
        /**
         * Compresses the message history in the provided session according to the specified strategy.
         *
         * @param llmSession The session used for writing and managing the large language model's state.
         * @param preserveMemory If true, ensures memory messages are preserved.
         * @param memoryMessages The list of memory messages that should be used or referenced during compression.
         */
        override suspend fun compress(
            llmSession: AIAgentLLMWriteSession,
            preserveMemory: Boolean,
            memoryMessages: List<Message>
        ) {
            llmSession.leaveMessagesFromTimestamp(timestamp)
            val tldr = compressPromptIntoTLDR(llmSession)
            composePromptWithRequiredMessages(llmSession, tldr, preserveMemory, memoryMessages)
        }
    }

    /**
     * A concrete implementation of the `HistoryCompressionStrategy` that splits the session's prompt
     * into chunks of a predefined size and generates summaries (TL;DR) for each chunk.
     *
     * The intent of this class is to manage long conversation histories by compressing them
     * into smaller, summarized chunks, preserving memory and usability for LLM interactions.
     *
     * @property chunkSize The size of chunks into which the prompt messages are divided.
     */
    public data class Chunked(val chunkSize: Int) : HistoryCompressionStrategy() {
        /**
         * Compresses the conversation history into a summarized form (TLDR) using chunked processing.
         *
         * @param llmSession The session used to interact with the LLM, which maintains the prompt and tool states.
         * @param preserveMemory A flag indicating whether to retain memory messages in the final prompt.
         * @param memoryMessages A list of memory messages to be retained if preserveMemory is true.
         */
        override suspend fun compress(
            llmSession: AIAgentLLMWriteSession,
            preserveMemory: Boolean,
            memoryMessages: List<Message>
        ) {
            val chunkedTLDR = llmSession.prompt.messages.chunked(chunkSize).flatMap { chunk ->
                llmSession.clearHistory()

                llmSession.prompt = llmSession.prompt.withMessages { chunk }

                compressPromptIntoTLDR(llmSession)
            }

            composePromptWithRequiredMessages(llmSession, chunkedTLDR, preserveMemory, memoryMessages)
        }
    }
}

