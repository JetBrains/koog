package ai.koog.prompt.executor.clients.bedrock.modelfamilies.cohere

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

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
    val inputType: String,
    @SerialName("truncate")
    val truncate: String? = null,
    @SerialName("embedding_types")
    val embeddingTypes: List<String>? = null,
    @SerialName("images")
    val images: List<String>? = null
)

/** RESPONSE SCHEMA FOR COHERE EMBED */
@Serializable
internal data class CohereEmbedResponse(
    @SerialName("id")
    val id: String? = null,
    @SerialName("response_type")
    val responseType: String? = null,
    @SerialName("embeddings")
    val embeddings: List<List<Double>>? = null,
    @SerialName("texts")
    val texts: List<String>? = null
)

internal object BedrockCohereSerialization {
    private val json = Json { ignoreUnknownKeys = true }

    /** Create request JSON for embeddings (text only). */
    fun createV3TextRequest(
        texts: List<String>,
        inputType: String = "search_document",
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
     * Returns the embeddings from the response.
     * For Bedrock Cohere v3 API, this is a list of embedding vectors (one per input text).
     *
     * @param response The Cohere embedding response.
     * @return A list of embedding vectors (List<List<Double>>). Each inner list is one embedding vector.
     * @throws IllegalStateException if no embeddings are found in the response.
     */
    fun extractEmbeddings(response: CohereEmbedResponse): List<List<Double>> =
        response.embeddings?.takeIf { it.isNotEmpty() }
            ?: error("No embeddings found in Cohere response")
}
