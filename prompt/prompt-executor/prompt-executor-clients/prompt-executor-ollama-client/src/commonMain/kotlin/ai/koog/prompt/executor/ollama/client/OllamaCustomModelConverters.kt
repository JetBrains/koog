package ai.koog.prompt.executor.ollama.client

import io.github.oshai.kotlinlogging.KotlinLogging


/**
 * Custom converters for specific Ollama models.
 */
public object OllamaCustomModelConverters {
    private val logger = KotlinLogging.logger {  }

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
