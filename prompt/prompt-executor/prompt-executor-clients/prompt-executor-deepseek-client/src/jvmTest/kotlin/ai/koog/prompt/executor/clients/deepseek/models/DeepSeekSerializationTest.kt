package ai.koog.prompt.executor.clients.deepseek.models

import ai.koog.prompt.executor.clients.openai.base.models.Content
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIMessage
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIResponseFormat
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIStreamOptions
import ai.koog.prompt.executor.clients.openai.base.models.OpenAITool
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIToolChoice
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIToolFunction
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test

class DeepSeekSerializationTest {

    private val json = Json {
        ignoreUnknownKeys = false
        explicitNulls = false
    }

    @Test
    fun `test basic serialization without optional fields`() {
        val request = DeepSeekChatCompletionRequest(
            model = "deepseek-chat",
            messages = listOf(OpenAIMessage.User(content = Content.Text("Hello"))),
            temperature = 0.7,
            maxTokens = 1000,
            stream = false
        )

        val jsonString = json.encodeToString(DeepSeekChatCompletionRequest.serializer(), request)

        jsonString shouldEqualJson
            // language=json
            """
            {
                "model": "deepseek-chat",
                "messages": [
                    {
                        "role": "user",
                        "content": "Hello"
                    }
                ],
                "temperature": 0.7,
                "maxTokens": 1000,
                "stream": false
            }
            """.trimIndent()
    }

    @Test
    fun `test serialization with DeepSeek-specific fields`() {
        val request = DeepSeekChatCompletionRequest(
            model = "deepseek-chat",
            messages = listOf(OpenAIMessage.User(content = Content.Text("Hello"))),
            temperature = 0.8,
            frequencyPenalty = 0.5,
            presencePenalty = 0.3,
            logprobs = true,
            topLogprobs = 5,
            topP = 0.9,
            stop = listOf("END", "STOP")
        )

        val jsonString = json.encodeToString(DeepSeekChatCompletionRequest.serializer(), request)

        jsonString shouldEqualJson
            // language=json
            """
            {
                "model": "deepseek-chat",
                "messages": [
                    {
                        "role": "user",
                        "content": "Hello"
                    }
                ],
                "temperature": 0.8,
                "frequencyPenalty": 0.5,
                "presencePenalty": 0.3,
                "logprobs": true,
                "topLogprobs": 5,
                "topP": 0.9,
                "stop": ["END", "STOP"]
            }
            """.trimIndent()
    }

    @Test
    fun `test deserialization serialization with DeepSeek-specific fields`() {
        val jsonInput =
            // language=json
            """
            {
                "model": "deepseek-reasoner",
                "messages": [
                    {
                        "role": "user",
                        "content": "Test message"
                    }
                ],
                "temperature": 0.5,
                "frequencyPenalty": 0.2,
                "presencePenalty": 0.1,
                "logprobs": true,
                "topLogprobs": 3,
                "topP": 0.95,
                "stop": ["STOP", "END"],
                "stream": true,
                "maxTokens": 2048
            }
            """.trimIndent()

        val request = json.decodeFromString(DeepSeekChatCompletionRequest.serializer(), jsonInput)
        val serialized = json.encodeToString(DeepSeekChatCompletionRequest.serializer(), request)
        serialized shouldEqualJson jsonInput
    }

    @Test
    fun `test serialization with additionalProperties`() {
        val request = DeepSeekChatCompletionRequest(
            model = "deepseek-chat",
            messages = listOf(OpenAIMessage.User(content = Content.Text("Hello"))),
            temperature = 0.7,
            additionalProperties = mapOf(
                "customString" to JsonPrimitive("value"),
                "customNumber" to JsonPrimitive(100),
                "customBoolean" to JsonPrimitive(true)
            )
        )

        val element = json.encodeToJsonElement(DeepSeekChatCompletionRequestSerializer, request)
            .jsonObject

        // Standard properties should be present
        element["model"]!!.toString() shouldBe "\"deepseek-chat\""
        element["temperature"]!!.toString() shouldBe "0.7"

        // Additional properties should be flattened to the root level
        element["customString"]!!.toString() shouldBe "\"value\""
        element["customNumber"]!!.toString() shouldBe "100"
        element["customBoolean"]!!.toString() shouldBe "true"

        // the additionalProperties name itself should not be present in serialized JSON
        withClue("additionalProperties should not be in a serialized JSON") {
            element["additionalProperties"] shouldBe null
        }
    }

