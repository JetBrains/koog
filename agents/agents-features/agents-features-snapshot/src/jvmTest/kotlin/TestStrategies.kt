import ai.koog.agents.core.dsl.builder.AIAgentNodeDelegate
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphBuilderBase
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.snapshot.feature.withPersistence
import kotlinx.serialization.json.JsonPrimitive
import kotlin.reflect.typeOf

/**
 * Creates a simple node that appends the output to the input.
 */
fun AIAgentSubgraphBuilderBase<*, *>.simpleNode(
    name: String? = null,
    output: String,
): AIAgentNodeDelegate<String, String> = node(name) {
    llm.writeSession {
        appendPrompt { user { text(output) } }
    }
    return@node it + "\n" + output
}

fun AIAgentSubgraphBuilderBase<*, *>.inputLogNode(
    name: String? = null,
): AIAgentNodeDelegate<String, String> = node(name) {
    llm.writeSession {
        appendPrompt { user { text(it) } }
    }
    return@node it
}

internal fun AIAgentSubgraphBuilderBase<*, *>.loggingNode(
    name: String? = null,
    message: String,
    collector: TestAgentLogsCollector
): AIAgentNodeDelegate<String, String> =
    node(name) {
        collector.log(message)
        return@node it
    }

fun AIAgentSubgraphBuilderBase<*, *>.collectHistoryNode(
    name: String? = null,
): AIAgentNodeDelegate<String, String> = node(name) {
    return@node llm.readSession {
        val history = this.prompt.messages.joinToString("\n") { it.content }
        return@readSession "History: $history"
    }
}

/**
 * Creates a strategy with a teleport node that jumps to a specific execution point.
 */
fun createTeleportStrategy() = strategy("teleport-test") {
    val node1 by simpleNode(
        "Node1",
        output = "Node 1 output"
    )

    val node2 by simpleNode(
        "Node2",
        output = "Node 2 output"
    )
    val teleportNode by teleportOnceNode("teleport", teleportState = TeleportState(false))

    edge(nodeStart forwardTo node1)
    edge(node1 forwardTo teleportNode)
    edge(teleportNode forwardTo node2)
    edge(node2 forwardTo nodeFinish)
}

/**
 * Creates a strategy with a checkpoint node that creates and saves a checkpoint.
 */
fun createCheckpointStrategy() = strategy("checkpoint-test") {
    val node1 by simpleNode(
        "Node1",
        output = "Node 1 output"
    )
    val checkpointNode by nodeCreateCheckpoint(
        "checkpointNode"
    )
    val node2 by simpleNode(
        "Node2",
        output = "Node 2 output"
    )

    edge(nodeStart forwardTo node1)
    edge(node1 forwardTo node2)
    edge(node2 forwardTo checkpointNode)
    edge(checkpointNode forwardTo nodeFinish)
}

private data class TeleportState(var teleported: Boolean = false)

/**
 * Creates a teleport node that jumps to a specific execution point.
 * Only teleports once to avoid infinite loops.
 */
private fun AIAgentSubgraphBuilderBase<*, *>.teleportOnceNode(
    name: String? = null,
    teleportToId: String = "Node1",
    teleportState: TeleportState
): AIAgentNodeDelegate<String, String> = node(name) {
    if (!teleportState.teleported) {
        teleportState.teleported = true
        withPersistence { ctx ->
            val history = llm.readSession { this.prompt.messages }
            setExecutionPoint(ctx, teleportToId, history, JsonPrimitive("$it\nTeleported"))
            return@withPersistence "Teleported"
        }
    } else {
        // If we've already teleported, just return the input
        return@node "$it\nAlready teleported, passing by"
    }
}

private fun AIAgentSubgraphBuilderBase<*, *>.nodeForSecondTry(
    name: String? = null,
    teleportState: TeleportState,
    collector: TestAgentLogsCollector,
): AIAgentNodeDelegate<String, String> = node(name) {
    if (teleportState.teleported) {
        collector.log("Second try successful")
        return@node "Second try successful"
    } else {
        teleportState.teleported = true
        error("This node will be successful only on the second try")
    }
}

