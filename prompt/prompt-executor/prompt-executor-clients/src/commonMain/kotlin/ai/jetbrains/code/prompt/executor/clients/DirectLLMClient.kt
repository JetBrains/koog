package ai.jetbrains.code.prompt.executor.clients

import ai.grazie.code.agents.core.tools.ToolDescriptor
import ai.jetbrains.code.prompt.dsl.Prompt
import ai.jetbrains.code.prompt.message.Message
import kotlinx.coroutines.flow.Flow

/**
 * Common interface for direct communication with LLM providers.
 * This interface defines methods for executing prompts and streaming responses.
 */
interface DirectLLMClient {
    /**
     * Executes a prompt and returns a list of response messages.
     *
     * @param prompt The prompt to execute
     * @param tools Optional list of tools that can be used by the LLM
     * @return List of response messages
     */
    suspend fun execute(prompt: Prompt, tools: List<ToolDescriptor> = emptyList()): List<Message.Response>

    /**
     * Executes a prompt and returns a streaming flow of response chunks.
     *
     * @param prompt The prompt to execute
     * @return Flow of response chunks
     */
    suspend fun executeStreaming(prompt: Prompt): Flow<String>
}


data class ConnectionTimeoutConfig(
    val requestTimeoutMillis: Long = DEFAULT_TIMEOUT_MS,
    val connectTimeoutMillis: Long = DEFAULT_CONNECT_TIMEOUT_MS,
    val socketTimeoutMillis: Long = DEFAULT_TIMEOUT_MS,
) {
    private companion object {
        private const val DEFAULT_TIMEOUT_MS: Long = 900000 // 900 seconds
        private const val DEFAULT_CONNECT_TIMEOUT_MS: Long = 60_000
    }
}