package ai.jetbrains.code.prompt.executor.ollama.client

import ai.grazie.utils.mpp.LoggerFactory
import ai.grazie.utils.mpp.create

/**
 * Custom converters for specific Ollama models.
 */
object OllamaCustomModelConverters {
    private val logger = LoggerFactory.create(OllamaCustomModelConverters::class)

    /**
     * Processes responses from the QWQ model by removing any content between <think> and </think> tags.
     */
    fun qwq(response: String): String {
        val thinkingStart = response.indexOf("<think>")
        val thinkingEnd = response.indexOf("</think>")
        if (thinkingStart == -1 || thinkingEnd == -1) return response
        logger.info { "Thinking response: ${response.substring(thinkingStart, thinkingEnd)}" }
        return response.substring(thinkingEnd + "</think>".length).trimStart()
    }
}