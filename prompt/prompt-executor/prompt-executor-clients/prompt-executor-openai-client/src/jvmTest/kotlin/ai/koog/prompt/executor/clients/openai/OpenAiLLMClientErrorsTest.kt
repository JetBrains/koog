package ai.koog.prompt.executor.clients.openai

import ai.koog.agents.testing.llm.AbstractLLMClientErrorsTest
import ai.koog.prompt.executor.clients.ConnectionTimeoutConfig
import ai.koog.prompt.executor.clients.openai.OpenAIModels.CostOptimized.GPT4_1Nano
import me.kpavlov.aimocks.core.AbstractBuildingStep
import me.kpavlov.aimocks.openai.MockOpenai
import kotlin.time.Duration

internal class OpenAiLLMClientErrorsTest : AbstractLLMClientErrorsTest<OpenAILLMClient, MockOpenai>(
    mock = MockOpenai(verbose = true),
    model = GPT4_1Nano,
) {

    override fun createClient(
        temperature: Double,
        requestTimeout: Duration?
    ) = OpenAILLMClient(
        apiKey = "dummy-api-key",
        settings = OpenAIClientSettings(
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

    override fun prepareMock(question: String, temperature: Double): AbstractBuildingStep<*, *> = mock.completion {
        userMessageContains(question)
        temperature(temperature)
    }
}
