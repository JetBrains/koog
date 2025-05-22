package com.jetbrains.example.kotlin_agents_demo_app.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

// Define the DataStore at the app level
private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

// Data stored in the settings
data class AppSettingsData(
    val openAiToken: String,
    val anthropicToken: String
)

/**
 * Class to handle settings interaction
 */
class AppSettings(private val context: Context) {

    // Define keys for the preferences
    companion object {
        val OPENAI_TOKEN_KEY = stringPreferencesKey("openai_token")
        val ANTHROPIC_TOKEN_KEY = stringPreferencesKey("anthropic_token")
    }


    suspend fun getCurrentSettings(): AppSettingsData {
        return context.settingsDataStore.data.map { preferences ->
            AppSettingsData(
                openAiToken = preferences[OPENAI_TOKEN_KEY].orEmpty(),
                anthropicToken = preferences[ANTHROPIC_TOKEN_KEY].orEmpty()
            )
        }.first()
    }

    suspend fun setCurrentSettings(settings: AppSettingsData) {
        context.settingsDataStore.edit { preferences ->
            preferences[OPENAI_TOKEN_KEY] = settings.openAiToken
            preferences[ANTHROPIC_TOKEN_KEY] = settings.anthropicToken
        }
    }
}