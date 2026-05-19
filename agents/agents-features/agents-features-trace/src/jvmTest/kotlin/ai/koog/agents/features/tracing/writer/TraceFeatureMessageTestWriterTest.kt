package ai.koog.agents.features.tracing.writer

import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.dsl.builder.node
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.builder.subgraph
import ai.koog.agents.core.dsl.extension.ToolCalls
import ai.koog.agents.core.dsl.extension.asUserMessage
import ai.koog.agents.core.dsl.extension.nodeAppendPrompt
import ai.koog.agents.core.dsl.extension.nodeExecuteToolsAndGetResults
import ai.koog.agents.core.dsl.extension.nodeLLMRequest
import ai.koog.agents.core.dsl.extension.nodeLLMRequestStreaming
import ai.koog.agents.core.dsl.extension.nodeLLMRequestWithoutTools
import ai.koog.agents.core.feature.model.AIAgentError
import ai.koog.agents.core.feature.model.events.LLMCallCompletedEvent
import ai.koog.agents.core.feature.model.events.LLMCallFailedEvent
import ai.koog.agents.core.feature.model.events.LLMCallStartingEvent
import ai.koog.agents.core.feature.model.events.LLMStreamingCompletedEvent
import ai.koog.agents.core.feature.model.events.LLMStreamingFailedEvent
import ai.koog.agents.core.feature.model.events.LLMStreamingFrameReceivedEvent
import ai.koog.agents.core.feature.model.events.LLMStreamingStartingEvent
import ai.koog.agents.core.feature.model.events.NodeExecutionFailedEvent
import ai.koog.agents.core.feature.model.events.SubgraphExecutionCompletedEvent
import ai.koog.agents.core.feature.model.events.SubgraphExecutionFailedEvent
import ai.koog.agents.core.feature.model.events.SubgraphExecutionStartingEvent
import ai.koog.agents.core.feature.model.events.ToolCallCompletedEvent
import ai.koog.agents.core.feature.model.events.ToolCallStartingEvent
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.features.tracing.feature.Tracing
import ai.koog.agents.features.tracing.mock.RecursiveTool
import ai.koog.agents.features.tracing.mock.TestFeatureMessageWriter
import ai.koog.agents.features.tracing.mock.TestLogger
import ai.koog.agents.features.tracing.mock.assistantMessage
import ai.koog.agents.features.tracing.mock.createAgent
import ai.koog.agents.features.tracing.mock.systemMessage
import ai.koog.agents.features.tracing.mock.testClock
import ai.koog.agents.features.tracing.mock.userMessage
import ai.koog.agents.testing.agent.agentExecutionInfo
import ai.koog.agents.testing.feature.message.singleEvent
import ai.koog.agents.testing.feature.message.singleNodeEvent
import ai.koog.agents.testing.tools.DummyTool
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.Prompt
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.llm.toModelInfo
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.prompt.streaming.collectText
import ai.koog.serialization.kotlinx.KotlinxSerializer
import ai.koog.serialization.typeToken
import ai.koog.utils.io.use
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFails

class TraceFeatureMessageTestWriterTest {
    private val serializer = KotlinxSerializer()

    private val targetLogger = TestLogger("test-logger")

    @AfterTest
    fun resetLogger() {
        targetLogger.reset()
    }

