package ai.koog.agents.example.acp

import com.agentclientprotocol.model.ContentBlock
import com.agentclientprotocol.model.SessionUpdate

fun SessionUpdate.render() {
    when (this) {
        is SessionUpdate.AgentMessageChunk -> {
            println("Agent: ${this.content.render()}")
        }

        is SessionUpdate.AgentThoughtChunk -> {
            println("Agent thinks: ${this.content.render()}")
        }

        is SessionUpdate.AvailableCommandsUpdate -> {
            println("Available commands updated:")
        }

        is SessionUpdate.CurrentModeUpdate -> {
            println("Session mode changed to: ${this.currentModeId.value}")
        }

        is SessionUpdate.PlanUpdate -> {
            println("Agent plan: ")
            for (entry in this.entries) {
                println("  [${entry.status}] ${entry.content} (${entry.priority})")
            }
        }

        is SessionUpdate.ToolCall -> {
            println("Tool call started: ${this.title} ${this.status} ${this.rawInput} ${this.rawOutput}")
        }

        is SessionUpdate.ToolCallUpdate -> {
            println("Tool call update: ${this.title} ${this.status} ${this.rawInput} ${this.rawOutput}")
        }

        is SessionUpdate.UserMessageChunk -> {
            println("User: ${this.content.render()}")
        }

    }
}

fun ContentBlock.render(): String {
    return when (this) {
        is ContentBlock.Text -> text
        else -> {
            "Unsupported chunk: [${this::class.simpleName}]"
        }
    }
}
