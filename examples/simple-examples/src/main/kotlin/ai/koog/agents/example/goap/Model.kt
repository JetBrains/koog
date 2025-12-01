package ai.koog.agents.example.goap

import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.params.LLMParams
import kotlinx.serialization.Serializable

/**
 * A logical message to be evaluated
 *
 * @param id id of the message, in case we have variants
 * @param content Content of the message, such as "smoking is bad"
 * @param objective Objective
 * @param deliverable what we want: e.g., a slogan or a blog
 */
class Message(
    val id: String,
    val content: String,
    val objective: String,
    val deliverable: String
) {
    override fun toString() =
        """
            Message is $content
            Objective is $objective
            The deliverable result is $deliverable
        """.trimIndent()
}

/**
 * Particular wording of a message
 *
 * @param wording Particular wording, such as "smoking is a health hazard"
 * @param score Score of the wording, e.g., 0.99
 */
data class RatedWording(
    val wording: String,
    val score: Double,
) {
    override fun toString() = "$wording: %.2f".format(score)
}

data class BestWordings(
    val wordings: List<RatedWording> = emptyList()
) {
    fun add(newWordings: List<RatedWording>, maxWordingsToStore: Int) =
        copy(wordings = (wordings + newWordings).sortedByDescending { it.score }.take(maxWordingsToStore))

    fun best(minScore: Double = 0.0) = wordings.filter { it.score >= minScore }
    fun best(n: Int, minScore: Double = 0.0) = wordings.take(n).filter { it.score >= minScore }
    fun show(n: Int) = best(n).joinToString("\n")
}

@Serializable
enum class LikertRating(val score: Double) {
    STRONGLY_DISAGREE(0.0),
    DISAGREE(0.25),
    NEUTRAL(0.5),
    AGREE(0.75),
    STRONGLY_AGREE(1.0)
}

/**
 * Participant in a focus group
 */
class Persona(
    val id: String,
    val name: String,
    val identity: String,
    val llModel: LLModel,
    val llmParams: LLMParams,
    val weight: Double = 1.0
)

class FocusGroup(
    val participants: List<Persona>
) {
    private val weights: List<Double>

    init {
        val totalWeight = participants.sumOf { it.weight }
        weights = participants.map { it.weight / totalWeight }
    }

    fun score(ratings: List<LikertRating>) =
        weights.zip(ratings).sumOf { (weight, rating) -> weight * rating.score }

    fun presentFeedback(reactions: List<Reaction>) =
        participants.zip(reactions).map { (participant, reaction) -> "${participant.name}: ${reaction.feedback}" }
}

class Creatives(
    private val creatives: List<Persona>
) {
    fun nextCreative(): Persona = creatives.random()
}

@Serializable
data class Reaction(
    val feedback: String,
    val ratings: List<LikertRating>
) {
    companion object {
        fun default(n: Int) = Reaction("", (1..n).map { LikertRating.STRONGLY_AGREE })
    }
}

@Serializable
data class Proposal(
    val learnings: String,
    val wordings: List<String>
) {
    companion object {
        fun default(n: Int) = Proposal("", (1..n).map { "" })
    }
}
