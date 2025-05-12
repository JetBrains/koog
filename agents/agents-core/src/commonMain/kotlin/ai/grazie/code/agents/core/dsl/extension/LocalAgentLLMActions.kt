package ai.grazie.code.agents.core.dsl.extension

import ai.grazie.code.agents.core.agent.entity.stage.LocalAgentLLMWriteSession
import ai.jetbrains.code.prompt.params.LLMParams

fun LocalAgentLLMWriteSession.clearHistory() {
    prompt = prompt.copy(messages = emptyList())
}

fun LocalAgentLLMWriteSession.leaveLastNMessages(n: Int) {
    prompt = prompt.copy(messages = prompt.messages.takeLast(n))
}

/**
 * Sets the [ai.jetbrains.code.prompt.params.LLMParams.ToolChoice] for this LLM session.
 */
fun LocalAgentLLMWriteSession.setToolChoice(toolChoice: LLMParams.ToolChoice?) {
    prompt = prompt.copy(params = prompt.params.copy(toolChoice = toolChoice))
}

/**
 * Set the [ai.jetbrains.code.prompt.params.LLMParams.ToolChoice] to [ai.jetbrains.code.prompt.params.LLMParams.ToolChoice.Auto] to make LLM automatically decide between calling tools and generating text
 */
fun LocalAgentLLMWriteSession.setToolChoiceAuto() {
    setToolChoice(LLMParams.ToolChoice.Auto)
}

/**
 * Set the [ai.jetbrains.code.prompt.params.LLMParams.ToolChoice] to [ai.jetbrains.code.prompt.params.LLMParams.ToolChoice.Required] to make LLM always call tools
 */
fun LocalAgentLLMWriteSession.setToolChoiceRequired() {
    setToolChoice(LLMParams.ToolChoice.Required)
}

/**
 * Set the [ai.jetbrains.code.prompt.params.LLMParams.ToolChoice] to [ai.jetbrains.code.prompt.params.LLMParams.ToolChoice.None] to make LLM never call tools
 */
fun LocalAgentLLMWriteSession.setToolChoiceNone() {
    setToolChoice(LLMParams.ToolChoice.None)
}

/**
 * Set the [ai.jetbrains.code.prompt.params.LLMParams.ToolChoice] to [ai.jetbrains.code.prompt.params.LLMParams.ToolChoice.None] to make LLM call one specific tool [toolName]
 */
fun LocalAgentLLMWriteSession.setToolChoiceNamed(toolName: String) {
    setToolChoice(LLMParams.ToolChoice.Named(toolName))
}

/**
 * Unset the [ai.jetbrains.code.prompt.params.LLMParams.ToolChoice].
 * Mostly, if left unspecified, the default value of this parameter is [ai.jetbrains.code.prompt.params.LLMParams.ToolChoice.Auto]
 */
fun LocalAgentLLMWriteSession.unsetToolChoice() {
    setToolChoice(null)
}

/**
 * Rewrites LLM message history, leaving only user message and resulting TLDR.
 *
 * @param fromLastN Number of last messages used as a context for TLDR.
 * Default is `null`, which means entire history will be used.
 * @param preserveMemory Whether to preserve memory-related messages in the history.
 */
suspend fun LocalAgentLLMWriteSession.replaceHistoryWithTLDR(
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
