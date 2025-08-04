package ai.koog.prompt.executor.ollama.client

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.prompt.executor.ollama.tools.json.toJSONSchema
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class JSONSchemaFunctionConverterTest {
    val json = Json {
        prettyPrint = true
    }

    @Test
    fun `test function definition json schema generation`() {
        val toolDescriptor = ToolDescriptor(
            name = "weather_forecast_result",
            description = """
                Finish tool to compile final weather forecast results for the user's requested locations and dates.
                Call to provide the final forecast results.
            """.trimIndent(),
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "locations",
                    description = "The list of locations and their forecasts",
                    type = ToolParameterType.List(
                        ToolParameterType.Object(
                            properties = listOf(
                                ToolParameterDescriptor(
                                    name = "name",
                                    description = "The name of the location",
                                    type = ToolParameterType.String
                                ),
                                ToolParameterDescriptor(
                                    name = "forecasts",
                                    description = "The list of forecasts for the location",
                                    type = ToolParameterType.List(
                                        ToolParameterType.Object(
                                            properties = listOf(
                                                ToolParameterDescriptor(
                                                    name = "date",
                                                    description = "The date of the forecast",
                                                    type = ToolParameterType.String
                                                ),
                                                ToolParameterDescriptor(
                                                    name = "forecastDescription",
                                                    description = "The forecast description",
                                                    type = ToolParameterType.String
                                                )
                                            ),
                                            requiredProperties = listOf("date", "forecastDescription")
                                        )
                                    )
                                )
                            ),
                            requiredProperties = listOf("name", "forecasts")
                        )
                    ),
                )
            ),
        )

        val generatedSchema = json.encodeToString(toolDescriptor.toJSONSchema())

        val expectedSchema = """
        {
            "title": "weather_forecast_result",
            "description": "Finish tool to compile final weather forecast results for the user's requested locations and dates.\nCall to provide the final forecast results.",
            "type": "object",
            "properties": {
                "locations": {
                    "type": "array",
                    "items": {
                        "type": "object",
                        "properties": {
                            "name": {
                                "type": "string",
                                "description": "The name of the location"
                            },
                            "forecasts": {
                                "type": "array",
                                "items": {
                                    "type": "object",
                                    "properties": {
                                        "date": {
                                            "type": "string",
                                            "description": "The date of the forecast"
                                        },
                                        "forecastDescription": {
                                            "type": "string",
                                            "description": "The forecast description"
                                        }
                                    },
                                    "required": [
                                        "date",
                                        "forecastDescription"
                                    ]
                                },
                                "description": "The list of forecasts for the location"
                            }
                        },
                        "required": [
                            "name",
                            "forecasts"
                        ]
                    },
                    "description": "The list of locations and their forecasts"
                }
            },
            "required": [
                "locations"
            ]
        }
        """.trimIndent()

        assertEquals(expectedSchema, generatedSchema)
    }
}
