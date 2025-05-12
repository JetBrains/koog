package ai.jetbrains.code.prompt.executor.ollama

import ai.jetbrains.code.prompt.dsl.Prompt
import ai.jetbrains.code.prompt.executor.ollama.client.OllamaClient
import ai.jetbrains.code.prompt.llm.OllamaModels
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OllamaClientTest {
    private val testModel = OllamaModels.Meta.LLAMA_3_2

    @Test
    fun testExecuteSimplePrompt() = runTest {
        val client = OllamaClient()

        val executor = OllamaPromptExecutor(client)

        val prompt = Prompt.build(testModel, "test-prompt") {
            system("You are a helpful assistant.")
            user("What is the capital of France?")
        }

        val response = executor.execute(prompt)

        assertNotNull(response, "Response should not be null")
        assertTrue(response.isNotEmpty(), "Response should not be empty")
        assertTrue(response.contains("Paris"), "Response should contain 'Paris'")
    }
}