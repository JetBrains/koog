package ai.jetbrains.code.prompt.executor.llms.all

import ai.grazie.code.agents.core.tools.ToolDescriptor
import ai.jetbrains.code.prompt.dsl.Prompt
import ai.jetbrains.code.prompt.executor.clients.anthropic.AnthropicDirectLLMClient
import ai.jetbrains.code.prompt.executor.clients.anthropic.AnthropicModels
import ai.jetbrains.code.prompt.executor.clients.openai.OpenAIDirectLLMClient
import ai.jetbrains.code.prompt.executor.clients.openai.OpenAIModels
import ai.jetbrains.code.prompt.message.Message
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class MultipleLLMPromptExecutorTest {

    // Mock client for OpenAI
    private class MockOpenAILLMClient : OpenAIDirectLLMClient("fake-key") {
        override suspend fun execute(prompt: Prompt, tools: List<ToolDescriptor>): List<Message.Response> {
            return listOf(Message.Assistant("OpenAI response"))
        }

        override suspend fun executeStreaming(prompt: Prompt): Flow<String> {
            return flowOf("OpenAI", " streaming", " response")
        }
    }

    // Mock client for Anthropic
    private class MockAnthropicLLMClient : AnthropicDirectLLMClient("fake-key") {
        override suspend fun execute(prompt: Prompt, tools: List<ToolDescriptor>): List<Message.Response> {
            return listOf(Message.Assistant("Anthropic response"))
        }

        override suspend fun executeStreaming(prompt: Prompt): Flow<String> {
            return flowOf("Anthropic", " streaming", " response")
        }
    }

    @Test
    fun testExecuteWithOpenAI() = runTest {
        val executor = DefaultMultiLLMPromptExecutor(
            MockOpenAILLMClient(),
            MockAnthropicLLMClient()
        )
        
        val prompt = Prompt.build(OpenAIModels.GPT4o, "test-prompt") {
            system("You are a helpful assistant.")
            user("What is the capital of France?")
        }
        
        val response = executor.execute(prompt)
        
        assertEquals("OpenAI response", response, "Response should be from OpenAI client")
    }
    
    @Test
    fun testExecuteWithAnthropic() = runTest {
        val executor = DefaultMultiLLMPromptExecutor(
            MockOpenAILLMClient(),
            MockAnthropicLLMClient()
        )
        
        val prompt = Prompt.build(AnthropicModels.Sonnet_3_7, "test-prompt") {
            system("You are a helpful assistant.")
            user("What is the capital of France?")
        }
        
        val response = executor.execute(prompt)
        
        assertEquals("Anthropic response", response, "Response should be from Anthropic client")
    }
    
    @Test
    fun testExecuteStreamingWithOpenAI() = runTest {
        val executor = DefaultMultiLLMPromptExecutor(
            MockOpenAILLMClient(),
            MockAnthropicLLMClient()
        )
        
        val prompt = Prompt.build(OpenAIModels.GPT4o, "test-prompt") {
            system("You are a helpful assistant.")
            user("What is the capital of France?")
        }
        
        val responseChunks = executor.executeStreaming(prompt).toList()
        
        assertEquals(3, responseChunks.size, "Response should have three chunks")
        assertEquals("OpenAI streaming response", responseChunks.joinToString(""), "Response should be from OpenAI client")
    }
    
    @Test
    fun testExecuteStreamingWithAnthropic() = runTest {
        val executor = DefaultMultiLLMPromptExecutor(
            MockOpenAILLMClient(),
            MockAnthropicLLMClient()
        )
        
        val prompt = Prompt.build(AnthropicModels.Sonnet_3_7, "test-prompt") {
            system("You are a helpful assistant.")
            user("What is the capital of France?")
        }
        
        val responseChunks = executor.executeStreaming(prompt).toList()
        
        assertEquals(3, responseChunks.size, "Response should have three chunks")
        assertEquals("Anthropic streaming response", responseChunks.joinToString(""), "Response should be from Anthropic client")
    }
}