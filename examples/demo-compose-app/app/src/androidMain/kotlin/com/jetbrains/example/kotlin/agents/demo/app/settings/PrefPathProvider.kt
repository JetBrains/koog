package com.jetbrains.example.kotlin.agents.demo.app.settings

import okio.Path

interface PrefPathProvider {
    fun get(): Path
}
