package ai.koog.prompt.dsl

import kotlinx.serialization.Serializable

/**
 * Represents categories for content moderation used to classify potentially harmful or inappropriate content.
 * These categories help identify specific types of violations that content may fall under.
 */
@Serializable
public open class ModerationCategory(
    public val name: String
) {

    /**
     * Compares this object with another for equality. Two instances of [ModerationCategory] are equal if their [name] is equal.
     *
     * @param other The object to compare with this instance.
     * @return `true` if the specified object is a `ModerationCategory` and has the same `name`; `false` otherwise.
     */
    override fun equals(other: Any?): Boolean {
        return other is ModerationCategory && other.name == name
    }

    /**
     * Represents the "Harassment" moderation category.
     *
     * This category is used to flag content that involves intimidation, bullying, or other behaviors
     * directed towards individuals or groups with the intent to harass or demean.
     *
     * Content flagged under this category may exhibit harmful or abusive language, intended to provoke
     * or distress others.
     */
    public object Harassment : ModerationCategory("harassment")

    /**
     * Represents the category of moderation specifically focused on identifying content
     * that involves harassment with a threatening nature.
     *
     * This includes harmful interactions or communications that are intended to intimidate,
     * coerce, or threaten individuals or groups.
     */
    public object HarassmentThreatening : ModerationCategory("harassment/threatening")

    /**
     * Represents content categorized as hate speech or related material.
     *
     * The HATE category is used to denote content that contains elements perceived
     * as offensive, discriminatory, or expressing hatred towards individuals
     * or groups based on attributes such as race, religion, gender, or other characteristics.
     * This designation is typically used in content moderation and classification
     * systems to identify and flag harmful material.
     */
    public object Hate : ModerationCategory("hate")

    /**
     * Represents the HATE_THREATENING moderation category.
     *
     * This category identifies content that exhibits hateful behavior combined with direct or indirect threats.
     * The HATE_THREATENING category is a specific subset of hate-related moderation, focusing on harmful content
     * that not only spreads hate but also includes threatening language, behavior, or implications.
     */
    public object HateThreatening : ModerationCategory("hate/threatening")

    /**
     * Represents the moderation category for content that may involve illegal or illicit activities.
     * This category is used to identify content that violates legal frameworks or ethical guidelines.
     */
    public object Illicit : ModerationCategory("illicit")

    /**
     * Represents content classified as both illicit and violent in nature.
     *
     * This category is used for moderation purposes where content involves a combination of illegal
     * or illicit activities with elements of violence. The classification may include materials
     * that promote or depict violent actions involving unlawful or prohibited activities.
     */
    public object IllicitViolent : ModerationCategory("illicit/violent")

    /**
     * Represents the "SELF_HARM" moderation category.
     * This category is used to identify content that pertains to self-harm or related behavior.
     */
    public object SelfHarm : ModerationCategory("self-harm")

    /**
     * Represents content that explicitly indicates an intent of self-harm.
     *
     * This category is used in moderation to evaluate material that contains expressions
     * or indications of an individual's intent to harm themselves.
     */
    public object SelfHarmIntent : ModerationCategory("self-harm/intent")

    /**
     * Represents the moderation category for instructions or content that encourages or promotes self-harm.
     *
     * This category is used to flag content that provides guidance, techniques, or encouragement for
     * engaging in self-harm behaviors. It helps in detecting and preventing the spread of harmful
     * instructional content.
     */
    public object SelfHarmInstructions : ModerationCategory("self-harm/instructions")

    /**
     * Represents content categorized as sexual in nature.
     *
     * This category is used to flag or identify content that is sexually explicit
     * or contains sexual references. It is often utilized in content moderation
     * systems to ensure compliance with guidelines and to prevent inappropriate
     * or harmful material from being disseminated.
     */
    public object Sexual : ModerationCategory("sexual")

    /**
     * Represents content related to sexual material involving minors.
     *
     * This category is used for moderation purposes to flag and handle inappropriate or harmful content
     * concerning the exploitation, abuse, or endangerment of minors in a sexual context.
     */
    public object SexualMinors : ModerationCategory("sexual/minors")

    /**
     * Represents the category of content classified as violent behavior or actions.
     *
     * This moderation category is used to identify content that promotes, incites,
     * or depicts violence and physical harm towards individuals or groups.
     */
    public object Violence : ModerationCategory("violence")

    /**
     * Represents the VIOLENCE_GRAPHIC moderation category.
     *
     * This category pertains to content that includes graphic depictions of violence,
     * which may be harmful, distressing, or triggering to viewers.
     *
     * It is used to classify and moderate content that explicitly involves detailed
     * graphic or extreme visual elements of violent acts or scenes.
     */
    public object ViolenceGraphic : ModerationCategory("violence/graphic")

    /**
     * Responses that are both verifiably false and likely to injure a living person’s reputation
     * */
    public object Defamation : ModerationCategory("defamation")

    /**
     * Responses that contain specialized financial, medical, or legal advice, or that indicate dangerous activities or objects are safe
     * */
    public object SpecializedAdvice : ModerationCategory("specialized advice ")

    /**
     * Responses that contain sensitive, nonpublic personal information that could undermine someone’s physical, digital, or financial security
     * */
    public object Privacy : ModerationCategory("privacy")

    /**
     * Responses that may violate the intellectual property rights of any third party
     * */
    public object IntellectualProperty : ModerationCategory("intellectual property")

    /**
     * Responses that contain factually incorrect information about electoral systems and processes, including in the time, place, or manner of voting in civic elections
     * */
    public object ElectionsMisinformation : ModerationCategory("elections")

    /**
     * Represents a predefined moderation category for cases associated with misconduct.
     *
     * Note: Used in AWS Bedrock filters
     */
    public object Misconduct : ModerationCategory("misconduct")

    /**
     * Represents a specific moderation category for identifying and handling potential prompt attacks.
     *
     * Note: Used in AWS Bedrock filters
     */
    public object PromptAttack : ModerationCategory("prompt-attack")

    override fun hashCode(): Int {
        return name.hashCode()
    }
}

