package com.jetbrains.example.koog.compose.screens.agentdemo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jetbrains.example.koog.compose.agents.common.AgentProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AgentDemoViewModel(
    private val navigationCallback: AgentDemoNavigationCallback,
    private val agentProvider: AgentProvider,
) : ViewModel() {
    // UI state
    private val _uiState = MutableStateFlow(
        AgentDemoUiState(
            title = agentProvider.title,
            messages = listOf(Message.SystemMessage(agentProvider.description))
        )
    )
    val uiState: StateFlow<AgentDemoUiState> = _uiState.asStateFlow()

    fun onEvent(event: AgentDemoUiEvents) {
        viewModelScope.launch {
            when (event) {
                is AgentDemoUiEvents.UpdateInputText -> updateInputText(event.text)
                AgentDemoUiEvents.SendMessage -> sendMessage()
                AgentDemoUiEvents.RestartChat -> restartChat()
                AgentDemoUiEvents.NavigateBack -> navigationCallback.goBack()
            }
        }
    }

    // Update input text
    private fun updateInputText(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    // Send user message and start agent processing
    private fun sendMessage() {
        val userInput = _uiState.value.inputText.trim()
        if (userInput.isEmpty()) return

        // If agent is waiting for a response to a question
        if (_uiState.value.userResponseRequested) {
            // Add user message to chat and update current response
            _uiState.update {
                it.copy(
                    messages = it.messages + Message.UserMessage(userInput),
                    inputText = "",
                    isLoading = true,
                    userResponseRequested = false,
                    currentUserResponse = userInput
                )
            }
        } else { // Initial message flow - add user message and start agent
            _uiState.update {
                it.copy(
                    messages = it.messages + Message.UserMessage(userInput),
                    inputText = "",
                    isInputEnabled = false,
                    isLoading = true
                )
            }

            // Start the agent processing
            viewModelScope.launch {
                runAgent(userInput)
            }
        }
    }

    // Run the agent
    private suspend fun runAgent(userInput: String) {
        withContext(Dispatchers.Default) {
            try {
                // Create and run the agent using the factory
                val agent = agentProvider.provideAgent(
                    onToolCallEvent = { message ->
                        // Add tool call messages to the chat
                        viewModelScope.launch {
                            _uiState.update {
                                it.copy(
                                    messages = it.messages + Message.ToolCallMessage(message)
                                )
                            }
                        }
                    },
                    onErrorEvent = { errorMessage ->
                        // Handle agent errors
                        viewModelScope.launch {
                            _uiState.update {
                                it.copy(
                                    messages = it.messages + Message.ErrorMessage(errorMessage),
                                    isInputEnabled = true,
                                    isLoading = false
                                )
                            }
                        }
                    },
                    onAssistantMessage = { message ->
                        // Handle agent asking user a question
                        _uiState.update {
                            it.copy(
                                messages = it.messages + Message.AgentMessage(message),
                                isInputEnabled = true,
                                isLoading = false,
                                userResponseRequested = true
                            )
                        }

                        // Wait for user response
                        val userResponse = _uiState
                            .first { it.currentUserResponse != null }
                            .currentUserResponse
                            ?: throw IllegalArgumentException("User response is null")

                        // Update the state to reset current response
                        _uiState.update {
                            it.copy(
                                currentUserResponse = null
                            )
                        }

                        // Return it to the agent
                        userResponse
                    },
                )

                // Run the agent
                val result = agent.run(userInput)

                // Update UI with final state and mark chat as ended
                _uiState.update {
                    it.copy(
                        messages = it.messages +
                            Message.ResultMessage(result) +
                            Message.SystemMessage("The agent has stopped."),
                        isInputEnabled = false,
                        isLoading = false,
                        isChatEnded = true
                    )
                }
            } catch (e: Exception) {
                // Handle errors
                _uiState.update {
                    it.copy(
                        messages = it.messages + Message.ErrorMessage("Error: ${e.message}"),
                        isInputEnabled = true,
                        isLoading = false
                    )
                }
            }
        }
    }

    // Restart the chat
    private fun restartChat() {
        _uiState.update {
            AgentDemoUiState(
                title = agentProvider.title,
                messages = listOf(Message.SystemMessage(agentProvider.description))
            )
        }
    }
}
