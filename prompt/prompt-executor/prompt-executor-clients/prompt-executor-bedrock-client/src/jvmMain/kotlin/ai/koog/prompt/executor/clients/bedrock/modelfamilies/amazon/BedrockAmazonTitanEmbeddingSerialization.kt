package ai.koog.prompt.executor.clients.bedrock.modelfamilies.amazon

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Amazon Titan Embedding Models (G1 and V2) - Serialization utilities.
 *
 * Documentation: https://docs.aws.amazon.com/bedrock/latest/userguide/model-parameters-titan-embed-text.html
 */

/** REQUEST for G1 ("amazon.titan-embed-text-v1") */
@Serializable
internal data class TitanEmbedTextG1Request(
    @SerialName("inputText")
    val inputText: String
)

/** RESPONSE for G1 */
@Serializable
internal data class TitanEmbedTextG1Response(
    @SerialName("embedding")
    val embedding: List<Double>,
    @SerialName("inputTextTokenCount")
    val inputTextTokenCount: Int
)

/** REQUEST for V2 ("amazon.titan-embed-text-v2:0") */
@Serializable
internal data class TitanEmbedTextV2Request(
    @SerialName("inputText")
    val inputText: String,
    @SerialName("dimensions")
    val dimensions: Int? = null,
    @SerialName("normalize")
    val normalize: Boolean? = null,
    @SerialName("embeddingTypes")
    val embeddingTypes: List<String>? = null
)

/** RESPONSE for V2 */
@Serializable
internal data class TitanEmbedTextV2Response(
    @SerialName("embedding")
    val embedding: List<Double>? = null,
    @SerialName("inputTextTokenCount")
    val inputTextTokenCount: Int,
    @SerialName("embeddingsByType")
    val embeddingsByType: Map<String, List<Double>>? = null
)

internal object BedrockAmazonTitanEmbeddingSerialization {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Build JSON string for a G1 request.
     */
    fun createG1Request(text: String): String =
        json.encodeToString(TitanEmbedTextG1Request(inputText = text))

    /**
     * Build JSON string for a V2 request.
     * All additional params (dimensions, normalize, embeddingTypes) are optional and can be null to omit.
     */
    fun createV2Request(
        text: String,
        dimensions: Int? = null,
        normalize: Boolean? = null,
        embeddingTypes: List<String>? = null
    ): String = json.encodeToString(
        TitanEmbedTextV2Request(
            inputText = text,
            dimensions = dimensions,
            normalize = normalize,
            embeddingTypes = embeddingTypes
        )
    )

    /**
     * Parse a G1 response given raw JSON string.
     */
    fun parseG1Response(responseJson: String): TitanEmbedTextG1Response =
        json.decodeFromString<TitanEmbedTextG1Response>(responseJson)

    /**
     * Parse a V2 response given raw JSON string.
     */
    fun parseV2Response(responseJson: String): TitanEmbedTextV2Response =
        json.decodeFromString<TitanEmbedTextV2Response>(responseJson)

    /**
     * Helper to get the embedding result (always float) transparently for V2:
     * Uses .embedding if present, else use embeddingsByType["float"]
     *
     * NOTE: This function currently only extracts the "float" embedding type,
     * since Vectors in Koog currently only support floating-point values.
     * As and when it is extended to support binary or int8/uint8 embeddings,
     * this function can be adapted to return additional or alternate types from embeddingsByType.
     */
    fun extractV2Embedding(response: TitanEmbedTextV2Response): List<Double> =
        response.embedding
            ?: response.embeddingsByType?.get("float")
            ?: error("No float embedding found in Titan V2 response")
}
