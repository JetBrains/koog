@file:Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")

package ai.koog.agents.snapshot

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.AIAgentService
import ai.koog.agents.core.agent.GraphAIAgentService
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.agent.entity.AIAgentStorage
import ai.koog.agents.core.agent.entity.createStorageKey
import ai.koog.agents.core.agent.execution.path
import ai.koog.agents.core.agent.session.callTool
import ai.koog.agents.core.dsl.builder.AIAgentGraphStrategyBuilder
import ai.koog.agents.core.dsl.builder.AIAgentNodeDelegate
import ai.koog.agents.core.dsl.builder.node
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.ToolCalls
import ai.koog.agents.core.dsl.extension.nodeDoNothing
import ai.koog.agents.core.dsl.extension.nodeExecuteTools
import ai.koog.agents.core.dsl.extension.nodeLLMRequest
import ai.koog.agents.core.dsl.extension.nodeLLMSendToolResults
import ai.koog.agents.core.dsl.extension.onTextMessage
import ai.koog.agents.core.dsl.extension.onToolCalls
import ai.koog.agents.core.feature.message.FeatureMessage
import ai.koog.agents.core.feature.message.FeatureMessageProcessor
import ai.koog.agents.core.feature.model.events.LLMCallCompletedEvent
import ai.koog.agents.core.feature.model.events.LLMCallStartingEvent
import ai.koog.agents.core.feature.model.events.NodeExecutionCompletedEvent
import ai.koog.agents.core.feature.model.events.NodeExecutionStartingEvent
import ai.koog.agents.core.feature.model.events.ToolCallCompletedEvent
import ai.koog.agents.core.feature.model.events.ToolCallStartingEvent
import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.ext.tool.SayToUser
import ai.koog.agents.features.tracing.feature.Tracing
import ai.koog.agents.snapshot.feature.AgentCheckpointData
import ai.koog.agents.snapshot.feature.GraphCheckpointProperties
import ai.koog.agents.snapshot.feature.Persistence
import ai.koog.agents.snapshot.feature.RollbackToolRegistry
import ai.koog.agents.snapshot.feature.withPersistence
import ai.koog.agents.snapshot.providers.InMemoryPersistenceStorageProvider
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.ollama.client.OllamaModels
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.params.LLMParams
import ai.koog.serialization.JSONPrimitive
import ai.koog.serialization.kotlinx.KotlinxSerializer
import ai.koog.serialization.kotlinx.toKoogJSONElement
import ai.koog.serialization.typeToken
import ai.koog.utils.time.KoogClock
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlin.math.absoluteValue
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

val databaseMap: MutableMap<String, String> = mutableMapOf()

class CheckpointsTests {

    private val serializer = KotlinxSerializer()

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
            promptExecutor = getMockExecutor(serializer) { },
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
                        createCheckpointAfterNode(
                            agentContext = ctx,
                            nodePath = ctx.executionInfo.path(),
                            lastOutput = input,
                            lastOutputType = typeToken<String>(),
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
            promptExecutor = getMockExecutor(serializer) { },
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

    object WriteKVTool : Tool<WriteArgs, String>(
        argsType = typeToken<WriteArgs>(),
        resultType = typeToken<String>(),
        name = "write_kv",
        description = "Writes a key-value pair (simulated)"
    ) {
        override suspend fun execute(args: WriteArgs): String {
            databaseMap[args.key] = args.value
            return "ok"
        }
    }

    object DeleteKVTool : Tool<WriteArgs, String>(
        argsType = typeToken<WriteArgs>(),
        resultType = typeToken<String>(),
        name = "delete_kv",
        description = "Deletes a key-value pair (rollback)"
    ) {
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
                appendPrompt {
                    toolCall(
                        id = "$callID",
                        tool = WriteKVTool.name,
                        args = WriteKVTool.encodeArgsToString(args, serializer)
                    )
                    toolResult(
                        id = "$callID",
                        tool = WriteKVTool.name,
                        output = WriteKVTool.encodeResultToString(result, serializer)
                    )
                }
            }
            it
        }

