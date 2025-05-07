package ai.jetbrains.code.prompt.executor.llms.all

import ai.jetbrains.code.prompt.dsl.Prompt
import ai.jetbrains.code.prompt.executor.clients.anthropic.AnthropicModels
import ai.jetbrains.code.prompt.executor.clients.anthropic.AnthropicDirectLLMClient
import ai.jetbrains.code.prompt.executor.clients.openai.OpenAIModels
import ai.jetbrains.code.prompt.executor.clients.openai.OpenAIDirectLLMClient
import ai.jetbrains.code.prompt.message.Message
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MultipleLLMPromptExecutorIntegrationTest {

    // API keys for testing
    private val openAIApiKey: String get() = readTestOpenAIKeyFromEnv()
    private val anthropicApiKey: String get() = readTestAnthropicKeyFromEnv()
    

    @Test
    fun testExecuteWithOpenAI() = runTest {
        // TODO: pass the `OPEN_AI_API_TEST_KEY` and `ANTHROPIC_API_TEST_KEY`
        return@runTest

        val openAIClient = OpenAIDirectLLMClient(openAIApiKey,)
        val anthropicClient = AnthropicDirectLLMClient(anthropicApiKey)
        
        val executor = DefaultMultiLLMPromptExecutor(openAIClient, anthropicClient)
        
        val prompt = Prompt.build(OpenAIModels.GPT4o, "test-prompt") {
            system("You are a helpful assistant.")
            user("What is the capital of France?")
        }
        
        val response = executor.execute(prompt, emptyList())
        
        assertNotNull(response, "Response should not be null")
        assertTrue(response.isNotEmpty(), "Response should not be empty")
        assertTrue(response.first() is Message.Assistant, "Response should be an Assistant message")
        assertTrue((response.first() as Message.Assistant).content.contains("Paris", ignoreCase = true), 
            "Response should contain 'Paris'")
    }
    
    @Test
    fun testExecuteWithAnthropic() = runTest {
        // TODO: pass the `OPEN_AI_API_TEST_KEY` and `ANTHROPIC_API_TEST_KEY`
        return@runTest

        val openAIClient = OpenAIDirectLLMClient(openAIApiKey)
        val anthropicClient = AnthropicDirectLLMClient(anthropicApiKey)
        
        val executor = DefaultMultiLLMPromptExecutor(openAIClient, anthropicClient)
        
        val prompt = Prompt.build(AnthropicModels.Sonnet_3_7, "test-prompt") {
            system("You are a helpful assistant.")
            user("What is the capital of France?")
        }
        
        val response = executor.execute(prompt, emptyList())
        
        assertNotNull(response, "Response should not be null")
        assertTrue(response.isNotEmpty(), "Response should not be empty")
        assertTrue(response.first() is Message.Assistant, "Response should be an Assistant message")
        assertTrue((response.first() as Message.Assistant).content.contains("Paris", ignoreCase = true), 
            "Response should contain 'Paris'")
    }
    
    @Test
    fun testExecuteStreamingWithOpenAI() = runTest {
        // TODO: pass the `OPEN_AI_API_TEST_KEY` and `ANTHROPIC_API_TEST_KEY`
        return@runTest

        val openAIClient = OpenAIDirectLLMClient(openAIApiKey)
        val anthropicClient = AnthropicDirectLLMClient(anthropicApiKey)
        
        val executor = DefaultMultiLLMPromptExecutor(openAIClient, anthropicClient)
        
        val prompt = Prompt.build(OpenAIModels.GPT4o, "test-streaming") {
            system("You are a helpful assistant.")
            user("Count from 1 to 5.")
        }
        
        val responseChunks = executor.executeStreaming(prompt).toList()
        
        assertNotNull(responseChunks, "Response chunks should not be null")
        assertTrue(responseChunks.isNotEmpty(), "Response chunks should not be empty")
        
        // Combine all chunks to check the full response
        val fullResponse = responseChunks.joinToString("")
        assertTrue(fullResponse.contains("1") && 
                   fullResponse.contains("2") && 
                   fullResponse.contains("3") && 
                   fullResponse.contains("4") && 
                   fullResponse.contains("5"), 
            "Full response should contain numbers 1 through 5")
    }
    
    @Test
    fun testExecuteStreamingWithAnthropic() = runTest {
        // TODO: pass the `OPEN_AI_API_TEST_KEY` and `ANTHROPIC_API_TEST_KEY`
        return@runTest

        val openAIClient = OpenAIDirectLLMClient(openAIApiKey)
        val anthropicClient = AnthropicDirectLLMClient(anthropicApiKey)
        
        val executor = DefaultMultiLLMPromptExecutor(openAIClient, anthropicClient)
        
        val prompt = Prompt.build(AnthropicModels.Sonnet_3_7, "test-streaming") {
            system("You are a helpful assistant.")
            user("Count from 1 to 5.")
        }
        
        val responseChunks = executor.executeStreaming(prompt).toList()
        
        assertNotNull(responseChunks, "Response chunks should not be null")
        assertTrue(responseChunks.isNotEmpty(), "Response chunks should not be empty")
        
        // Combine all chunks to check the full response
        val fullResponse = responseChunks.joinToString("")
        assertTrue(fullResponse.contains("1") && 
                   fullResponse.contains("2") && 
                   fullResponse.contains("3") && 
                   fullResponse.contains("4") && 
                   fullResponse.contains("5"), 
            "Full response should contain numbers 1 through 5")
    }
    
    @Test
    fun testCodeGenerationWithOpenAI() = runTest {
        // TODO: pass the `OPEN_AI_API_TEST_KEY` and `ANTHROPIC_API_TEST_KEY`
        return@runTest

        val openAIClient = OpenAIDirectLLMClient(openAIApiKey)
        val anthropicClient = AnthropicDirectLLMClient(anthropicApiKey)
        
        val executor = DefaultMultiLLMPromptExecutor(openAIClient, anthropicClient)
        
        val prompt = Prompt.build(OpenAIModels.GPT4o, "test-code") {
            system("You are a helpful coding assistant.")
            user("Write a simple Kotlin function to calculate the factorial of a number.")
        }
        
        val response = executor.execute(prompt, emptyList())
        
        assertNotNull(response, "Response should not be null")
        assertTrue(response.isNotEmpty(), "Response should not be empty")
        assertTrue(response.first() is Message.Assistant, "Response should be an Assistant message")
        
        val content = (response.first() as Message.Assistant).content
        assertTrue(content.contains("fun factorial"), "Response should contain a factorial function")
        assertTrue(content.contains("return"), "Response should contain a return statement")
    }
    
    @Test
    fun testCodeGenerationWithAnthropic() = runTest {
        // TODO: pass the `OPEN_AI_API_TEST_KEY` and `ANTHROPIC_API_TEST_KEY`
        return@runTest

        val openAIClient = OpenAIDirectLLMClient(openAIApiKey)
        val anthropicClient = AnthropicDirectLLMClient(anthropicApiKey)
        
        val executor = DefaultMultiLLMPromptExecutor(openAIClient, anthropicClient)
        
        val prompt = Prompt.build(AnthropicModels.Sonnet_3_7, "test-code") {
            system("You are a helpful coding assistant.")
            user("Write a simple Kotlin function to calculate the factorial of a number.")
        }
        
        val response = executor.execute(prompt, emptyList())
        
        assertNotNull(response, "Response should not be null")
        assertTrue(response.isNotEmpty(), "Response should not be empty")
        assertTrue(response.first() is Message.Assistant, "Response should be an Assistant message")
        
        val content = (response.first() as Message.Assistant).content
        assertTrue(content.contains("fun factorial"), "Response should contain a factorial function")
        assertTrue(content.contains("return"), "Response should contain a return statement")
    }
}