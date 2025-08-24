package ai.koog.prompt.executor.clients.deepseek

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
 * Configuration settings for connecting to the DeepSeek API.
 *
 * @property baseUrl The base URL of the DeepSeek API. The default is "https://api.deepseek.com".
 * @property timeoutConfig Configuration for connection timeouts including request, connection, and socket timeouts.
 */
public class DeepSeekClientSettings(
    baseUrl: String = "https://api.deepseek.com",
    chatCompletionsPath: String = "chat/completions",
    timeoutConfig: ConnectionTimeoutConfig = ConnectionTimeoutConfig()
) : OpenAIBasedSettings(baseUrl, chatCompletionsPath, timeoutConfig)

/**
 * Implementation of [LLMClient] for DeepSeek API.
 *
 * @param apiKey The API key for the DeepSeek API
 * @param settings The base URL, chat completion path, and timeouts for the DeepSeek API,
 * defaults to "https://api.deepseek.com" and 900s
 * @param clock Clock instance used for tracking response metadata timestamps.
 */
public class DeepSeekLLMClient(
    apiKey: String,
    private val settings: DeepSeekClientSettings = DeepSeekClientSettings(),
    baseClient: HttpClient = HttpClient(),
    clock: Clock = Clock.System
) : AbstractOpenAILLMClient(apiKey, settings, baseClient, clock) {

    private companion object {
        private val staticLogger = KotlinLogging.logger { }
    }

    override val logger: KLogger = staticLogger

    /**
     * Executes a moderation action on the given prompt using the specified language model.
     * This method is not supported by the DeepSeek API and will always throw an `UnsupportedOperationException`.
     *
     * @param prompt The [Prompt] object to be moderated, containing the messages and respective context.
     * @param model The [LLModel] to be used for the moderation process.
     * @return This method does not return a valid result as it always throws an exception.
     * @throws UnsupportedOperationException Always thrown because moderation is not supported by the DeepSeek API.
     */
    public override suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult {
        logger.warn { "Moderation is not supported by DeepSeek API" }
        throw UnsupportedOperationException("Moderation is not supported by DeepSeek API.")
    }
}