    @Test
    fun `test subsequent LLM calls`() = runBlocking {
        val strategy = strategy("tracing-test-strategy") {
            val setPrompt by nodeAppendPrompt<String>("Set prompt") {
                system("System 1")
                user("User 1")
            }

            val appendPrompt by nodeAppendPrompt<String>("Update prompt") {
                system("System 2")
                user("User 2")
            }

            val llmRequest0 by nodeLLMRequestWithoutTools("LLM Request 1")

            val llmRequest1 by nodeLLMRequestWithoutTools("LLM Request 2")

            edge(nodeStart forwardTo setPrompt)
            edge(setPrompt forwardTo llmRequest0 asUserMessage { it })
            edge(llmRequest0 forwardTo appendPrompt transformed { _ -> "" })
            edge(appendPrompt forwardTo llmRequest1 asUserMessage { "" })
            edge(
                llmRequest1 forwardTo nodeFinish transformed { input ->
                    input.parts.filterIsInstance<MessagePart.Text>().joinToString("\n") { it.text }
                }
            )
        }

        val messageProcessor = TestFeatureMessageWriter()

        val agent = createAgent(
            userPrompt = "User 0",
            systemPrompt = "System 0",
            strategy = strategy
        ) {
            install(Tracing) {
                addMessageProcessor(messageProcessor)
            }
        }

        agent.run("", null)
        agent.close()

        val llmStartEvents = messageProcessor.messages.filterIsInstance<LLMCallStartingEvent>().toList()
        assertEquals(2, llmStartEvents.size)

        assertEquals(
            listOf("User 0", "User 1", ""),
            llmStartEvents[0].prompt.messages.filter { it.role == Message.Role.User }
                .map { it.parts.filterIsInstance<MessagePart.Text>().joinToString("\n") { p -> p.text } }
        )
        assertEquals(
            listOf("User 0", "User 1", "", "User 2", ""),
            llmStartEvents[1].prompt.messages.filter { it.role == Message.Role.User }
                .map { it.parts.filterIsInstance<MessagePart.Text>().joinToString("\n") { p -> p.text } }
        )
    }

    @Test
    fun `test nonexistent tool call`() = runBlocking {
        val toolCallId = "0"
        val toolName = "there is no tool with this name"
        val rawToolArgs = "{}"
        val strategy = strategy<String, String>("tracing-tool-call-test") {
            val callTool by nodeExecuteToolsAndGetResults("Tool call")
            edge(
                nodeStart forwardTo callTool transformed { _ ->
                    ToolCalls(
                        listOf(
                            MessagePart.Tool.Call(
                                id = toolCallId,
                                tool = toolName,
                                args = rawToolArgs,
                            )
                        )
                    )
                }
            )
            edge(callTool forwardTo nodeFinish transformed { input -> input.toolResults.single().output })
        }

        val messageProcessor = TestFeatureMessageWriter()

        val agent = createAgent(
            strategy = strategy
        ) {
            install(Tracing) {
                addMessageProcessor(messageProcessor)
            }
        }

        // Calling a non-existent tool returns an observation with an error
        // instead of throwing an exception, allowing the agent to handle it gracefully
        val result = agent.run("", null)
        agent.close()

        // Verify the result contains the error message about the tool not being found
        assertEquals(
            "Tool with name '$toolName' not found in the tool registry. Use one of the available tools.",
            result
        )
    }

    @Test
    fun `test existing tool call`() = runBlocking {
        val strategy = strategy<String, String>("tracing-tool-call-test") {
            val callTool by nodeExecuteToolsAndGetResults("Tool call")
            edge(
                nodeStart forwardTo callTool transformed { _ ->
                    ToolCalls(
                        listOf(
                            MessagePart.Tool.Call(
                                id = "0",
                                tool = DummyTool().name,
                                args = "{}",
                            )
                        )
                    )
                }
            )
            edge(callTool forwardTo nodeFinish transformed { input -> input.toolResults.single().output })
        }

        val messageProcessor = TestFeatureMessageWriter()

        val agent = createAgent(
            strategy = strategy
        ) {
            install(Tracing) {
                addMessageProcessor(messageProcessor)
            }
        }

        agent.run("", null)

        val toolCallsStartEvent = messageProcessor.messages.filterIsInstance<ToolCallStartingEvent>().toList()
        assertEquals(1, toolCallsStartEvent.size, "Tool call start event for existing tool")

        val toolCallsEndEvent = messageProcessor.messages.filterIsInstance<ToolCallStartingEvent>().toList()
        assertEquals(1, toolCallsEndEvent.size, "Tool call end event for existing tool")
    }

