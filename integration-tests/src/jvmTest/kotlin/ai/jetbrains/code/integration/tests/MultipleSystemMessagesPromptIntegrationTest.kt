package ai.jetbrains.code.integration.tests

import ai.jetbrains.code.integration.tests.TestUtils.readTestAnthropicKeyFromEnv
import ai.jetbrains.code.integration.tests.TestUtils.readTestGeminiKeyFromEnv
import ai.jetbrains.code.integration.tests.TestUtils.readTestOpenAIKeyFromEnv
import ai.jetbrains.code.prompt.dsl.prompt
import ai.jetbrains.code.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.jetbrains.code.prompt.executor.clients.anthropic.AnthropicModels
import ai.jetbrains.code.prompt.executor.clients.google.GoogleLLMClient
import ai.jetbrains.code.prompt.executor.clients.google.GoogleModels
import ai.jetbrains.code.prompt.executor.clients.openai.OpenAILLMClient
import ai.jetbrains.code.prompt.executor.clients.openai.OpenAIModels
import ai.jetbrains.code.prompt.executor.llms.MultiLLMPromptExecutor
import ai.jetbrains.code.prompt.llm.LLMProvider
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

class MultipleSystemMessagesPromptIntegrationTest {
    private val openAIToken = readTestOpenAIKeyFromEnv()
    private val anthropicToken = readTestAnthropicKeyFromEnv()
    private val googleToken = readTestGeminiKeyFromEnv()

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