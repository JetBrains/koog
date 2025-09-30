package com.jetbrains.example.kotlin_agents_demo_app.screens.agentdemo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.jetbrains.example.kotlin_agents_demo_app.theme.AppDimension
import com.jetbrains.example.kotlin_agents_demo_app.theme.AppTheme
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
fun AgentDemoScreen(
    onNavigateBack: () -> Unit,
    viewModel: AgentDemoViewModel,
) {
    val uiState by viewModel.uiState.collectAsState()

    AgentDemoScreenContent(
        title = uiState.title,
        messages = uiState.messages,
        inputText = uiState.inputText,
        isInputEnabled = uiState.isInputEnabled,
        isLoading = uiState.isLoading,
        isChatEnded = uiState.isChatEnded,
        onInputTextChanged = viewModel::updateInputText,
        onSendClicked = viewModel::sendMessage,
        onRestartClicked = viewModel::restartChat,
        onNavigateBack = onNavigateBack
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AgentDemoScreenContent(
    title: String,
    messages: List<Message>,
    inputText: String,
    isInputEnabled: Boolean,
    isLoading: Boolean,
    isChatEnded: Boolean,
    onInputTextChanged: (String) -> Unit,
    onSendClicked: () -> Unit,
    onRestartClicked: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    // Scroll to bottom when messages change
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding()
        ) {
            // Messages list
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = AppDimension.spacingMedium),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(AppDimension.spacingMedium)
            ) {
                items(messages) { message ->
                    when (message) {
                        is Message.UserMessage -> UserMessageBubble(message.text)
                        is Message.AgentMessage -> AgentMessageBubble(message.text)
                        is Message.SystemMessage -> SystemMessageItem(message.text)
                        is Message.ErrorMessage -> ErrorMessageItem(message.text)
                        is Message.ToolCallMessage -> ToolCallMessageItem(message.text)
                        is Message.ResultMessage -> ResultMessageItem(message.text)
                    }
                }

                // Add extra space at the bottom for better UX
                item {
                    Spacer(modifier = Modifier.height(AppDimension.spacingMedium))
                }
            }

            // Input area or restart button
            if (isChatEnded) {
                RestartButton(onRestartClicked = onRestartClicked)
            } else {
                InputArea(
                    text = inputText,
                    onTextChanged = onInputTextChanged,
                    onSendClicked = {
                        onSendClicked()
                        focusManager.clearFocus()
                    },
                    isEnabled = isInputEnabled,
                    isLoading = isLoading,
                    focusRequester = focusRequester
                )
            }
        }
    }
}

@Composable
private fun UserMessageBubble(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(RoundedCornerShape(AppDimension.radiusExtraLarge))
                .background(MaterialTheme.colorScheme.primary)
                .padding(AppDimension.spacingMedium)
        ) {
            Text(
                text = text,
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
private fun AgentMessageBubble(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(RoundedCornerShape(AppDimension.radiusExtraLarge))
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(AppDimension.spacingMedium)
        ) {
            Text(
                text = text,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
private fun SystemMessageItem(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AppDimension.spacingMedium),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
private fun ErrorMessageItem(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 280.dp)
        ) {
            Text(
                text = "Error",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(start = AppDimension.spacingSmall)
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(AppDimension.radiusExtraLarge))
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(AppDimension.spacingMedium)
            ) {
                Text(
                    text = text,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

@Composable
private fun ToolCallMessageItem(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 280.dp)
        ) {
            Text(
                text = "Tool call",
                color = MaterialTheme.colorScheme.tertiary,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(start = AppDimension.spacingSmall)
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(AppDimension.radiusExtraLarge))
                    .background(MaterialTheme.colorScheme.tertiaryContainer)
                    .padding(AppDimension.spacingMedium)
            ) {
                Text(
                    text = text,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

@Composable
private fun ResultMessageItem(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 280.dp)
        ) {
            Text(
                text = "Result",
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(start = AppDimension.spacingSmall)
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(AppDimension.radiusExtraLarge))
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .padding(AppDimension.spacingMedium)
            ) {
                Text(
                    text = text,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

@Composable
private fun RestartButton(onRestartClicked: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimension.spacingMedium, vertical = AppDimension.spacingSmall),
        contentAlignment = Alignment.Center
    ) {
        Button(
            onClick = onRestartClicked,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Text("Start new chat")
        }
    }
}

@Composable
private fun InputArea(
    text: String,
    onTextChanged: (String) -> Unit,
    onSendClicked: () -> Unit,
    isEnabled: Boolean,
    isLoading: Boolean,
    focusRequester: FocusRequester
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = AppDimension.elevationMedium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = AppDimension.spacingMedium,
                    vertical = AppDimension.spacingSmall
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Text input field
            OutlinedTextField(
                value = text,
                onValueChange = onTextChanged,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                placeholder = { Text("Type a message...") },
                enabled = isEnabled,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSendClicked() }),
                singleLine = true,
                shape = RoundedCornerShape(AppDimension.radiusRound)
            )

            Spacer(modifier = Modifier.width(AppDimension.spacingSmall))

            // Send button or loading indicator
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(AppDimension.iconButtonSizeLarge)
                        .padding(AppDimension.spacingButtonPadding)
                )
            } else {
                IconButton(
                    onClick = onSendClicked,
                    enabled = isEnabled && text.isNotBlank(),
                    modifier = Modifier
                        .size(AppDimension.iconButtonSizeLarge)
                        .clip(CircleShape)
                        .background(
                            if (isEnabled && text.isNotBlank())
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.surfaceVariant
                        )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = if (isEnabled && text.isNotBlank())
                            MaterialTheme.colorScheme.onPrimary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Preview
@Composable
fun AgentDemoScreenPreview() {
    AppTheme {
        AgentDemoScreenContent(
            title = "Agent Demo",
            messages = listOf(
                Message.SystemMessage("Hi, I'm an agent that can help you"),
                Message.UserMessage("Hello!"),
                Message.ToolCallMessage("Tool example, args {a=2, b=2}"),
                Message.ResultMessage("Result: 4"),
                Message.AgentMessage("Hello! How can I help you today?"),
                Message.ErrorMessage("Error: Something went wrong")
            ),
            inputText = "",
            isInputEnabled = true,
            isLoading = false,
            isChatEnded = false,
            onInputTextChanged = {},
            onSendClicked = {},
            onRestartClicked = {},
            onNavigateBack = {}
        )
    }
}

@Preview
@Composable
fun AgentDemoScreenEndedPreview() {
    AppTheme {
        AgentDemoScreenContent(
            title = "Agent Demo",
            messages = listOf(
                Message.SystemMessage("Hi, I'm an agent that can help you"),
                Message.UserMessage("Hello!"),
                Message.AgentMessage("Hello! How can I help you today?"),
                Message.SystemMessage("The agent has stopped.")
            ),
            inputText = "",
            isInputEnabled = false,
            isLoading = false,
            isChatEnded = true,
            onInputTextChanged = {},
            onSendClicked = {},
            onRestartClicked = {},
            onNavigateBack = {}
        )
    }
}
