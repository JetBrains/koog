import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.AIAgentService
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.dsl.builder.AIAgentGraphStrategyBuilder
import ai.koog.agents.core.dsl.builder.AIAgentNodeDelegate
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeDoNothing
import ai.koog.agents.core.dsl.extension.nodeExecuteTool
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.ext.tool.SayToUser
import ai.koog.agents.snapshot.feature.AgentCheckpointData
import ai.koog.agents.snapshot.feature.Persistence
import ai.koog.agents.snapshot.feature.RollbackToolRegistry
import ai.koog.agents.snapshot.feature.withPersistence
import ai.koog.agents.snapshot.providers.InMemoryPersistenceStorageProvider
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.llm.OllamaModels
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonPrimitive
import kotlin.math.absoluteValue
import kotlin.random.Random
import kotlin.reflect.typeOf
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

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
    fun testCheckpointsOneMoreTime() = runTest {
        val agent = AIAgent(
            promptExecutor = getMockExecutor { },
            strategy = strategy("name") {
                var loaded = false
                val node1 by node<String, String> {
                    println("node1")
                    it
                }
                val node2 by node<String, String> {
                    println("node2")
                    it
                }
                val checkpoint by node<String, String> { input ->
                    println("checkpoint save")
                    withPersistence { ctx ->
                        createCheckpoint(
                            agentContext = ctx,
                            nodeId = currentNodeId ?: error("currentNodeId not set"),
                            lastInput = input,
                            lastInputType = typeOf<String>(),
                            checkpointId = "cpt-100500",
                            version = 0
                        )
                    }
                    input
                }
                val node3 by node<String, String> {
                    println("node3")
                    it
                }
                val loadCheckpoint by node<String, String> {
                    println("checkpoint load")
                    if (!loaded) {
                        loaded = true
                        withPersistence { ctx ->
                            rollbackToCheckpoint("cpt-100500", ctx)
                        }
                    }
                    it
                }
                val node4 by node<String, String> {
                    println("node4")
                    it
                }

                nodeStart then node1 then node2 then checkpoint then node3 then loadCheckpoint then node4 then nodeFinish
            },
            agentConfig = agentConfig,
            toolRegistry = toolRegistry
        ) {
            install(Persistence) {
                storage = InMemoryPersistenceStorageProvider()
            }
        }

        val output = agent.run("Start the test")
        assertEquals(
            "Start the test",
            output
        )
    }

    @Test
    fun testAgentExecutionWithRollback() = runTest {
        val agent = AIAgent(
            promptExecutor = getMockExecutor { },
            strategy = createCheckpointGraphWithRollback("checkpointId"),
            agentConfig = agentConfig,
            toolRegistry = toolRegistry
        ) {
            install(Persistence) {
                storage = InMemoryPersistenceStorageProvider()
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
        checkpointId: String
    ): TestRollbackableStrategy {
        val commands = Channel<String>(capacity = 100500)
        val notifications = Channel<String>(capacity = 100500)

        fun AIAgentGraphStrategyBuilder<String, String>.callUserToolNode(
            userName: String,
            userData: String
        ): AIAgentNodeDelegate<String, String> = node<String, String> {
            llm.writeSession {
                val args = WriteArgs(userName, userData)
                val result = callTool(WriteKVTool, args).asSuccessful().result
                val callID = Random.nextInt().absoluteValue
                updatePrompt {
                    tool {
                        call(id = "$callID", tool = WriteKVTool.name, content = WriteKVTool.encodeArgsToString(args))
                        result(
                            id = "$callID",
                            tool = WriteKVTool.name,
                            content = WriteKVTool.encodeResultToString(result)
                        )
                    }
                }
            }
            it
        }

        val strategy = strategy("ckpt-with-tool") {
            // Node that emits simple output
            val textNode1 by simpleNode(output = "Node 1 output")

            nodeExecuteTool()
            val createUser1 by callUserToolNode("user-1", "good man")

            // Node that creates a checkpoint
            val saveCheckpoint by node<String, Unit> { input ->
                withPersistence { ctx ->
                    createCheckpoint(
                        ctx,
                        currentNodeId ?: error("currentNodeId not set"),
                        input,
                        typeOf<String>(),
                        checkpointId = checkpointId,
                        version = 0
                    )
                    llm.writeSession { updatePrompt { user { text("Checkpoint created with ID: $checkpointId") } } }
                }
            }

            val awaitCommands1 by node<Unit, String> {
                notifications.send("after-checkpoint")
                commands.receive()
                ""
            }

            val createUser2 by callUserToolNode("user-2", "very good man")

            val textNode2 by simpleNode(output = "Node 2 output")

            val createUser3 by callUserToolNode("user-3", "the best man")

            val awaitCommands2 by node<String, String> {
                println("ctx inside: $this")
                println("ctx inside [hash]: ${this.hashCode()}")
                notifications.send("await-command")
                commands.receive()
            }

            val someOtherNode by nodeDoNothing<String>()

            nodeStart then textNode1 then createUser1 then saveCheckpoint then awaitCommands1
            awaitCommands1 then createUser2 then textNode2 then createUser3 then awaitCommands2 then someOtherNode then nodeFinish
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
            install(Persistence) {
                storage = InMemoryPersistenceStorageProvider()
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

        val rollbackConfig = createGraphWithOptionalToolCallAndRollback("ckpt-1")

        val agentService = AIAgentService(
            promptExecutor = getMockExecutor { },
            strategy = rollbackConfig.strategy,
            agentConfig = agentConfig,
            toolRegistry = localToolRegistry
        ) {
            install(Persistence) {
                storage = InMemoryPersistenceStorageProvider()
                rollbackToolRegistry = RollbackToolRegistry {
                    registerRollback(WriteKVTool, DeleteKVTool)
                }
            }
        }

        val agent = agentService.createAgent()

        val agentResult = async {
            println("agent.run()")
            agent.run("Input")
        }

        println("before second launch")

        launch {
            assertEquals("after-checkpoint", rollbackConfig.notifications.receive())
            rollbackConfig.commands.send("continue")

            assertEquals("await-command", rollbackConfig.notifications.receive())

            assertEquals(3, databaseMap.size)
            assertContains(databaseMap, "user-1")
            assertContains(databaseMap, "user-2")
            assertContains(databaseMap, "user-3")

            agent.withPersistence { ctx ->
                println("ctx outside: $this")
                println("ctx outside [hash]: ${this.hashCode()}")
                rollbackToCheckpoint("ckpt-1", ctx)
            }

            rollbackConfig.commands.send("go further!")

            assertEquals("after-checkpoint", rollbackConfig.notifications.receive())

            assertEquals(1, databaseMap.size)
            assertContains(databaseMap, "user-1")

            rollbackConfig.commands.send("continue")

            assertEquals("await-command", rollbackConfig.notifications.receive())
            rollbackConfig.commands.send("try to go to finish")
        }

        val result = agentResult.await()
        println("Result: $result")
    }

    @Test
    fun testRestoreFromSingleCheckpoint() = runTest {
        val checkpointStorageProvider = InMemoryPersistenceStorageProvider()
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
            ),
            version = 0
        )

        checkpointStorageProvider.saveCheckpoint(agentId, testCheckpoint)

        val agent = AIAgent(
            promptExecutor = getMockExecutor { },
            strategy = straightForwardGraphNoCheckpoint(),
            agentConfig = agentConfig,
            toolRegistry = toolRegistry,
            id = agentId
        ) {
            install(Persistence) {
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
        val checkpointStorageProvider = InMemoryPersistenceStorageProvider()
        val time = Clock.System.now()
        val agentId = "testAgentId"

        val testCheckpoint2 = AgentCheckpointData(
            checkpointId = "testCheckpointId",
            createdAt = time,
            nodeId = "Node1",
            lastInput = JsonPrimitive("Test input"),
            messageHistory = listOf(
                Message.User("User message", metaInfo = RequestMetaInfo(time)),
                Message.Assistant("Assistant message", metaInfo = ResponseMetaInfo(time))
            ),
            version = 0
        )

        val testCheckpoint = AgentCheckpointData(
            checkpointId = "testCheckpointId",
            createdAt = time,
            nodeId = "Node2",
            lastInput = JsonPrimitive("Test input"),
            messageHistory = listOf(
                Message.User("User message", metaInfo = RequestMetaInfo(time)),
                Message.Assistant("Assistant message", metaInfo = ResponseMetaInfo(time))
            ),
            version = testCheckpoint2.version + 1
        )

        checkpointStorageProvider.saveCheckpoint(agentId, testCheckpoint2)
        checkpointStorageProvider.saveCheckpoint(agentId, testCheckpoint)

        val agent = AIAgent(
            promptExecutor = getMockExecutor { },
            strategy = straightForwardGraphNoCheckpoint(),
            agentConfig = agentConfig,
            toolRegistry = toolRegistry,
            id = agentId
        ) {
            install(Persistence) {
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
