package ai.koog.agents.example.snapshot

import ai.koog.agents.core.dsl.builder.AIAgentNodeDelegate
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphBuilderBase
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.snapshot.feature.withPersistence
import kotlinx.serialization.json.JsonPrimitive

private fun AIAgentSubgraphBuilderBase<*, *>.simpleNode(
    name: String? = null,
    output: String,
): AIAgentNodeDelegate<String, String> = node(name) {
    return@node it + output
}

private data class TeleportState(var teleported: Boolean = false)

private fun AIAgentSubgraphBuilderBase<*, *>.teleportNode(
    name: String? = null,
    teleportState: TeleportState = TeleportState()
): AIAgentNodeDelegate<String, String> = node(name) {
    if (!teleportState.teleported) {
        teleportState.teleported = true
        withPersistence {
            setExecutionPoint(it, "Node1", listOf(), JsonPrimitive("Teleported!!!"))
            return@withPersistence "Teleported"
        }
    } else {
        return@node "$it\nAlready teleported, passing by"
    }
}

object SnapshotStrategy {
    private val teleportState = TeleportState()

    val strategy = strategy("test") {
        val node1 by simpleNode(
            "Node1",
            output = "Node 1 output"
        )
        val node2 by simpleNode(
            "Node2",
            output = "Node 2 output"
        )
        val teleportNode by teleportNode(teleportState = teleportState)

        edge(nodeStart forwardTo node1)
        edge(node1 forwardTo node2)
        edge(node2 forwardTo teleportNode)
        edge(teleportNode forwardTo nodeFinish)
    }
}
