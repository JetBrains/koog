package ai.grazie.code.agents.core.dsl.extension

import ai.grazie.code.agents.core.agent.entity.stage.AgentLLMWriteSession
import ai.jetbrains.code.prompt.params.LLMParams

fun AgentLLMWriteSession.clearHistory() {
    prompt = prompt.withMessages(emptyList())
}

fun AgentLLMWriteSession.leaveLastNMessages(n: Int) {
    prompt = prompt.withUpdatedMessages { takeLast(n) }
}

/**
 * Sets the [ai.jetbrains.code.prompt.params.LLMParams.ToolChoice] for this LLM session.
 */
fun AgentLLMWriteSession.setToolChoice(toolChoice: LLMParams.ToolChoice?) {
    prompt = prompt.withUpdatedParams { this.toolChoice = toolChoice }
}

/**
 * Set the [ai.jetbrains.code.prompt.params.LLMParams.ToolChoice] to [ai.jetbrains.code.prompt.params.LLMParams.ToolChoice.Auto] to make LLM automatically decide between calling tools and generating text
 */
fun AgentLLMWriteSession.setToolChoiceAuto() {
    setToolChoice(LLMParams.ToolChoice.Auto)
}

/**
 * Set the [ai.jetbrains.code.prompt.params.LLMParams.ToolChoice] to [ai.jetbrains.code.prompt.params.LLMParams.ToolChoice.Required] to make LLM always call tools
 */
fun AgentLLMWriteSession.setToolChoiceRequired() {
    setToolChoice(LLMParams.ToolChoice.Required)
}

/**
 * Set the [ai.jetbrains.code.prompt.params.LLMParams.ToolChoice] to [ai.jetbrains.code.prompt.params.LLMParams.ToolChoice.None] to make LLM never call tools
 */
fun AgentLLMWriteSession.setToolChoiceNone() {
    setToolChoice(LLMParams.ToolChoice.None)
}

/**
 * Set the [ai.jetbrains.code.prompt.params.LLMParams.ToolChoice] to [ai.jetbrains.code.prompt.params.LLMParams.ToolChoice.None] to make LLM call one specific tool [toolName]
 */
fun AgentLLMWriteSession.setToolChoiceNamed(toolName: String) {
    setToolChoice(LLMParams.ToolChoice.Named(toolName))
}

/**
 * Unset the [ai.jetbrains.code.prompt.params.LLMParams.ToolChoice].
 * Mostly, if left unspecified, the default value of this parameter is [ai.jetbrains.code.prompt.params.LLMParams.ToolChoice.Auto]
 */
fun AgentLLMWriteSession.unsetToolChoice() {
    setToolChoice(null)
}

/**
 * Rewrites LLM message history, leaving only user message and resulting TLDR.
 *
 * @param fromLastN Number of last messages used as a context for TLDR.
 * Default is `null`, which means entire history will be used.
 * @param preserveMemory Whether to preserve memory-related messages in the history.
 */
suspend fun AgentLLMWriteSession.replaceHistoryWithTLDR(
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
