package ai.koog.agents.examples.tripplanning.tools

//import ai.koog.agents.core.tools.*
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import ai.koog.agents.examples.tripplanning.api.ForecastGranularity
import ai.koog.agents.examples.tripplanning.api.OpenMeteoClient
import ai.koog.agents.examples.tripplanning.api.WeatherForecast
import ai.koog.prompt.markdown.markdown
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.format
import kotlinx.datetime.format.FormatStringsInDatetimeFormats
import kotlinx.datetime.format.byUnicodePattern

class WeatherTools(private val openMeteoClient: OpenMeteoClient) : ToolSet {
    @Tool
    @LLMDescription("Get a weather forecast for a location and a date interval with a specified granularity.")
    suspend fun getWeatherForecast(
        @LLMDescription("The location to get the weather forecast for (e.g., 'New York', 'London', 'Paris')")
        location: String,
        @LLMDescription("ISO 3166-1 alpha-2 country code of the location (e.g., 'US', 'GB', 'FR')")
        countryCodeISO2: String,
        @LLMDescription("Start of the interval (inclusive) to get the weather forecast for in ISO format (e.g., '2023-05-20').")
        startDate: String,
        @LLMDescription("End of the interval (inclusive) to get the weather forecast for in ISO format (e.g., '2023-05-20').")
        endDate: String,
        @LLMDescription("The granularity of the forecast.")
        granularity: ForecastGranularity
    ): String = weatherForecast(
        location,
        countryCodeISO2,
        startDate.parseLocalDate(),
        endDate.parseLocalDate(),
        granularity
    ).toString()


    sealed interface Forecast {
        data class ParameterValue<T>(
            val value: T,
            val unit: String
        ) {
            override fun toString() = "$value $unit"
        }

        data class Daily(
            val data: List<Data>
        ) : Forecast {
            data class Data(
                val date: LocalDate,
                val weatherDescription: String?,
                val maxTemperature: ParameterValue<Double>?,
                val minTemperature: ParameterValue<Double>?,
                val precipitationSum: ParameterValue<Double>?
            )
        }

        data class Hourly(
            val data: List<Data>
        ) : Forecast {
            data class Data(
                val datetime: LocalDateTime,
                val weatherDescription: String?,
                val temperature: ParameterValue<Double>?,
                val precipitationProbabilityPerc: ParameterValue<Int>?,
            )
        }
    }

    data class ForecastResult(
        val locationName: String?,
        val locationCountry: String?,
        val forecast: Forecast?,
        val error: String? = null,
    ) {
        companion object {
            @OptIn(FormatStringsInDatetimeFormats::class)
            private val dateTimeFormat = LocalDateTime.Format {
                byUnicodePattern("yyyy-MM-dd HH:mm")
            }
        }

        override fun toString(): String {
            return when {
                error != null -> "There was an error calling the tool:\n$error"

                locationName == null || locationCountry == null -> "No location found"

                forecast == null -> "No forecast available"

                else -> when (forecast) {
                    is Forecast.Daily -> markdown {
                        h1("Daily forecast for $locationName, $locationCountry:")
                        br()

                        forecast.data.forEach { data ->
                            h2(data.date.format(LocalDate.Formats.ISO))

                            +data.weatherDescription.orEmpty()
                            +"Max temperature: ${data.maxTemperature}"
                            +"Min temperature: ${data.minTemperature}"
                            +"Precipitation sum: ${data.precipitationSum}"

                            br()
                        }
                    }

                    is Forecast.Hourly -> markdown {
                        h1("Hourly forecast for $locationName, $locationCountry:")
                        br()

                        forecast.data.forEach { data ->
                            h2(data.datetime.format(dateTimeFormat))

                            +data.weatherDescription.orEmpty()
                            +"Max temperature: ${data.temperature}"
                            +"Precipitation probability: ${data.precipitationProbabilityPerc}"

                            br()
                        }
                    }
                }
            }
        }
    }

    private suspend fun weatherForecast(
        location: String,
        countryCodeISO2: String,
        startDate: LocalDate,
        endDate: LocalDate,
        granularity: ForecastGranularity
    ): WeatherTools.ForecastResult {
        try {
            // Search for the location
            val location = openMeteoClient
                .searchLocation(location)
                .find { it.countryCode == countryCodeISO2 }
                ?: run {
                    return ForecastResult(
                        locationName = null,
                        locationCountry = null,
                        forecast = null,
                    )
                }

            // Get the weather forecast
            val forecastResponse = openMeteoClient.getWeatherForecast(
                latitude = location.latitude,
                longitude = location.longitude,
                startDate = startDate,
                endDate = endDate,
                granularity = granularity,
            )

            // Format the forecast based on granularity
            val forecast = when (granularity) {
                ForecastGranularity.HOURLY -> getHourlyForecast(forecastResponse)
                ForecastGranularity.DAILY -> getDailyForecast(forecastResponse)
            }

            return ForecastResult(
                locationName = location.name,
                locationCountry = location.country,
                forecast = forecast,
            )
        } catch (e: Exception) {
            return ForecastResult(
                locationName = null,
                locationCountry = null,
                forecast = null,
                error = e.message
            )
        }
    }

    private fun getDailyForecast(forecast: WeatherForecast): Forecast.Daily {
        val daily = requireNotNull(forecast.daily)
        val units = requireNotNull(forecast.dailyUnits)

        val dailyData = buildList {
            for (i in 0 until daily.time.size) {
                add(
                    Forecast.Daily.Data(
                        date = LocalDate.parse(daily.time[i], LocalDate.Formats.ISO),
                        weatherDescription = daily.weatherDesc?.getOrNull(i),
                        maxTemperature = daily.temperature2mMax?.getOrNull(i)?.let {
                            Forecast.ParameterValue(it, units.getValue("temperature_2m_max"))
                        },
                        minTemperature = daily.temperature2mMin?.getOrNull(i)?.let {
                            Forecast.ParameterValue(it, units.getValue("temperature_2m_min"))
                        },
                        precipitationSum = daily.precipitationSum?.getOrNull(i)?.let {
                            Forecast.ParameterValue(it, units.getValue("precipitation_sum"))
                        },
                    )
                )
            }
        }

        return Forecast.Daily(dailyData)
    }

    private fun getHourlyForecast(forecast: WeatherForecast): Forecast.Hourly {
        val hourly = requireNotNull(forecast.hourly)
        val units = requireNotNull(forecast.hourlyUnits)

        val hourlyData = buildList {
            for (i in 0 until hourly.time.size) {
                add(
                    Forecast.Hourly.Data(
                        datetime = LocalDateTime.parse(hourly.time[i], LocalDateTime.Formats.ISO),
                        weatherDescription = hourly.weatherDesc?.getOrNull(i),
                        temperature = hourly.temperature2m?.getOrNull(i)?.let {
                            Forecast.ParameterValue(it, units.getValue("temperature_2m"))
                        },
                        precipitationProbabilityPerc = hourly.precipitationProbability?.getOrNull(i)?.let {
                            Forecast.ParameterValue(it, units.getValue("precipitation_probability"))
                        },
                    )
                )
            }
        }

        return Forecast.Hourly(hourlyData)
    }
}
