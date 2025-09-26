import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.ext.tool.SayToUser
import ai.koog.agents.snapshot.feature.AgentCheckpointData
import ai.koog.agents.snapshot.feature.Persistency
import ai.koog.agents.snapshot.providers.InMemoryPersistencyStorageProvider
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.llm.OllamaModels
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds
import ai.koog.agents.core.dsl.builder.AIAgentNodeDelegate
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphBuilderBase
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.snapshot.feature.RollbackToolRegistry
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import ai.koog.agents.snapshot.feature.withPersistency
import kotlin.reflect.typeOf

class CheckpointsTests {
    val systemPrompt = "You are a test agent."
    val agentConfig = AIAgentConfig(
        prompt = prompt("test") {
            system(systemPrompt)
        },
        model = OllamaModels.Meta.LLAMA_3_2,
        maxAgentIterations = 20
    )
    val toolRegistry = ToolRegistry {
        tool(SayToUser)
    }

    @Test
    fun testAgentExecutionWithRollback() = runTest {
        val agent = AIAgent(
            promptExecutor = getMockExecutor { },
            strategy = createCheckpointGraphWithRollback("checkpointId"),
            agentConfig = agentConfig,
            toolRegistry = toolRegistry
        ) {
            install(Persistency) {
                storage = InMemoryPersistencyStorageProvider("testAgentId")
            }
        }

        val output = agent.run("Start the test")
        assertEquals(
            "History: You are a test agent.\n" +
                "Node 1 output\n" +
                "Checkpoint created with ID: checkpointId\n" +
                "Node 2 output\n" +
                "Skipped rollback because it was already performed",
            output
        )
    }

    // ---------------------------- New test-only tooling ----------------------------
    @Serializable
    data class WriteArgs(val key: String, val value: String)

    object WriteKVTool : ai.koog.agents.core.tools.Tool<WriteArgs, String>() {
        override val argsSerializer: KSerializer<WriteArgs> = WriteArgs.serializer()
        override val resultSerializer: KSerializer<String> = String.serializer()
        override val description: String = "Writes a key-value pair (simulated)"
        override suspend fun execute(args: WriteArgs): String {
            // No-op write; side-effect simulated only by rollback tool
            return "ok"
        }
    }

    object DeleteKVRollbackTool : ai.koog.agents.core.tools.Tool<WriteArgs, String>() {
        override val argsSerializer: KSerializer<WriteArgs> = WriteArgs.serializer()
        override val resultSerializer: KSerializer<String> = String.serializer()
        override val description: String = "Deletes a key-value pair (rollback)"
        var calls: MutableList<WriteArgs> = mutableListOf()
        override suspend fun execute(args: WriteArgs): String {
            calls.add(args)
            return "rolled back"
        }
    }

    private fun createGraphWithOptionalToolCallAndRollback(checkpointId: String, appendToolCall: Boolean) = strategy("ckpt-with-tool") {
        // Node that emits simple output
        val node1 by simpleNode(output = "Node 1 output")

        // Node that creates a checkpoint
        val checkpointNode by node<String, String>("checkpointNode") {
            val input = it
            withPersistency(this) { ctx ->
                createCheckpoint(ctx, currentNodeId ?: error("currentNodeId not set"), input, typeOf<String>(), checkpointId)
                llm.writeSession { updatePrompt { user { text("Checkpoint created with ID: $checkpointId") } } }
            }
            "$input\nCheckpoint Created"
        }

        // Node that optionally appends a tool call after the checkpoint
        val toolCallNode by node<String, String>("toolCallNode") {
            val input = it
            if (appendToolCall) {
                llm.writeSession {
                    updatePrompt {
                        // Simulate a tool call message from the assistant/tools side
                        val args = WriteArgs("a", "1")
                        tool {
                            call(
                                id = "call-1",
                                tool = WriteKVTool.name,
                                content = WriteKVTool.encodeArgsToString(args)
                            )
                        }
                    }
                }
            }
            "$input\nAfter ToolCall"
        }

        // Rollback node that configures rollback registry and triggers rollback
        val rollbackNode by node<String, String>("rollbackNode") {
            withPersistency(this) {
                rollbackToCheckpoint(checkpointId, it)!!
                llm.writeSession { updatePrompt { user { text("Rolled back now") } } }
            }
            "$it\nrolled back"
        }

        val historyNode by collectHistoryNode("History Node")

        edge(nodeStart forwardTo node1)
        edge(node1 forwardTo checkpointNode)
        edge(checkpointNode forwardTo toolCallNode)
        edge(toolCallNode forwardTo rollbackNode)
        edge(rollbackNode forwardTo historyNode)
        edge(historyNode forwardTo nodeFinish)
    }

    @Test
    fun testAgentRestorationNoCheckpoint() = runTest {
        val agent = AIAgent(
            promptExecutor = getMockExecutor { },
            strategy = straightForwardGraphNoCheckpoint(),
            agentConfig = agentConfig,
            toolRegistry = toolRegistry
        ) {
            install(Persistency) {
                storage = InMemoryPersistencyStorageProvider("testAgentId")
            }
        }

        val output = agent.run("Start the test")
        assertEquals(
            "History: You are a test agent.\n" +
                "Node 1 output\n" +
                "Node 2 output",
            output
        )
    }

