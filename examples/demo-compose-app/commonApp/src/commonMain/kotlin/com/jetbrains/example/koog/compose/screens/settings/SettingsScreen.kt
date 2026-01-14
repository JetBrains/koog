package com.jetbrains.example.koog.compose.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import com.jetbrains.example.koog.compose.settings.SelectedOption
import com.jetbrains.example.koog.compose.theme.AppDimension
import com.jetbrains.example.koog.compose.theme.AppTheme

@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    SettingsScreenContent(
        uiState = uiState,
        onEvent = viewModel::onEvent
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreenContent(
    uiState: SettingsUiState,
    onEvent: (SettingsUiEvents) -> Unit,
) {
    val options = listOf(
        SelectedOption.OpenAI,
        SelectedOption.Anthropic,
        SelectedOption.Gemini,
    )

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
                    IconButton(onClick = { onEvent(SettingsUiEvents.NavigateBack) }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { onEvent(SettingsUiEvents.SaveSettings) }) {
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

            Column {
                options.forEach { provider ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = (provider == uiState.selectedOption),
                                onClick = { onEvent(SettingsUiEvents.UpdateOption(provider)) },
                                role = Role.RadioButton
                            )
                            .padding(vertical = AppDimension.spacingSmall),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (provider == uiState.selectedOption),
                            onClick = null // null recommended for accessibility with screenreaders
                        )
                        Text(
                            text = provider.title,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = AppDimension.spacingMedium)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(AppDimension.spacingLarge))

            val (credentialValue, credentialLabel) = when (uiState.selectedOption) {
                is SelectedOption.OpenAI -> uiState.openAiToken to "OpenAI Token"
                is SelectedOption.Anthropic -> uiState.anthropicToken to "Anthropic Token"
                is SelectedOption.Gemini -> uiState.geminiToken to "Gemini Token"
            }

            OutlinedTextField(
                value = credentialValue,
                onValueChange = { onEvent(SettingsUiEvents.UpdateCredential(it)) },
                label = { Text(credentialLabel, color = MaterialTheme.colorScheme.primary) },
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
            uiState = SettingsUiState(selectedOption = SelectedOption.OpenAI),
            onEvent = {},
        )
    }
}
