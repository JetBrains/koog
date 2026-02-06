package ai.coding.agent

import ai.koog.agents.core.tools.annotations.Tool
import com.agentclientprotocol.model.Plan
import com.agentclientprotocol.model.PlanEntry
import com.agentclientprotocol.model.PlanEntryPriority
import com.agentclientprotocol.model.PlanEntryStatus
import kotlinx.serialization.Serializable
import java.nio.file.Path
import kotlin.io.path.*


@Serializable
data class KoogPlanEntry(
    val content: String,
    val priority: PlanEntryPriority,
    val status: PlanEntryStatus,
)

@Serializable
data class KoogPlan(
    val entries: List<KoogPlanEntry>,
)

fun KoogPlan.toAcpPlan(): Plan = Plan(entries.map {
    PlanEntry(
        content = it.content,
        priority = it.priority,
        status = it.status
    )
})


@Tool("list_directory")
fun listDirectory(absolutePath: String, depth: Int = 1): List<String> {
    require(depth > 0) { "Depth must be at least 1 (got $depth)" }

    val rootPath = Path(absolutePath)
    require(rootPath.exists()) { "Path does not exist: $absolutePath" }
    require(rootPath.isDirectory()) { "Path is not a directory: $absolutePath" }

    val result = mutableListOf<String>()

    fun walk(current: Path, currentDepth: Int) {
        if (currentDepth > depth) return

        current.listDirectoryEntries().forEach { entry ->
            val relativePath = entry.relativeTo(rootPath).pathString
            result += relativePath

            if (entry.isDirectory() && currentDepth < depth) {
                walk(entry, currentDepth + 1)
            }
        }
    }

    walk(rootPath, 1)
    return result.sorted()
}

@Tool("edit_file")
fun editFile(absolutePath: String, original: String, replacement: String) {
    val path = Path(absolutePath)

    if (!path.exists()) {
        path.parent.createDirectories()
        path.createFile()
    }

    require(path.exists()) { "File does not exist: $absolutePath" }
    require(path.isRegularFile()) { "Path is not a regular file: $absolutePath" }

    val content = path.readText()

    require(original in content) {
        "Original text not found in file: $absolutePath"
    }

    val updated = content.replace(original, replacement)
    path.writeText(updated)
}

@Tool("read_file")
fun readFile(absolutePath: String): String {
    val path = Path(absolutePath)

    require(path.exists()) { "Path does not exist: $absolutePath" }
    require(path.isRegularFile()) { "Path is not a regular file: $absolutePath" }

    return path.readText()
}
