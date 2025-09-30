package com.jetbrains.example.kotlin_agents_demo_app.settings

internal class MemoryAppSettings : AppSettings {
    private var appSettingsData = AppSettingsData(openAiToken = "", anthropicToken = "")
    override suspend fun getCurrentSettings(): AppSettingsData = appSettingsData
    override suspend fun setCurrentSettings(settings: AppSettingsData) {
        appSettingsData = settings
    }
}