    @Test
    fun `test recursive tool call`() = runBlocking {
        val strategy = strategy<String, String>("recursive-tool-call-test") {
            val callTool by nodeExecuteToolsAndGetResults("Tool call")
            edge(
                nodeStart forwardTo callTool transformed { _ ->
                    ToolCalls(
                        listOf(
                            MessagePart.Tool.Call(
                                id = "0",
                                tool = RecursiveTool().name,
                                args = "{}",
                            )
                        )
                    )
                }
            )
            edge(callTool forwardTo nodeFinish transformed { it.toolResults.single().output })
        }

        val messageProcessor = TestFeatureMessageWriter()

        val toolRegistry = ToolRegistry.EMPTY
        val agent = createAgent(
            strategy = strategy,
            toolRegistry = toolRegistry
        ) {
            install(Tracing) {
                addMessageProcessor(messageProcessor)
            }
        }.apply {
            toolRegistry.add(RecursiveTool())
        }

        agent.run("", null)

        val toolCallsStartEvent = messageProcessor.messages.filterIsInstance<ToolCallStartingEvent>().toList()
        assertEquals(1, toolCallsStartEvent.size, "Tool call start event for existing tool")
    }

    @Test
    fun `test llm tool call`() = runBlocking {
        val dummyTool = DummyTool()

        val strategy = strategy<String, String>("llm-tool-call-test") {
            val callTool by nodeExecuteToolsAndGetResults("Tool call")
            edge(
                nodeStart forwardTo callTool transformed { _ ->
                    ToolCalls(
                        listOf(
                            MessagePart.Tool.Call(
                                id = "0",
                                tool = dummyTool.name,
                                args = "{}",
                            )
                        )
                    )
                }
            )
            edge(callTool forwardTo nodeFinish transformed { it.toolResults.single().output })
        }

        val messageProcessor = TestFeatureMessageWriter()

        val toolRegistry = ToolRegistry.EMPTY
        val agent = createAgent(
            strategy = strategy,
            toolRegistry = toolRegistry
        ) {
            install(Tracing) {
                addMessageProcessor(messageProcessor)
            }
        }.apply {
            toolRegistry.add(dummyTool)
        }

        agent.run("", null)

        val toolCallsStartEvent = messageProcessor.messages.filterIsInstance<ToolCallStartingEvent>().toList()
        assertEquals(1, toolCallsStartEvent.size, "Tool call start event for existing tool")

        val toolCallsEndEvent = messageProcessor.messages.filterIsInstance<ToolCallCompletedEvent>().toList()
        assertEquals(1, toolCallsEndEvent.size, "Tool call end event for existing tool")
    }

    @Test
    fun `test agent with node execution error`() = runBlocking {
        val agentId = "test-agent-id"
        val nodeWithErrorName = "node-with-error"
        val strategyName = "test-strategy"

        val testErrorMessage = "Test error"
        var expectedStackTrace = ""
        var expectedCause: String? = null
        var expectedType: String? = null

        val strategy = strategy(strategyName) {
            val nodeWithError by node<String, String>(nodeWithErrorName) {
                // Get expected stack trace before throwing exception
                try {
                    throw IllegalStateException(testErrorMessage)
                } catch (t: IllegalStateException) {
                    expectedStackTrace = t.stackTraceToString()
                    expectedCause = t.cause?.toString()
                    expectedType = t::class.qualifiedName
                    throw t
                }
            }

            edge(nodeStart forwardTo nodeWithError)
            edge(nodeWithError forwardTo nodeFinish)
        }

        TestFeatureMessageWriter().use { writer ->
            val agent = createAgent(
                agentId = agentId,
                strategy = strategy
            ) {
                install(Tracing) {
                    addMessageProcessor(writer)
                }
            }

            val throwable = assertFails { agent.run("", null) }
            assertEquals(testErrorMessage, throwable.message)

            val actualEvents = writer.messages.filterIsInstance<NodeExecutionFailedEvent>().toList()

            val actualNodeWithErrorEvent = writer.messages.singleNodeEvent(nodeWithErrorName)

            val expectedEvents = listOf(
                NodeExecutionFailedEvent(
                    eventId = actualNodeWithErrorEvent.eventId,
                    executionInfo = agentExecutionInfo(agentId, strategyName, nodeWithErrorName),
                    runId = writer.runId,
                    nodeName = nodeWithErrorName,
                    input = serializer.encodeToJSONElement("", typeToken<String>()),
                    error = AIAgentError(
                        message = testErrorMessage,
                        stackTrace = expectedStackTrace,
                        cause = expectedCause,
                        type = expectedType
                    ),
                    timestamp = testClock.now().toEpochMilliseconds()
                )
            )

            assertEquals(expectedEvents.size, actualEvents.size)
            assertContentEquals(expectedEvents, actualEvents)
        }
    }

