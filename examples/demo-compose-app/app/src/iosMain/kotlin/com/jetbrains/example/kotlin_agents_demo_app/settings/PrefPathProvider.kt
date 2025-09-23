package com.jetbrains.example.kotlin_agents_demo_app.settings

import okio.Path

interface PrefPathProvider {
    fun get(): Path
}
