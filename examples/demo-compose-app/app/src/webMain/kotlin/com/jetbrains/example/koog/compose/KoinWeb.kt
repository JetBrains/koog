package com.jetbrains.example.koog.compose

import com.jetbrains.example.koog.compose.settings.AppSettings
import com.jetbrains.example.koog.compose.settings.MemoryAppSettings
import org.koin.core.module.Module
import org.koin.dsl.module

actual val appPlatformModule: Module = module {
    single<AppSettings> { MemoryAppSettings() }
}
