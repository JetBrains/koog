package ai.koog.prompt.executor.clients.anthropic

import ai.koog.agents.testing.llm.AbstractLLMClientErrorsTest
import ai.koog.prompt.executor.clients.ConnectionTimeoutConfig
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels.Sonnet_3_7
import me.kpavlov.aimocks.anthropic.MockAnthropic
import me.kpavlov.aimocks.core.AbstractBuildingStep
import kotlin.time.Duration

internal class AnthropicLLMClientErrorsTest : AbstractLLMClientErrorsTest<AnthropicLLMClient, MockAnthropic>(
    mock = MockAnthropic(verbose = true),
    model = Sonnet_3_7,
) {

    // language=json
    override fun errorResponseBody(message: String) = """
        {
          "type": "error",
          "error": {
            "type": "does not matter",
            "message": "$message"
          }
        }
    """.trimIndent()

    override fun createClient(
        temperature: Double,
        requestTimeout: Duration?
    ) = AnthropicLLMClient(
        apiKey = "dummy-api-key",
        settings = AnthropicClientSettings(
            baseUrl = mock.baseUrl(),
            timeoutConfig = if (requestTimeout != null) {
                ConnectionTimeoutConfig(
                    requestTimeoutMillis = requestTimeout.inWholeMilliseconds,
                )
            } else {
                ConnectionTimeoutConfig()
            }
        )
    )

    override fun prepareMock(question: String, temperature: Double): AbstractBuildingStep<*, *> = mock.messages {
        userMessageContains(question)
        temperature(temperature)
    }
}
