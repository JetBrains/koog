package com.jetbrains.example.kotlin_agents_demo_app

import android.content.Context
import com.jetbrains.example.kotlin_agents_demo_app.settings.AppSettings
import com.jetbrains.example.kotlin_agents_demo_app.settings.DataStoreAppSettings
import com.jetbrains.example.kotlin_agents_demo_app.settings.PrefPathProvider
import okio.Path
import okio.Path.Companion.toPath
import org.koin.core.module.Module
import org.koin.dsl.module

actual val appPlatformModule: Module = module {
    single<PrefPathProvider> {
        val context: Context = get()
        object : PrefPathProvider {
            override fun get(): Path {
                val file = context.filesDir.resolve("settings.preferences_pb")
                return file.absolutePath.toPath()
            }
        }
    }
    single<AppSettings> { DataStoreAppSettings(prefPathProvider = get()) }
}
