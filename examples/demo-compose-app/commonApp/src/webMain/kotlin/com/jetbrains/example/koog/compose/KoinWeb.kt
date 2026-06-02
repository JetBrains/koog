package com.jetbrains.example.koog.compose

import androidx.datastore.core.Storage
import androidx.datastore.core.okio.WebLocalStorage
import com.jetbrains.example.koog.compose.settings.AppSettings
import com.jetbrains.example.koog.compose.settings.AppSettingsData
import com.jetbrains.example.koog.compose.settings.AppSettingsSerializer
import com.jetbrains.example.koog.compose.settings.DataStoreAppSettings
import com.jetbrains.example.koog.compose.settings.StorageProvider
import org.koin.core.module.Module
import org.koin.dsl.module

actual val appPlatformModule: Module = module {
    single<StorageProvider> {
        object : StorageProvider {
            override fun getStorage(): Storage<AppSettingsData> = WebLocalStorage(
                serializer = AppSettingsSerializer,
                name = "app_settings"
            )
        }
    }
    single<AppSettings> { DataStoreAppSettings(storageProvider = get()) }
}
