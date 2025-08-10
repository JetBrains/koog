package ai.koog.prompt.executor.clients.google.structure

import ai.koog.agents.core.tools.annotations.LLMDescription
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import kotlin.test.Test
import kotlin.test.assertEquals

class GoogleSimpleJsonSchemaGeneratorTest {
    private val json = Json {
        prettyPrint = true
        explicitNulls = false
        isLenient = true
        ignoreUnknownKeys = true
        prettyPrintIndent = "  "
    }

    private val simpleGenerator = GoogleBasicJsonSchemaGenerator

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
        @property:LLMDescription("Pollution level")
        val pollution: Pollution,
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
    }

    @Test
    fun testGenerateGoogleSimpleJsonSchemaWeatherForecast() {
        val result = simpleGenerator.generate(json, "WeatherForecast", serializer<WeatherForecast>(), emptyMap())
        val schema = json.encodeToString(result.schema)

        val expectedSchema = """
            {
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
                  "type": "integer",
                  "description": "Chance of precipitation in percentage",
                  "nullable": true
                },
                "latLon": {
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
                  "description": "Coordinates of the location"
                },
                "pollution": {
                  "type": "string",
                  "enum": [
                    "None",
                    "LOW",
                    "MEDIUM",
                    "HIGH"
                  ],
                  "description": "Pollution level"
                },
                "news": {
                  "type": "array",
                  "items": {
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
                    ]
                  },
                  "description": "List of news articles"
                }
              },
              "required": [
                "temperature",
                "precipitation",
                "latLon",
                "pollution",
                "news"
              ]
            }
        """.trimIndent()

        assertEquals(expectedSchema, schema)
    }
}
