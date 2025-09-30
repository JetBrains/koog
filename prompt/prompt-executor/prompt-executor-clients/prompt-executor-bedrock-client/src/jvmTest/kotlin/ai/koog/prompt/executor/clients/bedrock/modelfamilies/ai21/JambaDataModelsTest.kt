package ai.koog.prompt.executor.clients.bedrock.modelfamilies.ai21

import ai.koog.prompt.executor.clients.anthropic.AnthropicMessageRequest
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.clients.bedrock.modelfamilies.ai21.JambaRequest.Companion.MAX_TOKENS_DEFAULT
import ai.koog.test.utils.verifyDeserialization
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class JambaDataModelsTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    @Test
    fun `JambaRequest serialization`() {
        val request = JambaRequest(
            model = "ai21.jamba-1-5-large-v1:0",
            messages = listOf(
                JambaMessage(role = "user", content = "Tell me about Paris")
            ),
            maxTokens = 1000,
            temperature = 0.7
        )

        val serialized = json.encodeToString(JambaRequest.serializer(), request)

        assertNotNull(serialized)
        assert(serialized.contains("\"model\"")) { "Serialized JSON should contain 'model' field: $serialized" }
        assert(serialized.contains("\"ai21.jamba-1-5-large-v1:0\"")) {
            "Serialized JSON should contain the model ID: $serialized"
        }
        assert(serialized.contains("\"messages\"")) { "Serialized JSON should contain 'messages' field: $serialized" }
        assert(serialized.contains("\"role\"")) { "Serialized JSON should contain 'role' field: $serialized" }
        assert(serialized.contains("\"user\"")) { "Serialized JSON should contain 'user' role: $serialized" }
        assert(serialized.contains("\"content\"")) { "Serialized JSON should contain 'content' field: $serialized" }
        assert(serialized.contains("\"Tell me about Paris\"")) {
            "Serialized JSON should contain the message content: $serialized"
        }
        assert(serialized.contains("\"max_tokens\"")) {
            "Serialized JSON should contain 'max_tokens' field: $serialized"
        }
        assert(serialized.contains("1000")) { "Serialized JSON should contain the maxTokens value: $serialized" }
        assert(serialized.contains("\"temperature\"")) {
            "Serialized JSON should contain 'temperature' field: $serialized"
        }
        assert(serialized.contains("0.7")) { "Serialized JSON should contain the temperature value: $serialized" }
    }

    @Test
    fun `JambaRequest serialization with default maxTokens`() {
        val request = JambaRequest(
            model = "ai21.jamba-1-5-large-v1:0",
            messages = listOf(
                JambaMessage(role = "user", content = "Tell me about Paris")
            ),
            temperature = 0.7
        )
        assertEquals(MAX_TOKENS_DEFAULT, request.maxTokens)
    }

    @Test
    fun `JambaRequest serialization with maxTokens less than 1`() {
        val exception = assertFailsWith<IllegalArgumentException> {
            AnthropicMessageRequest(
                model = AnthropicModels.Opus_3.id,
                messages = emptyList(),
                maxTokens = 0
            )
        }
        assertEquals("maxTokens must be greater than 0, but was 0", exception.message)
    }

    @Test
    fun `JambaRequest serialization with null fields`() {
        val request = JambaRequest(
            model = "ai21.jamba-1-5-large-v1:0",
            messages = listOf(
                JambaMessage(role = "user", content = "Tell me about Paris")
            ),
            maxTokens = null,
            temperature = null
        )

        val serialized = json.encodeToString(JambaRequest.serializer(), request)

        assertNotNull(serialized)
        assert(serialized.contains("\"model\"")) { "Serialized JSON should contain 'model' field: $serialized" }
        assert(serialized.contains("\"messages\"")) { "Serialized JSON should contain 'messages' field: $serialized" }
        assert(!serialized.contains("\"max_tokens\"")) {
            "Serialized JSON should not contain 'max_tokens' field when it's null: $serialized"
        }
        assert(!serialized.contains("\"temperature\"")) {
            "Serialized JSON should not contain 'temperature' field when it's null: $serialized"
        }
    }

    @Test
    fun `JambaRequest deserialization`() {
        // language=JSON
        val jsonString = """
            {
                "model": "ai21.jamba-1-5-large-v1:0",
                "messages": [
                    {
                        "role": "user",
                        "content": "Tell me about Paris"
                    }
                ],
                "max_tokens": 1000,
                "temperature": 0.7
            }
        """.trimIndent()

        val request: JambaRequest = verifyDeserialization(jsonString, json)

        assertEquals("ai21.jamba-1-5-large-v1:0", request.model)
        assertEquals(1, request.messages.size)
        assertEquals("user", request.messages[0].role)
        assertEquals("Tell me about Paris", request.messages[0].content)
        assertEquals(1000, request.maxTokens)
        assertEquals(0.7, request.temperature)
    }

    @Test
    fun `JambaRequest deserialization with missing fields`() {
        // language=JSON
        val jsonString = """
            {
                "model": "ai21.jamba-1-5-large-v1:0",
                "messages": [
                    {
                        "role": "user",
                        "content": "Tell me about Paris"
                    }
                ]
            }
        """.trimIndent()

        val request: JambaRequest = verifyDeserialization(jsonString, json)

        assertEquals("ai21.jamba-1-5-large-v1:0", request.model)
        assertEquals(1, request.messages.size)
        assertEquals("user", request.messages[0].role)
        assertEquals("Tell me about Paris", request.messages[0].content)
        assertEquals(MAX_TOKENS_DEFAULT, request.maxTokens)
        assertNull(request.temperature)
    }

    @Test
    fun `JambaMessage serialization`() {
        val message = JambaMessage(
            role = "user",
            content = "Tell me about Paris"
        )

        val serialized = json.encodeToString(JambaMessage.serializer(), message)

        assertNotNull(serialized)
        assert(serialized.contains("\"role\":\"user\""))
        assert(serialized.contains("\"content\":\"Tell me about Paris\""))
    }

    @Test
    fun `JambaMessage serialization with tool calls`() {
        val message = JambaMessage(
            role = "assistant",
            content = null,
            toolCalls = listOf(
                JambaToolCall(
                    id = "call_01234567",
                    function = JambaFunctionCall(
                        name = "get_weather",
                        arguments = "{\"city\":\"Paris\"}"
                    )
                )
            )
        )

        val serialized = json.encodeToString(JambaMessage.serializer(), message)

        assertNotNull(serialized)
        assert(serialized.contains("\"role\":\"assistant\""))
        assert(serialized.contains("\"tool_calls\""))
        assert(serialized.contains("\"id\":\"call_01234567\""))
        assert(serialized.contains("\"function\""))
        assert(serialized.contains("\"name\":\"get_weather\""))
        assert(serialized.contains("\"arguments\":\"{\\\"city\\\":\\\"Paris\\\"}\""))
    }

    @Test
    fun `JambaMessage deserialization`() {
        // language=JSON
        val jsonString = """
            {
                "role": "user",
                "content": "Tell me about Paris"
            }
        """.trimIndent()

        val message: JambaMessage = verifyDeserialization(jsonString, json)

        assertEquals("user", message.role)
        assertEquals("Tell me about Paris", message.content)
        assertNull(message.toolCalls)
        assertNull(message.toolCallId)
    }

    @Test
    fun `JambaMessage deserialization with tool calls`() {
        // language=JSON
        val jsonString = """
            {
                "role": "assistant",
                "tool_calls": [
                    {
                        "id": "call_01234567",
                        "type": "function",
                        "function": {
                            "name": "get_weather",
                            "arguments": "{\"city\":\"Paris\"}"
                        }
                    }
                ]
            }
        """.trimIndent()

        val message: JambaMessage = verifyDeserialization(jsonString, json)

        assertEquals("assistant", message.role)
        assertNull(message.content)
        assertNotNull(message.toolCalls)
        assertEquals(1, message.toolCalls.size)
        assertEquals("call_01234567", message.toolCalls[0].id)
        assertEquals("function", message.toolCalls[0].type)
        assertEquals("get_weather", message.toolCalls[0].function.name)
        assertEquals("{\"city\":\"Paris\"}", message.toolCalls[0].function.arguments)
    }

    @Test
    fun `JambaTool serialization`() {
        val tool = JambaTool(
            function = JambaFunction(
                name = "get_weather",
                description = "Get current weather for a city",
                parameters = buildJsonObject {
                    put("type", "object")
                    put(
                        "properties",
                        buildJsonObject {
                            put(
                                "city",
                                buildJsonObject {
                                    put("type", "string")
                                    put("description", "The city name")
                                }
                            )
                        }
                    )
                    put(
                        "required",
                        buildJsonObject {
                            put("0", "city")
                        }
                    )
                }
            )
        )

        val serialized = json.encodeToString(JambaTool.serializer(), tool)
        println("Serialized JambaTool: $serialized")

        assertNotNull(serialized)
        assert(serialized.contains("\"function\""))
        assert(serialized.contains("\"name\":\"get_weather\""))
        assert(serialized.contains("\"description\":\"Get current weather for a city\""))
        assert(serialized.contains("\"parameters\""))
        assert(serialized.contains("\"properties\""))
        assert(serialized.contains("\"city\""))
        assert(serialized.contains("\"required\""))
    }

    @Test
    fun `JambaResponse serialization`() {
        val response = JambaResponse(
            id = "resp_01234567",
            model = "ai21.jamba-1-5-large-v1:0",
            choices = listOf(
                JambaChoice(
                    index = 0,
                    message = JambaMessage(
                        role = "assistant",
                        content = "Paris is the capital of France."
                    ),
                    finishReason = "stop"
                )
            ),
            usage = JambaUsage(
                promptTokens = 10,
                completionTokens = 15,
                totalTokens = 25
            )
        )

        val serialized = json.encodeToString(JambaResponse.serializer(), response)

        assertNotNull(serialized)
        assert(serialized.contains("\"id\":\"resp_01234567\""))
        assert(serialized.contains("\"model\":\"ai21.jamba-1-5-large-v1:0\""))
        assert(serialized.contains("\"choices\""))
        assert(serialized.contains("\"index\":0"))
        assert(serialized.contains("\"message\""))
        assert(serialized.contains("\"role\":\"assistant\""))
        assert(serialized.contains("\"content\":\"Paris is the capital of France.\""))
        assert(serialized.contains("\"finish_reason\":\"stop\""))
        assert(serialized.contains("\"usage\""))
        assert(serialized.contains("\"prompt_tokens\":10"))
        assert(serialized.contains("\"completion_tokens\":15"))
        assert(serialized.contains("\"total_tokens\":25"))
    }

    @Test
    fun `JambaResponse deserialization`() {
        // language=JSON
        val jsonString = """
            {
                "id": "resp_01234567",
                "model": "ai21.jamba-1-5-large-v1:0",
                "choices": [
                    {
                        "index": 0,
                        "message": {
                            "role": "assistant",
                            "content": "Paris is the capital of France."
                        },
                        "finish_reason": "stop"
                    }
                ],
                "usage": {
                    "prompt_tokens": 10,
                    "completion_tokens": 15,
                    "total_tokens": 25
                }
            }
        """.trimIndent()

        val response: JambaResponse = verifyDeserialization(jsonString, json)

        assertEquals("resp_01234567", response.id)
        assertEquals("ai21.jamba-1-5-large-v1:0", response.model)
        assertEquals(1, response.choices.size)
        assertEquals(0, response.choices[0].index)
        assertEquals("assistant", response.choices[0].message.role)
        assertEquals("Paris is the capital of France.", response.choices[0].message.content)
        assertEquals("stop", response.choices[0].finishReason)
        assertNotNull(response.usage)
        assertEquals(10, response.usage.promptTokens)
        assertEquals(15, response.usage.completionTokens)
        assertEquals(25, response.usage.totalTokens)
    }

    @Test
    fun `JambaStreamResponse serialization`() {
        val streamResponse = JambaStreamResponse(
            id = "resp_01234567",
            choices = listOf(
                JambaStreamChoice(
                    index = 0,
                    delta = JambaStreamDelta(
                        content = "Paris is "
                    )
                )
            )
        )

        val serialized = json.encodeToString(JambaStreamResponse.serializer(), streamResponse)

        assertNotNull(serialized)
        assert(serialized.contains("\"id\":\"resp_01234567\""))
        assert(serialized.contains("\"choices\""))
        assert(serialized.contains("\"index\":0"))
        assert(serialized.contains("\"delta\""))
        assert(serialized.contains("\"content\":\"Paris is \""))
    }

    @Test
    fun `JambaStreamResponse deserialization`() {
        // language=JSON
        val jsonString = """
            {
                "id": "resp_01234567",
                "choices": [
                    {
                        "index": 0,
                        "delta": {
                            "content": "Paris is "
                        }
                    }
                ]
            }
        """.trimIndent()

        val streamResponse: JambaStreamResponse = verifyDeserialization(jsonString, json)

        assertEquals("resp_01234567", streamResponse.id)
        assertEquals(1, streamResponse.choices.size)
        assertEquals(0, streamResponse.choices[0].index)
        assertEquals("Paris is ", streamResponse.choices[0].delta.content)
    }
}
