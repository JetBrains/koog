package com.jetbrains.example.koog.compose.settings

import androidx.datastore.core.Storage

interface StorageProvider {
    fun getStorage(): Storage<AppSettingsData>
}
