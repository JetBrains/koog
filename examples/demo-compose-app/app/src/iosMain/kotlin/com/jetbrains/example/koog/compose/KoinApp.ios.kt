package com.jetbrains.example.koog.compose

import com.jetbrains.example.koog.compose.settings.AppSettings
import com.jetbrains.example.koog.compose.settings.DataStoreAppSettings
import com.jetbrains.example.koog.compose.settings.PrefPathProvider
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCClass
import kotlinx.cinterop.getOriginalKotlinClass
import okio.Path
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
    single<PrefPathProvider> {
        object : PrefPathProvider {
            override fun get(): Path {
                val documentDirectory: NSURL? = NSFileManager.defaultManager.URLForDirectory(
                    directory = NSDocumentDirectory,
                    inDomain = NSUserDomainMask,
                    appropriateForURL = null,
                    create = false,
                    error = null,
                )
                return (documentDirectory?.path + "/settings.preferences_pb").toPath()
            }
        }
    }
    single<AppSettings> { DataStoreAppSettings(prefPathProvider = get<PrefPathProvider>()) }
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
