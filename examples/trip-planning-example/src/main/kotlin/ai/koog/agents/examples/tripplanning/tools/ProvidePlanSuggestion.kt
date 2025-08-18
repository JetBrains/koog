package ai.koog.agents.examples.tripplanning.tools

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.agents.examples.tripplanning.TripPlan
import ai.koog.agents.ext.agent.ProvideSubgraphResult

object ProvidePlanSuggestion : ProvideSubgraphResult<TripPlan>() {
    override val argsSerializer = TripPlan.serializer()

    // TODO: will be available out-of-the-box since Koog 0.4.0 / 0.5.0
    override val descriptor = ToolDescriptor(
        name = "provide_plan_suggestion",
        description = """
            Finish tool to compile final plan suggestion for the user's request.
            Call to provide the final plan suggestion result.
        """.trimIndent(),
        requiredParameters = listOf(
            ToolParameterDescriptor(
                name = "steps",
                description = "The steps in the suggested plan, corresponding to the steps in the plan from the user's request",
                type = ToolParameterType.List(
                    ToolParameterType.Object(
                        properties = listOf(
                            ToolParameterDescriptor(
                                name = "location",
                                description = "The name of the location",
                                type = ToolParameterType.String
                            ),
                            ToolParameterDescriptor(
                                name = "countryCodeISO2",
                                description = "ISO 3166-1 alpha-2 country code of the location (e.g. US, GB, FR).",
                                type = ToolParameterType.String
                            ),
                            ToolParameterDescriptor(
                                name = "fromDate",
                                description = "Start date when the user arrives in this location in the ISO format, e.g. 2022-01-01",
                                type = ToolParameterType.String
                            ),
                            ToolParameterDescriptor(
                                name = "toDate",
                                description = "End date when the user leaves this location in the ISO format, e.g. 2022-01-01",
                                type = ToolParameterType.String
                            ),
                            ToolParameterDescriptor(
                                name = "description",
                                description = """
                                    Suggested list of places to visit for this step in the plan, in the markdown format.
                                    Should be a list of places with the name, location, link, review score and a short description.
                                    Should include a rationale for the selection of these activities (e.g. weather conditions or user preferences).
                                """.trimIndent(),
                                type = ToolParameterType.String
                            )
                        ),
                        requiredProperties = listOf("location", "countryCodeISO2", "fromDate", "toDate", "description")
                    )
                ),
            )
        ),
    )

    override suspend fun execute(args: TripPlan): TripPlan = args
}
