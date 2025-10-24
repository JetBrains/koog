package com.jetbrains.example.koog.compose.settings

import okio.Path

interface PrefPathProvider {
    fun get(): Path
}
