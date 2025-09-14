package com.jetbrains.example.kotlin_agents_demo_app.agents.weather

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Client for interacting with the Open Meteo API
 */
class OpenMeteoClient {
    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                coerceInputValues = true
            })
        }
    }

    /**
     * Search for locations by name
     * @param name The name of the location to search for
     * @param count The maximum number of results to return
     * @return A list of locations matching the search query
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
     * @param latitude The latitude of the location
     * @param longitude The longitude of the location
     * @param forecastDays The number of days to forecast
     * @param hourly The hourly weather variables to include
     * @param daily The daily weather variables to include
     * @param timezone The timezone to use for the forecast
     * @return The weather forecast for the location
     */
    suspend fun getWeatherForecast(
        latitude: Double,
        longitude: Double,
        forecastDays: Int = 7,
        hourly: List<String> = listOf("temperature_2m", "precipitation_probability", "weather_code"),
        daily: List<String> = listOf("weather_code", "temperature_2m_max", "temperature_2m_min", "precipitation_sum"),
        timezone: String = "auto"
    ): WeatherForecast {
        return client.get("https://api.open-meteo.com/v1/forecast") {
            parameter("latitude", latitude)
            parameter("longitude", longitude)
            parameter("forecast_days", forecastDays)
            parameter("hourly", hourly.joinToString(","))
            parameter("daily", daily.joinToString(","))
            parameter("timezone", timezone)
        }.body()
    }
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
)

@Serializable
data class DailyForecast(
    val time: List<String>,
    @SerialName("weather_code") val weatherCode: List<Int>? = null,
    @SerialName("temperature_2m_max") val temperature2mMax: List<Double>? = null,
    @SerialName("temperature_2m_min") val temperature2mMin: List<Double>? = null,
    @SerialName("precipitation_sum") val precipitationSum: List<Double>? = null
)
