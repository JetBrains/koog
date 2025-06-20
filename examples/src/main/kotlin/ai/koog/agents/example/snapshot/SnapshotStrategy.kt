package ai.koog.agents.example.snapshot

import ai.koog.agents.core.dsl.builder.AIAgentNodeDelegateBase
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphBuilderBase
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.snapshot.feature.withCheckpoints
import ai.koog.agents.snapshot.providers.NoAgentCheckpointStorageProvider.saveCheckpoint

object SnapshotStrategy {
    const val checkpointId = "snapshot-id"

    val strategy = strategy("test") {
        val node1 by simpleNode(
            output = "Node 1 output"
        )
        val node2 by simpleNode(
            output = "Node 2 output"
        )
        val node3 by simpleNode(
            output = "Node 3 output"
        )

        val snapshotNode by nodeCreateCheckpoint()

        val subgraphNode by subgraph("subgraph") {
            val subgraphNode1 by simpleNode(
                output = "Subgraph Node 1 output"
            )
            val subgraphNode2 by simpleNode(
                output = "Subgraph Node 2 output"
            )

            edge(nodeStart forwardTo subgraphNode1)
            edge(subgraphNode1 forwardTo subgraphNode2)
            edge(subgraphNode2 forwardTo nodeFinish)
        }

        edge(nodeStart forwardTo node1)
        edge(node1 forwardTo node2)
        edge(node2 forwardTo subgraphNode)
        edge(subgraphNode forwardTo snapshotNode)
        edge(snapshotNode forwardTo node3)
        edge(node3 forwardTo nodeFinish)
    }


    private fun AIAgentSubgraphBuilderBase<*, *>.nodeCreateCheckpoint(
        name: String? = null,
    ): AIAgentNodeDelegateBase<String, String> = node(name) {
        val ctx = this
        val input = it
        withCheckpoints {
            val checkpoint = createCheckpoint(checkpointId, ctx, currentNodeId ?: error("currentNodeId not set"), input)
            saveCheckpoint(checkpoint)

            val snapshot = it + "Snapshot created"
            println("Creating snapshot: $snapshot")
            return@withCheckpoints snapshot
        }
        return@node "End"
    }
}

private fun AIAgentSubgraphBuilderBase<*, *>.simpleNode(
    name: String? = null,
    output: String,
): AIAgentNodeDelegateBase<String, String> = node(name) {
    return@node it + output
}

private fun AIAgentSubgraphBuilderBase<*, *>.teleportNode(
    name: String? = null,
): AIAgentNodeDelegateBase<String, String> = node(name) {
    val ctx = this
    withCheckpoints {
        setExecutionPoint(ctx, "Node1", listOf(), "Teleported!!!")
        return@withCheckpoints "Teleported"
    }
}

object SnapshotStrategy2 {
    val strategy = strategy("test") {
        val node1 by simpleNode(
            "Node1",
            output = "Node 1 output"
        )
        val node2 by simpleNode(
            "Node2",
            output = "Node 2 output"
        )
        val teleportNode by teleportNode()

        edge(nodeStart forwardTo node1)
        edge(node1 forwardTo node2)
        edge(node2 forwardTo teleportNode)
        edge(teleportNode forwardTo nodeFinish)
    }
}