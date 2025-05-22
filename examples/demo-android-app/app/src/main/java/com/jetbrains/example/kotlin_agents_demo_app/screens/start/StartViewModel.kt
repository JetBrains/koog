package com.jetbrains.example.kotlin_agents_demo_app.screens.start

import androidx.lifecycle.ViewModel
import com.jetbrains.example.kotlin_agents_demo_app.NavRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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

class StartViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(StartUiState())
    val uiState: StateFlow<StartUiState> = _uiState.asStateFlow()
}
