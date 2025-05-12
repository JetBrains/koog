package ai.jetbrains.code.prompt.executor.ollama.client

import ai.grazie.utils.mpp.LoggerFactory

/**
 * Custom converters for specific Ollama models.
 */
public object OllamaCustomModelConverters {
    private val logger = LoggerFactory.create(OllamaCustomModelConverters::class.simpleName!!)

    /**
     * Processes responses from the QWQ model by removing any content between <think> and </think> tags.
     */
    public fun qwq(response: String): String {
        val thinkingStart = response.indexOf("<think>")
        val thinkingEnd = response.indexOf("</think>")
        if (thinkingStart == -1 || thinkingEnd == -1) return response
        logger.info { "Thinking response: ${response.substring(thinkingStart, thinkingEnd)}" }
        return response.substring(thinkingEnd + "</think>".length).trimStart()
    }
}
