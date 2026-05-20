package ai.koog.prompt.executor.clients.openrouter

import ai.koog.http.client.ktor.KtorKoogHttpClient
import ai.koog.prompt.Prompt
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.message.MessagePart
import ai.koog.utils.time.KoogClock
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class OpenRouterLLMClientTest {

    object FixedClock : KoogClock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(0)
    }

    private val apiKey = "test-api-key"

    //language=json
    private val toolCallWithReasoningBody = """
        {
          "id": "chatcmpl-tool",
          "created": 1716920005,
          "model": "openai/gpt-4o-mini",
          "object": "chat.completion",
          "choices": [
            {
              "index": 0,
              "message": {
                "role": "assistant",
                "content": "",
                "reasoning_content": "I should call the weather tool first.",
                "tool_calls": [
                  {
                    "id": "call_weather",
                    "type": "function",
                    "function": {
                      "name": "weather",
                      "arguments": "{\"city\":\"Boston\"}"
                    }
                  }
                ]
              },
              "finish_reason": "tool_calls"
            }
          ],
          "usage": {"total_tokens": 10, "prompt_tokens": 5, "completion_tokens": 5}
        }
    """.trimIndent()

    //language=json
    private val modelsBody = """
        {
          "data": [
            {
              "id": "google/gemini-3.5-flash",
              "canonical_slug": "google/gemini-3.5-flash-20260519",
              "hugging_face_id": null,
              "name": "Google: Gemini 3.5 Flash",
              "created": 1779193800,
              "description": "Gemini 3.5 Flash",
              "context_length": 1048576,
              "architecture": {
                "modality": "text+image+file+audio+video->text",
                "input_modalities": ["text", "image", "video", "file", "audio"],
                "output_modalities": ["text"],
                "tokenizer": "Gemini",
                "instruct_type": null
              },
              "pricing": {
                "prompt": "0.0000015",
                "completion": "0.000009",
                "image": "0.0000015",
                "audio": "0.000003",
                "internal_reasoning": "0.000009",
                "input_cache_read": "0.00000015",
                "input_cache_write": "0.00000008333333333333334"
              },
              "top_provider": {
                "context_length": 1048576,
                "max_completion_tokens": 65536,
                "is_moderated": false
              },
              "per_request_limits": null,
              "supported_parameters": [
                "include_reasoning",
                "max_tokens",
                "reasoning",
                "response_format",
                "structured_outputs",
                "temperature",
                "tool_choice",
                "tools"
              ],
              "default_parameters": {
                "temperature": null,
                "top_p": null,
                "top_k": null
              },
              "knowledge_cutoff": "2025-01-01"
            },
            {
              "id": "openrouter/context-fallback",
              "canonical_slug": "openrouter/context-fallback",
              "hugging_face_id": null,
              "name": "Context fallback",
              "created": 1779193800,
              "description": "Model with limits from nested fields",
              "context_length": null,
              "architecture": {
                "input_modalities": ["text"],
                "output_modalities": ["text"],
                "tokenizer": "Unknown"
              },
              "pricing": {
                "prompt": "0.000001",
                "completion": "0.000002"
              },
              "top_provider": {
                "context_length": 8192,
                "max_completion_tokens": null,
                "is_moderated": false
              },
              "per_request_limits": {
                "prompt_tokens": 4096,
                "completion_tokens": 2048
              },
              "supported_parameters": null
            }
          ]
        }
    """.trimIndent()

    @Test
    fun testExecuteToolCallResponsePreservesReasoningMessage() = runTest {
        val engine = MockEngine.Companion { _ ->
            respond(
                content = toolCallWithReasoningBody,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        val http = HttpClient(engine) {}
        val client = OpenRouterLLMClient(httpClientFactory = KtorKoogHttpClient.Factory(http), apiKey = apiKey, clock = FixedClock)

        val prompt = Prompt.build(id = "p-tool-response", clock = FixedClock) {
            user("What is the weather in Boston?")
        }

        val responses = client.execute(prompt, OpenRouterModels.GPT4oMini)

        assertEquals(2, responses.parts.size, "Response should contain reasoning and tool call")
        val reasoningPart = assertIs<MessagePart.Reasoning>(responses.parts[0])
        assertEquals(1, reasoningPart.content.size, "Reasoning should contain one message")
        assertEquals("I should call the weather tool first.", reasoningPart.content.first())

        val toolCall = assertIs<MessagePart.Tool.Call>(responses.parts[1])
        assertEquals("call_weather", toolCall.id)
        assertEquals("weather", toolCall.tool)
        assertEquals(buildJsonObject { put("city", JsonPrimitive("Boston")) }, toolCall.argsJson)
    }

    @Test
    fun testModelsUsesOpenRouterResponseMetadata() = runTest {
        val engine = MockEngine.Companion { _ ->
            respond(
                content = modelsBody,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        val http = HttpClient(engine) {}
        val client = OpenRouterLLMClient(httpClientFactory = KtorKoogHttpClient.Factory(http), apiKey = apiKey)

        val models = client.models()

        val gemini = models.single { it.id == "google/gemini-3.5-flash" }
        assertEquals(1_048_576, gemini.contextLength)
        assertEquals(65_536, gemini.maxOutputTokens)

        val capabilities = assertNotNull(gemini.capabilities)
        listOf(
            LLMCapability.Completion,
            LLMCapability.Temperature,
            LLMCapability.Tools,
            LLMCapability.ToolChoice,
            LLMCapability.Schema.JSON.Basic,
            LLMCapability.Schema.JSON.Standard,
            LLMCapability.Thinking,
            LLMCapability.PromptCaching,
            LLMCapability.Vision.Image,
            LLMCapability.Vision.Video,
            LLMCapability.Audio,
            LLMCapability.Document,
        ).forEach { capability ->
            assertTrue(capability in capabilities, "Expected ${gemini.id} to support ${capability.id}")
        }

        val fallback = models.single { it.id == "openrouter/context-fallback" }
        assertEquals(8_192, fallback.contextLength)
        assertEquals(2_048, fallback.maxOutputTokens)
        assertEquals(listOf(LLMCapability.Completion), fallback.capabilities)
    }
}
