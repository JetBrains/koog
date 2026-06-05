package com.jetbrains.example.koog.compose

import androidx.datastore.core.Storage
import androidx.datastore.core.okio.OkioStorage
import com.jetbrains.example.koog.compose.settings.AppSettings
import com.jetbrains.example.koog.compose.settings.AppSettingsData
import com.jetbrains.example.koog.compose.settings.AppSettingsSerializer
import com.jetbrains.example.koog.compose.settings.DataStoreAppSettings
import com.jetbrains.example.koog.compose.settings.StorageProvider
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCClass
import kotlinx.cinterop.getOriginalKotlinClass
import okio.FileSystem
import okio.Path.Companion.toPath
import org.koin.core.Koin
import org.koin.core.module.Module
import org.koin.core.parameter.parametersOf
import org.koin.core.qualifier.Qualifier
import org.koin.dsl.module
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

@OptIn(ExperimentalForeignApi::class)
actual val appPlatformModule: Module = module {
    single<StorageProvider> {
        object : StorageProvider {
            override fun getStorage(): Storage<AppSettingsData> = OkioStorage(
                fileSystem = FileSystem.SYSTEM,
                serializer = AppSettingsSerializer,
                producePath = {
                    val documentDirectory: NSURL? = NSFileManager.defaultManager.URLForDirectory(
                        directory = NSDocumentDirectory,
                        inDomain = NSUserDomainMask,
                        appropriateForURL = null,
                        create = false,
                        error = null,
                    )
                    (documentDirectory?.path + "/app_settings.json").toPath()
                }
            )
        }
    }
    single<AppSettings> { DataStoreAppSettings(storageProvider = get()) }
}

@OptIn(BetaInteropApi::class)
fun Koin.get(objCClass: ObjCClass): Any {
    val kClazz = getOriginalKotlinClass(objCClass)!!
    return get(kClazz)
}

@OptIn(BetaInteropApi::class)
fun Koin.get(objCClass: ObjCClass, qualifier: Qualifier?, parameter: Any): Any {
    val kClazz = getOriginalKotlinClass(objCClass)!!
    return get(kClazz, qualifier) { parametersOf(parameter) }
}
