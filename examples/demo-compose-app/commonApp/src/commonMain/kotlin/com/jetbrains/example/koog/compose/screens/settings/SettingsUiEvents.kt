package com.jetbrains.example.koog.compose.screens.settings

import com.jetbrains.example.koog.compose.settings.SelectedOption

// Define UI Events for the settings screen
sealed interface SettingsUiEvents {
    data object NavigateBack : SettingsUiEvents
    data object SaveSettings : SettingsUiEvents
    data class UpdateOption(val selectedOption: SelectedOption) : SettingsUiEvents
    data class UpdateCredential(val credential: String) : SettingsUiEvents
}