    @Test
    fun `test llm streaming events success`() = runBlocking {
        val agentId = "test-agent-id"
        val strategyName = "tracing-streaming-success"
        val userPrompt = "Test user request"
        val systemPrompt = "Test system prompt"
        val assistantPrompt = "Test assistant prompt"
        val promptId = "Test prompt id"
        val nodeLLMRequestStreamingName = "stream-and-collect"

        val model = OpenAIModels.Chat.GPT4o

        val strategy = strategy<String, String>(strategyName) {
            val streamAndCollect by nodeLLMRequestStreaming(nodeLLMRequestStreamingName)

            edge(nodeStart forwardTo streamAndCollect asUserMessage { it })
            edge(streamAndCollect forwardTo nodeFinish transformed { it.collectText() })
        }

        val testLLMResponse = "Default test response"

        val testExecutor = getMockExecutor(serializer, clock = testClock) {
            mockLLMAnswer(testLLMResponse).asDefaultResponse onUserRequestEquals userPrompt
        }

        val toolRegistry = ToolRegistry { tool(DummyTool()) }

        TestFeatureMessageWriter().use { writer ->
            createAgent(
                agentId = agentId,
                strategy = strategy,
                promptExecutor = testExecutor,
                systemPrompt = systemPrompt,
                userPrompt = userPrompt,
                assistantPrompt = assistantPrompt,
                promptId = promptId,
                model = model,
                toolRegistry = toolRegistry,
            ) {
                install(Tracing) {
                    addMessageProcessor(writer)
                }
            }.use { agent ->
                agent.run(userPrompt, null)

                val actualEvents = writer.messages.filter { event ->
                    event is LLMStreamingStartingEvent ||
                        event is LLMStreamingFrameReceivedEvent ||
                        event is LLMStreamingCompletedEvent ||
                        event is LLMStreamingFailedEvent
                }

                val expectedPrompt = Prompt(
                    messages = listOf(
                        systemMessage(systemPrompt),
                        userMessage(userPrompt),
                        assistantMessage(assistantPrompt),
                        userMessage(userPrompt),
                    ),
                    id = promptId
                )

                val actualStreamingStartingEvent = writer.messages.singleEvent<LLMStreamingStartingEvent>()

                val expectedEvents = listOf(
                    LLMStreamingStartingEvent(
                        eventId = actualStreamingStartingEvent.eventId,
                        executionInfo = agentExecutionInfo(agentId, strategyName),
                        runId = writer.runId,
                        prompt = expectedPrompt,
                        model = model.toModelInfo(),
                        tools = toolRegistry.tools.map { it.name },
                        timestamp = testClock.now().toEpochMilliseconds()
                    ),
                    LLMStreamingFrameReceivedEvent(
                        eventId = actualStreamingStartingEvent.eventId,
                        executionInfo = agentExecutionInfo(agentId, strategyName),
                        runId = writer.runId,
                        prompt = expectedPrompt,
                        model = model.toModelInfo(),
                        frame = StreamFrame.TextDelta(testLLMResponse, index = 0),
                        timestamp = testClock.now().toEpochMilliseconds()
                    ),
                    LLMStreamingFrameReceivedEvent(
                        eventId = actualStreamingStartingEvent.eventId,
                        executionInfo = agentExecutionInfo(agentId, strategyName),
                        runId = writer.runId,
                        prompt = expectedPrompt,
                        model = model.toModelInfo(),
                        frame = StreamFrame.TextComplete(testLLMResponse, index = 0),
                        timestamp = testClock.now().toEpochMilliseconds()
                    ),
                    LLMStreamingFrameReceivedEvent(
                        eventId = actualStreamingStartingEvent.eventId,
                        executionInfo = agentExecutionInfo(agentId, strategyName),
                        runId = writer.runId,
                        prompt = expectedPrompt,
                        model = model.toModelInfo(),
                        frame = StreamFrame.End(null, ResponseMetaInfo.create(testClock)),
                        timestamp = testClock.now().toEpochMilliseconds()
                    ),
                    LLMStreamingCompletedEvent(
                        eventId = actualStreamingStartingEvent.eventId,
                        executionInfo = agentExecutionInfo(agentId, strategyName),
                        runId = writer.runId,
                        prompt = expectedPrompt,
                        model = model.toModelInfo(),
                        tools = toolRegistry.tools.map { it.name },
                        timestamp = testClock.now().toEpochMilliseconds()
                    )
                )

                assertEquals(expectedEvents.size, actualEvents.size)
                assertContentEquals(expectedEvents, actualEvents)
            }
        }
    }

