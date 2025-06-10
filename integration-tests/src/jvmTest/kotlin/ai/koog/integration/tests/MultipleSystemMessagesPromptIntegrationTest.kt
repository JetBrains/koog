package ai.koog.integration.tests

import ai.koog.integration.tests.utils.TestUtils.readTestAnthropicKeyFromEnv
import ai.koog.integration.tests.utils.TestUtils.readTestGoogleAIKeyFromEnv
import ai.koog.integration.tests.utils.TestUtils.readTestOpenAIKeyFromEnv
import ai.koog.integration.tests.utils.annotations.Retry
import ai.koog.integration.tests.utils.annotations.RetryExtension
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.model.PromptExecutorExt.execute
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.clients.google.GoogleLLMClient
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.llm.LLMProvider
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.Test
import kotlin.test.assertTrue

@ExtendWith(RetryExtension::class)
class MultipleSystemMessagesPromptIntegrationTest {
    private val openAIApiKey = readTestOpenAIKeyFromEnv()
    private val anthropicApiKey = readTestAnthropicKeyFromEnv()
    private val googleApiKey = readTestGoogleAIKeyFromEnv()

    @Retry
    @Test
    fun integration_testMultipleSystemMessages() = runBlocking {
        val openAIClient = OpenAILLMClient(openAIApiKey)
        val anthropicClient = AnthropicLLMClient(anthropicApiKey)
        val googleClient = GoogleLLMClient(googleApiKey)

        val executor = MultiLLMPromptExecutor(
            LLMProvider.OpenAI to openAIClient,
            LLMProvider.Anthropic to anthropicClient,
            LLMProvider.Google to googleClient
        )

        val prompt = prompt("multiple-system-messages-test") {
            system("You are a helpful assistant.")
            user("Hi")
            system("You can handle multiple system messages.")
            user("Respond with a short message.")
        }

        val modelOpenAI = OpenAIModels.CostOptimized.GPT4oMini
        val modelAnthropic = AnthropicModels.Haiku_3_5
        val modelGemini = GoogleModels.Gemini2_0Flash

        val responseOpenAI = executor.execute(prompt, modelOpenAI)
        val responseAnthropic = executor.execute(prompt, modelAnthropic)
        val responseGemini = executor.execute(prompt, modelGemini)

        assertTrue(responseOpenAI.content.isNotEmpty(), "OpenAI response should not be empty")
        assertTrue(responseAnthropic.content.isNotEmpty(), "Anthropic response should not be empty")
        assertTrue(responseGemini.content.isNotEmpty(), "Gemini response should not be empty")
        println("OpenAI Response: ${responseOpenAI.content}")
        println("Anthropic Response: ${responseAnthropic.content}")
        println("Gemini Response: ${responseGemini.content}")
    }
}