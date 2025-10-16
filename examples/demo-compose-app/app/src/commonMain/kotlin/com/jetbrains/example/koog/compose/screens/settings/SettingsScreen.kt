package com.jetbrains.example.koog.compose.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.jetbrains.example.koog.compose.theme.AppDimension
import com.jetbrains.example.koog.compose.theme.AppTheme
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onSaveSettings: () -> Unit,
    viewModel: SettingsViewModel,
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

@Preview
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
