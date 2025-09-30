package ai.koog.prompt.executor.clients.dashscope

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.params.LLMParams
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DashscopeLLMClientTest {

    private val apiKey = System.getenv("DASHSCOPE_API_KEY")

    private fun createClient(): DashscopeLLMClient {
        return DashscopeLLMClient(
            apiKey = apiKey ?: throw IllegalStateException("DASHSCOPE_API_KEY environment variable is not set"),
            settings = DashscopeClientSettings(),
        )
    }

    @Test
    fun `should execute simple prompt successfully`() {
        if (apiKey.isNullOrEmpty()) {
            println("Skipping integration test - DASHSCOPE_API_KEY not set")
            return
        }

        runBlocking {
            val client = createClient()
            val prompt = Prompt(
                id = "test-prompt",
                messages = listOf(
                    Message.User(
                        content = "Hello! Please respond with 'Hi there!'",
                        metaInfo = RequestMetaInfo.create(Clock.System)
                    )
                ),
                params = LLMParams(temperature = 0.1, maxTokens = 50)
            )

            val responses = client.execute(prompt, DashscopeModels.QWEN_FLASH)

            assertNotNull(responses)
            assertTrue(responses.isNotEmpty())
            val response = responses.first()
            assertTrue(response is Message.Assistant)
            assertNotNull(response.content)
            assertTrue(response.content.isNotEmpty())
            assertNotNull(response.metaInfo)
            assertTrue((response.metaInfo.inputTokensCount ?: 0) > 0)
            assertTrue((response.metaInfo.outputTokensCount ?: 0) > 0)
        }
    }

    @Test
    fun `should execute streaming prompt successfully`() {
        if (apiKey.isNullOrEmpty()) {
            println("Skipping integration test - DASHSCOPE_API_KEY not set")
            return
        }

        runBlocking {
            val client = createClient()
            val prompt = Prompt(
                id = "test-prompt",
                messages = listOf(
                    Message.User(
                        content = "Count from 1 to 5",
                        metaInfo = RequestMetaInfo.create(Clock.System)
                    )
                ),
                params = LLMParams(temperature = 0.1, maxTokens = 100)
            )

            val chunks = client.executeStreaming(prompt, DashscopeModels.QWEN_FLASH).toList()

            assertTrue(chunks.isNotEmpty())
            val fullResponse = chunks.joinToString("")
            assertTrue(fullResponse.isNotEmpty())
        }
    }

    @Test
    fun `should handle conversation with system message`() {
        if (apiKey.isNullOrEmpty()) {
            println("Skipping integration test - DASHSCOPE_API_KEY not set")
            return
        }

        runBlocking {
            val client = createClient()
            val prompt = Prompt(
                id = "test-prompt",
                messages = listOf(
                    Message.System(
                        content = "You are a helpful assistant that always responds in exactly 3 words.",
                        metaInfo = RequestMetaInfo.create(Clock.System)
                    ),
                    Message.User(
                        content = "What is the capital of France?",
                        metaInfo = RequestMetaInfo.create(Clock.System)
                    )
                ),
                params = LLMParams(temperature = 0.1, maxTokens = 10)
            )

            val responses = client.execute(prompt, DashscopeModels.QWEN_FLASH)

            assertNotNull(responses)
            assertTrue(responses.isNotEmpty())
            val response = responses.first()
            assertTrue(response is Message.Assistant)
            assertNotNull(response.content)
            assertTrue(response.content.isNotEmpty())
        }
    }

    @Test
    fun `should handle tool calls`() {
        if (apiKey.isNullOrEmpty()) {
            println("Skipping integration test - DASHSCOPE_API_KEY not set")
            return
        }

        runBlocking {
            val client = createClient()
            val tool = ToolDescriptor(
                name = "get_weather",
                description = "Get current weather for a location",
                requiredParameters = listOf(
                    ToolParameterDescriptor(
                        name = "location",
                        description = "The location to get weather for",
                        type = ToolParameterType.String
                    )
                ),
                optionalParameters = listOf(
                    ToolParameterDescriptor(
                        name = "unit",
                        description = "Temperature unit (celsius or fahrenheit)",
                        type = ToolParameterType.String
                    )
                )
            )

            val prompt = Prompt(
                id = "test-prompt",
                messages = listOf(
                    Message.User(
                        content = "What's the weather like in Beijing?",
                        metaInfo = RequestMetaInfo.create(Clock.System)
                    )
                ),
                params = LLMParams(temperature = 0.1, maxTokens = 200)
            )

            val responses = client.execute(prompt, DashscopeModels.QWEN_FLASH, listOf(tool))

            assertNotNull(responses)
            assertTrue(responses.isNotEmpty())

            // The model might respond with text or a tool call
            val response = responses.first()
            assertTrue(response is Message.Assistant || response is Message.Tool.Call)
        }
    }

    @Test
    fun `should throw exception for moderation`() {
        val client = DashscopeLLMClient("fake-key")
        val prompt = Prompt(
            id = "test-prompt",
            messages = listOf(
                Message.User(
                    content = "Test message",
                    metaInfo = RequestMetaInfo.create(Clock.System)
                )
            ),
            params = LLMParams()
        )

        runBlocking {
            assertFailsWith<UnsupportedOperationException> {
                client.moderate(prompt, DashscopeModels.QWEN_FLASH)
            }
        }
    }
}
