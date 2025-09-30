package ai.koog.integration.tests.tools

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.annotations.LLMDescription
import kotlinx.serialization.Serializable

object GeographyQueryTool : SimpleTool<GeographyQueryTool.Args>() {
    @Serializable
    data class Args(
        @property:LLMDescription("The geographical query (e.g., 'capital of France')")
        val query: String,
        @property:LLMDescription("The language code to return the response in (e.g., 'en', 'fr')")
        val language: String? = null
    )

    override val argsSerializer = Args.serializer()

    override val name: String = "geography_query_tool"
    override val description: String = "A tool for retrieving geographical information such as capitals of countries"

    override suspend fun doExecute(args: Args): String {
        return "Geography query processed: ${args.query}, language: ${args.language ?: "not specified"}"
    }
}
