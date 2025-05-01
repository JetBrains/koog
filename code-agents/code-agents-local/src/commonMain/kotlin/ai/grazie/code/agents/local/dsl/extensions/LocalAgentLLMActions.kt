package ai.grazie.code.agents.local.dsl.extensions

import ai.grazie.code.agents.local.agent.stage.LocalAgentLLMWriteSession

fun LocalAgentLLMWriteSession.clearHistory() {
    prompt = prompt.copy(messages = emptyList())
}

fun LocalAgentLLMWriteSession.leaveLastNMessages(n: Int) {
    prompt = prompt.copy(messages = prompt.messages.takeLast(n))
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
