package ai.koog.prompt.executor.clients

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import kotlinx.coroutines.flow.Flow

/**
 * Common interface for direct communication with LLM providers.
 * This interface defines methods for executing prompts and streaming responses.
 */
public interface LLMClient {
    /**
     * Executes a prompt and returns a list of response messages.
     *
     * @param prompt The prompt to execute
     * @param tools Optional list of tools that can be used by the LLM
     * @param model The LLM model to use
     * @return List of response messages
     */
    public suspend fun execute(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor> = emptyList()
    ): List<Message.Response>

    /**
     * Executes a prompt and returns a streaming flow of response chunks.
     *
     * @param prompt The prompt to execute
     * @param model The LLM model to use
     * @return Flow of response chunks
     */
    public suspend fun executeStreaming(prompt: Prompt, model: LLModel): Flow<String>
}

public data class ConnectionTimeoutConfig(
    val requestTimeoutMillis: Long = DEFAULT_TIMEOUT_MS,
    val connectTimeoutMillis: Long = DEFAULT_CONNECT_TIMEOUT_MS,
    val socketTimeoutMillis: Long = DEFAULT_TIMEOUT_MS,
) {
    private companion object {
        private const val DEFAULT_TIMEOUT_MS: Long = 900000 // 900 seconds
        private const val DEFAULT_CONNECT_TIMEOUT_MS: Long = 60_000
    }
}
