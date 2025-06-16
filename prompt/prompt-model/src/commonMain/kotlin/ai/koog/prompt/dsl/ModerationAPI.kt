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
}

/**
 * Represents the result of a content moderation request.
 *
 * @property model The model used to generate the moderation results.
 * @property isHarmful Whether the content is classified as harmful (i.e. any of the categories are flagged).
 * @property categories A map of ModerationCategory objects to boolean values indicating whether each category is flagged.
 * @property categoryScores A map of ModerationCategory objects to scores as predicted by the model.
 * @property categoryAppliedInputTypes A map of ModerationCategory objects to lists of input types that the score applies to.
 *                                    This is only populated for multi-modal inputs (e.g., text and images).
 */
@Serializable
public data class ModerationResult(
    val isHarmful: Boolean,
    val categories: Map<ModerationCategory, Boolean>,
    val categoryScores: Map<ModerationCategory, Double> = emptyMap(),
    val categoryAppliedInputTypes: Map<ModerationCategory, List<InputType>> = emptyMap()
) {
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