    @Test
    fun `test llm streaming events failure`() = runBlocking {
        val agentId = "test-agent-id"
        val userPrompt = "Call the dummy tool with argument: test"
        val systemPrompt = "Test system prompt"
        val assistantPrompt = "Test assistant prompt"
        val promptId = "Test prompt id"
        val strategyName = "tracing-streaming-failure"
        val nodeStreamingFailedName = "test-node-streaming-failed"
        val model = OpenAIModels.Chat.GPT4o

        val strategy = strategy<String, String>(strategyName) {
            val streamAndCollect by nodeLLMRequestStreaming(nodeStreamingFailedName)

            edge(nodeStart forwardTo streamAndCollect asUserMessage { it })
            edge(streamAndCollect forwardTo nodeFinish transformed { it.collectText() })
        }

        val toolRegistry = ToolRegistry { tool(DummyTool()) }

        val expectedErrorMessage = "Test streaming error"
        var expectedStackTrace = ""
        var expectedCause: String? = null
        var expectedType: String? = null

        val testStreamingExecutor = object : PromptExecutor() {
            override suspend fun execute(
                prompt: Prompt,
                model: LLModel,
                tools: List<ToolDescriptor>
            ): Message.Assistant = Message.Assistant("", ResponseMetaInfo.Empty)

            override fun executeStreaming(
                prompt: Prompt,
                model: LLModel,
                tools: List<ToolDescriptor>
            ): Flow<StreamFrame> = flow {
                val testException = IllegalStateException(expectedErrorMessage)
                expectedStackTrace = testException.stackTraceToString()
                expectedCause = testException.cause?.toString()
                expectedType = testException::class.qualifiedName
                throw testException
            }

            override suspend fun moderate(
                prompt: Prompt,
                model: LLModel
            ): ai.koog.prompt.dsl.ModerationResult {
                throw UnsupportedOperationException("Not used in test")
            }

            override fun close() {}
        }

        TestFeatureMessageWriter().use { writer ->

            createAgent(
                agentId = agentId,
                systemPrompt = systemPrompt,
                userPrompt = userPrompt,
                assistantPrompt = assistantPrompt,
                promptId = promptId,
                model = model,
                strategy = strategy,
                promptExecutor = testStreamingExecutor,
                toolRegistry = toolRegistry,
            ) {
                install(Tracing) {
                    addMessageProcessor(writer)
                }
            }.use { agent ->
                val throwable = assertFails {
                    agent.run("", null)
                }

                assertEquals(expectedErrorMessage, throwable.message)

                val expectedPrompt = Prompt(
                    messages = listOf(
                        systemMessage(systemPrompt),
                        userMessage(userPrompt),
                        assistantMessage(assistantPrompt),
                        userMessage(""),
                    ),
                    id = promptId
                )

                val actualEvents = writer.messages.filterIsInstance<LLMStreamingStartingEvent>() +
                    writer.messages.filterIsInstance<LLMStreamingFrameReceivedEvent>() +
                    writer.messages.filterIsInstance<LLMStreamingFailedEvent>() +
                    writer.messages.filterIsInstance<LLMStreamingCompletedEvent>()

                val actualStreamingStartingEvent = writer.messages.singleEvent<LLMStreamingStartingEvent>()

                val expectedEvents = listOf(
                    LLMStreamingStartingEvent(
                        eventId = actualStreamingStartingEvent.eventId,
                        executionInfo = agentExecutionInfo(agentId, strategyName),
                        runId = writer.runId,
                        prompt = expectedPrompt,
                        model = model.toModelInfo(),
                        tools = toolRegistry.tools.map { it.name },
                        timestamp = testClock.now().toEpochMilliseconds()
                    ),
                    LLMStreamingFailedEvent(
                        eventId = actualStreamingStartingEvent.eventId,
                        executionInfo = agentExecutionInfo(agentId, strategyName),
                        runId = writer.runId,
                        prompt = expectedPrompt,
                        model = model.toModelInfo(),
                        error = AIAgentError(
                            message = expectedErrorMessage,
                            stackTrace = expectedStackTrace,
                            cause = expectedCause,
                            type = expectedType
                        ),
                        timestamp = testClock.now().toEpochMilliseconds()
                    ),
                    LLMStreamingCompletedEvent(
                        eventId = actualStreamingStartingEvent.eventId,
                        executionInfo = agentExecutionInfo(agentId, strategyName),
                        runId = writer.runId,
                        prompt = expectedPrompt,
                        model = model.toModelInfo(),
                        tools = toolRegistry.tools.map { it.name },
                        timestamp = testClock.now().toEpochMilliseconds()
                    )
                )

                assertEquals(expectedEvents.size, actualEvents.size)
                assertContentEquals(expectedEvents, actualEvents)
            }
        }
    }

