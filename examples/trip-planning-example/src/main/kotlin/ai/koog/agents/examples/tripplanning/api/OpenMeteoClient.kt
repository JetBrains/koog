package ai.koog.agents.examples.tripplanning.api

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.Json

/**
 * Client for interacting with the Open Meteo API
 */
class OpenMeteoClient {
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                coerceInputValues = true
            })
        }
    }

    /**
     * Search for locations by name
     */
    suspend fun searchLocation(name: String, count: Int = 10): List<GeocodingResult> {
        val response: GeocodingResponse = client.get("https://geocoding-api.open-meteo.com/v1/search") {
            parameter("name", name)
            parameter("count", count)
            parameter("format", "json")
            parameter("language", "en")
        }.body()
        
        return response.results ?: emptyList()
    }

    /**
     * Get weather forecast for a location
     */
    suspend fun getWeatherForecast(
        latitude: Double,
        longitude: Double,
        startDate: LocalDate,
        endDate: LocalDate,
        granularity: ForecastGranularity,
        timezone: String = "auto"
    ): WeatherForecast {
        require(startDate <= endDate) { "startDate must be before endDate" }

        val response = client.get("https://api.open-meteo.com/v1/forecast") {
            parameter("latitude", latitude)
            parameter("longitude", longitude)
            parameter("start_date", startDate.toString())
            parameter("end_date", endDate.toString())

            when (granularity) {
                ForecastGranularity.HOURLY ->
                    parameter(
                        "hourly",
                        listOf("weather_code", "temperature_2m", "precipitation_probability").joinToString(",")
                    )

                ForecastGranularity.DAILY ->
                    parameter(
                        "daily",
                        listOf("weather_code", "temperature_2m_max", "temperature_2m_min", "precipitation_sum").joinToString(",")
                    )
            }

            parameter("timezone", timezone)
        }

        return response.body()
    }
}

/**
 * Granularity options for weather forecasts
 */
@Serializable
enum class ForecastGranularity {
    @SerialName("daily")
    DAILY,

    @SerialName("hourly")
    HOURLY
}

@Serializable
data class GeocodingResponse(
    val results: List<GeocodingResult>? = null
)

@Serializable
data class GeocodingResult(
    val id: Long,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val elevation: Double? = null,
    @SerialName("feature_code") val featureCode: String? = null,
    @SerialName("country_code") val countryCode: String? = null,
    val timezone: String? = null,
    val country: String? = null,
    val admin1: String? = null,
    val admin2: String? = null,
    val admin3: String? = null,
    val admin4: String? = null
)

@Serializable
data class WeatherForecast(
    val latitude: Double,
    val longitude: Double,
    val elevation: Double? = null,
    @SerialName("generationtime_ms") val generationTimeMs: Double? = null,
    @SerialName("utc_offset_seconds") val utcOffsetSeconds: Int? = null,
    val timezone: String? = null,
    @SerialName("timezone_abbreviation") val timezoneAbbreviation: String? = null,
    val hourly: HourlyForecast? = null,
    @SerialName("hourly_units") val hourlyUnits: Map<String, String>? = null,
    val daily: DailyForecast? = null,
    @SerialName("daily_units") val dailyUnits: Map<String, String>? = null
)

@Serializable
data class HourlyForecast(
    val time: List<String>,
    @SerialName("temperature_2m") val temperature2m: List<Double>? = null,
    @SerialName("precipitation_probability") val precipitationProbability: List<Int>? = null,
    @SerialName("weather_code") val weatherCode: List<Int>? = null
) {
    @Transient
    val weatherDesc: List<String>? = weatherCode?.map { it.toWeatherDesc() }
}

@Serializable
data class DailyForecast(
    val time: List<String>,
    @SerialName("weather_code") val weatherCode: List<Int>? = null,
    @SerialName("temperature_2m_max") val temperature2mMax: List<Double>? = null,
    @SerialName("temperature_2m_min") val temperature2mMin: List<Double>? = null,
    @SerialName("precipitation_sum") val precipitationSum: List<Double>? = null
) {
    @Transient
    val weatherDesc: List<String>? = weatherCode?.map { it.toWeatherDesc() }
}

private fun Int.toWeatherDesc() = when (this) {
    0 -> "Clear sky"
    1 -> "Mainly clear"
    2 -> "Partly cloudy"
    3 -> "Overcast"
    45, 48 -> "Fog"
    51, 53, 55 -> "Drizzle"
    56, 57 -> "Freezing drizzle"
    61, 63, 65 -> "Rain"
    66, 67 -> "Freezing rain"
    71, 73, 75 -> "Snow fall"
    77 -> "Snow grains"
    80, 81, 82 -> "Rain showers"
    85, 86 -> "Snow showers"
    95 -> "Thunderstorm"
    96, 99 -> "Thunderstorm with hail"
    else -> "Unknown"
}