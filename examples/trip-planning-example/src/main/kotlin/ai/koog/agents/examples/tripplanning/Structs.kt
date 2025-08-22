package ai.koog.agents.examples.tripplanning

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.ext.agent.SubgraphResult
import ai.koog.prompt.markdown.markdown
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.serialization.Serializable

data class UserInput(
    val message: String,
    val currentDate: LocalDate,
    val timezone: TimeZone,
)


sealed interface SuggestPlanRequest {
    data class InitialRequest(
        val userPlan: TripPlan,
    ) : SuggestPlanRequest

    data class CorrectionRequest(
        val userPlan: TripPlan,
        val userFeedback: String,
        val prevSuggestedPlan: TripPlan,
    ) : SuggestPlanRequest
}

@Serializable
@LLMDescription("User feedback for the plan suggestion.")
data class PlanSuggestionFeedback(
    @property:LLMDescription("Whether the plan suggestion is accepted.")
    val isAccepted: Boolean,
    @property:LLMDescription("The original message from the user.")
    val message: String,
)


@Serializable
data class TripPlan(
    val steps: List<Step>,
) : SubgraphResult {
    @Serializable
    data class Step(
        val location: String,
        val countryCodeISO2: String? = null,
        val fromDate: LocalDate,
        val toDate: LocalDate,
        val description: String
    )

    override fun toStringDefault(): String = markdown {
        h1("Plan:")
        br()

        steps.forEach { step ->
            h2("${step.location}, ${step.fromDate} - ${step.toDate}")
            +step.description
            br()
        }
    }
}