package ai.koog.integration.tests.utils.tools

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.annotations.LLMDescription
import kotlinx.serialization.Serializable

object GeographyQueryTool : SimpleTool<GeographyQueryTool.Args>(
    argsSerializer = Args.serializer(),
    name = "geography_query_tool",
    description = "A tool for retrieving geographical information such as capitals of countries"
) {
    @Serializable
    data class Args(
        @property:LLMDescription("The geographical query (e.g., 'capital of France')")
        val query: String,
        @property:LLMDescription("The language code to return the response in (e.g., 'en', 'fr')")
        val language: String? = null
    )

    override suspend fun execute(args: Args): String {
        return "Geography query processed: ${args.query}, language: ${args.language ?: "not specified"}"
    }
}
