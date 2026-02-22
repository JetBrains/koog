package com.jetbrains.example.koog.compose.screens.start

import com.jetbrains.example.koog.compose.NavRoute

sealed interface StartUiEvents {
    data object Settings : StartUiEvents
    data class AgentDemo(val agentDemoRoute: NavRoute.AgentDemoRoute) : StartUiEvents
}