    @Test
    fun testRollbackToolsExecutedWhenTravelingBackInTime() = runTest {
        // Reset recorder
        DeleteKVRollbackTool.calls = mutableListOf()

        val localToolRegistry = ToolRegistry {
            tool(SayToUser)
            tool(WriteKVTool)
            tool(DeleteKVRollbackTool)
        }

        val agent = AIAgent(
            promptExecutor = getMockExecutor { },
            strategy = createGraphWithOptionalToolCallAndRollback("ckpt-1", appendToolCall = true),
            agentConfig = agentConfig,
            toolRegistry = localToolRegistry
        ) {
            install(Persistency) {
                storage = InMemoryPersistencyStorageProvider("agent-tools-rollback-1")
                rollbackToolRegistry = RollbackToolRegistry {
                    registerRollback(WriteKVTool, DeleteKVRollbackTool)
                }
            }
        }

        agent.run("Start")

        assertEquals(1, DeleteKVRollbackTool.calls.size)
        assertEquals("a", DeleteKVRollbackTool.calls[0].key)
        assertEquals("1", DeleteKVRollbackTool.calls[0].value)
    }

    @Test
    fun testRollbackToolsNotExecutedWhenNoDiff() = runTest {
        // Reset recorder
        DeleteKVRollbackTool.calls = mutableListOf()

        val localToolRegistry = ToolRegistry {
            tool(SayToUser)
            tool(WriteKVTool)
            tool(DeleteKVRollbackTool)
        }

        val agent = AIAgent(
            promptExecutor = getMockExecutor { },
            strategy = createGraphWithOptionalToolCallAndRollback("ckpt-2", appendToolCall = false),
            agentConfig = agentConfig,
            toolRegistry = localToolRegistry
        ) {
            install(Persistency) {
                storage = InMemoryPersistencyStorageProvider("agent-tools-rollback-2")
                rollbackToolRegistry = RollbackToolRegistry {
                    registerRollback(WriteKVTool, DeleteKVRollbackTool)
                }
            }
        }

        agent.run("Start")

        assertEquals(0, DeleteKVRollbackTool.calls.size)
    }

    @Test
    fun testRestoreFromSingleCheckpoint() = runTest {
        val checkpointStorageProvider = InMemoryPersistencyStorageProvider("testAgentId")
        val time = Clock.System.now()
        val agentId = "testAgentId"

        val testCheckpoint = AgentCheckpointData(
            checkpointId = "testCheckpointId",
            createdAt = time,
            nodeId = "Node2",
            lastInput = JsonPrimitive("Test input"),
            messageHistory = listOf(
                Message.User("User message", metaInfo = RequestMetaInfo(time)),
                Message.Assistant("Assistant message", metaInfo = ResponseMetaInfo(time))
            )
        )

        checkpointStorageProvider.saveCheckpoint(testCheckpoint)

        val agent = AIAgent(
            promptExecutor = getMockExecutor { },
            strategy = straightForwardGraphNoCheckpoint(),
            agentConfig = agentConfig,
            toolRegistry = toolRegistry,
            id = agentId
        ) {
            install(Persistency) {
                storage = checkpointStorageProvider
            }
        }

        val output = agent.run("Start the test")

        assertEquals(
            "History: User message\n" +
                "Assistant message\n" +
                "Node 2 output",
            output
        )
    }

    @Test
    fun testRestoreFromLatestCheckpoint() = runTest {
        val checkpointStorageProvider = InMemoryPersistencyStorageProvider("testAgentId")
        val time = Clock.System.now()
        val agentId = "testAgentId"

        val testCheckpoint = AgentCheckpointData(
            checkpointId = "testCheckpointId",
            createdAt = time,
            nodeId = "Node2",
            lastInput = JsonPrimitive("Test input"),
            messageHistory = listOf(
                Message.User("User message", metaInfo = RequestMetaInfo(time)),
                Message.Assistant("Assistant message", metaInfo = ResponseMetaInfo(time))
            )
        )

        val testCheckpoint2 = AgentCheckpointData(
            checkpointId = "testCheckpointId",
            createdAt = time - 10.seconds,
            nodeId = "Node1",
            lastInput = JsonPrimitive("Test input"),
            messageHistory = listOf(
                Message.User("User message", metaInfo = RequestMetaInfo(time)),
                Message.Assistant("Assistant message", metaInfo = ResponseMetaInfo(time))
            )
        )

        checkpointStorageProvider.saveCheckpoint(testCheckpoint)
        checkpointStorageProvider.saveCheckpoint(testCheckpoint2)

        val agent = AIAgent(
            promptExecutor = getMockExecutor { },
            strategy = straightForwardGraphNoCheckpoint(),
            agentConfig = agentConfig,
            toolRegistry = toolRegistry,
            id = agentId
        ) {
            install(Persistency) {
                storage = checkpointStorageProvider
            }
        }

        val output = agent.run("Start the test")

        assertEquals(
            "History: User message\n" +
                "Assistant message\n" +
                "Node 2 output",
            output
        )
    }
}
