package ai.codereview.client

import com.agentclientprotocol.model.ContentBlock
import com.agentclientprotocol.model.PlanEntryStatus
import com.agentclientprotocol.model.SessionUpdate

fun SessionUpdate.render() {
    when (this) {
        is SessionUpdate.PlanUpdate -> {
            println("\n=== Review Plan ===")
            entries.forEach { println("  [${it.status.label()}] ${it.content}") }
            println("===================")
        }
        is SessionUpdate.AgentMessageChunk ->
            print(content.render())
        is SessionUpdate.AgentThoughtChunk -> {}
        is SessionUpdate.ToolCall -> {}
        is SessionUpdate.ToolCallUpdate -> {}
        else -> {}
    }
}

fun ContentBlock.render(): String = when (this) {
    is ContentBlock.Text -> text
    else -> ""
}

private fun PlanEntryStatus.label(): String = when (this) {
    PlanEntryStatus.PENDING -> "Pending"
    PlanEntryStatus.IN_PROGRESS -> "In progress"
    PlanEntryStatus.COMPLETED -> "Completed"
}
