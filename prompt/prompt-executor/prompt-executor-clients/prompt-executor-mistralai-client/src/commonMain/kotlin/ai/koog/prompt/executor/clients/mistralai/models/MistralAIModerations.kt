package ai.koog.prompt.executor.clients.mistralai.models

import ai.koog.prompt.executor.clients.openai.base.models.Content
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Request model for MistralAI moderation API.
 *
 * @property model ID of the model to use.
 * @property input Text to classify. Can be a string, an array of strings, or conversational messages.
 */
@Serializable
internal class MistralAIModerationRequest(
    val model: String,
    val input: Content,
)

/**
 * Response model for MistralAI moderation API.
 *
 * @property id The unique identifier for the moderation request.
 * @property model The model used to generate the moderation results.
 * @property results A list of moderation results.
 */
@Serializable
internal data class MistralAIModerationResponse(
    val id: String,
    val model: String,
    val results: List<MistralAIModerationResult>
)

/**
 * Represents a single moderation result from MistralAI.
 *
 * @property categories Boolean flags indicating whether each category is flagged.
 * @property categoryScores Scores (0.0 to 1.0) for each category as predicted by the model.
 */
@Serializable
internal data class MistralAIModerationResult(
    val categories: MistralAIModerationCategories,
    val categoryScores: MistralAIModerationCategoryScores,
)

/**
 * MistralAI moderation categories with boolean flags.
 *
 * @property sexual Material that explicitly depicts, describes, or promotes sexual activities, nudity, or sexual services.
 * @property hateAndDiscrimination Content that expresses prejudice, hostility, or advocates discrimination based on protected characteristics.
 * @property violenceAndThreats Content that describes, glorifies, incites, or threatens physical violence.
 * @property dangerousAndCriminalContent Content that promotes or provides instructions for illegal activities or extremely hazardous behaviors.
 * @property selfharm Content that promotes, instructs, plans, or encourages deliberate self-injury, suicide, or eating disorders.
 * @property health Content that contains or tries to elicit detailed or tailored medical advice.
 * @property financial Content that contains or tries to elicit detailed or tailored financial advice.
 * @property law Content that contains or tries to elicit detailed or tailored legal advice.
 * @property pii Content that requests, shares, or attempts to elicit personal identifying information.
 */
@Serializable
internal data class MistralAIModerationCategories(
    val sexual: Boolean,
    @SerialName("hate_and_discrimination")
    val hateAndDiscrimination: Boolean,
    @SerialName("violence_and_threats")
    val violenceAndThreats: Boolean,
    @SerialName("dangerous_and_criminal_content")
    val dangerousAndCriminalContent: Boolean,
    val selfharm: Boolean,
    val health: Boolean,
    val financial: Boolean,
    val law: Boolean,
    val pii: Boolean,
)

/**
 * MistralAI moderation category scores.
 *
 * @property sexual Score for sexual content (0.0 to 1.0).
 * @property hateAndDiscrimination Score for hate and discrimination content (0.0 to 1.0).
 * @property violenceAndThreats Score for violence and threats content (0.0 to 1.0).
 * @property dangerousAndCriminalContent Score for dangerous and criminal content (0.0 to 1.0).
 * @property selfharm Score for self-harm content (0.0 to 1.0).
 * @property health Score for health-related content (0.0 to 1.0).
 * @property financial Score for financial advice content (0.0 to 1.0).
 * @property law Score for legal advice content (0.0 to 1.0).
 * @property pii Score for personal identifying information content (0.0 to 1.0).
 */
@Serializable
internal data class MistralAIModerationCategoryScores(
    val sexual: Double,
    @SerialName("hate_and_discrimination")
    val hateAndDiscrimination: Double,
    @SerialName("violence_and_threats")
    val violenceAndThreats: Double,
    @SerialName("dangerous_and_criminal_content")
    val dangerousAndCriminalContent: Double,
    val selfharm: Double,
    val health: Double,
    val financial: Double,
    val law: Double,
    val pii: Double,
)
