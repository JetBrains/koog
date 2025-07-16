package ai.koog.prompt.executor.clients.google

import ai.koog.agents.testing.llm.AbstractLLMClientErrorsTest
import ai.koog.prompt.executor.clients.ConnectionTimeoutConfig
import ai.koog.prompt.executor.clients.google.GoogleModels.Gemini2_0Flash001
import me.kpavlov.aimocks.core.AbstractBuildingStep
import me.kpavlov.aimocks.gemini.MockGemini
import kotlin.time.Duration

internal class GoogleLLMClientErrorsTest : AbstractLLMClientErrorsTest<GoogleLLMClient, MockGemini>(
    mock = MockGemini(verbose = true),
    model = Gemini2_0Flash001,
) {

    override fun createClient(
        temperature: Double,
        requestTimeout: Duration?
    ) = GoogleLLMClient(
        apiKey = "dummy-api-key",
        settings = GoogleClientSettings(
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

    override fun prepareMock(question: String, temperature: Double): AbstractBuildingStep<*, *> =
        mock.generateContent {
            path("/v1beta/models/${super.model.id}:generateContent")
            userMessageContains(question)
            temperature(temperature)
        }
}
