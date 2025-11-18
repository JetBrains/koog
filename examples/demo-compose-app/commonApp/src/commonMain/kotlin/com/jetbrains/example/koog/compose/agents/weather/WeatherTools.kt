package com.jetbrains.example.koog.compose.agents.weather

import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolResult
import ai.koog.agents.core.tools.ToolResultUtils
import ai.koog.agents.core.tools.annotations.LLMDescription
import kotlinx.datetime.DateTimePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.offsetAt
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Tools for the weather agent
 */
@OptIn(ExperimentalTime::class)
sealed interface WeatherTools {
    companion object {
        private val CLOCK = Clock.System
        private val UTC_ZONE = TimeZone.UTC
    }

    /**
     * Granularity options for weather forecasts
     */
    @Serializable
    enum class Granularity {
        @SerialName("daily")
        DAILY,

        @SerialName("hourly")
        HOURLY
    }

    /**
     * Tool for getting the current date and time
     */
    class CurrentDatetimeTool(
        val defaultTimeZone: TimeZone = UTC_ZONE,
        val clock: Clock = CLOCK,
    ) : Tool<CurrentDatetimeTool.Args, CurrentDatetimeTool.Result>() {
        @Serializable
        data class Args(
            @property:LLMDescription("The timezone to get the current date and time in (e.g., 'UTC', 'America/New_York', 'Europe/London'). Defaults to UTC.")
            val timezone: String = "UTC"
        )

        @Serializable
        data class Result(
            val datetime: String,
            val date: String,
            val time: String,
            val timezone: String
        ) : ToolResult.TextSerializable() {
            override fun textForLLM(): String {
                return "Current datetime: $datetime, Date: $date, Time: $time, Timezone: $timezone"
            }
        }

        override val argsSerializer = Args.serializer()
        override val resultSerializer: KSerializer<Result> = ToolResultUtils.toTextSerializer<Result>()

        override val name = "current_datetime"
        override val description = "Get the current date and time in the specified timezone"

        override suspend fun execute(args: Args): Result {
            val zoneId = try {
                TimeZone.of(args.timezone)
            } catch (_: Exception) {
                defaultTimeZone
            }

            val now = clock.now()
            val localDateTime = now.toLocalDateTime(zoneId)
            val offset = zoneId.offsetAt(now)

            val time = localDateTime.time
            val timeStr = "${time.hour.toString().padStart(2, '0')}:${
                time.minute.toString().padStart(2, '0')
            }:${time.second.toString().padStart(2, '0')}"

            return Result(
                datetime = "${localDateTime.date}T$timeStr$offset",
                date = localDateTime.date.toString(),
                time = timeStr,
                timezone = zoneId.id
            )
        }
    }

    /**
     * Tool for adding a duration to a date
     */
    class AddDatetimeTool(
        val defaultTimeZone: TimeZone = UTC_ZONE,
        val clock: Clock = CLOCK,
    ) : Tool<AddDatetimeTool.Args, AddDatetimeTool.Result>() {
        @Serializable
        data class Args(
            @property:LLMDescription("The date to add to in ISO format (e.g., '2023-05-20')")
            val date: String,
            @property:LLMDescription("The number of days to add")
            val days: Int,
            @property:LLMDescription("The number of hours to add")
            val hours: Int,
            @property:LLMDescription("The number of minutes to add")
            val minutes: Int
        )

        @Serializable
        data class Result(
            val date: String,
            val originalDate: String,
            val daysAdded: Int,
            val hoursAdded: Int,
            val minutesAdded: Int
        ) : ToolResult.TextSerializable() {
            override fun textForLLM(): String {
                return buildString {
                    append("Date: $date")
                    if (originalDate.isBlank()) {
                        append(" (starting from today)")
                    } else {
                        append(" (starting from $originalDate)")
                    }

                    if (daysAdded != 0 || hoursAdded != 0 || minutesAdded != 0) {
                        append(" after adding")

                        if (daysAdded != 0) {
                            append(" $daysAdded days")
                        }

                        if (hoursAdded != 0) {
                            if (daysAdded != 0) append(",")
                            append(" $hoursAdded hours")
                        }

                        if (minutesAdded != 0) {
                            if (daysAdded != 0 || hoursAdded != 0) append(",")
                            append(" $minutesAdded minutes")
                        }
                    }
                }
            }
        }

        override val argsSerializer = Args.serializer()
        override val resultSerializer = ToolResultUtils.toTextSerializer<Result>()

        override val name = "add_datetime"
        override val description =
            "Add a duration to a date. Use this tool when you need to calculate offsets, such as tomorrow, in two days, etc."

        override suspend fun execute(args: Args): Result {
            val baseDate = if (args.date.isNotBlank()) {
                try {
                    LocalDate.parse(args.date)
                } catch (_: Exception) {
                    // Use current date if parsing fails
                    clock.now().toLocalDateTime(defaultTimeZone).date
                }
            } else {
                clock.now().toLocalDateTime(defaultTimeZone).date
            }

            // Convert to LocalDateTime to handle hours and minutes
            val baseDateTime = LocalDateTime(baseDate.year, baseDate.month, baseDate.day, 0, 0)
            val baseInstant = baseDateTime.toInstant(defaultTimeZone)

            val period = DateTimePeriod(
                days = args.days,
                hours = args.hours,
                minutes = args.minutes
            )

            val newInstant = baseInstant.plus(period, defaultTimeZone)
            val resultDate = newInstant.toLocalDateTime(defaultTimeZone).date.toString()

            return Result(
                date = resultDate,
                originalDate = args.date,
                daysAdded = args.days,
                hoursAdded = args.hours,
                minutesAdded = args.minutes
            )
        }
    }

    /**
     * Tool for getting a weather forecast
     */
    class WeatherForecastTool(
        private val openMeteoClient: OpenMeteoClient = OpenMeteoClient(),
        val defaultTimeZone: TimeZone = UTC_ZONE
    ) : Tool<WeatherForecastTool.Args, WeatherForecastTool.Result>() {
        @Serializable
        data class Args(
            @property:LLMDescription("The location to get the weather forecast for (e.g., 'New York', 'London', 'Paris')")
            val location: String,
            @property:LLMDescription("The date to get the weather forecast for in ISO format (e.g., '2023-05-20'). If empty, the forecast starts from today.")
            val date: String = "",
            @property:LLMDescription("The number of days to forecast (1-7)")
            val days: Int = 1,
            @property:LLMDescription("The granularity of the forecast: 'daily' for day-by-day forecast or 'hourly' for hour-by-hour forecast. Default is 'daily'.")
            val granularity: Granularity = Granularity.DAILY
        )

        @Serializable
        data class Result(
            val locationName: String,
            val locationCountry: String? = null,
            val forecast: String,
            val date: String,
            val granularity: Granularity
        ) : ToolResult.TextSerializable() {
            override fun textForLLM(): String {
                val granularityText = when (granularity) {
                    Granularity.DAILY -> "daily"
                    Granularity.HOURLY -> "hourly"
                }
                val dateInfo = if (date.isBlank()) "starting from today" else "for $date"
                val formattedLocation = if (locationCountry.isNullOrBlank()) {
                    locationName
                } else {
                    "$locationName, $locationCountry"
                }.trim().trimEnd(',')

                return "Weather forecast for $formattedLocation ($granularityText, $dateInfo):\n$forecast"
            }
        }

        override val argsSerializer = Args.serializer()
        override val resultSerializer = ToolResultUtils.toTextSerializer<Result>()

        override val name = "weather_forecast"
        override val description = "Get a weather forecast for a location with specified granularity (daily or hourly)"

        override suspend fun execute(args: Args): Result {
            // Search for the location
            val locations = openMeteoClient.searchLocation(args.location)
            if (locations.isEmpty()) {
                return Result(
                    locationName = args.location,
                    forecast = "Location not found",
                    date = args.date,
                    granularity = args.granularity
                )
            }

            val location = locations.first()
            val forecastDays = args.days.coerceIn(1, 7)

            // Get the weather forecast
            val forecast = openMeteoClient.getWeatherForecast(
                latitude = location.latitude,
                longitude = location.longitude,
                forecastDays = forecastDays
            )

            // Format the forecast based on granularity
            val formattedForecast = when (args.granularity) {
                Granularity.HOURLY -> formatHourlyForecast(forecast, args.date)
                Granularity.DAILY -> formatDailyForecast(forecast, args.date)
            }

            return Result(
                locationName = location.name,
                locationCountry = location.country,
                forecast = formattedForecast,
                date = args.date,
                granularity = args.granularity
            )
        }

        private fun formatDailyForecast(forecast: WeatherForecast, date: String): String {
            val daily = forecast.daily ?: return "No daily forecast data available"

            val startDate = date.ifBlank {
                Clock.System.now().toLocalDateTime(defaultTimeZone).date.toString()
            }

            val startIndex = daily.time.indexOfFirst { it >= startDate }.coerceAtLeast(0)

            return buildString {
                for (i in startIndex until daily.time.size) {
                    val dateStr = daily.time[i]
                    val maxTemp = daily.temperature2mMax?.getOrNull(i)?.toString() ?: "N/A"
                    val minTemp = daily.temperature2mMin?.getOrNull(i)?.toString() ?: "N/A"
                    val weatherCode = daily.weatherCode?.getOrNull(i)
                    val weatherDesc = getWeatherDescription(weatherCode)
                    val precipSum = daily.precipitationSum?.getOrNull(i)?.toString() ?: "0"

                    append("$dateStr: $weatherDesc, ")
                    append("Temperature: $minTemp°C to $maxTemp°C, ")
                    append("Precipitation: $precipSum mm")

                    if (i < daily.time.size - 1) {
                        append("\n")
                    }
                }
            }
        }

        private fun formatHourlyForecast(forecast: WeatherForecast, date: String): String {
            val hourly = forecast.hourly ?: return "No hourly forecast data available"

            val startDate = date.ifBlank {
                Clock.System.now().toLocalDateTime(defaultTimeZone).date.toString()
            }

            // Find the starting index for the requested date
            val startIndex = hourly.time.indexOfFirst {
                it.startsWith(startDate) || it > startDate
            }.coerceAtLeast(0)

            return buildString {
                // Group hourly forecasts by date for better readability
                val groupedByDate = hourly.time.subList(startIndex, hourly.time.size).mapIndexed { index, time ->
                    val actualIndex = startIndex + index
                    val dateTime = time.split("T")
                    val date = dateTime[0]
                    val hour = if (dateTime.size > 1) dateTime[1].substringBefore(":") else "00"

                    val temp = hourly.temperature2m?.getOrNull(actualIndex)?.toString() ?: "N/A"
                    val precipProb = hourly.precipitationProbability?.getOrNull(actualIndex)?.toString() ?: "N/A"
                    val weatherCode = hourly.weatherCode?.getOrNull(actualIndex)
                    val weatherDesc = getWeatherDescription(weatherCode)

                    Triple(
                        date,
                        "$hour:00: $weatherDesc, Temperature: $temp°C, Precipitation probability: $precipProb%",
                        actualIndex
                    )
                }.groupBy { it.first }

                groupedByDate.forEach { (date, forecasts) ->
                    append("$date:\n")
                    forecasts.forEach { (_, forecast, _) ->
                        append("  $forecast\n")
                    }
                }
            }
        }

        private fun getWeatherDescription(code: Int?): String {
            return when (code) {
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
        }
    }
}