    @Test
    fun `test llm call events failure`() = runBlocking {
        val agentId = "test-agent-id"
        val userPrompt = "Call the dummy tool with argument: test"
        val systemPrompt = "Test system prompt"
        val assistantPrompt = "Test assistant prompt"
        val promptId = "Test prompt id"
        val strategyName = "tracing-call-failure"
        val nodeCallFailedName = "test-node-call-failed"
        val model = OpenAIModels.Chat.GPT4o

        val strategy = strategy<String, String>(strategyName) {
            val call by nodeLLMRequest(nodeCallFailedName)

            edge(nodeStart forwardTo call asUserMessage { it })
            edge(
                call forwardTo nodeFinish transformed { input ->
                    input.parts.filterIsInstance<MessagePart.Text>().joinToString("\n") { it.text }
                }
            )
        }

        val toolRegistry = ToolRegistry { tool(DummyTool()) }

        val expectedErrorMessage = "Test LLM call error"
        var expectedStackTrace = ""
        var expectedCause: String? = null
        var expectedType: String? = null

        val testCallExecutor = object : PromptExecutor() {
            override suspend fun execute(
                prompt: Prompt,
                model: LLModel,
                tools: List<ToolDescriptor>
            ): Message.Assistant {
                val testException = IllegalStateException(expectedErrorMessage)
                expectedStackTrace = testException.stackTraceToString()
                expectedCause = testException.cause?.toString()
                expectedType = testException::class.qualifiedName
                throw testException
            }

            override fun executeStreaming(
                prompt: Prompt,
                model: LLModel,
                tools: List<ToolDescriptor>
            ): Flow<StreamFrame> = flow { }

            override suspend fun moderate(
                prompt: Prompt,
                model: LLModel
            ): ai.koog.prompt.dsl.ModerationResult {
                throw UnsupportedOperationException("Not used in test")
            }

            override fun close() {}
        }

        TestFeatureMessageWriter().use { writer ->

            createAgent(
                agentId = agentId,
                systemPrompt = systemPrompt,
                userPrompt = userPrompt,
                assistantPrompt = assistantPrompt,
                promptId = promptId,
                model = model,
                strategy = strategy,
                promptExecutor = testCallExecutor,
                toolRegistry = toolRegistry,
            ) {
                install(Tracing) {
                    addMessageProcessor(writer)
                }
            }.use { agent ->
                val throwable = assertFails {
                    agent.run("", null)
                }

                assertEquals(expectedErrorMessage, throwable.message)

                val expectedPrompt = Prompt(
                    messages = listOf(
                        systemMessage(systemPrompt),
                        userMessage(userPrompt),
                        assistantMessage(assistantPrompt),
                        userMessage(""),
                    ),
                    id = promptId
                )

                val actualEvents = writer.messages.filterIsInstance<LLMCallStartingEvent>() +
                    writer.messages.filterIsInstance<LLMCallFailedEvent>() +
                    writer.messages.filterIsInstance<LLMCallCompletedEvent>()

                val actualCallStartingEvent = writer.messages.singleEvent<LLMCallStartingEvent>()

                val expectedEvents = listOf(
                    LLMCallStartingEvent(
                        eventId = actualCallStartingEvent.eventId,
                        executionInfo = agentExecutionInfo(agentId, strategyName, nodeCallFailedName),
                        runId = writer.runId,
                        prompt = expectedPrompt,
                        model = model.toModelInfo(),
                        tools = toolRegistry.tools.map { it.name },
                        timestamp = testClock.now().toEpochMilliseconds()
                    ),
                    LLMCallFailedEvent(
                        eventId = actualCallStartingEvent.eventId,
                        executionInfo = agentExecutionInfo(agentId, strategyName, nodeCallFailedName),
                        runId = writer.runId,
                        prompt = expectedPrompt,
                        model = model.toModelInfo(),
                        tools = toolRegistry.tools.map { it.name },
                        error = AIAgentError(
                            message = expectedErrorMessage,
                            stackTrace = expectedStackTrace,
                            cause = expectedCause,
                            type = expectedType
                        ),
                        timestamp = testClock.now().toEpochMilliseconds()
                    ),
                )

                assertEquals(expectedEvents.size, actualEvents.size)
                assertContentEquals(expectedEvents, actualEvents)
            }
        }
    }

