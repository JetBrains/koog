package com.jetbrains.example.kotlin_agents_demo_app

import com.jetbrains.example.kotlin_agents_demo_app.settings.AppSettings
import com.jetbrains.example.kotlin_agents_demo_app.settings.MemoryAppSettings
import org.koin.core.module.Module
import org.koin.dsl.module

actual val appPlatformModule: Module = module {
    single<AppSettings> { MemoryAppSettings() }
}