private fun AIAgentSubgraphBuilderBase<*, *>.createCheckpointNode(name: String? = null, checkpointId: String) =
    node<String, String>(name) {
        val input = it
        withPersistence { ctx ->
            createCheckpoint(ctx, name!!, input, typeOf<String>(), 0L, checkpointId)
            llm.writeSession {
                appendPrompt {
                    user {
                        text("Checkpoint created with ID: $checkpointId")
                    }
                }
            }
        }
        return@node "$input\nCheckpoint Created"
    }

private fun AIAgentSubgraphBuilderBase<*, *>.nodeRollbackToCheckpoint(
    name: String? = null,
    checkpointId: String,
    teleportState: TeleportState
) =
    node<String, String>(name) {
        if (teleportState.teleported) {
            llm.writeSession {
                appendPrompt { user { text("Skipped rollback because it was already performed") } }
            }
            return@node "Skipping rollback"
        }

        withPersistence {
            val checkpoint = rollbackToCheckpoint(checkpointId, it)!!
            teleportState.teleported = true
            llm.writeSession {
                appendPrompt { user { text("Rolling back to node ${checkpoint.nodeId}") } }
            }
        }
        return@node "$it\nrolled back"
    }

/**
 * Creates a checkpoint node that creates and saves a checkpoint.
 */
private fun AIAgentSubgraphBuilderBase<*, *>.nodeCreateCheckpoint(
    name: String? = null,
): AIAgentNodeDelegate<String, String> = node(name) {
    val input = it
    withPersistence { ctx ->
        val checkpoint = createCheckpoint(
            ctx,
            currentNodeId ?: error("currentNodeId not set"),
            input,
            typeOf<String>(),
            0L
        )

        saveCheckpoint(ctx.agentId, checkpoint ?: error("Checkpoint creation failed"))

        return@withPersistence "$input\nSnapshot created"
    }
}

fun createSimpleTeleportSubgraphStrategy(teleportToId: String) = strategy("teleport-test") {
    val node1 by simpleNode(
        "Node1",
        output = "Node 1 output"
    )

    val node2 by simpleNode(
        "Node2",
        output = "Node 2 output"
    )

    val sg by subgraph("sg1") {
        val sgNode1 by simpleNode(output = "sg1 node output")
        val teleportNode by teleportOnceNode("teleport", teleportToId = teleportToId, teleportState = TeleportState())
        val sgNode2 by simpleNode(output = "sg2 node output")

        nodeStart then sgNode1 then teleportNode then sgNode2 then nodeFinish
    }

    edge(nodeStart forwardTo node1)
    edge(node1 forwardTo sg)
    edge(sg forwardTo node2)
    edge(node2 forwardTo nodeFinish)
}

fun createCheckpointGraphWithRollback(checkpointId: String) = strategy("") {
    val node1 by simpleNode(output = "Node 1 output")
    val checkpointNode by createCheckpointNode("checkpointNode", checkpointId = checkpointId)
    val node2 by simpleNode(output = "Node 2 output")
    val nodeRollback by nodeRollbackToCheckpoint(checkpointId = checkpointId, teleportState = TeleportState())
    val historyNode by collectHistoryNode("History Node")

    edge(nodeStart forwardTo node1)
    edge(node1 forwardTo checkpointNode)
    edge(checkpointNode forwardTo node2)
    edge(node2 forwardTo nodeRollback)
    edge(nodeRollback forwardTo historyNode)
    edge(historyNode forwardTo nodeFinish)
}

fun straightForwardGraphNoCheckpoint() = strategy("straight-forward") {
    val node1 by simpleNode(
        name = "Node1",
        output = "Node 1 output"
    )

    val node2 by simpleNode(
        name = "Node2",
        output = "Node 2 output"
    )

    val historyNode by collectHistoryNode("History Node")

    edge(nodeStart forwardTo node1)
    edge(node1 forwardTo node2)
    edge(node2 forwardTo historyNode)
    edge(historyNode forwardTo nodeFinish)
}

