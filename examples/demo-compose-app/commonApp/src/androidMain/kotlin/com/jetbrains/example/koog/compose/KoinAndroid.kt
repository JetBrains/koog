package com.jetbrains.example.koog.compose

import android.content.Context
import androidx.datastore.core.Storage
import androidx.datastore.core.okio.OkioStorage
import com.jetbrains.example.koog.compose.settings.AppSettings
import com.jetbrains.example.koog.compose.settings.AppSettingsData
import com.jetbrains.example.koog.compose.settings.AppSettingsSerializer
import com.jetbrains.example.koog.compose.settings.DataStoreAppSettings
import com.jetbrains.example.koog.compose.settings.StorageProvider
import okio.FileSystem
import okio.Path.Companion.toPath
import org.koin.core.module.Module
import org.koin.dsl.module

actual val appPlatformModule: Module = module {
    single<StorageProvider> {
        val context: Context = get()
        object : StorageProvider {
            override fun getStorage(): Storage<AppSettingsData> = OkioStorage(
                fileSystem = FileSystem.SYSTEM,
                serializer = AppSettingsSerializer,
                producePath = { context.filesDir.resolve("app_settings.json").absolutePath.toPath() }
            )
        }
    }
    single<AppSettings> { DataStoreAppSettings(storageProvider = get()) }
}
