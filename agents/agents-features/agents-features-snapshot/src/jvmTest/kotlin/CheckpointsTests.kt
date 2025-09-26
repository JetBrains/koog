import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.AIAgentSession
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeExecuteSingleTool
import ai.koog.agents.core.dsl.extension.nodeExecuteTool
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.ext.tool.SayToUser
import ai.koog.agents.snapshot.feature.AgentCheckpointData
import ai.koog.agents.snapshot.feature.Persistency
import ai.koog.agents.snapshot.feature.RollbackToolRegistry
import ai.koog.agents.snapshot.feature.persistency
import ai.koog.agents.snapshot.feature.withPersistency
import ai.koog.agents.snapshot.providers.InMemoryPersistencyStorageProvider
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.llm.OllamaModels
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.assertDoesNotThrow
import kotlin.reflect.typeOf
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

val databaseMap: MutableMap<String, String> = mutableMapOf()

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


    object WriteKVTool : Tool<WriteArgs, String>() {
        override val argsSerializer: KSerializer<WriteArgs> = WriteArgs.serializer()
        override val resultSerializer: KSerializer<String> = String.serializer()
        override val description: String = "Writes a key-value pair (simulated)"
        override suspend fun execute(args: WriteArgs): String {
            databaseMap[args.key] = args.value
            return "ok"
        }
    }

    object DeleteKVTool : Tool<WriteArgs, String>() {
        override val argsSerializer: KSerializer<WriteArgs> = WriteArgs.serializer()
        override val resultSerializer: KSerializer<String> = String.serializer()
        override val description: String = "Deletes a key-value pair (rollback)"
        var calls: MutableList<WriteArgs> = mutableListOf()
        override suspend fun execute(args: WriteArgs): String {
            databaseMap.remove(args.key)
            return "rolled back"
        }
    }

    private data class TestRollbackableStrategy(
        val strategy: AIAgentGraphStrategy<String, String>,
        val notifications: Channel<String>,
        val commands: Channel<String>
    )

    private fun createGraphWithOptionalToolCallAndRollback(
        checkpointId: String,
        appendToolCall: Boolean
    ): TestRollbackableStrategy {
        val commands = Channel<String>(capacity = 100500)
        val notifications = Channel<String>(capacity = 100500)

        val strategy = strategy("ckpt-with-tool") {
            // Node that emits simple output
            val textNode1 by simpleNode(output = "Node 1 output")

            val createUser1 by node<String, String> {
                llm.writeSession {
                    callTool(WriteKVTool, WriteArgs("user-1", "good man"))
                }
                it
            }

            // Node that creates a checkpoint
            val saveCheckpoint by node<String, Unit>("checkpointNode") { input ->
                withPersistency(this) { ctx ->
                    createCheckpoint(
                        ctx,
                        currentNodeId ?: error("currentNodeId not set"),
                        input,
                        typeOf<String>(),
                        checkpointId
                    )
                    llm.writeSession { updatePrompt { user { text("Checkpoint created with ID: $checkpointId") } } }
                }
            }

            val awaitCommands1 by node<Unit, Unit> {
                notifications.send("after-checkpoint")
                commands.receive()
            }

            val createUser2 by node<Unit, Unit> {
                llm.writeSession {
                    callTool(WriteKVTool, WriteArgs("user-2", "very good man"))
                }
            }

            val textNode2 by simpleNode(output = "Node 2 output")

            val createUser3 by node<Unit, Unit> {
                llm.writeSession {
                    callTool(WriteKVTool, WriteArgs("user-3", "the best man"))
                }
            }

            val awaitCommands2 by node<Unit, String> {
                notifications.send("await-command")
                commands.receive()
            }

            nodeStart then textNode1 then createUser1 then saveCheckpoint then awaitCommands1
            awaitCommands1 then createUser2 then createUser3 then awaitCommands2 then nodeFinish
        }

        return TestRollbackableStrategy(
            strategy = strategy,
            notifications = notifications,
            commands = commands
        )
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
        DeleteKVTool.calls = mutableListOf()

        val localToolRegistry = ToolRegistry {
            tool(SayToUser)
            tool(WriteKVTool)
            tool(DeleteKVTool)
        }

        val rollbackConfig = createGraphWithOptionalToolCallAndRollback("ckpt-1", appendToolCall = true)

        val agent = AIAgent(
            promptExecutor = getMockExecutor { },
            strategy = rollbackConfig.strategy,
            agentConfig = agentConfig,
            toolRegistry = localToolRegistry
        ) {
            install(Persistency) {
                storage = InMemoryPersistencyStorageProvider("agent-tools-rollback-1")
                rollbackToolRegistry = RollbackToolRegistry {
                    registerRollback(WriteKVTool, DeleteKVTool)
                }
            }
        }

        var sessionDef: CompletableDeferred<AIAgentSession<String, String>> = CompletableDeferred()

        val task1 = GlobalScope.launch(Dispatchers.Default.limitedParallelism(3)) {
            sessionDef.complete(agent.launch("Start"))
        }

        val task2 = GlobalScope.launch(Dispatchers.Default.limitedParallelism(3)) {
            val session = sessionDef.await()
            assertEquals("after-checkpoint", rollbackConfig.notifications.receive())
            rollbackConfig.commands.send("continue")

            assertEquals("await-command", rollbackConfig.notifications.receive())

            assertEquals(3, databaseMap.size)
            assertContains(databaseMap, "user-1")
            assertContains(databaseMap, "user-2")
            assertContains(databaseMap, "user-3")

            session.withContext {
                persistency().rollbackToCheckpoint("checkpoint-1", this)
            }

            assertEquals("after-checkpoint", rollbackConfig.notifications.receive())

            assertEquals(1, databaseMap.size)
            assertContains(databaseMap, "user-1")

            rollbackConfig.commands.send("continue")

            assertEquals("await-command", rollbackConfig.notifications.receive())

            assertDoesNotThrow {
                session.stop()
            }
        }

        task1.join()
        task2.join()
    }

//    @Test
//    fun testRollbackToolsNotExecutedWhenNoDiff() = runTest {
//        // Reset recorder
//        DeleteKVTool.calls = mutableListOf()
//
//        val localToolRegistry = ToolRegistry {
//            tool(SayToUser)
//            tool(WriteKVTool)
//            tool(DeleteKVTool)
//        }
//
//        val agent = AIAgent(
//            promptExecutor = getMockExecutor { },
//            strategy = createGraphWithOptionalToolCallAndRollback("ckpt-2", appendToolCall = false),
//            agentConfig = agentConfig,
//            toolRegistry = localToolRegistry
//        ) {
//            install(Persistency) {
//                storage = InMemoryPersistencyStorageProvider("agent-tools-rollback-2")
//                rollbackToolRegistry = RollbackToolRegistry {
//                    registerRollback(WriteKVTool, DeleteKVTool)
//                }
//            }
//        }
//
//        agent.run("Start")
//
//        assertEquals(0, DeleteKVTool.calls.size)
//    }

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
