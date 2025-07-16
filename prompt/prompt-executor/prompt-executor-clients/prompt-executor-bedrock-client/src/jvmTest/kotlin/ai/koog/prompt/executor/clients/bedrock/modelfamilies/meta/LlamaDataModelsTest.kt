package ai.koog.prompt.executor.clients.bedrock.modelfamilies.meta

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class LlamaDataModelsTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    @Test
    fun `LlamaRequest serialization`() {
        val request = LlamaRequest(
            prompt = "Tell me about Paris",
            maxGenLen = 1000, // Non-default value
            temperature = 0.7
        )

        val serialized = json.encodeToString(LlamaRequest.serializer(), request)

        assertNotNull(serialized)
        assert(serialized.contains("\"prompt\"")) { "Serialized JSON should contain 'prompt' field: $serialized" }
        assert(serialized.contains("\"Tell me about Paris\"")) { "Serialized JSON should contain the prompt text: $serialized" }
        assert(serialized.contains("\"max_gen_len\"")) { "Serialized JSON should contain 'max_gen_len' field: $serialized" }
        assert(serialized.contains("1000")) { "Serialized JSON should contain the maxGenLen value: $serialized" }
        assert(serialized.contains("\"temperature\"")) { "Serialized JSON should contain 'temperature' field: $serialized" }
        assert(serialized.contains("0.7")) { "Serialized JSON should contain the temperature value: $serialized" }
    }

    @Test
    fun `LlamaRequest serialization with null temperature`() {
        val request = LlamaRequest(
            prompt = "Tell me about Paris",
            maxGenLen = 1000, // Non-default value
            temperature = null
        )

        val serialized = json.encodeToString(LlamaRequest.serializer(), request)

        assertNotNull(serialized)
        assert(serialized.contains("\"prompt\"")) { "Serialized JSON should contain 'prompt' field: $serialized" }
        assert(serialized.contains("\"Tell me about Paris\"")) { "Serialized JSON should contain the prompt text: $serialized" }
        assert(serialized.contains("\"max_gen_len\"")) { "Serialized JSON should contain 'max_gen_len' field: $serialized" }
        assert(serialized.contains("1000")) { "Serialized JSON should contain the maxGenLen value: $serialized" }
        assert(!serialized.contains("\"temperature\"")) { "Serialized JSON should not contain 'temperature' field when it's null: $serialized" }
    }

    @Test
    fun `LlamaRequest deserialization`() {
        val jsonString = """
            {
                "prompt": "Tell me about Paris",
                "max_gen_len": 2048,
                "temperature": 0.7
            }
        """.trimIndent()

        val request = json.decodeFromString(LlamaRequest.serializer(), jsonString)

        assertEquals("Tell me about Paris", request.prompt)
        assertEquals(2048, request.maxGenLen)
        assertEquals(0.7, request.temperature)
    }

    @Test
    fun `LlamaRequest deserialization with missing fields`() {
        val jsonString = """
            {
                "prompt": "Tell me about Paris"
            }
        """.trimIndent()

        val request = json.decodeFromString(LlamaRequest.serializer(), jsonString)

        assertEquals("Tell me about Paris", request.prompt)
        assertEquals(2048, request.maxGenLen) // Default value
        assertNull(request.temperature) // Default value
    }

    @Test
    fun `LlamaResponse serialization`() {
        val response = LlamaResponse(
            generation = "Paris is the capital of France.",
            promptTokenCount = 10,
            generationTokenCount = 15,
            stopReason = "stop"
        )

        val serialized = json.encodeToString(LlamaResponse.serializer(), response)

        assertNotNull(serialized)
        assert(serialized.contains("\"generation\":\"Paris is the capital of France.\""))
        assert(serialized.contains("\"prompt_token_count\":10"))
        assert(serialized.contains("\"generation_token_count\":15"))
        assert(serialized.contains("\"stop_reason\":\"stop\""))
    }

    @Test
    fun `LlamaResponse serialization with null fields`() {
        val response = LlamaResponse(
            generation = "Paris is the capital of France.",
            promptTokenCount = null,
            generationTokenCount = null,
            stopReason = null
        )

        val serialized = json.encodeToString(LlamaResponse.serializer(), response)

        assertNotNull(serialized)
        assert(serialized.contains("\"generation\":\"Paris is the capital of France.\""))
        assert(!serialized.contains("\"prompt_token_count\":"))
        assert(!serialized.contains("\"generation_token_count\":"))
        assert(!serialized.contains("\"stop_reason\":"))
    }

    @Test
    fun `LlamaResponse deserialization`() {
        val jsonString = """
            {
                "generation": "Paris is the capital of France.",
                "prompt_token_count": 10,
                "generation_token_count": 15,
                "stop_reason": "stop"
            }
        """.trimIndent()

        val response = json.decodeFromString(LlamaResponse.serializer(), jsonString)

        assertEquals("Paris is the capital of France.", response.generation)
        assertEquals(10, response.promptTokenCount)
        assertEquals(15, response.generationTokenCount)
        assertEquals("stop", response.stopReason)
    }

    @Test
    fun `LlamaResponse deserialization with missing fields`() {
        val jsonString = """
            {
                "generation": "Paris is the capital of France."
            }
        """.trimIndent()

        val response = json.decodeFromString(LlamaResponse.serializer(), jsonString)

        assertEquals("Paris is the capital of France.", response.generation)
        assertNull(response.promptTokenCount)
        assertNull(response.generationTokenCount)
        assertNull(response.stopReason)
    }

    @Test
    fun `LlamaStreamChunk serialization`() {
        val chunk = LlamaStreamChunk(
            generation = "Hello, "
        )

        val serialized = json.encodeToString(LlamaStreamChunk.serializer(), chunk)

        assertNotNull(serialized)
        assert(serialized.contains("\"generation\":\"Hello, \""))
    }

    @Test
    fun `LlamaStreamChunk serialization with null generation`() {
        val chunk = LlamaStreamChunk(
            generation = null
        )

        val serialized = json.encodeToString(LlamaStreamChunk.serializer(), chunk)

        assertNotNull(serialized)
        assert(!serialized.contains("\"generation\":"))
    }

    @Test
    fun `LlamaStreamChunk deserialization`() {
        val jsonString = """
            {
                "generation": "Hello, "
            }
        """.trimIndent()

        val chunk = json.decodeFromString(LlamaStreamChunk.serializer(), jsonString)

        assertEquals("Hello, ", chunk.generation)
    }

    @Test
    fun `LlamaStreamChunk deserialization with null generation`() {
        val jsonString = """
            {
                "generation": null
            }
        """.trimIndent()

        val chunk = json.decodeFromString(LlamaStreamChunk.serializer(), jsonString)

        assertNull(chunk.generation)
    }

    @Test
    fun `LlamaStreamChunk deserialization with missing generation`() {
        val jsonString = """
            {
            }
        """.trimIndent()

        val chunk = json.decodeFromString(LlamaStreamChunk.serializer(), jsonString)

        assertNull(chunk.generation)
    }
}