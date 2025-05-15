<<<<<<<< HEAD:prompt/prompt-executor/prompt-executor-llms/src/jvmTest/kotlin/ai/koog/prompt/executor/llms/MultipleSystemMessagesPromptIntegrationTest.kt
package ai.koog.prompt.executor.llms

import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.clients.google.GoogleLLMClient
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.llm.LLMProvider
========
package ai.jetbrains.code.prompt.integration.tests

import ai.jetbrains.code.prompt.dsl.prompt
import ai.jetbrains.code.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.jetbrains.code.prompt.executor.clients.anthropic.AnthropicModels
import ai.jetbrains.code.prompt.executor.clients.google.GoogleLLMClient
import ai.jetbrains.code.prompt.executor.clients.google.GoogleModels
import ai.jetbrains.code.prompt.executor.clients.openai.OpenAILLMClient
import ai.jetbrains.code.prompt.executor.clients.openai.OpenAIModels
import ai.jetbrains.code.prompt.executor.llms.MultiLLMPromptExecutor
import ai.jetbrains.code.prompt.integration.tests.TestUtils.readTestAnthropicKeyFromEnv
import ai.jetbrains.code.prompt.integration.tests.TestUtils.readTestGeminiKeyFromEnv
import ai.jetbrains.code.prompt.integration.tests.TestUtils.readTestOpenAIKeyFromEnv
import ai.jetbrains.code.prompt.llm.LLMProvider
>>>>>>>> 807c9c1 (JBAI-13946 Extend integration tests to cover new models and move to separate module):integration-tests/src/jvmTest/kotlin/ai/jetbrains/code/prompt/integration/tests/MultipleSystemMessagesPromptIntegrationTest.kt
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Disabled
import kotlin.test.Test
import kotlin.test.assertTrue

@Disabled("TODO: pass the `OPEN_AI_API_TEST_KEY`, `ANTHROPIC_API_TEST_KEY`, `GEMINI_API_TEST_KEY`")
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
        println("Gemini Response: $responseAnthropic")
    }
}