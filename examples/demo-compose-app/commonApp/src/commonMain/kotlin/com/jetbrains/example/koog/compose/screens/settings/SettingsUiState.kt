package com.jetbrains.example.koog.compose.screens.settings

import com.jetbrains.example.koog.compose.settings.SelectedOption

// State for the UI
data class SettingsUiState(
    val openAiToken: String = "",
    val anthropicToken: String = "",
    val geminiToken: String = "",
    val selectedOption: SelectedOption = SelectedOption.OpenAI,
    val isLoading: Boolean = true
)
