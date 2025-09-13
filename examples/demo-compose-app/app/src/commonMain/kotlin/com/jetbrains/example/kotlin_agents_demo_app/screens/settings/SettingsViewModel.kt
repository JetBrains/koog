package com.jetbrains.example.kotlin_agents_demo_app.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jetbrains.example.kotlin_agents_demo_app.settings.AppSettings
import com.jetbrains.example.kotlin_agents_demo_app.settings.AppSettingsData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// State for the UI
data class SettingsUiState(
    val openAiToken: String = "",
    val anthropicToken: String = "",
    val isLoading: Boolean = true
)

/**
 * ViewModel for the Settings screen
 */
class SettingsViewModel(private val appSettings: AppSettings) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        // Load settings when ViewModel is created
        loadSettings()
    }

    /**
     * Load settings from AppSettings
     */
    private fun loadSettings() {
        viewModelScope.launch {
            val settings = appSettings.getCurrentSettings()

            _uiState.value = SettingsUiState(
                openAiToken = settings.openAiToken,
                anthropicToken = settings.anthropicToken,
                isLoading = false
            )
        }
    }

    /**
     * Update OpenAI token in the UI state
     */
    fun updateOpenAiToken(token: String) {
        _uiState.value = _uiState.value.copy(openAiToken = token)
    }

    /**
     * Update Anthropic token in the UI state
     */
    fun updateAnthropicToken(token: String) {
        _uiState.value = _uiState.value.copy(anthropicToken = token)
    }

    /**
     * Save settings to AppSettings
     */
    fun saveSettings() {
        viewModelScope.launch {
            val currentSettingsState = _uiState.value

            appSettings.setCurrentSettings(
                AppSettingsData(
                    openAiToken = currentSettingsState.openAiToken,
                    anthropicToken = currentSettingsState.anthropicToken
                )
            )
        }
    }
}
