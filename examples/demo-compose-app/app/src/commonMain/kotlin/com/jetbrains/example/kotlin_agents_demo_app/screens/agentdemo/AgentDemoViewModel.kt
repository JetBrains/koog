package com.jetbrains.example.kotlin_agents_demo_app.screens.agentdemo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jetbrains.example.kotlin_agents_demo_app.agents.common.AgentProvider
import com.jetbrains.example.kotlin_agents_demo_app.settings.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Define message types for the chat
sealed class Message {
    data class UserMessage(val text: String) : Message()
    data class AgentMessage(val text: String) : Message()
    data class SystemMessage(val text: String) : Message()
    data class ErrorMessage(val text: String) : Message()
    data class ToolCallMessage(val text: String) : Message()
    data class ResultMessage(val text: String) : Message()
}

// Define UI state for the agent demo screen
data class AgentDemoUiState(
    val title: String = "Agent Demo",
    val messages: List<Message> = listOf(Message.SystemMessage("Hi, I'm an agent that can help you")),
    val inputText: String = "",
    val isInputEnabled: Boolean = true,
    val isLoading: Boolean = false,
    val isChatEnded: Boolean = false,

    // For handling user responses when agent asks a question
    val userResponseRequested: Boolean = false,
    val currentUserResponse: String? = null,
)

class AgentDemoViewModel(
    private val agentProvider: AgentProvider,
    private val appSettings: AppSettings,
) : ViewModel() {
    // UI state
    private val _uiState = MutableStateFlow(
        AgentDemoUiState(
            title = agentProvider.title,
            messages = listOf(Message.SystemMessage(agentProvider.description))
        )
    )
    val uiState: StateFlow<AgentDemoUiState> = _uiState.asStateFlow()

    // Update input text
    fun updateInputText(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    // Send user message and start agent processing
    fun sendMessage() {
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
                    appSettings = appSettings,
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
    fun restartChat() {
        _uiState.update {
            AgentDemoUiState(
                title = agentProvider.title,
                messages = listOf(Message.SystemMessage(agentProvider.description))
            )
        }
    }
}
