package ai.jetbrains.code.prompt.executor.llms

import ai.jetbrains.code.prompt.dsl.prompt
import ai.jetbrains.code.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.jetbrains.code.prompt.executor.clients.anthropic.AnthropicModels
import ai.jetbrains.code.prompt.executor.clients.openai.OpenAILLMClient
import ai.jetbrains.code.prompt.executor.clients.openai.OpenAIModels
import ai.jetbrains.code.prompt.llm.LLMProvider
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Disabled
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A simple test to check that varios providers (Anthropic and OpenAI only for now) support multiple system messages in the prompt.
 * It is ad-hoc, so it's marked Disabled for now after we verified that it works.
 * But maybe we can use it later as a proper test, so I'll leave it there.
 */
@Disabled
class MultipleSystemMessagesPromptIntegrationTest {
    // Get API tokens from environment variables
    private val openAIToken = System.getenv("OPEN_AI_TOKEN")
    private val anthropicToken = System.getenv("ANTHROPIC_TOKEN")

    @Test
    fun `test OpenAI model with multiple system messages`() = runBlocking {
        // Create real OpenAI client with token from environment
        val openAIClient = OpenAILLMClient(openAIToken)

        // Create MultiLLMPromptExecutor with OpenAI client
        val executor = MultiLLMPromptExecutor(
            LLMProvider.OpenAI to openAIClient
        )

        // Create a prompt with multiple system messages
        val prompt = prompt("multiple-system-messages-test") {
            system("You are a helpful assistant.")
            user("Hi")
            system("You can handle multiple system messages.")
            user("Respond with a short message.")
        }

        // Execute the prompt with OpenAI model
        val model = OpenAIModels.CostOptimized.GPT4oMini
        val response = executor.execute(prompt, model)

        // Verify the response is not empty
        assertTrue(response.isNotEmpty())
        println("OpenAI Response: $response")
    }

    @Test
    fun `test Anthropic model with multiple system messages`() = runBlocking {
        // Create real Anthropic client with token from environment
        val anthropicClient = AnthropicLLMClient(anthropicToken)

        // Create MultiLLMPromptExecutor with Anthropic client
        val executor = MultiLLMPromptExecutor(
            LLMProvider.Anthropic to anthropicClient
        )

        // Create a prompt with multiple system messages
        val prompt = prompt("multiple-system-messages-test") {
            system("You are a helpful assistant.")
            user("Hi")
            system("You can handle multiple system messages.")
            user("Respond with a short message.")
        }

        // Execute the prompt with Anthropic model
        val model = AnthropicModels.Haiku_3_5
        val response = executor.execute(prompt, model)

        // Verify the response is not empty
        assertTrue(response.isNotEmpty())
        println("Anthropic Response: $response")
    }
}
