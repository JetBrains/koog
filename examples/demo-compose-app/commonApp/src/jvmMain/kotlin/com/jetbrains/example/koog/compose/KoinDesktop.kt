package com.jetbrains.example.koog.compose

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
import java.io.File

actual val appPlatformModule: Module = module {
    single<StorageProvider> {
        object : StorageProvider {
            override fun getStorage(): Storage<AppSettingsData> {
                val storageFile =
                    File(
                        listOf(
                            System.getProperty("user.home"),
                            ".koog-demo",
                            "datastore",
                            "app.pref.json",
                        ).joinToString(separator = File.separator),
                    ).also { it.parentFile?.mkdirs() }
                return OkioStorage(
                    fileSystem = FileSystem.SYSTEM,
                    serializer = AppSettingsSerializer,
                    producePath = { storageFile.absolutePath.toPath() },
                )
            }
        }
    }
    single<AppSettings> { DataStoreAppSettings(storageProvider = get()) }
}
