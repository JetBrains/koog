package ai.koog.prompt.executor.clients.openai.structure

import ai.koog.agents.core.tools.annotations.LLMDescription
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.ClassDiscriminatorMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import kotlin.test.Test
import kotlin.test.assertEquals

class OpenAIStandardJsonSchemaGeneratorTest {
    private val json = Json {
        prettyPrint = true
        explicitNulls = false
        isLenient = true
        ignoreUnknownKeys = true
        prettyPrintIndent = "  "
        classDiscriminator = "kind"
        classDiscriminatorMode = ClassDiscriminatorMode.POLYMORPHIC
    }

    private val fullGenerator = OpenAIStandardJsonSchemaGenerator

    @Serializable
    @SerialName("WeatherForecast")
    @LLMDescription("Weather forecast for a given location")
    data class WeatherForecast(
        @property:LLMDescription("Temperature in Celsius")
        val temperature: Int,
        @property:LLMDescription("Weather conditions (e.g., sunny, cloudy, rainy)")
        val conditions: String = "sunny",
        @property:LLMDescription("Chance of precipitation in percentage")
        val precipitation: Int?,
        @property:LLMDescription("Coordinates of the location")
        val latLon: LatLon,
        val pollution: Pollution,
        val alert: WeatherAlert,
        @property:LLMDescription("List of news articles")
        val news: List<WeatherNews>
    ) {
        @Serializable
        @SerialName("LatLon")
        data class LatLon(
            @property:LLMDescription("Latitude of the location")
            val lat: Double,
            @property:LLMDescription("Longitude of the location")
            val lon: Double
        )

        @Serializable
        @SerialName("WeatherNews")
        data class WeatherNews(
            @property:LLMDescription("Title of the news article")
            val title: String,
            @property:LLMDescription("Link to the news article")
            val link: String
        )

        @Suppress("unused")
        @SerialName("Pollution")
        @Serializable
        enum class Pollution {
            @SerialName("None")
            None,

            @SerialName("LOW")
            Low,

            @SerialName("MEDIUM")
            Medium,

            @SerialName("HIGH")
            High
        }

        @Suppress("unused")
        @Serializable
        @SerialName("WeatherAlert")
        sealed class WeatherAlert {
            abstract val severity: Severity
            abstract val message: String

            @Serializable
            @SerialName("Severity")
            enum class Severity { Low, Moderate, Severe, Extreme }

            @Serializable
            @SerialName("StormAlert")
            data class StormAlert(
                override val severity: Severity,
                override val message: String,
                @property:LLMDescription("Wind speed in km/h")
                val windSpeed: Double
            ) : WeatherAlert()

            @Serializable
            @SerialName("FloodAlert")
            data class FloodAlert(
                override val severity: Severity,
                override val message: String,
                @property:LLMDescription("Expected rainfall in mm")
                val expectedRainfall: Double
            ) : WeatherAlert()

            @Serializable
            @SerialName("TemperatureAlert")
            data class TemperatureAlert(
                override val severity: Severity,
                override val message: String,
                @property:LLMDescription("Temperature threshold in Celsius")
                val threshold: Int,
                @property:LLMDescription("Whether the alert is a heat warning")
                val isHeatWarning: Boolean
            ) : WeatherAlert()
        }
    }

