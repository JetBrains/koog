package com.jetbrains.example.kotlin_agents_demo_app.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jetbrains.example.kotlin_agents_demo_app.theme.AppDimension
import com.jetbrains.example.kotlin_agents_demo_app.theme.AppTheme

@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onSaveSettings: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    SettingsScreenContent(
        openAiToken = uiState.openAiToken,
        anthropicToken = uiState.anthropicToken,
        onOpenAiTokenChange = viewModel::updateOpenAiToken,
        onAnthropicTokenChange = viewModel::updateAnthropicToken,
        onNavigateBack = onNavigateBack,
        onSaveSettings = {
            viewModel.saveSettings()
            onSaveSettings()
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreenContent(
    openAiToken: String,
    anthropicToken: String,
    onOpenAiTokenChange: (String) -> Unit,
    onAnthropicTokenChange: (String) -> Unit,
    onNavigateBack: () -> Unit,
    onSaveSettings: () -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Settings", color = MaterialTheme.colorScheme.onSurface) },
                colors = TopAppBarDefaults.topAppBarColors(
                    navigationIconContentColor = MaterialTheme.colorScheme.primary,
                    actionIconContentColor = MaterialTheme.colorScheme.primary
                ),
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onSaveSettings) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Save"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(AppDimension.spacingContentPadding)
        ) {
            Text(
                text = "API Tokens",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = AppDimension.spacingMedium)
            )

            OutlinedTextField(
                value = openAiToken,
                onValueChange = onOpenAiTokenChange,
                label = { Text("OpenAI Token", color = MaterialTheme.colorScheme.primary) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    cursorColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(AppDimension.spacingMedium))

            OutlinedTextField(
                value = anthropicToken,
                onValueChange = onAnthropicTokenChange,
                label = { Text("Anthropic Token", color = MaterialTheme.colorScheme.primary) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    cursorColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true)
@Composable
fun SettingsScreenContentPreview() {
    AppTheme {
        SettingsScreenContent(
            openAiToken = "sample-token",
            anthropicToken = "sample-token",
            onOpenAiTokenChange = {},
            onAnthropicTokenChange = {},
            onNavigateBack = {},
            onSaveSettings = {}
        )
    }
}
