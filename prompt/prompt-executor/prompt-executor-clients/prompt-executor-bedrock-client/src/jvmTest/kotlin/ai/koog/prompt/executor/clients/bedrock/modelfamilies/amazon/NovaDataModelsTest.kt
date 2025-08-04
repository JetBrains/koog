package ai.koog.prompt.executor.clients.bedrock.modelfamilies.amazon

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class NovaDataModelsTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    @Test
    fun `NovaRequest serialization`() {
        val request = NovaRequest(
            messages = listOf(
                NovaMessage(
                    role = "user",
                    content = listOf(NovaContent(text = "Tell me about Paris"))
                )
            ),
            inferenceConfig = NovaInferenceConfig(
                temperature = 0.7,
                maxTokens = 1000
            ),
            system = listOf(
                NovaSystemMessage(text = "You are a helpful assistant.")
            )
        )

        val serialized = json.encodeToString(NovaRequest.serializer(), request)

        assertNotNull(serialized)
        assert(serialized.contains("\"messages\"")) { "Serialized JSON should contain 'messages' field: $serialized" }
        assert(serialized.contains("\"role\"")) { "Serialized JSON should contain 'role' field: $serialized" }
        assert(serialized.contains("\"user\"")) { "Serialized JSON should contain 'user' role: $serialized" }
        assert(serialized.contains("\"content\"")) { "Serialized JSON should contain 'content' field: $serialized" }
        assert(serialized.contains("\"text\"")) { "Serialized JSON should contain 'text' field: $serialized" }
        assert(serialized.contains("\"Tell me about Paris\"")) {
            "Serialized JSON should contain the message text: $serialized"
        }
        assert(serialized.contains("\"inferenceConfig\"")) {
            "Serialized JSON should contain 'inferenceConfig' field: $serialized"
        }
        assert(serialized.contains("\"temperature\"")) {
            "Serialized JSON should contain 'temperature' field: $serialized"
        }
        assert(serialized.contains("0.7")) { "Serialized JSON should contain the temperature value: $serialized" }
        assert(serialized.contains("\"maxTokens\"")) { "Serialized JSON should contain 'maxTokens' field: $serialized" }
        assert(serialized.contains("1000")) { "Serialized JSON should contain the maxTokens value: $serialized" }
        assert(serialized.contains("\"system\"")) { "Serialized JSON should contain 'system' field: $serialized" }
        assert(serialized.contains("\"You are a helpful assistant.\"")) {
            "Serialized JSON should contain the system message: $serialized"
        }
    }

    @Test
    fun `NovaRequest serialization with null fields`() {
        val request = NovaRequest(
            messages = listOf(
                NovaMessage(
                    role = "user",
                    content = listOf(NovaContent(text = "Tell me about Paris"))
                )
            ),
            inferenceConfig = null,
            system = null
        )

        val serialized = json.encodeToString(NovaRequest.serializer(), request)

        assertNotNull(serialized)
        assert(serialized.contains("\"messages\"")) { "Serialized JSON should contain 'messages' field: $serialized" }
        assert(!serialized.contains("\"inferenceConfig\"")) {
            "Serialized JSON should not contain 'inferenceConfig' field when it's null: $serialized"
        }
        assert(!serialized.contains("\"system\"")) {
            "Serialized JSON should not contain 'system' field when it's null: $serialized"
        }
    }

    @Test
    fun `NovaRequest deserialization`() {
        val jsonString = """
            {
                "messages": [
                    {
                        "role": "user",
                        "content": [
                            {
                                "text": "Tell me about Paris"
                            }
                        ]
                    }
                ],
                "inferenceConfig": {
                    "temperature": 0.7,
                    "maxTokens": 1000
                },
                "system": [
                    {
                        "text": "You are a helpful assistant."
                    }
                ]
            }
        """.trimIndent()

        val request = json.decodeFromString(NovaRequest.serializer(), jsonString)

        assertEquals(1, request.messages.size)
        assertEquals("user", request.messages[0].role)
        assertEquals(1, request.messages[0].content.size)
        assertEquals("Tell me about Paris", request.messages[0].content[0].text)
        assertNotNull(request.inferenceConfig)
        assertEquals(0.7, request.inferenceConfig.temperature)
        assertEquals(1000, request.inferenceConfig.maxTokens)
        assertNotNull(request.system)
        assertEquals(1, request.system.size)
        assertEquals("You are a helpful assistant.", request.system[0].text)
    }

    @Test
    fun `NovaRequest deserialization with missing fields`() {
        val jsonString = """
            {
                "messages": [
                    {
                        "role": "user",
                        "content": [
                            {
                                "text": "Tell me about Paris"
                            }
                        ]
                    }
                ]
            }
        """.trimIndent()

        val request = json.decodeFromString(NovaRequest.serializer(), jsonString)

        assertEquals(1, request.messages.size)
        assertEquals("user", request.messages[0].role)
        assertEquals(1, request.messages[0].content.size)
        assertEquals("Tell me about Paris", request.messages[0].content[0].text)
        assertNull(request.inferenceConfig)
        assertNull(request.system)
    }

    @Test
    fun `NovaMessage serialization`() {
        val message = NovaMessage(
            role = "user",
            content = listOf(NovaContent(text = "Tell me about Paris"))
        )

        val serialized = json.encodeToString(NovaMessage.serializer(), message)

        assertNotNull(serialized)
        assert(serialized.contains("\"role\":\"user\""))
        assert(serialized.contains("\"content\""))
        assert(serialized.contains("\"text\":\"Tell me about Paris\""))
    }

    @Test
    fun `NovaMessage deserialization`() {
        val jsonString = """
            {
                "role": "user",
                "content": [
                    {
                        "text": "Tell me about Paris"
                    }
                ]
            }
        """.trimIndent()

        val message = json.decodeFromString(NovaMessage.serializer(), jsonString)

        assertEquals("user", message.role)
        assertEquals(1, message.content.size)
        assertEquals("Tell me about Paris", message.content[0].text)
    }

    @Test
    fun `NovaInferenceConfig serialization`() {
        val config = NovaInferenceConfig(
            temperature = 0.7,
            topP = 0.9,
            topK = 50,
            maxTokens = 1000
        )

        val serialized = json.encodeToString(NovaInferenceConfig.serializer(), config)

        assertNotNull(serialized)
        assert(serialized.contains("\"temperature\":0.7"))
        assert(serialized.contains("\"topP\":0.9"))
        assert(serialized.contains("\"topK\":50"))
        assert(serialized.contains("\"maxTokens\":1000"))
    }

    @Test
    fun `NovaInferenceConfig serialization with null fields`() {
        val config = NovaInferenceConfig(
            temperature = null,
            topP = null,
            topK = null,
            maxTokens = null
        )

        val serialized = json.encodeToString(NovaInferenceConfig.serializer(), config)

        assertNotNull(serialized)
        assert(!serialized.contains("\"temperature\""))
        assert(!serialized.contains("\"topP\""))
        assert(!serialized.contains("\"topK\""))
        assert(!serialized.contains("\"maxTokens\""))
    }

    @Test
    fun `NovaInferenceConfig deserialization`() {
        val jsonString = """
            {
                "temperature": 0.7,
                "topP": 0.9,
                "topK": 50,
                "maxTokens": 1000
            }
        """.trimIndent()

        val config = json.decodeFromString(NovaInferenceConfig.serializer(), jsonString)

        assertEquals(0.7, config.temperature)
        assertEquals(0.9, config.topP)
        assertEquals(50, config.topK)
        assertEquals(1000, config.maxTokens)
    }

    @Test
    fun `NovaResponse serialization`() {
        val response = NovaResponse(
            output = NovaOutput(
                message = NovaMessage(
                    role = "assistant",
                    content = listOf(NovaContent(text = "Paris is the capital of France."))
                )
            ),
            usage = NovaUsage(
                inputTokens = 10,
                outputTokens = 15,
                totalTokens = 25
            ),
            stopReason = "stop"
        )

        val serialized = json.encodeToString(NovaResponse.serializer(), response)

        assertNotNull(serialized)
        assert(serialized.contains("\"output\""))
        assert(serialized.contains("\"message\""))
        assert(serialized.contains("\"role\":\"assistant\""))
        assert(serialized.contains("\"content\""))
        assert(serialized.contains("\"text\":\"Paris is the capital of France.\""))
        assert(serialized.contains("\"usage\""))
        assert(serialized.contains("\"inputTokens\":10"))
        assert(serialized.contains("\"outputTokens\":15"))
        assert(serialized.contains("\"totalTokens\":25"))
        assert(serialized.contains("\"stopReason\":\"stop\""))
    }

    @Test
    fun `NovaResponse deserialization`() {
        val jsonString = """
            {
                "output": {
                    "message": {
                        "role": "assistant",
                        "content": [
                            {
                                "text": "Paris is the capital of France."
                            }
                        ]
                    }
                },
                "usage": {
                    "inputTokens": 10,
                    "outputTokens": 15,
                    "totalTokens": 25
                },
                "stopReason": "stop"
            }
        """.trimIndent()

        val response = json.decodeFromString(NovaResponse.serializer(), jsonString)

        assertEquals("assistant", response.output.message.role)
        assertEquals(1, response.output.message.content.size)
        assertEquals("Paris is the capital of France.", response.output.message.content[0].text)
        assertNotNull(response.usage)
        assertEquals(10, response.usage.inputTokens)
        assertEquals(15, response.usage.outputTokens)
        assertEquals(25, response.usage.totalTokens)
        assertEquals("stop", response.stopReason)
    }

    @Test
    fun `NovaStreamChunk serialization`() {
        val chunk = NovaStreamChunk(
            contentBlockDelta = NovaContentBlockDelta(
                delta = NovaContentDelta(text = "Paris is ")
            )
        )

        val serialized = json.encodeToString(NovaStreamChunk.serializer(), chunk)

        assertNotNull(serialized)
        assert(serialized.contains("\"contentBlockDelta\""))
        assert(serialized.contains("\"delta\""))
        assert(serialized.contains("\"text\":\"Paris is \""))
    }

    @Test
    fun `NovaStreamChunk serialization with message stop`() {
        val chunk = NovaStreamChunk(
            messageStop = NovaMessageStop(stopReason = "stop"),
            metadata = NovaStreamMetadata(
                usage = NovaUsage(outputTokens = 20)
            )
        )

        val serialized = json.encodeToString(NovaStreamChunk.serializer(), chunk)

        assertNotNull(serialized)
        assert(serialized.contains("\"messageStop\""))
        assert(serialized.contains("\"stopReason\":\"stop\""))
        assert(serialized.contains("\"metadata\""))
        assert(serialized.contains("\"usage\""))
        assert(serialized.contains("\"outputTokens\":20"))
    }

    @Test
    fun `NovaStreamChunk deserialization`() {
        val jsonString = """
            {
                "contentBlockDelta": {
                    "delta": {
                        "text": "Paris is "
                    }
                }
            }
        """.trimIndent()

        val chunk = json.decodeFromString(NovaStreamChunk.serializer(), jsonString)

        assertNotNull(chunk.contentBlockDelta)
        assertEquals("Paris is ", chunk.contentBlockDelta.delta.text)
        assertNull(chunk.messageStop)
        assertNull(chunk.metadata)
    }

    @Test
    fun `NovaStreamChunk deserialization with message stop`() {
        val jsonString = """
            {
                "messageStop": {
                    "stopReason": "stop"
                },
                "metadata": {
                    "usage": {
                        "outputTokens": 20
                    }
                }
            }
        """.trimIndent()

        val chunk = json.decodeFromString(NovaStreamChunk.serializer(), jsonString)

        assertNull(chunk.contentBlockDelta)
        assertNotNull(chunk.messageStop)
        assertEquals("stop", chunk.messageStop.stopReason)
        assertNotNull(chunk.metadata)
        assertNotNull(chunk.metadata.usage)
        assertEquals(20, chunk.metadata.usage.outputTokens)
    }
}