fun restoreStrategyGraph() = strategy("restore-strategy") {
    val inputNode by inputLogNode()

    val node1 by simpleNode(
        "Node1",
        output = "Node 1 output"
    )

    val node2 by simpleNode(
        "Node2",
        output = "Node 2 output"
    )

    val historyNode by collectHistoryNode("History Node")

    edge(nodeStart forwardTo inputNode)
    edge(inputNode forwardTo node1)
    edge(node1 forwardTo node2)
    edge(node2 forwardTo historyNode)
    edge(historyNode forwardTo nodeFinish)
}

internal fun loggingGraphStrategy(collector: TestAgentLogsCollector) = strategy("logging-test") {
    val node1 by loggingNode(
        "Node1",
        message = "First Step",
        collector = collector
    )

    val node2 by loggingNode(
        "Node2",
        message = "Second Step",
        collector = collector
    )
    edge(nodeStart forwardTo node1)
    edge(node1 forwardTo node2)
    edge(node2 forwardTo nodeFinish)
}

internal fun loggingGraphForRunFromSecondTry(collector: TestAgentLogsCollector) = strategy("logging-test") {
    val teleportState = TeleportState()

    val node1 by loggingNode(
        "Node1",
        message = "First Step",
        collector = collector
    )

    val node2 by loggingNode(
        "Node2",
        message = "Second Step",
        collector = collector
    )

    val nodeForSecondTry by nodeForSecondTry(
        "NodeForSecondTry",
        teleportState = teleportState,
        collector = collector,
    )

    edge(nodeStart forwardTo node1)
    edge(node1 forwardTo node2)
    edge(node2 forwardTo nodeForSecondTry)
    edge(nodeForSecondTry forwardTo nodeFinish)
}

fun simpleTeleportSubgraphWithInnerSubgraph(teleportToId: String) = strategy("teleport-test") {
    val node1 by simpleNode(
        "Node1",
        output = "Node 1 output"
    )

    val node2 by simpleNode(
        "Node2",
        output = "Node 2 output"
    )

    val sg by subgraph("sg1") {
        val sgNode1 by simpleNode(output = "sgNode1 node output")
        val sgNode2 by simpleNode(output = "sgNode2 node output")

        val sg2 by subgraph {
            val sg2Node1 by simpleNode(output = "sg2Node1 node output")
            val sg2Node2 by simpleNode(output = "sg2Node2 node output")
            val teleportNode by teleportOnceNode(teleportToId = teleportToId, teleportState = TeleportState())
            nodeStart then sg2Node1 then sg2Node2 then teleportNode then nodeFinish
        }

        nodeStart then sgNode1 then sg2 then sgNode2 then nodeFinish
    }

    edge(nodeStart forwardTo node1)
    edge(node1 forwardTo sg)
    edge(sg forwardTo node2)
    edge(node2 forwardTo nodeFinish)
}

/**
 * Creates a strategy with a subgraph that contains a checkpoint node.
 */
fun checkpointSubgraphStrategy(checkpointId: String) = strategy("checkpoint-subgraph-test") {
    val node1 by simpleNode(
        "Node1",
        output = "Node 1 output"
    )

    val node2 by simpleNode(
        "Node2",
        output = "Node 2 output"
    )

    val sg by subgraph("sg1") {
        val sgNode1 by simpleNode(output = "sg1 node output")
        val checkpointNode by createCheckpointNode("checkpointNode", checkpointId = checkpointId)
        val sgNode2 by simpleNode(output = "sg2 node output")

        nodeStart then sgNode1 then checkpointNode then sgNode2 then nodeFinish
    }

    edge(nodeStart forwardTo node1)
    edge(node1 forwardTo sg)
    edge(sg forwardTo node2)
    edge(node2 forwardTo nodeFinish)
}

/**
 * Creates a strategy with a subgraph that contains a checkpoint node and a rollback node.
 */
