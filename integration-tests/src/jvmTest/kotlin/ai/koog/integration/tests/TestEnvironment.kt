package ai.koog.integration.tests

import ai.koog.prompt.executor.clients.openai.OpenAIClientSettings
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import me.kpavlov.aimocks.openai.MockOpenai

/**
 * Provides a test environment setup for working with the LLM APIs.
 *
 * This object manages configuration for both mock and real interactions with the LLMs.
 * It reads environment variables and determines whether to use a mock implementation
 * or the actual API based on the availability of the API key in the environment variables.
 * It also supplies the necessary LLM simulator,
 * supporting testing and development use cases.
 */
object TestEnvironment {

    private const val DUMMY_OPEN_AI_API_KEY = "dummy_open_ai_api_key"

    private val openAIApiKey: String get() = readTestOpenAIKeyFromEnv(fallbackKey = DUMMY_OPEN_AI_API_KEY)

    val mockOpenAI: MockOpenai by lazy {
        require(isMockOpenai) {
            """
            ERROR: 🧐Are you sure you want to use MockOpenai when real API key is provided?   
            """.trimIndent()
        }
        MockOpenai(verbose = true)
    }

    val isMockOpenai: Boolean get() = openAIApiKey == DUMMY_OPEN_AI_API_KEY

    fun createOpenAILLMClient(): OpenAILLMClient {
        val settings = if (isMockOpenai) {
            OpenAIClientSettings(baseUrl = mockOpenAI.baseUrl())
        } else {
            OpenAIClientSettings()
        }
        return OpenAILLMClient(openAIApiKey, settings)
    }

    private fun readTestKeyFromEnv(keyName: String, fallbackKey: String? = null): String {
        val key = System.getenv(keyName)?.let { fallbackKey }
        requireNotNull(key) {
            "ERROR: environment variable `$keyName` is not set"
        }
        return key
    }

    fun readTestAnthropicKeyFromEnv(fallbackKey: String? = null): String =
        readTestKeyFromEnv("ANTHROPIC_API_TEST_KEY", fallbackKey)

    fun readTestOpenAIKeyFromEnv(fallbackKey: String? = null): String =
        readTestKeyFromEnv("OPEN_AI_API_TEST_KEY", fallbackKey)

    fun readTestGoogleAIKeyFromEnv(fallbackKey: String? = null): String =
        readTestKeyFromEnv("GEMINI_API_TEST_KEY", fallbackKey)

    fun readTestOpenRouterKeyFromEnv(fallbackKey: String? = null): String =
        readTestKeyFromEnv("OPEN_ROUTER_API_TEST_KEY", fallbackKey)
}
