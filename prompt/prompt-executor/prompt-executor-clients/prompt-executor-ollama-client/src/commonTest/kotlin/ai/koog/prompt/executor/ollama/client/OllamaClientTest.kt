package ai.koog.prompt.executor.ollama.client

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.http.client.ktor.KtorKoogHttpClient
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.LLMClientException
import ai.koog.prompt.executor.ollama.client.dto.OllamaChatMessageDTO
import ai.koog.prompt.executor.ollama.client.dto.OllamaChatResponseDTO
import ai.koog.prompt.executor.ollama.client.dto.OllamaToolCallDTO
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.streaming.StreamFrame
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OllamaClientTest {

    @Test
    fun testExecuteWithContentAndToolCalls() = runTest {
        val responseContent = "I will check the weather for you."
        val toolName = "get_weather"
        val toolArgs = JsonObject(mapOf("city" to JsonPrimitive("London")))

        val mockServer = MockOllamaChatServer { request ->
            OllamaChatResponseDTO(
                model = request.model,
                message = OllamaChatMessageDTO(
                    role = "assistant",
                    content = responseContent,
                    toolCalls = listOf(
                        OllamaToolCallDTO(
                            function = OllamaToolCallDTO.Call(
                                name = toolName,
                                arguments = toolArgs
                            )
                        )
                    )
                ),
                done = true
            )
        }

        val ollamaClient = OllamaClient(
            httpClientFactory = KtorKoogHttpClient.Factory(HttpClient(mockServer.mockEngine))
        )

        val responses = ollamaClient.execute(
            prompt = prompt("test") { },
            model = OllamaModels.Meta.LLAMA_3_2
        )

        assertEquals(2, responses.parts.size)

        val textMessage = assertIs<MessagePart.Text>(responses.parts[0])
        assertEquals(responseContent, textMessage.text)

        val toolCallPart = assertIs<MessagePart.Tool.Call>(responses.parts[1])
        assertEquals(toolName, toolCallPart.tool)
        assertTrue(toolCallPart.args.contains("London"))
    }

    @Test
    fun testExecuteStreamingSendsTools() = runTest {
        val tool = ToolDescriptor(
            name = "get_weather",
            description = "Get the weather for a city",
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "city",
                    description = "The city",
                    type = ToolParameterType.String
                )
            )
        )
        val toolArgs = JsonObject(mapOf("city" to JsonPrimitive("London")))
        val mockServer = MockStreamingOllamaChatServer {
            OllamaChatResponseDTO(
                model = "test-model",
                message = OllamaChatMessageDTO(
                    role = "assistant",
                    content = "",
                    toolCalls = listOf(
                        OllamaToolCallDTO(function = OllamaToolCallDTO.Call(name = tool.name, arguments = toolArgs))
                    )
                ),
                done = true
            )
        }

        val ollamaClient = OllamaClient(
            httpClientFactory = KtorKoogHttpClient.Factory(HttpClient(mockServer.mockEngine))
        )

        val frames = ollamaClient.executeStreaming(
            prompt = prompt("test") { },
            model = OllamaModels.Meta.LLAMA_3_2,
            tools = listOf(tool)
        ).toList()

        val sentTools = assertNotNull(mockServer.requestHistory.single().tools)
        assertEquals(listOf(tool.name), sentTools.map { it.function.name })
        assertEquals(tool.description, sentTools.single().function.description)

        val toolCall = assertIs<StreamFrame.ToolCallComplete>(
            frames.single { it is StreamFrame.ToolCallComplete }
        )
        assertEquals(tool.name, toolCall.name)
    }

    @Test
    fun testExecuteStreamingWithoutToolsSendsNoTools() = runTest {
        val mockServer = MockStreamingOllamaChatServer {
            OllamaChatResponseDTO(
                model = "test-model",
                message = OllamaChatMessageDTO(role = "assistant", content = "Hello"),
                done = true
            )
        }

        val ollamaClient = OllamaClient(
            httpClientFactory = KtorKoogHttpClient.Factory(HttpClient(mockServer.mockEngine))
        )

        ollamaClient.executeStreaming(
            prompt = prompt("test") { },
            model = OllamaModels.Meta.LLAMA_3_2
        ).toList()

        assertNull(mockServer.requestHistory.single().tools)
    }

    @Test
    fun testGetModelOrNullReportsPullErrorResponse() = runTest {
        val errorMessage = "pull model manifest: file does not exist"
        val mockEngine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/api/tags" -> respond(
                    content = """{"models":[]}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType to listOf("application/json")),
                )

                "/api/pull" -> respond(
                    content = """{"error":"$errorMessage"}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType to listOf("application/json")),
                )

                else -> error("Unexpected request to ${request.url.encodedPath}")
            }
        }

        val ollamaClient = OllamaClient(
            httpClientFactory = KtorKoogHttpClient.Factory(HttpClient(mockEngine))
        )

        val exception = assertFailsWith<LLMClientException> {
            ollamaClient.getModelOrNull("missing-model", pullIfMissing = true)
        }

        assertTrue(exception.message.orEmpty().contains(errorMessage))
    }
}
