package ai.koog.agents.example.acp

import ai.koog.agents.core.tools.annotations.Tool
import kotlin.io.path.Path
import kotlin.io.path.listDirectoryEntries

@Tool
fun listFiles(directory: String): List<String> {
    return Path(directory).listDirectoryEntries().map { it.toString() }
}

