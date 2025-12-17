package ai.koog.agents.example.acp

import ai.koog.agents.core.tools.annotations.Tool
import com.agentclientprotocol.model.Plan
import com.agentclientprotocol.model.PlanEntry
import com.agentclientprotocol.model.PlanEntryPriority
import com.agentclientprotocol.model.PlanEntryStatus
import kotlinx.serialization.Serializable
import kotlin.io.path.Path
import kotlin.io.path.createFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readText
import kotlin.io.path.writeText

@Serializable
public data class KoogPlanEntry(
    val content: String,
    val priority: PlanEntryPriority,
    val status: PlanEntryStatus,
)

@Serializable
public data class KoogPlan(
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
fun listDirectory(directory: String): List<String> {
    return Path(directory).listDirectoryEntries().map { it.toString() }
}

@Tool("create_file")
fun createFile(directory: String, fileName: String, content: String) {
    Path("$directory/$fileName").createFile().writeText(content)
}

@Tool("read_file")
fun readFile(path: String) {
    Path(path).readText(Charsets.UTF_8)
}