    @Test
    fun `test subgraph execution events success`() = runBlocking {
        val agentId = "test-agent-id"
        val strategyName = "test-strategy"
        val subgraphName = "test-subgraph"
        val subgraphOutput = "test-subgraph-output"
        val inputRequest = "Test input"

        val strategy = strategy<String, String>(strategyName) {
            val subgraph by subgraph<String, String>(subgraphName) {
                edge(nodeStart forwardTo nodeFinish transformed { subgraphOutput })
            }
            nodeStart then subgraph then nodeFinish
        }

        TestFeatureMessageWriter().use { writer ->
            val agentOutput = createAgent(
                agentId = agentId,
                strategy = strategy,
            ) {
                install(Tracing) {
                    addMessageProcessor(writer)
                }
            }.use { agent ->
                agent.run(inputRequest, null)
            }

            val actualEvents = writer.messages.filter { event ->
                event is SubgraphExecutionStartingEvent ||
                    event is SubgraphExecutionCompletedEvent ||
                    event is SubgraphExecutionFailedEvent
            }

            val actualSubgraphStartingEvent = writer.messages.singleEvent<SubgraphExecutionStartingEvent>()

            val runIdFromEvents = (actualEvents.first() as SubgraphExecutionStartingEvent).runId

            val expectedInput = serializer.encodeToJSONElement(inputRequest, typeToken<String>())
            val expectedOutput = serializer.encodeToJSONElement(agentOutput, typeToken<String>())

            val expectedEvents = listOf(
                SubgraphExecutionStartingEvent(
                    eventId = actualSubgraphStartingEvent.eventId,
                    executionInfo = agentExecutionInfo(agentId, strategyName, subgraphName),
                    runId = runIdFromEvents,
                    subgraphName = subgraphName,
                    input = expectedInput,
                    timestamp = testClock.now().toEpochMilliseconds()
                ),
                SubgraphExecutionCompletedEvent(
                    eventId = actualSubgraphStartingEvent.eventId,
                    executionInfo = agentExecutionInfo(agentId, strategyName, subgraphName),
                    runId = runIdFromEvents,
                    subgraphName = subgraphName,
                    input = expectedInput,
                    output = expectedOutput,
                    timestamp = testClock.now().toEpochMilliseconds()
                ),
            )

            assertEquals(expectedEvents.size, actualEvents.size)
            assertContentEquals(expectedEvents, actualEvents)
        }
    }

