package ai.koog.prompt.executor.clients.openai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Request model for OpenAI moderation API.
 *
 * @property input The input to moderate. Can be a string, an array of strings, or an array of multi-modal input objects.
 * @property model The moderation model to use (optional).
 */
@Serializable
internal data class OpenAIModerationRequest(
    @Serializable(with = ContentSerializer::class)
    val input: Content?,
    val model: String? = null
)

/**
 * Response model for OpenAI moderation API.
 *
 * @property id The unique identifier for the moderation request.
 * @property model The model used to generate the moderation results.
 * @property results A list of moderation results.
 */
@Serializable
internal data class OpenAIModerationResponse(
    val id: String,
    val model: String,
    val results: List<OpenAIModerationResult>
)

/**
 * Represents a single moderation result from OpenAI.
 *
 * @property flagged Whether any of the categories are flagged.
 * @property categories A map of category names to boolean values indicating whether each category is flagged.
 * @property categoryScores A map of category names to scores as predicted by the model.
 * @property categoryAppliedInputTypes A map of category names to lists of input types that the score applies to.
 */
@Serializable
internal data class OpenAIModerationResult(
    val flagged: Boolean,
    val categories: OpenAIModerationCategories,
    @SerialName("category_scores")
    val categoryScores: OpenAIModerationCategoryScores,
    @SerialName("category_applied_input_types")
    val categoryAppliedInputTypes: OpenAIModerationCategoryAppliedInputTypes? = null
)

/**
 * Represents the categories in a moderation result.
 */
@Serializable
internal data class OpenAIModerationCategories(
    val harassment: Boolean,
    @SerialName("harassment/threatening") val harassmentThreatening: Boolean,
    val hate: Boolean,
    @SerialName("hate/threatening") val hateThreatening: Boolean,
    val sexual: Boolean,
    @SerialName("sexual/minors") val sexualMinors: Boolean,
    val violence: Boolean,
    @SerialName("violence/graphic") val violenceGraphic: Boolean,
    @SerialName("self-harm") val selfHarm: Boolean,
    @SerialName("self-harm/intent") val selfHarmIntent: Boolean,
    @SerialName("self-harm/instructions") val selfHarmInstructions: Boolean,
    val illicit: Boolean? = null,
    @SerialName("illicit/violent") val illicitViolent: Boolean? = null
)

/**
 * Represents the category scores in a moderation result.
 */
@Serializable
internal data class OpenAIModerationCategoryScores(
    val harassment: Double,
    @SerialName("harassment/threatening") val harassmentThreatening: Double,
    val hate: Double,
    @SerialName("hate/threatening") val hateThreatening: Double,
    val sexual: Double,
    @SerialName("sexual/minors") val sexualMinors: Double,
    val violence: Double,
    @SerialName("violence/graphic") val violenceGraphic: Double,
    @SerialName("self-harm") val selfHarm: Double,
    @SerialName("self-harm/intent") val selfHarmIntent: Double,
    @SerialName("self-harm/instructions") val selfHarmInstructions: Double,
    val illicit: Double? = null,
    @SerialName("illicit/violent") val illicitViolent: Double? = null
)

/**
 * Represents the input types that each category score applies to in a moderation result.
 * This is used to indicate whether a category was flagged based on text, image, or both.
 */
@Serializable
internal data class OpenAIModerationCategoryAppliedInputTypes(
    val harassment: List<String>? = null,
    @SerialName("harassment/threatening") val harassmentThreatening: List<String>? = null,
    val hate: List<String>? = null,
    @SerialName("hate/threatening") val hateThreatening: List<String>? = null,
    val sexual: List<String>? = null,
    @SerialName("sexual/minors") val sexualMinors: List<String>? = null,
    val violence: List<String>? = null,
    @SerialName("violence/graphic") val violenceGraphic: List<String>? = null,
    @SerialName("self-harm") val selfHarm: List<String>? = null,
    @SerialName("self-harm/intent") val selfHarmIntent: List<String>? = null,
    @SerialName("self-harm/instructions") val selfHarmInstructions: List<String>? = null,
    val illicit: List<String>? = null,
    @SerialName("illicit/violent") val illicitViolent: List<String>? = null
)
