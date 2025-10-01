package ai.koog.agents.example.structuredoutput.tools

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import ai.koog.prompt.text.text

class WeatherTools : ToolSet {
    @Tool
    @LLMDescription("Get the weather forecast for the specified city and country")
    fun getWeatherForecast(
        @LLMDescription("The city for which to get the weather forecast")
        city: String,
    ) = text {
        +"The weather forecast for "
        +city
        +" is "
        +"Cloudy"
        +"temperature = 18"
        +"precipitation = 30"
        +"lat = 34.0522, lon = -118.2437"
        +"pollution = medium"
        +"alert = Moderate. Possible thunderstorms in the evening, windSpeed = 45.5"
        +"news = Local news: https://example.com/news, Global news: https://example.com/global-news"
    }
}
