package ai.koog.agents.example.snapshot

import ai.koog.agents.core.dsl.builder.AIAgentNodeDelegateBase
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphBuilderBase
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.snapshot.feature.withCheckpoints

object SnapshotStrategy {

    val strategy = strategy("test") {
        val node1 by simpleNode(
            name = "Node 1", output = "Node 1 output"
        )
        val node2 by simpleNode(
            name = "Node 2", output = "Node 2 output"
        )
        val node3 by simpleNode(
            name = "Node 3", output = "Node 3 output"
        )
        val snapshotNode by nodeExampleSnapshot()

        edge(nodeStart forwardTo node1)
        edge(node1 forwardTo node2)
        edge(node2 forwardTo snapshotNode)
        edge(snapshotNode forwardTo node3)
        edge(node3 forwardTo nodeFinish)
    }

    private fun AIAgentSubgraphBuilderBase<*, *>.simpleNode(
        name: String? = null,
        output: String,
    ): AIAgentNodeDelegateBase<String, String> = node(name) {
        return@node it + output
    }

    private fun AIAgentSubgraphBuilderBase<*, *>.nodeExampleSnapshot(
        name: String? = null,
    ): AIAgentNodeDelegateBase<String, String> = node(name) {
        withCheckpoints {
            // This node will create a snapshot of the agent state
            // and save it to the checkpoint provider.
            val snapshot = it + "Snapshot created"
            println("Creating snapshot: $snapshot")
            return@withCheckpoints snapshot
        }
        return@node "End"
    }
}