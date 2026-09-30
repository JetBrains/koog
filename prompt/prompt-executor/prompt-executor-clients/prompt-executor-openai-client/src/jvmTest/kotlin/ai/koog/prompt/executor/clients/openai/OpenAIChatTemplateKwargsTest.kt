package ai.koog.prompt.executor.clients.openai

import ai.koog.http.client.ktor.KtorKoogHttpClient
import ai.koog.prompt.Prompt
import ai.koog.prompt.executor.clients.openai.models.OpenAIChatCompletionRequest
import ai.koog.prompt.executor.clients.openai.models.OpenAIChatCompletionRequestSerializer
import ai.koog.test.utils.runWithBothJsonConfigurations
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class OpenAIChatTemplateKwargsTest {
    private val kwargs = buildJsonObject {
        put("enable_thinking", JsonPrimitive(false))
        put(
            "provider_options",
            buildJsonArray {
                add(JsonPrimitive(7))
                add(JsonNull)
                add(buildJsonObject { put("nested", JsonPrimitive("value")) })
            },
        )
    }

    @Test
    fun testCopyPreservesOtherParametersAndOriginalProperties() {
        val original = OpenAIChatParams(
            temperature = 0.7,
            additionalProperties = mapOf("other" to JsonPrimitive(true)),
        )
        val configured = original.withChatTemplateKwargs(kwargs)

        assertNull(original.chatTemplateKwargs)
        assertEquals(0.7, configured.temperature)
        assertEquals(JsonPrimitive(true), configured.additionalProperties?.get("other"))
        assertEquals(kwargs, configured.copy().chatTemplateKwargs)
        assertEquals(original, configured.withChatTemplateKwargs(null))
        assertNull(OpenAIChatParams().withChatTemplateKwargs(kwargs).withChatTemplateKwargs(null).additionalProperties)
        assertEquals(buildJsonObject {}, configured.withChatTemplateKwargs(buildJsonObject {}).chatTemplateKwargs)
    }

    @Test
    fun testExistingAdditionalPropertyIsRecognisedAndReplaced() {
        val original = OpenAIChatParams(additionalProperties = mapOf("chat_template_kwargs" to kwargs))
        assertEquals(kwargs, original.chatTemplateKwargs)
        assertNull(OpenAIChatParams(additionalProperties = mapOf("chat_template_kwargs" to JsonPrimitive(true))).chatTemplateKwargs)
        val replacement = buildJsonObject { put("enable_thinking", JsonPrimitive(true)) }
        assertEquals(replacement, original.withChatTemplateKwargs(replacement).chatTemplateKwargs)
    }

    @Test
    fun testRequestRoundTripPreservesArbitraryJson() =
        runWithBothJsonConfigurations("chat template arguments") { json ->
            val request = OpenAIChatCompletionRequest(
                model = "provider/model",
                messages = emptyList(),
                additionalProperties = OpenAIChatParams().withChatTemplateKwargs(kwargs).additionalProperties,
            )
            val encoded = json.encodeToString(OpenAIChatCompletionRequestSerializer, request)
            val body = json.parseToJsonElement(encoded).jsonObject
            assertEquals(kwargs, body["chat_template_kwargs"])
            assertFalse("chatTemplateKwargs" in body)
            val decoded = json.decodeFromString(OpenAIChatCompletionRequestSerializer, encoded)
            assertEquals(kwargs, decoded.additionalProperties?.get("chat_template_kwargs"))
        }

    @Test
    fun testClientSendsArgumentsAndOmitsThemAfterRemoval() = runTest {
        val bodies = mutableListOf<String>()
        val engine = MockEngine { request ->
            bodies.add((request.body as TextContent).text)
            respond(
                content = """{"id":"test","object":"chat.completion","created":0,"model":"gpt-4o","choices":[{"index":0,"message":{"role":"assistant","content":"Hello"},"finish_reason":"stop"}]}""",
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val client = OpenAILLMClient(
            apiKey = "test-key",
            httpClientFactory = KtorKoogHttpClient.Factory(HttpClient(engine)),
        )
        try {
            val configured = OpenAIChatParams().withChatTemplateKwargs(kwargs)
            for (params in listOf(configured, configured.withChatTemplateKwargs(null), OpenAIChatParams())) {
                client.execute(Prompt.build("kwargs", params = params) { user("Hello") }, OpenAIModels.Chat.GPT4o)
            }
            assertEquals(kwargs, Json.parseToJsonElement(bodies[0]).jsonObject["chat_template_kwargs"])
            for (body in bodies.drop(1)) {
                assertFalse("chat_template_kwargs" in Json.parseToJsonElement(body).jsonObject)
            }
        } finally {
            client.close()
        }
    }
}
