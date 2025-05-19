package ai.koog.agents.core.dsl.extension

import ai.koog.agents.core.agent.session.AIAgentLLMWriteSession
import ai.koog.prompt.params.LLMParams

public fun AIAgentLLMWriteSession.clearHistory() {
    prompt = prompt.withMessages(emptyList())
}

public fun AIAgentLLMWriteSession.leaveLastNMessages(n: Int) {
    prompt = prompt.withUpdatedMessages { takeLast(n) }
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