fun checkpointSubgraphWithRollbackStrategy(checkpointId: String) = strategy("checkpoint-rollback-subgraph-test") {
    val node1 by simpleNode(
        "Node1",
        output = "Node 1 output"
    )

    val node2 by collectHistoryNode(
        "Node2"
    )

    val sg by subgraph("sg1") {
        val sgNode1 by simpleNode(output = "sg1 node output")
        val checkpointNode by createCheckpointNode("checkpointNode", checkpointId = checkpointId)
        val sgNode2 by simpleNode(output = "sg2 node output")
        val rollbackNode by nodeRollbackToCheckpoint(
            "rollbackNode",
            checkpointId = checkpointId,
            teleportState = TeleportState()
        )

        nodeStart then sgNode1 then checkpointNode then sgNode2 then rollbackNode then nodeFinish
    }

    edge(nodeStart forwardTo node1)
    edge(node1 forwardTo sg)
    edge(sg forwardTo node2)
    edge(node2 forwardTo nodeFinish)
}

/**
 * Creates a strategy with nested subgraphs that contain checkpoint and rollback nodes.
 */
fun nestedSubgraphCheckpointStrategy(checkpointId: String) = strategy("nested-checkpoint-subgraph-test") {
    val node1 by simpleNode(
        "Node1",
        output = "Node 1 output"
    )

    val node2 by collectHistoryNode(
        "Node2"
    )

    val sg by subgraph("sg1") {
        val sgNode1 by simpleNode(output = "sgNode1 node output")
        val sgNode2 by simpleNode(output = "sgNode2 node output")

        val sg2 by subgraph {
            val sg2Node1 by simpleNode(output = "sg2Node1 node output")
            val checkpointNode by createCheckpointNode("checkpointNode", checkpointId = checkpointId)
            val sg2Node2 by simpleNode(output = "sg2Node2 node output")

            nodeStart then sg2Node1 then checkpointNode then sg2Node2 then nodeFinish
        }

        nodeStart then sgNode1 then sg2 then sgNode2 then nodeFinish
    }

    edge(nodeStart forwardTo node1)
    edge(node1 forwardTo sg)
    edge(sg forwardTo node2)
    edge(node2 forwardTo nodeFinish)
}

/**
 * Creates a strategy with nested subgraphs that contain checkpoint and rollback nodes.
 */
fun nestedSubgraphCheckpointWithRollbackStrategy(
    checkpointId: String
) = strategy("nested-checkpoint-rollback-subgraph-test") {
    val node1 by simpleNode(
        "Node1",
        output = "Node 1 output"
    )

    val sg by subgraph("sg1") {
        val sgNode1 by simpleNode(output = "sgNode1 node output")
        val sgNode2 by simpleNode(output = "sgNode2 node output")

        val sg2 by subgraph {
            val sg2Node1 by simpleNode(output = "sg2Node1 node output")
            val checkpointNode by createCheckpointNode("checkpointNode", checkpointId = checkpointId)
            val sg2Node2 by simpleNode(output = "sg2Node2 node output")
            val rollbackNode by nodeRollbackToCheckpoint(
                "rollbackNode",
                checkpointId = checkpointId,
                teleportState = TeleportState()
            )

            nodeStart then sg2Node1 then checkpointNode then sg2Node2 then rollbackNode then nodeFinish
        }

        nodeStart then sgNode1 then sg2 then sgNode2 then nodeFinish
    }

    val node2 by collectHistoryNode(
        "Node2"
    )

    edge(nodeStart forwardTo node1)
    edge(node1 forwardTo sg)
    edge(sg forwardTo node2)
    edge(node2 forwardTo nodeFinish)
}

fun strategyWithRepeatedSubgraphs() = strategy("repeated-subgraphs-test") {
    val node1 by simpleNode(
        "Node1",
        output = "Node 1 output"
    )

    fun createSimpleSubgraph(name: String) = subgraph(name) {
        val sgNode1 by simpleNode(name = "sgNode1", output = "sg1 node output")
        val sgNode2 by simpleNode(name = "sgNode2", output = "sg2 node output")

        nodeStart then sgNode1 then sgNode2 then nodeFinish
    }

    val simpleSubgraph by createSimpleSubgraph("sg1")
    val simpleSubgraph2 by createSimpleSubgraph("sg2")

    nodeStart then node1 then simpleSubgraph then simpleSubgraph2 then nodeFinish
}