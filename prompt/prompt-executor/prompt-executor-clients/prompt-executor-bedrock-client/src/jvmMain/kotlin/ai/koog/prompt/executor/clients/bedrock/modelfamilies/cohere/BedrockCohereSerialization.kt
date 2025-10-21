package ai.koog.prompt.executor.clients.bedrock.modelfamilies.cohere

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

/**
 * Cohere Embed Models - Serialization & utility for AWS Bedrock API.
 * Docs: https://docs.aws.amazon.com/bedrock/latest/userguide/model-parameters-embed-v3.html
 */

/** REQUEST SCHEMA FOR COHERE EMBED */
@Serializable
internal data class CohereEmbedRequest(
    @SerialName("texts")
    val texts: List<String>,
    @SerialName("input_type")
    val inputType: String? = null,
    @SerialName("truncate")
    val truncate: String? = null,
    @SerialName("embedding_types")
    val embeddingTypes: List<String>? = null,
    @SerialName("images")
    val images: List<String>? = null // Only one of texts or images is supported
)

/** RESPONSE SCHEMA FOR COHERE EMBED */
@Serializable
internal data class CohereEmbedResponse(
    @SerialName("id")
    val id: String? = null,
    @SerialName("response_type")
    val responseType: String? = null,
    @SerialName("embeddings")
    val embeddings: Map<String, List<List<Double>>>? = null, // "float", "int8", etc.: list of embeddings, one per input
    @SerialName("texts")
    val texts: List<String>? = null
)

internal object BedrockCohereSerialization {
    private val json = Json { ignoreUnknownKeys = true }

    /** Create request JSON for embeddings (text only). */
    fun createV3TextRequest(
        texts: List<String>,
        inputType: String? = null,
        truncate: String? = null,
        embeddingTypes: List<String>? = null
    ): String = json.encodeToString(
        CohereEmbedRequest(
            texts = texts,
            inputType = inputType,
            truncate = truncate,
            embeddingTypes = embeddingTypes,
            images = null
        )
    )

    /** Parse a response into a CohereEmbedResponse object. */
    fun parseResponse(responseBody: String): CohereEmbedResponse =
        json.decodeFromString(responseBody)

    /**
     * Get embedding vectors of a chosen type (e.g., "float") for all texts.
     * By default, returns "float" embeddings.
     * Throws if none found.
     */
    fun extractEmbeddings(
        response: CohereEmbedResponse,
        type: String = "float"
    ): List<List<Double>> =
        response.embeddings?.get(type)
            ?: error("No embedding type '$type' found in Cohere response. Available: ${response.embeddings?.keys}")

    /**
     * Helper to get the first set of "float" embeddings for single text input.
     */
    fun extractFirstFloatEmbedding(response: CohereEmbedResponse): List<Double> =
        extractEmbeddings(response, type = "float").firstOrNull()
            ?: error("No embedding found in Cohere response")
}
