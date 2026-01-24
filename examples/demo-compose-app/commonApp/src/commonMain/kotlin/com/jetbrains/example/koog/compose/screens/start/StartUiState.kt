package com.jetbrains.example.koog.compose.screens.start

import com.jetbrains.example.koog.compose.NavRoute

data class StartUiState(
    val demoCards: List<CardItem> = listOf(
        CardItem(
            title = "Calculator",
            description = "A calculator agent that can solve math problems. Ask it any calculation and get the result.",
            agentDemoRoute = NavRoute.AgentDemoRoute.CalculatorScreen
        ),
        CardItem(
            title = "Weather Forecast",
            description = "A weather agent that can provide forecasts for any location. Ask about weather conditions, dates, and more.",
            agentDemoRoute = NavRoute.AgentDemoRoute.WeatherScreen
        ),
    )
)

data class CardItem(
    val title: String,
    val description: String,
    val agentDemoRoute: NavRoute.AgentDemoRoute? = null,
)