/**
 * Represents the detailed result of moderating a specific category of content.
 *
 * @property detected Indicates whether the category was flagged in the moderation process.
 * @property confidenceScore The confidence score of the detected category, where higher values indicate stronger confidence.
 * @property appliedInputTypes A list of input types (e.g., text, image) to which the moderation result applies.
 */
@Serializable
public data class ModerationCategoryResult(
    val detected: Boolean,
    val confidenceScore: Double? = null,
    val appliedInputTypes: List<ModerationResult.InputType> = emptyList()
)

/**
 * Represents the result of a content moderation request.
 *
 * @property isHarmful Whether the content is classified as harmful (i.e. any of the categories are flagged).
 * @property categories A map of ModerationCategory objects to [ModerationCategoryResult] values indicating whether
 *  each category is flagged with metainformation about assessment.
 */
@Serializable
public data class ModerationResult(
    val isHarmful: Boolean,
    val categories: Map<ModerationCategory, ModerationCategoryResult>
) {

    /**
     * A list of moderation categories that have been flagged as detected in the moderation result.
     *
     * Used to identify the specific types of violations found in the moderated content.
     */
    public val violatedCategories: List<ModerationCategory> = categories.filter { it.value.detected }.keys.toList()

    /**
     * Checks if the specified moderation category is flagged as detected in the moderation result.
     *
     * @param category The moderation category to verify within the current moderation result.
     * @return `true` if the specified category is flagged as detected; `false` otherwise.
     */
    public fun violatesCategory(category: ModerationCategory): Boolean {
        return categories[category]?.detected == true
    }

    /**
     * Evaluates whether the content violates any of the specified moderation categories.
     *
     * @param checkedCategories A variable number of [ModerationCategory] objects to check
     *        if the content is flagged in any of these categories.
     * @return `true` if any of the specified categories are flagged as detected,
     *         `false` otherwise.
     */
    public fun violatesOneOf(vararg checkedCategories: ModerationCategory): Boolean {
        return checkedCategories.any { categories[it]?.detected == true }
    }

    /**
     * Checks if all the provided moderation categories are violated in this moderation result.
     *
     * This method evaluates whether all specified categories have been flagged as "detected"
     * within the current moderation result's category map.
     *
     * @param checkedCategories A variable number of [ModerationCategory] objects representing
     * the categories to check for violations.
     * @return `true` if all provided categories are flagged as detected; `false` otherwise.
     */
    public fun violatesAll(vararg checkedCategories: ModerationCategory): Boolean {
        return checkedCategories.all { categories[it]?.detected == true }
    }

    /**
     * Represents the type of input provided for content moderation.
     *
     * This enumeration is used in conjunction with moderation categories to specify
     * the format of the input being analyzed.
     */
    @Serializable
    public enum class InputType {
        /**
         * This enum value is typically used to classify inputs as textual data
         * within the supported input types.
         */
        TEXT,

        /**
         * Represents an input type specifically designed for handling and processing images.
         * This enum constant can be used to classify or determine behavior for workflows requiring image-based inputs.
         */
        IMAGE,
    }
}
