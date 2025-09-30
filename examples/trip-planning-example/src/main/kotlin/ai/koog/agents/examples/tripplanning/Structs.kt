package ai.koog.agents.examples.tripplanning

import ai.koog.agents.core.tools.annotations.LLMDescription
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
@LLMDescription(
    "Finish tool to compile final plan suggestion for the user's request. \n" +
        "Call to provide the final plan suggestion result."
)
data class TripPlan(
    @property:LLMDescription("The steps in the user travel plan.")
    val steps: List<Step>,
) {
    @Serializable
    @LLMDescription("The steps in the user travel plan.")
    data class Step(
        @property:LLMDescription("The location of the destination (e.g. city name)")
        val location: String,
        @property:LLMDescription("ISO 3166-1 alpha-2 country code of the location (e.g. US, GB, FR).")
        val countryCodeISO2: String? = null,
        @property:LLMDescription("Start date when the user arrives in this location in the ISO format, e.g. 2022-01-01.")
        val fromDate: LocalDate,
        @property:LLMDescription("End date when the user leaves this location in the ISO format, e.g. 2022-01-01.")
        val toDate: LocalDate,
        @property:LLMDescription("More information about this step from the plan")
        val description: String
    )

    fun toMarkdownString(): String = markdown {
        h1("Plan:")
        br()

        steps.forEach { step ->
            h2("${step.location}, ${step.fromDate} - ${step.toDate}")
            +step.description
            br()
        }
    }
}
