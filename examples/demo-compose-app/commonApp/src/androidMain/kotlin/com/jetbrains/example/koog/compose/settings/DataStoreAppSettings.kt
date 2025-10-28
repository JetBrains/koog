package com.jetbrains.example.koog.compose.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

internal class DataStoreAppSettings(prefPathProvider: PrefPathProvider) : AppSettings {

    // Define keys for the preferences
    companion object {
        val OPENAI_TOKEN_KEY = stringPreferencesKey("openai_token")
        val ANTHROPIC_TOKEN_KEY = stringPreferencesKey("anthropic_token")
    }

    private val dataStore: DataStore<Preferences> by lazy {
        PreferenceDataStoreFactory.createWithPath(
            produceFile = { prefPathProvider.get() }
        )
    }

    override suspend fun getCurrentSettings(): AppSettingsData = dataStore.data.map { preferences ->
        AppSettingsData(
            openAiToken = preferences[OPENAI_TOKEN_KEY].orEmpty(),
            anthropicToken = preferences[ANTHROPIC_TOKEN_KEY].orEmpty()
        )
    }.first()

    override suspend fun setCurrentSettings(settings: AppSettingsData) {
        dataStore.edit { preferences ->
            preferences[OPENAI_TOKEN_KEY] = settings.openAiToken
            preferences[ANTHROPIC_TOKEN_KEY] = settings.anthropicToken
        }
    }
}