    @Test
    fun `test subgraph execution events failure`() = runBlocking {
        val agentId = "test-agent-id"
        val strategyName = "test-strategy"
        val subgraphName = "test-subgraph"
        val subgraphErrorNodeName = "test-subgraph-error-node"
        val subgraphNodeErrorMessage = "Test subgraph error"
        val inputRequest = "Test input"

        val strategy = strategy<String, String>(strategyName) {
            val subgraph by subgraph<String, String>(subgraphName) {
                val nodeWithError by node<String, String>(subgraphErrorNodeName) {
                    throw IllegalStateException(subgraphNodeErrorMessage)
                }
                nodeStart then nodeWithError then nodeFinish
            }
            nodeStart then subgraph then nodeFinish
        }

        TestFeatureMessageWriter().use { writer ->
            var expectedStackTrace = ""
            var expectedCause: String? = null
            var expectedType: String? = null

            val agent = createAgent(
                agentId = agentId,
                strategy = strategy,
            ) {
                install(Tracing) {
                    addMessageProcessor(writer)
                }
            }

            val agentThrowable = assertFails {
                try {
                    agent.run(inputRequest, null)
                } catch (t: Throwable) {
                    expectedStackTrace = t.stackTraceToString()
                    expectedCause = t.cause?.stackTraceToString()
                    expectedType = t::class.qualifiedName
                    throw t
                }
            }

            // Ensure the error message is as expected
            assertEquals(subgraphNodeErrorMessage, agentThrowable.message)

            val actualEvents = writer.messages.filter { event ->
                event is SubgraphExecutionStartingEvent ||
                    event is SubgraphExecutionCompletedEvent ||
                    event is SubgraphExecutionFailedEvent
            }

            val actualSubgraphStartingEvent = writer.messages.singleEvent<SubgraphExecutionStartingEvent>()

            val runIdFromEvents = (actualEvents.first() as SubgraphExecutionStartingEvent).runId

            val expectedInput = @OptIn(InternalAgentsApi::class)
            serializer.encodeToJSONElement(
                inputRequest,
                typeToken<String>()
            )

            val expectedEvents = listOf(
                SubgraphExecutionStartingEvent(
                    eventId = actualSubgraphStartingEvent.eventId,
                    executionInfo = agentExecutionInfo(agentId, strategyName, subgraphName),
                    runId = runIdFromEvents,
                    subgraphName = subgraphName,
                    input = expectedInput,
                    timestamp = testClock.now().toEpochMilliseconds()
                ),
                SubgraphExecutionFailedEvent(
                    eventId = actualSubgraphStartingEvent.eventId,
                    executionInfo = agentExecutionInfo(agentId, strategyName, subgraphName),
                    runId = runIdFromEvents,
                    subgraphName = subgraphName,
                    input = expectedInput,
                    error = AIAgentError(
                        message = subgraphNodeErrorMessage,
                        stackTrace = expectedStackTrace,
                        cause = expectedCause,
                        type = expectedType
                    ),
                    timestamp = testClock.now().toEpochMilliseconds()
                )
            )

            assertEquals(expectedEvents.size, actualEvents.size)
            assertContentEquals(expectedEvents, actualEvents)
        }
    }
}
