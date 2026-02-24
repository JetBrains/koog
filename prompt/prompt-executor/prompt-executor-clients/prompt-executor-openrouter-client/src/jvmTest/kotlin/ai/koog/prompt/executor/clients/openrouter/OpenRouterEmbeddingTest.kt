package ai.koog.prompt.executor.clients.openrouter

import ai.koog.prompt.executor.clients.openrouter.models.OpenRouterEmbeddingData
import ai.koog.prompt.executor.clients.openrouter.models.OpenRouterEmbeddingRequest
import ai.koog.prompt.executor.clients.openrouter.models.OpenRouterEmbeddingResponse
import ai.koog.prompt.executor.clients.openrouter.models.OpenRouterError
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.serialization.json.Json
import kotlin.test.Test

class OpenRouterEmbeddingTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        namingStrategy = kotlinx.serialization.json.JsonNamingStrategy.SnakeCase
    }

    private val allEmbeddingModels = listOf(
        // OpenAI
        OpenRouterModels.Embeddings.OpenAITextEmbedding3Small,
        OpenRouterModels.Embeddings.OpenAITextEmbedding3Large,
        OpenRouterModels.Embeddings.OpenAITextEmbeddingAda002,
        // Google
        OpenRouterModels.Embeddings.GoogleGeminiEmbedding001,
        // Mistral
        OpenRouterModels.Embeddings.MistralEmbed2312,
        OpenRouterModels.Embeddings.MistralCodestralEmbed2505,
        // Qwen
        OpenRouterModels.Embeddings.Qwen3Embedding8B,
        OpenRouterModels.Embeddings.Qwen3Embedding4B,
        // BAAI
        OpenRouterModels.Embeddings.BaaiGbeBaseEnV15,
        OpenRouterModels.Embeddings.BaaiBgeLargeEnV15,
        OpenRouterModels.Embeddings.BaaiBgeM3,
        // Thenlper
        OpenRouterModels.Embeddings.ThenlperGteBase,
        OpenRouterModels.Embeddings.ThenlperGteLarge,
        // Intfloat
        OpenRouterModels.Embeddings.IntfloatE5BaseV2,
        OpenRouterModels.Embeddings.IntfloatE5LargeV2,
        OpenRouterModels.Embeddings.IntfloatMultilingualE5Large,
        // Sentence Transformers
        OpenRouterModels.Embeddings.SentenceTransformersAllMiniLmL6V2,
        OpenRouterModels.Embeddings.SentenceTransformersAllMiniLmL12V2,
        OpenRouterModels.Embeddings.SentenceTransformersAllMpnetBaseV2,
        OpenRouterModels.Embeddings.SentenceTransformersMultiQaMpnetBaseDotV1,
        OpenRouterModels.Embeddings.SentenceTransformersParaphraseMiniLmL6V2,
    )

    @Test
    fun `OpenRouterEmbeddingRequest serializes correctly`() {
        val request = OpenRouterEmbeddingRequest(
            model = "openai/text-embedding-3-small",
            input = "Hello, world!"
        )
        val jsonString = json.encodeToString(OpenRouterEmbeddingRequest.serializer(), request)
        jsonString shouldBe """{"model":"openai/text-embedding-3-small","input":"Hello, world!"}"""
    }

    @Test
    fun `OpenRouterEmbeddingResponse deserializes correctly`() {
        val jsonString = """
        {
            "data": [{"embedding": [0.1, 0.2, 0.3], "index": 0}],
            "model": "openai/text-embedding-3-small",
            "usage": {"prompt_tokens": 5, "total_tokens": 5}
        }
        """.trimIndent()

        val response = json.decodeFromString(OpenRouterEmbeddingResponse.serializer(), jsonString)
        response.data shouldHaveSize 1
        response.data.first().embedding shouldBe listOf(0.1, 0.2, 0.3)
        response.data.first().index shouldBe 0
        response.model shouldBe "openai/text-embedding-3-small"
        response.error shouldBe null
    }

    @Test
    fun `OpenRouterEmbeddingResponse with error deserializes correctly`() {
        val jsonString = """
        {
            "data": [],
            "error": {"message": "Invalid API key", "type": "invalid_request_error", "code": "401"}
        }
        """.trimIndent()

        val response = json.decodeFromString(OpenRouterEmbeddingResponse.serializer(), jsonString)
        response.data shouldHaveSize 0
        response.model shouldBe null
        response.error shouldNotBe null
        response.error?.message shouldBe "Invalid API key"
        response.error?.type shouldBe "invalid_request_error"
        response.error?.code shouldBe "401"
    }

    @Test
    fun `OpenRouterEmbeddingResponse with empty data deserializes correctly`() {
        val jsonString = """
        {
            "data": [],
            "model": "openai/text-embedding-3-small"
        }
        """.trimIndent()

        val response = json.decodeFromString(OpenRouterEmbeddingResponse.serializer(), jsonString)
        response.data shouldHaveSize 0
        response.model shouldBe "openai/text-embedding-3-small"
        response.error shouldBe null
    }

    @Test
    fun `OpenRouterEmbeddingData deserializes correctly`() {
        val jsonString = """{"embedding": [0.1, 0.2, 0.3, 0.4, 0.5], "index": 2}"""
        val data = json.decodeFromString(OpenRouterEmbeddingData.serializer(), jsonString)
        data.embedding shouldBe listOf(0.1, 0.2, 0.3, 0.4, 0.5)
        data.index shouldBe 2
    }

    @Test
    fun `all 21 embedding models are defined`() {
        allEmbeddingModels shouldHaveSize 21
    }

    @Test
    fun `all embedding models have Embed capability`() {
        allEmbeddingModels.forEach { model ->
            model.capabilities shouldNotBe null
            model.capabilities!! shouldContain LLMCapability.Embed
        }
    }

    @Test
    fun `all embedding models have OpenRouter provider`() {
        allEmbeddingModels.forEach { model ->
            model.provider shouldBe LLMProvider.OpenRouter
        }
    }

    @Test
    fun `embedding models have valid context lengths`() {
        allEmbeddingModels.forEach { model ->
            model.contextLength shouldNotBe null
            model.contextLength!! shouldBe model.contextLength!!.coerceAtLeast(1)
        }
    }

    @Test
    fun `chat model does not have Embed capability`() {
        val chatModel = OpenRouterModels.GPT4oMini
        chatModel.capabilities shouldNotBe null
        chatModel.capabilities!! shouldNotContain LLMCapability.Embed
    }

    @Test
    fun `OpenAI embedding model IDs are correct`() {
        OpenRouterModels.Embeddings.OpenAITextEmbedding3Small.id shouldBe "openai/text-embedding-3-small"
        OpenRouterModels.Embeddings.OpenAITextEmbedding3Large.id shouldBe "openai/text-embedding-3-large"
        OpenRouterModels.Embeddings.OpenAITextEmbeddingAda002.id shouldBe "openai/text-embedding-ada-002"
    }

    @Test
    fun `Google embedding model ID is correct`() {
        OpenRouterModels.Embeddings.GoogleGeminiEmbedding001.id shouldBe "google/gemini-embedding-001"
    }

    @Test
    fun `Mistral embedding model IDs are correct`() {
        OpenRouterModels.Embeddings.MistralEmbed2312.id shouldBe "mistralai/mistral-embed-2312"
        OpenRouterModels.Embeddings.MistralCodestralEmbed2505.id shouldBe "mistralai/codestral-embed-2505"
    }

    @Test
    fun `Qwen embedding model IDs are correct`() {
        OpenRouterModels.Embeddings.Qwen3Embedding8B.id shouldBe "qwen/qwen3-embedding-8b"
        OpenRouterModels.Embeddings.Qwen3Embedding4B.id shouldBe "qwen/qwen3-embedding-4b"
    }

    @Test
    fun `BAAI embedding model IDs are correct`() {
        OpenRouterModels.Embeddings.BaaiGbeBaseEnV15.id shouldBe "baai/bge-base-en-v1.5"
        OpenRouterModels.Embeddings.BaaiBgeLargeEnV15.id shouldBe "baai/bge-large-en-v1.5"
        OpenRouterModels.Embeddings.BaaiBgeM3.id shouldBe "baai/bge-m3"
    }

    @Test
    fun `Thenlper embedding model IDs are correct`() {
        OpenRouterModels.Embeddings.ThenlperGteBase.id shouldBe "thenlper/gte-base"
        OpenRouterModels.Embeddings.ThenlperGteLarge.id shouldBe "thenlper/gte-large"
    }

    @Test
    fun `Intfloat embedding model IDs are correct`() {
        OpenRouterModels.Embeddings.IntfloatE5BaseV2.id shouldBe "intfloat/e5-base-v2"
        OpenRouterModels.Embeddings.IntfloatE5LargeV2.id shouldBe "intfloat/e5-large-v2"
        OpenRouterModels.Embeddings.IntfloatMultilingualE5Large.id shouldBe "intfloat/multilingual-e5-large"
    }

    @Test
    fun `Sentence Transformers embedding model IDs are correct`() {
        OpenRouterModels.Embeddings.SentenceTransformersAllMiniLmL6V2.id shouldBe "sentence-transformers/all-minilm-l6-v2"
        OpenRouterModels.Embeddings.SentenceTransformersAllMiniLmL12V2.id shouldBe "sentence-transformers/all-minilm-l12-v2"
        OpenRouterModels.Embeddings.SentenceTransformersAllMpnetBaseV2.id shouldBe "sentence-transformers/all-mpnet-base-v2"
        OpenRouterModels.Embeddings.SentenceTransformersMultiQaMpnetBaseDotV1.id shouldBe "sentence-transformers/multi-qa-mpnet-base-dot-v1"
        OpenRouterModels.Embeddings.SentenceTransformersParaphraseMiniLmL6V2.id shouldBe "sentence-transformers/paraphrase-minilm-l6-v2"
    }

    @Test
    fun `OpenRouterEmbeddingResponse with usage deserializes correctly`() {
        val jsonString = """
        {
            "data": [{"embedding": [0.1], "index": 0}],
            "model": "openai/text-embedding-3-small",
            "usage": {"prompt_tokens": 10, "completion_tokens": 0, "total_tokens": 10}
        }
        """.trimIndent()

        val response = json.decodeFromString(OpenRouterEmbeddingResponse.serializer(), jsonString)
        response.usage shouldNotBe null
        response.usage?.promptTokens shouldBe 10
        response.usage?.totalTokens shouldBe 10
    }

    @Test
    fun `OpenRouterError deserializes correctly`() {
        val jsonString = """{"message": "Rate limit exceeded", "type": "rate_limit_error", "code": "429"}"""
        val error = json.decodeFromString(OpenRouterError.serializer(), jsonString)
        error.message shouldBe "Rate limit exceeded"
        error.type shouldBe "rate_limit_error"
        error.code shouldBe "429"
    }

    @Test
    fun `high context models have correct context lengths`() {
        OpenRouterModels.Embeddings.GoogleGeminiEmbedding001.contextLength shouldBe 20_000
        OpenRouterModels.Embeddings.Qwen3Embedding8B.contextLength shouldBe 32_000
        OpenRouterModels.Embeddings.Qwen3Embedding4B.contextLength shouldBe 32_768
        OpenRouterModels.Embeddings.BaaiBgeM3.contextLength shouldBe 8_192
    }

    @Test
    fun `small context models have correct context lengths`() {
        OpenRouterModels.Embeddings.ThenlperGteBase.contextLength shouldBe 512
        OpenRouterModels.Embeddings.IntfloatE5BaseV2.contextLength shouldBe 512
        OpenRouterModels.Embeddings.SentenceTransformersAllMiniLmL6V2.contextLength shouldBe 512
    }
}
