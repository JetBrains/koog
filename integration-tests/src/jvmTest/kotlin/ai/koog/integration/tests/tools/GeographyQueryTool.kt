package ai.koog.integration.tests.tools

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import kotlinx.serialization.Serializable

object GeographyQueryTool : SimpleTool<GeographyQueryTool.Args>() {
    @Serializable
    data class Args(
        val query: String,
        val language: String? = null
    )

    override val argsSerializer = Args.serializer()

    override val descriptor = ToolDescriptor(
        name = "geography_query_tool",
        description = "A tool for retrieving geographical information such as capitals of countries",
        requiredParameters = listOf(
            ToolParameterDescriptor(
                name = "query",
                description = "The geographical query (e.g., 'capital of France')",
                type = ToolParameterType.String
            )
        ),
        optionalParameters = listOf(
            ToolParameterDescriptor(
                name = "language",
                description = "The language code to return the response in (e.g., 'en', 'fr')",
                type = ToolParameterType.String
            )
        )
    )

    override suspend fun doExecute(args: Args): String {
        return "Geography query processed: ${args.query}, language: ${args.language ?: "not specified"}"
    }
}