    @Test
    fun `test deserialization with additionalProperties`() {
        val jsonInput =
            """
            {
                "model": "deepseek-chat",
                "messages": [ { "role": "user", "content": "Hello" } ],
                "temperature": 0.7,
                "customString": "value",
                "customNumber": 100,
                "customBoolean": true
            }
            """.trimIndent()

        val request = json.decodeFromString(DeepSeekChatCompletionRequestSerializer, jsonInput)
        val props = request.additionalProperties
        withClue("additionalProperties should be in a deserialized JSON") {
            props shouldNotBe null
        }
        props?.get("customString").toString() shouldBe "\"value\""
        props?.get("customNumber").toString() shouldBe "100"
        props?.get("customBoolean").toString() shouldBe "true"
    }

    @Test
    fun `test serialization deserialization with additionalProperties`() {
        val original = DeepSeekChatCompletionRequest(
            model = "deepseek-chat",
            messages = listOf(OpenAIMessage.User(content = Content.Text("Hello"))),
            additionalProperties = mapOf(
                "x" to JsonPrimitive("y"),
                "n" to JsonPrimitive(7)
            )
        )

        val jsonStr = json.encodeToString(DeepSeekChatCompletionRequestSerializer, original)
        val decoded = json.decodeFromString(DeepSeekChatCompletionRequestSerializer, jsonStr)

        decoded.model shouldBe original.model
        kotlin.test.assertNotNull(decoded.additionalProperties)
        decoded.additionalProperties.size shouldBe 2
        decoded.additionalProperties["x"].toString() shouldBe "\"y\""
        decoded.additionalProperties["n"].toString() shouldBe "7"
    }

    @Test
    fun `test serialization of extended parameters`() {
        val tool = OpenAITool(
            OpenAIToolFunction(
                name = "weather",
                description = "Get weather",
                parameters = JsonObject(
                    mapOf(
                        "type" to JsonPrimitive("object"),
                        "properties" to JsonObject(emptyMap())
                    )
                ),
                strict = true
            )
        )

        val request = DeepSeekChatCompletionRequest(
            model = "deepseek-chat",
            messages = listOf(OpenAIMessage.User(content = Content.Text("Hello"))),
            temperature = 0.4,
            maxTokens = 1024,
            stream = true,
            tools = listOf(tool),
            toolChoice = OpenAIToolChoice.function("weather"),
            responseFormat = OpenAIResponseFormat.JsonObject(),
            streamOptions = OpenAIStreamOptions(includeUsage = true),
            logprobs = true,
            topLogprobs = 10,
            topP = 0.8,
            frequencyPenalty = 0.1,
            presencePenalty = 0.2,
            stop = listOf("END")
        )

        val obj = json.encodeToJsonElement(DeepSeekChatCompletionRequest.serializer(), request).jsonObject

        obj["model"]!!.toString() shouldBe "\"deepseek-chat\""
        obj["temperature"]!!.toString() shouldBe "0.4"
        obj["maxTokens"]!!.toString() shouldBe "1024"
        obj["stream"].toString() shouldBe "true"
        obj["topLogprobs"].toString() shouldBe "10"
        obj["topP"].toString() shouldBe "0.8"
        obj["frequencyPenalty"].toString() shouldBe "0.1"
        obj["presencePenalty"].toString() shouldBe "0.2"
        (obj["stop"] as JsonArray).size shouldBe 1

        val toolsArr = obj["tools"] as JsonArray
        toolsArr.size shouldBe 1
        val t0 = toolsArr[0].jsonObject
        val fn = t0["function"]!!.jsonObject
        fn["name"]!!.toString() shouldBe "\"weather\""
        fn["description"]!!.toString() shouldBe "\"Get weather\""
        fn["parameters"]!!.jsonObject["type"]!!.toString() shouldBe "\"object\""
        fn["strict"].toString() shouldBe "true"

        val tc = obj["toolChoice"]!!.jsonObject
        tc["function"]!!.jsonObject["name"].toString() shouldBe "\"weather\""

        val rf = obj["responseFormat"]!!.jsonObject
        rf["type"].toString() shouldBe "\"json_object\""

        val so = obj["streamOptions"]!!.jsonObject
        so["includeUsage"].toString() shouldBe "true"
    }
}
