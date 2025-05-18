package ai.koog.prompt.executor.llms

import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.clients.google.GoogleLLMClient
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.llm.LLMProvider
import kotlinx.coroutines.runBlocking
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertTrue

class MultipleSystemMessagesPromptIntegrationTest {
    private val openAIToken = System.getenv("OPEN_AI_API_TEST_KEY")
    private val anthropicToken = System.getenv("ANTHROPIC_API_TEST_KEY")

    private val googleToken = System.getenv("Gemini_API_TEST_KEY")

    @Ignore // TODO: `GEMINI_API_TEST_KEY`
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
        println("Gemini Response: $responseAnthropic")
    }
}
