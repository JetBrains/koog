package com.jetbrains.example.kotlin_agents_demo_app

import com.jetbrains.example.kotlin_agents_demo_app.settings.AppSettings
import com.jetbrains.example.kotlin_agents_demo_app.settings.DataStoreAppSettings
import com.jetbrains.example.kotlin_agents_demo_app.settings.PrefPathProvider
import okio.Path
import okio.Path.Companion.toPath
import org.koin.core.module.Module
import org.koin.dsl.module
import java.io.File

actual val appPlatformModule: Module = module {
    single<PrefPathProvider> {
        object : PrefPathProvider {
            override fun get(): Path {
                val file = File(System.getProperty("java.io.tmpdir"), "settings.preferences_pb")
                return file.absolutePath.toPath()
            }
        }
    }
    single<AppSettings> { DataStoreAppSettings(prefPathProvider = get()) }
}
