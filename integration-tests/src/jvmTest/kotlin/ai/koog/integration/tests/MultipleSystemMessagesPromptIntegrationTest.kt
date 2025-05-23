package ai.koog.integration.tests

import ai.koog.integration.tests.TestEnvironment.readTestAnthropicKeyFromEnv
import ai.koog.integration.tests.TestEnvironment.readTestGoogleAIKeyFromEnv
import ai.koog.integration.tests.TestEnvironment.readTestOpenAIKeyFromEnv
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.clients.google.GoogleLLMClient
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.llm.LLMProvider
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

class MultipleSystemMessagesPromptIntegrationTest {
    private val openAIToken = readTestOpenAIKeyFromEnv()
    private val anthropicToken = readTestAnthropicKeyFromEnv()
    private val googleToken = readTestGoogleAIKeyFromEnv()

    @Test
    fun integration_testMultipleSystemMessages() = runBlocking {
        val openAIClient = OpenAILLMClient(openAIToken)
        val anthropicClient = AnthropicLLMClient(anthropicToken)
        val googleClient = GoogleLLMClient(googleToken)

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

        assertTrue(responseOpenAI.isNotEmpty(), "OpenAI response should not be empty")
        assertTrue(responseAnthropic.isNotEmpty(), "Anthropic response should not be empty")
        assertTrue(responseGemini.isNotEmpty(), "Gemini response should not be empty")
        println("OpenAI Response: $responseOpenAI")
        println("Anthropic Response: $responseAnthropic")
        println("Gemini Response: $responseGemini")
    }
}
