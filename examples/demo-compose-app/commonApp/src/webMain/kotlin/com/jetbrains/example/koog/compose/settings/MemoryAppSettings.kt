package com.jetbrains.example.koog.compose.settings

internal class MemoryAppSettings : AppSettings {
    private var appSettingsData = AppSettingsData(
        openAiToken = "",
        anthropicToken = "",
        geminiToken = "",
        selectedOption = SelectedOption.OpenAI
    )

    override suspend fun getCurrentSettings(): AppSettingsData = appSettingsData
    override suspend fun setCurrentSettings(settings: AppSettingsData) {
        appSettingsData = settings
    }
}
