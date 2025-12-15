package ai.koog.agents.features.opentelemetry.mock

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.annotations.LLMDescription
import kotlinx.serialization.Serializable

internal object TestGetWeatherTool : SimpleTool<TestGetWeatherTool.Args>(
    argsSerializer = Args.serializer(),
    name = "Get whether",
    description = "The test tool to get a whether based on provided location."
) {
    const val DEFAULT_PARIS_RESULT: String = "rainy, 57°F"
    const val DEFAULT_LONDON_RESULT: String = "cloudy, 62°F"

    @Serializable
    data class Args(
        @property:LLMDescription("Whether location")
        val location: String
    )

    override suspend fun execute(args: Args): String =
        if (args.location.contains("Paris")) {
            DEFAULT_PARIS_RESULT
        } else if (args.location.contains("London")) {
            DEFAULT_LONDON_RESULT
        } else {
            DEFAULT_PARIS_RESULT
        }
}