    @Test
    fun testGenerateOpenAIStandardJsonSchemaWeatherForecast() {
        val result = fullGenerator.generate(json, "WeatherForecast", serializer<WeatherForecast>(), emptyMap())
        val schema = json.encodeToString(result.schema)

        val expectedSchema = """
            {
              "${"$"}id": "WeatherForecast",
              "${"$"}defs": {
                "LatLon": {
                  "type": "object",
                  "properties": {
                    "lat": {
                      "type": "number",
                      "description": "Latitude of the location"
                    },
                    "lon": {
                      "type": "number",
                      "description": "Longitude of the location"
                    }
                  },
                  "required": [
                    "lat",
                    "lon"
                  ],
                  "additionalProperties": false
                },
                "FloodAlert": {
                  "type": "object",
                  "properties": {
                    "severity": {
                      "type": "string",
                      "enum": [
                        "Low",
                        "Moderate",
                        "Severe",
                        "Extreme"
                      ]
                    },
                    "message": {
                      "type": "string"
                    },
                    "expectedRainfall": {
                      "type": "number",
                      "description": "Expected rainfall in mm"
                    },
                    "kind": {
                      "type": "string",
                      "enum": [
                        "FloodAlert"
                      ]
                    }
                  },
                  "required": [
                    "severity",
                    "message",
                    "expectedRainfall",
                    "kind"
                  ],
                  "additionalProperties": false
                },
                "StormAlert": {
                  "type": "object",
                  "properties": {
                    "severity": {
                      "type": "string",
                      "enum": [
                        "Low",
                        "Moderate",
                        "Severe",
                        "Extreme"
                      ]
                    },
                    "message": {
                      "type": "string"
                    },
                    "windSpeed": {
                      "type": "number",
                      "description": "Wind speed in km/h"
                    },
                    "kind": {
                      "type": "string",
                      "enum": [
                        "StormAlert"
                      ]
                    }
                  },
                  "required": [
                    "severity",
                    "message",
                    "windSpeed",
                    "kind"
                  ],
                  "additionalProperties": false
                },
                "TemperatureAlert": {
                  "type": "object",
                  "properties": {
                    "severity": {
                      "type": "string",
                      "enum": [
                        "Low",
                        "Moderate",
                        "Severe",
                        "Extreme"
                      ]
                    },
                    "message": {
                      "type": "string"
                    },
                    "threshold": {
                      "type": "integer",
                      "description": "Temperature threshold in Celsius"
                    },
                    "isHeatWarning": {
                      "type": "boolean",
                      "description": "Whether the alert is a heat warning"
                    },
                    "kind": {
                      "type": "string",
                      "enum": [
                        "TemperatureAlert"
                      ]
                    }
                  },
                  "required": [
                    "severity",
                    "message",
                    "threshold",
                    "isHeatWarning",
                    "kind"
                  ],
                  "additionalProperties": false
                },
                "WeatherNews": {
                  "type": "object",
                  "properties": {
                    "title": {
                      "type": "string",
                      "description": "Title of the news article"
                    },
                    "link": {
                      "type": "string",
                      "description": "Link to the news article"
                    }
                  },
                  "required": [
                    "title",
                    "link"
                  ],
                  "additionalProperties": false
                },
                "WeatherForecast": {
                  "type": "object",
                  "properties": {
                    "temperature": {
                      "type": "integer",
                      "description": "Temperature in Celsius"
                    },
                    "conditions": {
                      "type": "string",
                      "description": "Weather conditions (e.g., sunny, cloudy, rainy)"
                    },
                    "precipitation": {
                      "type": [
                        "integer",
                        "null"
                      ],
                      "description": "Chance of precipitation in percentage"
                    },
                    "latLon": {
                      "description": "Coordinates of the location",
                      "anyOf": [
                        {
                          "${"$"}ref": "#/${"$"}defs/LatLon"
                        }
                      ]
                    },
                    "pollution": {
                      "type": "string",
                      "enum": [
                        "None",
                        "LOW",
                        "MEDIUM",
                        "HIGH"
                      ]
                    },
                    "alert": {
                      "anyOf": [
                        {
                          "${"$"}ref": "#/${"$"}defs/FloodAlert"
                        },
                        {
                          "${"$"}ref": "#/${"$"}defs/StormAlert"
                        },
                        {
                          "${"$"}ref": "#/${"$"}defs/TemperatureAlert"
                        }
                      ]
                    },
                    "news": {
                      "type": "array",
                      "items": {
                        "${"$"}ref": "#/${"$"}defs/WeatherNews"
                      },
                      "description": "List of news articles"
                    }
                  },
                  "required": [
                    "temperature",
                    "conditions",
                    "precipitation",
                    "latLon",
                    "pollution",
                    "alert",
                    "news"
                  ],
                  "additionalProperties": false,
                  "description": "Weather forecast for a given location"
                }
              },
              "type": "object",
              "properties": {
                "temperature": {
                  "type": "integer",
                  "description": "Temperature in Celsius"
                },
                "conditions": {
                  "type": "string",
                  "description": "Weather conditions (e.g., sunny, cloudy, rainy)"
                },
                "precipitation": {
                  "type": [
                    "integer",
                    "null"
                  ],
                  "description": "Chance of precipitation in percentage"
                },
                "latLon": {
                  "description": "Coordinates of the location",
                  "anyOf": [
                    {
                      "${"$"}ref": "#/${"$"}defs/LatLon"
                    }
                  ]
                },
                "pollution": {
                  "type": "string",
                  "enum": [
                    "None",
                    "LOW",
                    "MEDIUM",
                    "HIGH"
                  ]
                },
                "alert": {
                  "anyOf": [
                    {
                      "${"$"}ref": "#/${"$"}defs/FloodAlert"
                    },
                    {
                      "${"$"}ref": "#/${"$"}defs/StormAlert"
                    },
                    {
                      "${"$"}ref": "#/${"$"}defs/TemperatureAlert"
                    }
                  ]
                },
                "news": {
                  "type": "array",
                  "items": {
                    "${"$"}ref": "#/${"$"}defs/WeatherNews"
                  },
                  "description": "List of news articles"
                }
              },
              "required": [
                "temperature",
                "conditions",
                "precipitation",
                "latLon",
                "pollution",
                "alert",
                "news"
              ],
              "additionalProperties": false,
              "description": "Weather forecast for a given location"
            }
        """.trimIndent()

        assertEquals(expectedSchema, schema)
    }
}
