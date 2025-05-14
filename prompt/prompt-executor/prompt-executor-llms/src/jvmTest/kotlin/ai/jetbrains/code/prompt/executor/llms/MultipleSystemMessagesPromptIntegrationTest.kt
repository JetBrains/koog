package ai.jetbrains.code.prompt.executor.llms

import ai.jetbrains.code.prompt.dsl.prompt
import ai.jetbrains.code.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.jetbrains.code.prompt.executor.clients.anthropic.AnthropicModels
import ai.jetbrains.code.prompt.executor.clients.openai.OpenAILLMClient
import ai.jetbrains.code.prompt.executor.clients.openai.OpenAIModels
import ai.jetbrains.code.prompt.llm.LLMProvider
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

class MultipleSystemMessagesPromptIntegrationTest {
    private val openAIToken = System.getenv("OPEN_AI_API_TEST_KEY")
    private val anthropicToken = System.getenv("ANTHROPIC_API_TEST_KEY")

    @Test
    fun integration_testMultipleSystemMessages() = runBlocking {
        val openAIClient = OpenAILLMClient(openAIToken)
        val anthropicClient = AnthropicLLMClient(anthropicToken)

        val executor = MultiLLMPromptExecutor(
            LLMProvider.OpenAI to openAIClient,
            LLMProvider.Anthropic to anthropicClient
        )

        val prompt = prompt("multiple-system-messages-test") {
            system("You are a helpful assistant.")
            user("Hi")
            system("You can handle multiple system messages.")
            user("Respond with a short message.")
        }

        val modelOpenAI = OpenAIModels.CostOptimized.GPT4oMini
        val modelAnthropic = AnthropicModels.Haiku_3_5

        val responseOpenAI = executor.execute(prompt, modelOpenAI)
        val responseAnthropic = executor.execute(prompt, modelAnthropic)

        assertTrue(responseOpenAI.isNotEmpty(), "OpenAI response should not be empty")
        assertTrue(responseAnthropic.isNotEmpty(), "Anthropic response should not be empty")
        println("OpenAI Response: $responseOpenAI")
        println("Anthropic Response: $responseAnthropic")
    }
}
