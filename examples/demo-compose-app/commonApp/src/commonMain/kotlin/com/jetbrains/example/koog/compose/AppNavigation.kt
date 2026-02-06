package com.jetbrains.example.koog.compose

import androidx.navigation3.runtime.NavKey
import com.jetbrains.example.koog.compose.screens.agentdemo.AgentDemoNavigationCallback
import com.jetbrains.example.koog.compose.screens.settings.SettingsNavigationCallback
import com.jetbrains.example.koog.compose.screens.start.StartNavigationCallback

internal class AppNavigation(private val backStack: MutableList<NavKey>) :
    AgentDemoNavigationCallback,
    SettingsNavigationCallback,
    StartNavigationCallback {

    override fun goBack() {
        backStack.removeLastOrNull()
    }

    override fun goSettings() {
        backStack.add(NavRoute.SettingsScreen)
    }

    override fun goAgentDemo(agentDemoRoute: NavRoute.AgentDemoRoute) {
        backStack.add(agentDemoRoute)
    }
}
