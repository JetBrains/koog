package com.jetbrains.example.kotlin_agents_demo_app.settings

interface AppSettings {
    suspend fun getCurrentSettings(): AppSettingsData
    suspend fun setCurrentSettings(settings: AppSettingsData)
}

// Data stored in the settings
data class AppSettingsData(
    val openAiToken: String,
    val anthropicToken: String
)
