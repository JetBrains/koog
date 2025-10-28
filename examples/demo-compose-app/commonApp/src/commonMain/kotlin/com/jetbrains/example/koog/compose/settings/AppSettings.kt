package com.jetbrains.example.koog.compose.settings

interface AppSettings {
    suspend fun getCurrentSettings(): AppSettingsData
    suspend fun setCurrentSettings(settings: AppSettingsData)
}

// Data stored in the settings
data class AppSettingsData(
    val openAiToken: String,
    val anthropicToken: String
)
