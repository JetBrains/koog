package ai.koog.agents.examples.tripplanning.tools

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.agents.examples.tripplanning.TripPlan
import ai.koog.agents.ext.agent.ProvideSubgraphResult

object ProvideUserPlan : ProvideSubgraphResult<TripPlan>() {
    override val argsSerializer = TripPlan.serializer()

    // TODO: will be available out-of-the-box since Koog 0.4.0 / 0.5.0
    override val descriptor = ToolDescriptor(
        name = "provide_user_plan",
        description = """
            Finish tool to compile the final user trip plan.
            Call to provide the user's requested trip plan.
        """.trimIndent(),
        requiredParameters = listOf(
            ToolParameterDescriptor(
                name = "steps",
                description = "The steps in the user travel plan.",
                type = ToolParameterType.List(
                    ToolParameterType.Object(
                        properties = listOf(
                            ToolParameterDescriptor(
                                name = "location",
                                description = "The location of the destination (e.g. city name)",
                                type = ToolParameterType.String
                            ),
                            ToolParameterDescriptor(
                                name = "countryCodeISO2",
                                description = "ISO 3166-1 alpha-2 country code of the location (e.g. US, GB, FR).",
                                type = ToolParameterType.String
                            ),
                            ToolParameterDescriptor(
                                name = "fromDate",
                                description = "Start date when the user arrives in this location in the ISO format, e.g. 2022-01-01.",
                                type = ToolParameterType.String
                            ),
                            ToolParameterDescriptor(
                                name = "toDate",
                                description = "End date when the user leaves this location in the ISO format, e.g. 2022-01-01.",
                                type = ToolParameterType.String
                            ),
                            ToolParameterDescriptor(
                                name = "description",
                                description = "More information about this step from the plan",
                                type = ToolParameterType.String
                            )
                        ),
                        requiredProperties = listOf(
                            "location",
                            "countryCodeISO2",
                            "fromDate",
                            "toDate",
                            "description"
                        )
                    )
                )
            )
        )
    )

    override suspend fun execute(args: TripPlan): TripPlan = args
}

