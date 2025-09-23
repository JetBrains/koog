package ai.koog.agents.example.structuredoutput.models

import ai.koog.agents.core.tools.annotations.LLMDescription
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * This is a more advanced example showing how to configure various parameters of structured output manually, to fine-tune
 * it for your needs when necessary.
 *
 * Structured output that uses "full" JSON schema.
 * More advanced features are supported, e.g. polymorphism and recursive type references, and schemas can be more complex.
 */

@Serializable
@SerialName("FullWeatherForecast")
@LLMDescription("Weather forecast for a given location")
data class FullWeatherForecast(
    @property:LLMDescription("Temperature in Celsius")
    val temperature: Int,
    // properties with default values
    @property:LLMDescription("Weather conditions (e.g., sunny, cloudy, rainy)")
    val conditions: String = "sunny",
    // nullable properties
    @property:LLMDescription("Chance of precipitation in percentage")
    val precipitation: Int?,
    // nested classes
    @property:LLMDescription("Coordinates of the location")
    val latLon: LatLon,
    // enums
    val pollution: Pollution,
    // polymorphism
    val alert: WeatherAlert,
    // lists
    @property:LLMDescription("List of news articles")
    val news: List<WeatherNews>,
//    // maps (string keys only, some providers don't support maps at all)
//    @property:LLMDescription("Map of weather sources")
//    val sources: Map<String, WeatherSource>
) {
    companion object {
        // Optional examples, to help LLM understand the format better in manual mode
        val exampleForecasts = listOf(
            FullWeatherForecast(
                temperature = 18,
                conditions = "Cloudy",
                precipitation = 30,
                latLon = FullWeatherForecast.LatLon(lat = 34.0522, lon = -118.2437),
                pollution = FullWeatherForecast.Pollution.Medium,
                alert = FullWeatherForecast.WeatherAlert.StormAlert(
                    severity = FullWeatherForecast.WeatherAlert.Severity.Moderate,
                    message = "Possible thunderstorms in the evening",
                    windSpeed = 45.5
                ),
                news = listOf(
                    FullWeatherForecast.WeatherNews(title = "Local news", link = "https://example.com/news"),
                    FullWeatherForecast.WeatherNews(title = "Global news", link = "https://example.com/global-news")
                ),
//            sources = mapOf(
//                "MeteorologicalWatch" to FullWeatherForecast.WeatherSource(
//                    stationName = "MeteorologicalWatch",
//                    stationAuthority = "US Department of Agriculture"
//                ),
//                "MeteorologicalWatch2" to FullWeatherForecast.WeatherSource(
//                    stationName = "MeteorologicalWatch2",
//                    stationAuthority = "US Department of Agriculture"
//                )
//            )
            ),
            FullWeatherForecast(
                temperature = 10,
                conditions = "Rainy",
                precipitation = null,
                latLon = FullWeatherForecast.LatLon(lat = 37.7739, lon = -122.4194),
                pollution = FullWeatherForecast.Pollution.Low,
                alert = FullWeatherForecast.WeatherAlert.FloodAlert(
                    severity = FullWeatherForecast.WeatherAlert.Severity.Severe,
                    message = "Heavy rainfall may cause local flooding",
                    expectedRainfall = 75.2
                ),
                news = listOf(
                    FullWeatherForecast.WeatherNews(title = "Local news", link = "https://example.com/news"),
                    FullWeatherForecast.WeatherNews(title = "Global news", link = "https://example.com/global-news")
                ),
//            sources = mapOf(
//                "MeteorologicalWatch" to WeatherForecast.WeatherSource(
//                    stationName = "MeteorologicalWatch",
//                    stationAuthority = "US Department of Agriculture"
//                ),
//            )
            )
        )
    }

    // Nested classes
    @Serializable
    @SerialName("LatLon")
    data class LatLon(
        @property:LLMDescription("Latitude of the location")
        val lat: Double,
        @property:LLMDescription("Longitude of the location")
        val lon: Double
    )

    // Nested classes in lists...
    @Serializable
    @SerialName("WeatherNews")
    data class WeatherNews(
        @property:LLMDescription("Title of the news article")
        val title: String,
        @property:LLMDescription("Link to the news article")
        val link: String
    )

    // ... and maps (but only with string keys!)
    @Suppress("unused")
    @Serializable
    @SerialName("WeatherSource")
    data class WeatherSource(
        @property:LLMDescription("Name of the weather station")
        val stationName: String,
        @property:LLMDescription("Authority of the weather station")
        val stationAuthority: String
    )

    // Enums
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

    /*
     Polymorphism:
      1. Closed with sealed classes,
      2. Open: non-sealed classes with subclasses registered in json config
         https://github.com/Kotlin/kotlinx.serialization/blob/master/docs/polymorphism.md#registered-subclasses
     */
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
            val threshold: Int, // in Celsius
            @property:LLMDescription("Whether the alert is a heat warning")
            val isHeatWarning: Boolean
        ) : WeatherAlert()
    }
}

data class FullWeatherForecastRequest(
    val city: String,
    val country: String
)
