package ai.koog.prompt.executor.clients.openrouter

import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.ConnectionTimeoutConfig
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.openai.AbstractOpenAILLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIBasedSettings
import ai.koog.prompt.llm.LLModel
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import kotlinx.datetime.Clock

/**
 * Configuration settings for connecting to the OpenRouter API.
 *
 * @property baseUrl The base URL of the OpenRouter API. Default is "https://openrouter.ai/api/v1".
 * @property timeoutConfig Configuration for connection timeouts including request, connection, and socket timeouts.
 */
public class OpenRouterClientSettings(
    baseUrl: String = "https://openrouter.ai",
    chatCompletionsPath: String = "api/v1/chat/completions",
    timeoutConfig: ConnectionTimeoutConfig = ConnectionTimeoutConfig()
) : OpenAIBasedSettings(baseUrl, chatCompletionsPath, timeoutConfig)

/**
 * Implementation of [LLMClient] for OpenRouter API.
 * OpenRouter is an API that routes requests to multiple LLM providers.
 *
 * @param apiKey The API key for the OpenRouter API
 * @param settings The base URL and timeouts for the OpenRouter API, defaults to "https://openrouter.ai" and 900s
 * @param clock Clock instance used for tracking response metadata timestamps.
 */
public class OpenRouterLLMClient(
    apiKey: String,
    private val settings: OpenRouterClientSettings = OpenRouterClientSettings(),
    baseClient: HttpClient = HttpClient(),
    clock: Clock = Clock.System
) : AbstractOpenAILLMClient(apiKey, settings, baseClient, clock) {

    private companion object {
        private val staticLogger = KotlinLogging.logger { }
    }

    override val logger: KLogger = staticLogger

    /**
     * Executes a moderation action on the given prompt using the specified language model.
     * This method is not supported by the OpenRouter API and will always throw an `UnsupportedOperationException`.
     *
     * @param prompt The [Prompt] object to be moderated, containing the messages and respective context.
     * @param model The [LLModel] to be used for the moderation process.
     * @return This method does not return a valid result as it always throws an exception.
     * @throws UnsupportedOperationException Always thrown because moderation is not supported by the OpenRouter API.
     */
    public override suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult {
        logger.warn { "Moderation is not supported by OpenRouter API" }
        throw UnsupportedOperationException("Moderation is not supported by OpenRouter API.")
    }
}
