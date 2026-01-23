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
            "model": "",
            "error": {"message": "Invalid API key", "type": "invalid_request_error", "code": "401"}
        }
        """.trimIndent()

        val response = json.decodeFromString(OpenRouterEmbeddingResponse.serializer(), jsonString)
        response.data shouldHaveSize 0
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
    fun `all embedding models have Embed capability`() {
        val embeddingModels = listOf(
            OpenRouterModels.Embeddings.OpenAITextEmbedding3Small,
            OpenRouterModels.Embeddings.OpenAITextEmbedding3Large,
            OpenRouterModels.Embeddings.OpenAITextEmbeddingAda002,
            OpenRouterModels.Embeddings.CohereEmbedEnglishV3,
            OpenRouterModels.Embeddings.CohereEmbedMultilingualV3,
            OpenRouterModels.Embeddings.Voyage2,
            OpenRouterModels.Embeddings.VoyageCode2,
            OpenRouterModels.Embeddings.VoyageLarge2,
            OpenRouterModels.Embeddings.GoogleTextEmbedding004,
            OpenRouterModels.Embeddings.MistralEmbed,
        )

        embeddingModels.forEach { model ->
            model.capabilities shouldContain LLMCapability.Embed
        }
    }

    @Test
    fun `all embedding models have OpenRouter provider`() {
        val embeddingModels = listOf(
            OpenRouterModels.Embeddings.OpenAITextEmbedding3Small,
            OpenRouterModels.Embeddings.OpenAITextEmbedding3Large,
            OpenRouterModels.Embeddings.OpenAITextEmbeddingAda002,
            OpenRouterModels.Embeddings.CohereEmbedEnglishV3,
            OpenRouterModels.Embeddings.CohereEmbedMultilingualV3,
            OpenRouterModels.Embeddings.Voyage2,
            OpenRouterModels.Embeddings.VoyageCode2,
            OpenRouterModels.Embeddings.VoyageLarge2,
            OpenRouterModels.Embeddings.GoogleTextEmbedding004,
            OpenRouterModels.Embeddings.MistralEmbed,
        )

        embeddingModels.forEach { model ->
            model.provider shouldBe LLMProvider.OpenRouter
        }
    }

    @Test
    fun `embedding models have valid context lengths`() {
        val embeddingModels = listOf(
            OpenRouterModels.Embeddings.OpenAITextEmbedding3Small,
            OpenRouterModels.Embeddings.OpenAITextEmbedding3Large,
            OpenRouterModels.Embeddings.OpenAITextEmbeddingAda002,
            OpenRouterModels.Embeddings.CohereEmbedEnglishV3,
            OpenRouterModels.Embeddings.CohereEmbedMultilingualV3,
            OpenRouterModels.Embeddings.Voyage2,
            OpenRouterModels.Embeddings.VoyageCode2,
            OpenRouterModels.Embeddings.VoyageLarge2,
            OpenRouterModels.Embeddings.GoogleTextEmbedding004,
            OpenRouterModels.Embeddings.MistralEmbed,
        )

        embeddingModels.forEach { model ->
            model.contextLength shouldNotBe null
            model.contextLength!! shouldBe model.contextLength!!.coerceAtLeast(1)
        }
    }

    @Test
    fun `chat model does not have Embed capability`() {
        val chatModel = OpenRouterModels.GPT4oMini
        chatModel.capabilities shouldNotContain LLMCapability.Embed
    }

    @Test
    fun `embedding model IDs follow expected format`() {
        OpenRouterModels.Embeddings.OpenAITextEmbedding3Small.id shouldBe "openai/text-embedding-3-small"
        OpenRouterModels.Embeddings.OpenAITextEmbedding3Large.id shouldBe "openai/text-embedding-3-large"
        OpenRouterModels.Embeddings.OpenAITextEmbeddingAda002.id shouldBe "openai/text-embedding-ada-002"
        OpenRouterModels.Embeddings.CohereEmbedEnglishV3.id shouldBe "cohere/embed-english-v3.0"
        OpenRouterModels.Embeddings.CohereEmbedMultilingualV3.id shouldBe "cohere/embed-multilingual-v3.0"
        OpenRouterModels.Embeddings.Voyage2.id shouldBe "voyage/voyage-2"
        OpenRouterModels.Embeddings.VoyageCode2.id shouldBe "voyage/voyage-code-2"
        OpenRouterModels.Embeddings.VoyageLarge2.id shouldBe "voyage/voyage-large-2"
        OpenRouterModels.Embeddings.GoogleTextEmbedding004.id shouldBe "google/text-embedding-004"
        OpenRouterModels.Embeddings.MistralEmbed.id shouldBe "mistralai/mistral-embed"
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
}
