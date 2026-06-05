package com.jetbrains.example.koog.compose.settings

import androidx.datastore.core.DataStoreFactory
import kotlinx.coroutines.flow.first

internal class DataStoreAppSettings(private val storageProvider: StorageProvider) : AppSettings {
    private val dataStore by lazy {
        DataStoreFactory.create(storage = storageProvider.getStorage())
    }

    override suspend fun getCurrentSettings(): AppSettingsData = dataStore.data.first()

    override suspend fun setCurrentSettings(settings: AppSettingsData) {
        dataStore.updateData { settings }
    }
}
