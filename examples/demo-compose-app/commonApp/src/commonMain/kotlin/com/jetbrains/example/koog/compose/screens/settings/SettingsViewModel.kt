package com.jetbrains.example.koog.compose.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jetbrains.example.koog.compose.settings.AppSettings
import com.jetbrains.example.koog.compose.settings.AppSettingsData
import com.jetbrains.example.koog.compose.settings.SelectedOption
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the Settings screen
 */
class SettingsViewModel(
    private val navigationCallback: SettingsNavigationCallback,
    private val appSettings: AppSettings,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        // Load settings when ViewModel is created
        loadSettings()
    }

    fun onEvent(event: SettingsUiEvents) {
        viewModelScope.launch {
            when (event) {
                is SettingsUiEvents.UpdateOption -> updateSelectedOption(selectedOption = event.selectedOption)
                is SettingsUiEvents.UpdateCredential -> updateCredential(credential = event.credential)
                SettingsUiEvents.NavigateBack -> navigationCallback.goBack()
                SettingsUiEvents.SaveSettings -> {
                    saveSettings()
                    navigationCallback.goBack()
                }
            }
        }
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
                geminiToken = settings.geminiToken,
                selectedOption = settings.selectedOption,
                isLoading = false
            )
        }
    }

    /**
     * Update the selected option in the UI state
     */
    private fun updateSelectedOption(selectedOption: SelectedOption) {
        _uiState.value = _uiState.value.copy(selectedOption = selectedOption)
    }

    /**
     * Update the credential for the selected option in the UI state
     */
    private fun updateCredential(credential: String) {
        val state = _uiState.value
        _uiState.value = when (state.selectedOption) {
            is SelectedOption.OpenAI -> state.copy(openAiToken = credential.trim())
            is SelectedOption.Anthropic -> state.copy(anthropicToken = credential.trim())
            is SelectedOption.Gemini -> state.copy(geminiToken = credential.trim())
        }
    }

    /**
     * Save settings to AppSettings
     */
    private fun saveSettings() {
        viewModelScope.launch {
            val currentSettingsState = _uiState.value

            appSettings.setCurrentSettings(
                AppSettingsData(
                    openAiToken = currentSettingsState.openAiToken,
                    anthropicToken = currentSettingsState.anthropicToken,
                    geminiToken = currentSettingsState.geminiToken,
                    selectedOption = currentSettingsState.selectedOption
                )
            )
        }
    }
}
