package ai.koog.agents.core.dsl.extension

import ai.koog.agents.core.agent.session.AIAgentLLMWriteSession
import ai.koog.prompt.message.Message
import ai.koog.prompt.params.LLMParams
import kotlinx.datetime.Instant

/**
 * Clears the history of messages in the current AI Agent LLM Write Session.
 *
 * This method resets the message history by setting it to an empty list.
 * It is useful when you want to start a new conversation or reset the session's context.
 */
public fun AIAgentLLMWriteSession.clearHistory() {
    prompt = prompt.withMessages { emptyList() }
}

/**
 * Keeps only the last N messages in the session's prompt by removing all earlier messages.
 *
 * @param n The number of most recent messages to retain in the session's prompt.
 */
public fun AIAgentLLMWriteSession.leaveLastNMessages(n: Int) {
    prompt = prompt.withMessages { it.takeLast(n) }
}

/**
 * Removes the last `n` messages from the current prompt in the write session.
 *
 * @param n The number of messages to remove from the end of the current message list.
 */
public fun AIAgentLLMWriteSession.dropLastNMessages(n: Int) {
    prompt = prompt.withMessages { it.dropLast(n) }
}

/**
 * Removes all messages from the current session's prompt that have a timestamp
 * earlier than the specified timestamp.
 *
 * @param timestamp The threshold timestamp. Messages with a timestamp earlier than this will be removed.
 */
public fun AIAgentLLMWriteSession.leaveMessagesFromTimestamp(timestamp: Instant) {
    prompt = prompt.withMessages { it.filter { it.metaInfo.timestamp >= timestamp } }
}

/**
 * Sets the [ai.koog.prompt.params.LLMParams.ToolChoice] for this LLM session.
 */
public fun AIAgentLLMWriteSession.setToolChoice(toolChoice: LLMParams.ToolChoice?) {
    prompt = prompt.withUpdatedParams { this.toolChoice = toolChoice }
}

/**
 * Set the [ai.koog.prompt.params.LLMParams.ToolChoice] to [ai.koog.prompt.params.LLMParams.ToolChoice.Auto] to make LLM automatically decide between calling tools and generating text
 */
public fun AIAgentLLMWriteSession.setToolChoiceAuto() {
    setToolChoice(LLMParams.ToolChoice.Auto)
}

/**
 * Set the [ai.koog.prompt.params.LLMParams.ToolChoice] to [ai.koog.prompt.params.LLMParams.ToolChoice.Required] to make LLM always call tools
 */
public fun AIAgentLLMWriteSession.setToolChoiceRequired() {
    setToolChoice(LLMParams.ToolChoice.Required)
}

/**
 * Set the [ai.koog.prompt.params.LLMParams.ToolChoice] to [ai.koog.prompt.params.LLMParams.ToolChoice.None] to make LLM never call tools
 */
public fun AIAgentLLMWriteSession.setToolChoiceNone() {
    setToolChoice(LLMParams.ToolChoice.None)
}

/**
 * Set the [ai.koog.prompt.params.LLMParams.ToolChoice] to [ai.koog.prompt.params.LLMParams.ToolChoice.None] to make LLM call one specific tool [toolName]
 */
public fun AIAgentLLMWriteSession.setToolChoiceNamed(toolName: String) {
    setToolChoice(LLMParams.ToolChoice.Named(toolName))
}

/**
 * Unset the [ai.koog.prompt.params.LLMParams.ToolChoice].
 * Mostly, if left unspecified, the default value of this parameter is [ai.koog.prompt.params.LLMParams.ToolChoice.Auto]
 */
public fun AIAgentLLMWriteSession.unsetToolChoice() {
    setToolChoice(null)
}

/**
 * Rewrites LLM message history, leaving only user message and resulting TLDR.
 *
 * Default is `null`, which means entire history will be used.
 * @param preserveMemory Whether to preserve memory-related messages in the history.
 */
public suspend fun AIAgentLLMWriteSession.replaceHistoryWithTLDR(
    strategy: HistoryCompressionStrategy = HistoryCompressionStrategy.WholeHistory,
    preserveMemory: Boolean = true
) {
    // Store memory-related messages if needed
    val memoryMessages = if (preserveMemory) {
        prompt.messages.filter { message ->
            message.content.contains("Here are the relevant facts from memory") ||
                    message.content.contains("Memory feature is not enabled")
        }
    } else {
        emptyList()
    }

    strategy.compress(this, preserveMemory, memoryMessages)
}

/**
 * Drops all trailing tool call messages from the current prompt
 */
public fun AIAgentLLMWriteSession.dropTrailingToolCalls() {
    rewritePrompt { prompt -> prompt.withMessages { messages -> messages.dropLastWhile { it is Message.Tool.Call } } }
}
