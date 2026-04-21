package com.jetbrains.example.koog.compose.screens.start

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class StartViewModel(private val navigationCallback: StartNavigationCallback) : ViewModel() {
    private val _uiState = MutableStateFlow(StartUiState())
    val uiState: StateFlow<StartUiState> = _uiState.asStateFlow()

    fun onEvent(event: StartUiEvents) {
        viewModelScope.launch {
            when (event) {
                is StartUiEvents.AgentDemo -> navigationCallback.goAgentDemo(event.agentDemoRoute)
                StartUiEvents.Settings -> navigationCallback.goSettings()
            }
        }
    }
}