        val strategy = strategy("ckpt-with-tool") {
            // Node that emits simple output
            val textNode1 by simpleNode(output = "Node 1 output")

            nodeExecuteTools()
            val createUser1 by callUserToolNode("user-1", "good man")

            // Node that creates a checkpoint
            val saveCheckpoint by node<String, Unit> { input ->
                withPersistence { ctx ->
                    llm.writeSession { appendPrompt { user { text("Checkpoint created with ID: $checkpointId") } } }
                    createCheckpointAfterNode(
                        ctx,
                        ctx.executionInfo.path(),
                        Unit,
                        typeToken<Unit>(),
                        checkpointId = checkpointId,
                        version = 0
                    )
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
            promptExecutor = getMockExecutor(serializer) { },
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

        val agentService: GraphAIAgentService<String, String> = AIAgentService(
            promptExecutor = getMockExecutor(serializer) { },
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

        val session = agent.createSession()
        val agentResult = async {
            println("agent.run()")
            session.run("Input")
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

            session.withPersistence { agent ->
                println("ctx outside: $this")
                println("ctx outside [hash]: ${this.hashCode()}")
                rollbackToCheckpoint("ckpt-1", agent)
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
        val time = KoogClock.System.now()
        val convId = "testAgentId"

        // Checkpoint represents "Node2 has completed with the given lastOutput".
        // The messageHistory must already include Node2's contribution since we won't re-execute it.
        val testCheckpoint = AgentCheckpointData(
            checkpointId = "testCheckpointId",
            createdAt = time,
            messageHistory = listOf(
                Message.User("User message", metaInfo = RequestMetaInfo(time)),
                Message.Assistant("Assistant message", metaInfo = ResponseMetaInfo(time)),
                Message.User("Node 2 output", metaInfo = RequestMetaInfo(time))
            ),
            version = 0,
            graphProperties = GraphCheckpointProperties(
                nodePath = path(convId, "straight-forward", "Node2"),
                lastOutput = JSONPrimitive("Node 2 output")
            ),
            plannerProperties = null,
            properties = null,
        )

        checkpointStorageProvider.saveCheckpoint(convId, testCheckpoint)

        val agent = AIAgent(
            promptExecutor = getMockExecutor(serializer) { },
            strategy = straightForwardGraphNoCheckpoint(),
            agentConfig = agentConfig,
            toolRegistry = toolRegistry,
        ) {
            install(Persistence) {
                storage = checkpointStorageProvider
            }
        }

        val output = agent.run("Start the test", convId)

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
        val time = KoogClock.System.now()
        val sessionId = "testAgentId"

        val testCheckpoint2 = AgentCheckpointData(
            checkpointId = "testCheckpointId",
            createdAt = time,
            messageHistory = listOf(
                Message.User("User message", metaInfo = RequestMetaInfo(time)),
                Message.Assistant("Assistant message", metaInfo = ResponseMetaInfo(time)),
                Message.User("Node 1 output", metaInfo = RequestMetaInfo(time))
            ),
            version = 0,
            graphProperties = GraphCheckpointProperties(
                nodePath = path(sessionId, "straight-forward", "Node1"),
                lastOutput = JSONPrimitive("Node 1 output")
            ),
            plannerProperties = null,
            properties = null,
        )

        val testCheckpoint = AgentCheckpointData(
            checkpointId = "testCheckpointId",
            createdAt = time,
            messageHistory = listOf(
                Message.User("User message", metaInfo = RequestMetaInfo(time)),
                Message.Assistant("Assistant message", metaInfo = ResponseMetaInfo(time)),
                Message.User("Node 2 output", metaInfo = RequestMetaInfo(time))
            ),
            version = testCheckpoint2.version + 1,
            graphProperties = GraphCheckpointProperties(
                nodePath = path(sessionId, "straight-forward", "Node2"),
                lastOutput = JSONPrimitive("Node 2 output")
            ),
            plannerProperties = null,
            properties = null,
        )

        checkpointStorageProvider.saveCheckpoint(sessionId, testCheckpoint2)
        checkpointStorageProvider.saveCheckpoint(sessionId, testCheckpoint)

        val agent = AIAgent(
            promptExecutor = getMockExecutor(serializer) { },
            strategy = straightForwardGraphNoCheckpoint(),
            agentConfig = agentConfig,
            toolRegistry = toolRegistry,
        ) {
            install(Persistence) {
                storage = checkpointStorageProvider
            }
        }

        val output = agent.run("Start the test", sessionId = sessionId)

        assertEquals(
            "History: User message\n" +
                "Assistant message\n" +
                "Node 2 output",
            output
        )
    }

    class TestTracer : FeatureMessageProcessor() {
        val processedMessages = mutableListOf<FeatureMessage>()

        private var _isOpen = MutableStateFlow(false)

        override val isOpen: StateFlow<Boolean>
            get() = _isOpen.asStateFlow()

        override suspend fun initialize() {
            super.initialize()
            _isOpen.value = true
        }

        override suspend fun processMessage(message: FeatureMessage) {
            processedMessages.add(message)
        }

        override suspend fun close() {
            _isOpen.value = false
        }

        fun clear() {
            processedMessages.clear()
        }

        fun traceAsString(): String = buildString {
            appendLine("Trace:")
            processedMessages.forEach {
                when (it) {
                    is NodeExecutionStartingEvent -> appendLine(" - enter node: `${it.nodeName}`")
                    is NodeExecutionCompletedEvent -> appendLine(" - exit node: `${it.nodeName}`")
                    is LLMCallStartingEvent -> appendLine("       - LLM call: `${it.prompt.messages.last().displayText()}`")
                    is LLMCallCompletedEvent -> appendLine("       - LLM response: `${it.response?.displayText().orEmpty()}`")
                    is ToolCallStartingEvent -> appendLine("       - tool call: `${it.toolName}` (${it.toolArgs})")
                    is ToolCallCompletedEvent -> appendLine("       - tool result: `${it.toolName}` == ${it.result}")
                }
            }
        }

        /**
         * Renders a [Message] as the human-readable text the trace was historically built around.
         * Pre-refactor `Message.toString()` returned just the content; now it returns the full
         * data-class form, so we extract the relevant text out of the part list ourselves:
         * Text parts emit their `text`, Tool.Call parts emit their JSON `args`, Tool.Result parts
         * emit their `output`. Multiple parts are joined with newlines.
         */
        private fun Message.displayText(): String = parts.joinToString(separator = "\n") { part ->
            when (part) {
                is MessagePart.Text -> part.text
                is MessagePart.Tool.Call -> part.args
                is MessagePart.Tool.Result -> part.output
                is MessagePart.Reasoning -> part.content.joinToString("\n")
                is MessagePart.Attachment -> ""
            }
        }
    }

    class CLI(private val userAnswer: (String) -> String? = { null }) {
        enum class Role {
            USER,
            SYSTEM
        }

        var currentLines: MutableList<String> = mutableListOf()
        private val lock = Mutex()

        suspend fun printLN(text: String) = lock.withLock {
            currentLines += text
        }

        suspend fun readLN(): String = lock.withLock {
            val latestLine = currentLines.lastOrNull() ?: ""
            val answer = userAnswer(latestLine) ?: throw IllegalStateException("No answer provided for `$latestLine`")
            currentLines += answer
            return answer
        }

        suspend fun clear() = lock.withLock {
            currentLines.clear()
        }

        suspend fun text(): String = lock.withLock {
            currentLines.joinToString("\n")
        }
    }

    class AskCLIQuestion(val cli: CLI) : SimpleTool<AskCLIQuestion.Args>(
        typeToken<Args>(),
        "ask",
        "prints line in CLI and reads user's response"
    ) {
        @Serializable
        data class Args(val message: String)

        override suspend fun execute(args: Args): String {
            cli.printLN(args.message)
            return cli.readLN()
        }
    }

    @Test
    fun testLastSuccessfulNodeIsNotExecutedTwice() = runTest {
        // Expected communication (via tool):
        // user input: Test my Earth knowledge
        // `askQuestion` tool : Is the Earth a sphere?
        // user output: Yes
        // `askQuestion` tool : Why?
        // user output: Because when ships sail away, they start to disappear from the bottom
        // `askQuestion` tool : Who discovered this?
        // user output: Ferdinand Magellan
        // assistant: Excellent job! You are smart

        val cli = CLI { systemMessage ->
            when (systemMessage) {
                "Is the Earth a sphere?" -> "Yes"
                "Why?" -> "Because when ships sail away, they start to disappear from the bottom"
                "Who discovered this?" -> "Ferdinand Magellan"
                else -> null
            }
        }
        val askQuestion = AskCLIQuestion(cli)

        val localToolRegistry = ToolRegistry {
            tool(askQuestion)
        }

        var counter = 0
        var isFirstRun = true

        val checkpointStorage = InMemoryPersistenceStorageProvider()

        fun agentInterrupted(): Boolean = isFirstRun && (counter++ > 1)

        val tracer = TestTracer()

        val agent = AIAgent(
            promptExecutor = getMockExecutor(serializer) {
                mockLLMToolCall(
                    askQuestion,
                    AskCLIQuestion.Args("Is the Earth a sphere?")
                ) onRequestEquals "Test my Earth knowledge"
                mockLLMToolCall(askQuestion, AskCLIQuestion.Args("Why?")) onRequestEquals "Yes"
                mockLLMToolCall(askQuestion, AskCLIQuestion.Args("Why?")) onRequestEquals "Yes"
                mockLLMToolCall(
                    askQuestion,
                    AskCLIQuestion.Args("Who discovered this?")
                ) onRequestEquals "Because when ships sail away, they start to disappear from the bottom"
                mockLLMAnswer("Excellent job! You are smart") onRequestEquals "Ferdinand Magellan"
            },
            strategy = strategy("simple-with-interrupt") {
                val callLLM by nodeLLMRequest()
                val executeTool by nodeExecuteTools()
                val sendToolResult by nodeLLMSendToolResults()

                val nodeThrow by node<Any?, String> { throw Exception("TERMINATED AFTER THIRD TOOL CALL") }

                edge(nodeStart forwardTo callLLM)
                edge(callLLM forwardTo executeTool onToolCalls { true })
                edge(callLLM forwardTo nodeFinish onTextMessage { true })
                edge(executeTool forwardTo sendToolResult onCondition { !agentInterrupted() })
                edge(executeTool forwardTo nodeThrow onCondition { agentInterrupted() })
                edge(sendToolResult forwardTo executeTool onToolCalls { true })
                edge(sendToolResult forwardTo nodeFinish onTextMessage { true })
            },
            agentConfig = agentConfig,
            toolRegistry = localToolRegistry
        ) {
            install(Persistence) {
                storage = checkpointStorage
            }

            install(Tracing) {
                addMessageProcessor(tracer)
            }
        }

        println("Running agent first time")

        val convId = "my-conv-id"
        val output = runCatching {
            agent.run("Test my Earth knowledge", sessionId = convId)
        }.getOrElse { it.message }

        println("Finished first run")

        assertEquals("TERMINATED AFTER THIRD TOOL CALL", output)

        assertEquals(
            """
                Trace:
                 - enter node: `__start__`
                 - exit node: `__start__`
                 - enter node: `callLLM`
                       - LLM call: `Test my Earth knowledge`
                       - LLM response: `{"message":"Is the Earth a sphere?"}`
                 - exit node: `callLLM`
                 - enter node: `executeTool`
                       - tool call: `ask` ({"message":"Is the Earth a sphere?"})
                       - tool result: `ask` == "Yes"
                 - exit node: `executeTool`
                 - enter node: `sendToolResult`
                       - LLM call: `Yes`
                       - LLM response: `{"message":"Why?"}`
                 - exit node: `sendToolResult`
                 - enter node: `executeTool`
                       - tool call: `ask` ({"message":"Why?"})
                       - tool result: `ask` == "Because when ships sail away, they start to disappear from the bottom"
                 - exit node: `executeTool`
                 - enter node: `sendToolResult`
                       - LLM call: `Because when ships sail away, they start to disappear from the bottom`
                       - LLM response: `{"message":"Who discovered this?"}`
                 - exit node: `sendToolResult`
                 - enter node: `executeTool`
                       - tool call: `ask` ({"message":"Who discovered this?"})
                       - tool result: `ask` == "Ferdinand Magellan"
                 - exit node: `executeTool`
                 - enter node: `nodeThrow`
            """.trimIndent(),
            tracer.traceAsString().trimIndent()
        )

        val lastCheckpoint = checkpointStorage.getLatestCheckpoint(convId)!!
        val lastMessageHistory = lastCheckpoint.messageHistory.joinToString("\n") { msg ->
            formatHistoryMessage(msg)
        }

        assertEquals(
            """
                - system: You are a test agent.
                - user: Test my Earth knowledge
                - tool call `ask` ({"message":"Is the Earth a sphere?"})
                - tool result `ask` == Yes
                - tool call `ask` ({"message":"Why?"})
                - tool result `ask` == Because when ships sail away, they start to disappear from the bottom
                - tool call `ask` ({"message":"Who discovered this?"})
            """.trimIndent(),
            lastMessageHistory
        )

        val nodePath = lastCheckpoint.graphProperties?.nodePath
        assertEquals(nodePath?.endsWith("executeTool"), true, "Last checkpoint node should be `executeTool`")

        val lastOutput = lastCheckpoint.graphProperties?.lastOutput
        assertTrue(
            lastOutput.toString().contains("Ferdinand Magellan"),
            message = "Last checkpointed node should be an `executeTool` with \"Ferdinand Magellan\" as an output (already calculated)"
        )

        println("Running agent second time")
        isFirstRun = false
        tracer.clear()

        val output2 = agent.run("Test my Earth knowledge", convId)

        println("Finished second run")

        assertEquals("Excellent job! You are smart", output2)

        // EXPECT THAT "tool call: `ask` ({"message":"Who discovered this?"})" WILL NOT HAPPEN TWICE!!!!!!!
        assertEquals(
            """
                Trace:
                 - enter node: `sendToolResult`
                       - LLM call: `Ferdinand Magellan`
                       - LLM response: `Excellent job! You are smart`
                 - exit node: `sendToolResult`
                 - enter node: `__finish__`
                 - exit node: `__finish__`
            """.trimIndent(),
            tracer.traceAsString().trimIndent()
        )
    }

    @Test
    fun testContextDataIsSavedInCheckpoint() = runTest {
        val storageKey = createStorageKey<String>("test-value")
        val checkpointProvider = InMemoryPersistenceStorageProvider()
        val sessionId = "storage-checkpoint-session"
        val expectedLlmParams = LLMParams(temperature = 0.42)

        val agent = AIAgent(
            promptExecutor = getMockExecutor(serializer) { },
            strategy = strategy("storage-checkpoint") {
                // write a key to the storage that will be persisted in the checkpoint
                val writeNode by node<String, String>("writeNode") {
                    storage.set(storageKey, "persisted-value")
                    it
                }

                val checkpointNode by node<String, String>("checkpointNode") {
                    withPersistence { ctx ->
                        createCheckpointAfterNode(
                            agentContext = ctx,
                            nodePath = ctx.executionInfo.path(),
                            lastOutput = it,
                            lastOutputType = typeToken<String>(),
                            version = 0L
                        )
                    }
                    it
                }

                nodeStart then writeNode then checkpointNode then nodeFinish
            },
            agentConfig = AIAgentConfig(
                prompt = prompt("test") {
                    system(systemPrompt)
                }.copy(params = expectedLlmParams),
                model = OllamaModels.Meta.LLAMA_3_2,
                maxAgentIterations = 20
            ),
            toolRegistry = toolRegistry
        ) {
            install(Persistence) {
                storage = checkpointProvider
            }
        }

        agent.run("start", sessionId = sessionId)

        // get second to last checkpoint because the latest checkpoint is a tombstone checkpoint that doesn't have storage and message history
        val checkpoint = checkpointProvider.getCheckpoints(sessionId).let { it[it.lastIndex - 1] }

        // Verify storage is saved
        val restoredStorage = AIAgentStorage(serializer)
        restoredStorage.putAllSerialized(checkpoint.storage?.entries ?: emptyMap())
        assertEquals("persisted-value", restoredStorage.get(storageKey))

        // Verify LLM model, params, tools, and iteration count are saved
        assertEquals(OllamaModels.Meta.LLAMA_3_2, checkpoint.llmModel)
        assertEquals(expectedLlmParams, checkpoint.llmParams)
        assertEquals(listOf(SayToUser.name), checkpoint.tools)
        assertEquals(3, checkpoint.agentIterations)
    }

    /**
     * The idea of this test is to evaluate the following situation:
     * 1. some checkpoint has been saved by an older version of a Koog agent (before 0.6.1) and is ALREADY saved to the persistence storage (ex: datab)
     * */
    @org.junit.jupiter.api.Disabled("Tests removed `lastInput` re-execution semantics; lastInput was removed alongside the deprecated APIs.")
    @Test
    fun testLastSuccessfulNodeExecutedTwiceInCompatibilityModeWithOlderCheckpointVersions() = runTest {
        val cli = CLI { systemMessage ->
            when (systemMessage) {
                "Is the Earth a sphere?" -> "Yes"
                "Why?" -> "Because when ships sail away, they start to disappear from the bottom"
                "Who discovered this?" -> "Ferdinand Magellan"
                else -> null
            }
        }
        val askQuestion = AskCLIQuestion(cli)

        val localToolRegistry = ToolRegistry {
            tool(askQuestion)
        }

        val convId = "my-conv-id"
        var counter = 0
        var isFirstRun = true

        val checkpointStorage = InMemoryPersistenceStorageProvider()

        fun agentInterrupted(): Boolean = isFirstRun && (counter++ > 1)

        val tracer = TestTracer()

        val agent = AIAgent(
            promptExecutor = getMockExecutor(serializer) {
                mockLLMToolCall(
                    askQuestion,
                    AskCLIQuestion.Args("Is the Earth a sphere?")
                ) onRequestEquals "Test my Earth knowledge"
                mockLLMToolCall(askQuestion, AskCLIQuestion.Args("Why?")) onRequestEquals "Yes"
                mockLLMToolCall(askQuestion, AskCLIQuestion.Args("Why?")) onRequestEquals "Yes"
                mockLLMToolCall(
                    askQuestion,
                    AskCLIQuestion.Args("Who discovered this?")
                ) onRequestEquals "Because when ships sail away, they start to disappear from the bottom"
                mockLLMAnswer("Excellent job! You are smart") onRequestEquals "Ferdinand Magellan"
            },
            strategy = strategy("simple-with-interrupt") {
                val callLLM by nodeLLMRequest()
                val executeTool by nodeExecuteTools()
                val sendToolResult by nodeLLMSendToolResults()

                val nodeThrow by node<Any?, String> { throw Exception("TERMINATED AFTER THIRD TOOL CALL") }

                edge(nodeStart forwardTo callLLM)
                edge(callLLM forwardTo executeTool onToolCalls { true })
                edge(callLLM forwardTo nodeFinish onTextMessage { true })
                edge(executeTool forwardTo sendToolResult onCondition { !agentInterrupted() })
                edge(executeTool forwardTo nodeThrow onCondition { agentInterrupted() })
                edge(sendToolResult forwardTo executeTool onToolCalls { true })
                edge(sendToolResult forwardTo nodeFinish onTextMessage { true })
            },
            agentConfig = agentConfig,
            toolRegistry = localToolRegistry
        ) {
            install(Persistence) {
                storage = checkpointStorage
            }

            install(Tracing) {
                addMessageProcessor(tracer)
            }
        }

        println("Running agent first time")

        val output = runCatching {
            agent.run("Test my Earth knowledge", sessionId = convId)
        }.getOrElse { it.message }

        println("Finished first run")

        assertEquals("TERMINATED AFTER THIRD TOOL CALL", output)

        assertEquals(
            """
                Trace:
                 - enter node: `__start__`
                 - exit node: `__start__`
                 - enter node: `callLLM`
                       - LLM call: `Test my Earth knowledge`
                       - LLM response: `{"message":"Is the Earth a sphere?"}`
                 - exit node: `callLLM`
                 - enter node: `executeTool`
                       - tool call: `ask` ({"message":"Is the Earth a sphere?"})
                       - tool result: `ask` == "Yes"
                 - exit node: `executeTool`
                 - enter node: `sendToolResult`
                       - LLM call: `Yes`
                       - LLM response: `{"message":"Why?"}`
                 - exit node: `sendToolResult`
                 - enter node: `executeTool`
                       - tool call: `ask` ({"message":"Why?"})
                       - tool result: `ask` == "Because when ships sail away, they start to disappear from the bottom"
                 - exit node: `executeTool`
                 - enter node: `sendToolResult`
                       - LLM call: `Because when ships sail away, they start to disappear from the bottom`
                       - LLM response: `{"message":"Who discovered this?"}`
                 - exit node: `sendToolResult`
                 - enter node: `executeTool`
                       - tool call: `ask` ({"message":"Who discovered this?"})
                       - tool result: `ask` == "Ferdinand Magellan"
                 - exit node: `executeTool`
                 - enter node: `nodeThrow`
            """.trimIndent(),
            tracer.traceAsString().trimIndent()
        )

        val lastCheckpoint = checkpointStorage.getLatestCheckpoint(convId)!!
        val lastMessageHistory = lastCheckpoint.messageHistory.joinToString("\n") { msg ->
            formatHistoryMessage(msg)
        }

        assertEquals(
            """
                - system: You are a test agent.
                - user: Test my Earth knowledge
                - tool call `ask` ({"message":"Is the Earth a sphere?"})
                - tool result `ask` == Yes
                - tool call `ask` ({"message":"Why?"})
                - tool result `ask` == Because when ships sail away, they start to disappear from the bottom
                - tool call `ask` ({"message":"Who discovered this?"})
            """.trimIndent(),
            lastMessageHistory
        )

        checkpointStorage.removeCheckpoints()

        val nodePathStr = lastCheckpoint.graphProperties?.nodePath!!
        checkpointStorage.saveCheckpoint(
            agent.id,
            lastCheckpoint.copy(
                version = 0,
                graphProperties = GraphCheckpointProperties(
                    nodePath = nodePathStr,
                    // The executeTool node now consumes a `ToolCalls(toolCalls=[Tool.Call, …])`
                    // (output of the `onToolCalls { … }` edge transform), not a bare Assistant
                    // message. Store the value in that shape so the agent's resume path can
                    // deserialize it back into the executeTool node's input type.
                    lastOutput = Json.encodeToJsonElement(
                        ToolCalls(
                            toolCalls = listOf(
                                MessagePart.Tool.Call(
                                    id = "call-1",
                                    tool = "ask",
                                    args = "{\"message\":\"Who discovered this?\"}",
                                )
                            )
                        )
                    ).toKoogJSONElement()
                )
            )
        )

        println(checkpointStorage.getLatestCheckpoint(agent.id))

        assertEquals(nodePathStr?.endsWith("executeTool"), true, "Last checkpoint node should be `executeTool`")

        val lastOutput = lastCheckpoint.graphProperties?.lastOutput
        assertTrue(
            lastOutput.toString().contains("Ferdinand Magellan"),
            message = "Last checkpointed node should be an `executeTool` with \"Ferdinand Magellan\" as an output (already calculated)"
        )

        println("Running agent second time")
        isFirstRun = false
        tracer.clear()

        val output2 = agent.run("Test my Earth knowledge", sessionId = agent.id)

        println("Finished second run")

        assertEquals("Excellent job! You are smart", output2)

        // EXPECT THAT "tool call: `ask` ({"message":"Who discovered this?"})" will be re-executed (because we saved nodeInput in the checkpoint)
        assertEquals(
            """
                Trace:
                 - enter node: `executeTool`
                       - tool call: `ask` ({"message":"Who discovered this?"})
                       - tool result: `ask` == "Ferdinand Magellan"
                 - exit node: `executeTool`
                 - enter node: `sendToolResult`
                       - LLM call: `Ferdinand Magellan`
                       - LLM response: `Excellent job! You are smart`
                 - exit node: `sendToolResult`
                 - enter node: `__finish__`
                 - exit node: `__finish__`
            """.trimIndent(),
            tracer.traceAsString().trimIndent()
        )
    }

    //region Private Methods

    /**
     * Renders a single [Message] from the persisted checkpoint history into the line format
     * the assertion below expects.
     */
    private fun formatHistoryMessage(message: Message): String {
        val toolCall = message.parts.filterIsInstance<MessagePart.Tool.Call>().firstOrNull()
        if (toolCall != null) return "- tool call `${toolCall.tool}` (${toolCall.args})"

        val toolResult = message.parts.filterIsInstance<MessagePart.Tool.Result>().firstOrNull()
        if (toolResult != null) return "- tool result `${toolResult.tool}` == ${toolResult.output}"

        val text = message.parts.filterIsInstance<MessagePart.Text>().joinToString("\n") { it.text }
        val role = when (message) {
            is Message.System -> "system"
            is Message.User -> "user"
            is Message.Assistant -> "assistant"
        }
        return "- $role: $text"
    }

    //endregion Private Methods
}
