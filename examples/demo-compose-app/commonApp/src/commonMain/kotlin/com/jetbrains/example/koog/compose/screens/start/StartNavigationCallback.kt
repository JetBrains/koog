package com.jetbrains.example.koog.compose.screens.start

import com.jetbrains.example.koog.compose.NavRoute

interface StartNavigationCallback {
    fun goSettings()
    fun goAgentDemo(agentDemoRoute: NavRoute.AgentDemoRoute)
}
