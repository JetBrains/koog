package com.jetbrains.example.koog.compose.screens.agentdemo

// Define UI Events for the agent demo screen
sealed interface AgentDemoUiEvents {
    data class UpdateInputText(val text: String) : AgentDemoUiEvents
    data object SendMessage : AgentDemoUiEvents
    data object RestartChat : AgentDemoUiEvents
    data object NavigateBack : AgentDemoUiEvents
}
