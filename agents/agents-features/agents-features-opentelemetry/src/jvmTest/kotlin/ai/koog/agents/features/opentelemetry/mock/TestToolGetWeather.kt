package ai.koog.agents.features.opentelemetry.mock

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

internal object TestGetWeatherTool : SimpleTool<TestGetWeatherTool.Args>() {

    const val DEFAULT_PARIS_RESULT: String = "rainy, 57°F"
    const val DEFAULT_LONDON_RESULT: String = "cloudy, 62°F"

    @Serializable
    data class Args(val location: String)

    override val argsSerializer: KSerializer<Args> = Args.serializer()

    override val descriptor: ToolDescriptor = ToolDescriptor(
        name = "Get whether",
        description = "The test tool to get a whether based on provided location.",
        requiredParameters = listOf(
            ToolParameterDescriptor(
                name = "location",
                description = "Whether location",
                type = ToolParameterType.String
            )
        )
    )

    override suspend fun doExecute(args: Args): String =
        if (args.location.contains("Paris")) {
            DEFAULT_PARIS_RESULT
        } else if (args.location.contains("London")) {
            DEFAULT_LONDON_RESULT
        } else {
            DEFAULT_PARIS_RESULT
        }
}
