package ai.koog.prompt.executor.clients.siliconflow

import ai.koog.prompt.executor.clients.siliconflow.models.SiliconFlowEmbeddingData
import ai.koog.prompt.executor.clients.siliconflow.models.SiliconFlowEmbeddingRequest
import ai.koog.prompt.executor.clients.siliconflow.models.SiliconFlowEmbeddingResponse
import ai.koog.prompt.executor.clients.siliconflow.models.SiliconFlowError
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.serialization.json.Json
import kotlin.test.Test

class SiliconFlowEmbeddingTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        namingStrategy = kotlinx.serialization.json.JsonNamingStrategy.SnakeCase
    }

    private val embeddingModels = listOf(
        SiliconFlowModels.Embeddings.BgeLarge_En_V1_5,
        SiliconFlowModels.Embeddings.BgeLarge_Zh_V1_5,
        SiliconFlowModels.Embeddings.BgeM3,
        SiliconFlowModels.Embeddings.BceEmbedding_Base_V1,
        SiliconFlowModels.Embeddings.ProBgeM3,
        SiliconFlowModels.Embeddings.Qwen3_Embedding_0_6B,
        SiliconFlowModels.Embeddings.Qwen3_Embedding_4B,
        SiliconFlowModels.Embeddings.Qwen3_Embedding_8B,
    )

    @Test
    fun testEmbeddingRequestSerialization() {
        val request = SiliconFlowEmbeddingRequest(
            model = "BAAI/bge-large-en-v1.5",
            input = "Hello, world!"
        )

        val jsonString = json.encodeToString(SiliconFlowEmbeddingRequest.serializer(), request)

        jsonString shouldBe """{"model":"BAAI/bge-large-en-v1.5","input":"Hello, world!"}"""
    }

    @Test
    fun testEmbeddingResponseDeserialization() {
        val jsonString = """
        {
            "data": [{"embedding": [0.1, 0.2, 0.3], "index": 0}],
            "model": "BAAI/bge-large-en-v1.5",
            "usage": {"prompt_tokens": 5, "total_tokens": 5}
        }
        """.trimIndent()

        val response = json.decodeFromString(SiliconFlowEmbeddingResponse.serializer(), jsonString)

        response.data.size shouldBe 1
        response.data.first().embedding shouldBe listOf(0.1, 0.2, 0.3)
        response.data.first().index shouldBe 0
        response.model shouldBe "BAAI/bge-large-en-v1.5"
        response.error shouldBe null
        response.usage shouldNotBe null
        response.usage?.promptTokens shouldBe 5
        response.usage?.totalTokens shouldBe 5
    }

    @Test
    fun testEmbeddingResponseWithErrorDeserialization() {
        val jsonString = """
        {
            "data": [],
            "error": {"message": "Invalid API key", "type": "invalid_request_error", "code": "401"}
        }
        """.trimIndent()

        val response = json.decodeFromString(SiliconFlowEmbeddingResponse.serializer(), jsonString)

        response.data.size shouldBe 0
        response.model shouldBe null
        response.error shouldNotBe null
        response.error?.message shouldBe "Invalid API key"
        response.error?.type shouldBe "invalid_request_error"
        response.error?.code shouldBe "401"
    }

    @Test
    fun testEmbeddingDataDeserialization() {
        val jsonString = """{"embedding": [0.1, 0.2, 0.3, 0.4], "index": 2}"""

        val data = json.decodeFromString(SiliconFlowEmbeddingData.serializer(), jsonString)

        data.embedding shouldBe listOf(0.1, 0.2, 0.3, 0.4)
        data.index shouldBe 2
    }

    @Test
    fun testSiliconFlowErrorDeserialization() {
        val jsonString = """{"message": "Rate limit exceeded", "type": "rate_limit_error", "code": "429"}"""

        val error = json.decodeFromString(SiliconFlowError.serializer(), jsonString)

        error.message shouldBe "Rate limit exceeded"
        error.type shouldBe "rate_limit_error"
        error.code shouldBe "429"
    }

    @Test
    fun testAllEmbeddingModelsHaveEmbedCapabilityAndProvider() {
        embeddingModels.forEach { model ->
            model.provider shouldBe LLMProvider.SiliconFlow
            model.capabilities shouldNotBe null
            model.capabilities!! shouldContain LLMCapability.Embed
            model.contextLength shouldNotBe null
            model.contextLength shouldBe model.contextLength?.coerceAtLeast(1)
        }
    }

    @Test
    fun testChatModelDoesNotExposeEmbedCapability() {
        val chatModel = SiliconFlowModels.Qwen3_8B
        chatModel.capabilities shouldNotBe null
        chatModel.capabilities!! shouldNotContain LLMCapability.Embed
    }
}
