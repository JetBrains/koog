package ai.koog.agents.features.tracing.writer

import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeExecuteTool
import ai.koog.agents.core.dsl.extension.nodeLLMRequest
import ai.koog.agents.core.dsl.extension.nodeUpdatePrompt
import ai.koog.agents.core.feature.model.BeforeLLMCallEvent
import ai.koog.agents.core.feature.model.ToolCallEvent
import ai.koog.agents.core.feature.model.ToolCallResultEvent
import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.ToolArgs
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.features.tracing.TestLLMExecutor
import ai.koog.agents.features.tracing.createAgent
import ai.koog.agents.features.tracing.feature.Tracing
import ai.koog.agents.testing.tools.DummyTool
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.llm.OllamaModels
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class TraceFeatureMessageTestWriterTest {

    private val targetLogger = TestLogger("test-logger")

    @AfterTest
    fun resetLogger() {
        targetLogger.reset()
    }

    @Test
    fun `test subsequent LLM calls`() = runTest {

        val strategy = strategy("tracing-test-strategy") {

            val setPrompt by nodeUpdatePrompt<String>("Set prompt") {
                system("System 1")
                user("User 1")
            }

            val updatePrompt by nodeUpdatePrompt<String>("Update prompt") {
                system("System 2")
                user("User 2")
            }

            val llmRequest0 by nodeLLMRequest("LLM Request 1", allowToolCalls = false)

            val llmRequest1 by nodeLLMRequest("LLM Request 2", allowToolCalls = false)

            edge(nodeStart forwardTo setPrompt)
            edge(setPrompt forwardTo llmRequest0)
            edge(llmRequest0 forwardTo updatePrompt transformed { _ -> "" })
            edge(updatePrompt forwardTo llmRequest1 transformed { _ -> "" })
            edge(llmRequest1 forwardTo nodeFinish transformed { input -> input.content })
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

        agent.run("")
        agent.close()

        val llmStartEvents = messageProcessor.messages.filterIsInstance<BeforeLLMCallEvent>().toList()
        assertEquals(2, llmStartEvents.size)
        assertEquals(
            listOf("User 0", "User 1", ""),
            llmStartEvents[0].prompt.messages.filter { it.role == Message.Role.User }.map { it.content })
        assertEquals(
            listOf("User 0", "User 1", "", "User 2", ""),
            llmStartEvents[1].prompt.messages.filter { it.role == Message.Role.User }.map { it.content })
    }

    @Test
    fun `test nonexistent tool call`() = runTest {

        val strategy = strategy<String, String>("tracing-tool-call-test") {
            val callTool by nodeExecuteTool("Tool call")
            edge(nodeStart forwardTo callTool transformed { _ ->
                Message.Tool.Call(
                    id = "0",
                    tool = "there is no tool with this name",
                    content = "{}",
                    metaInfo = ResponseMetaInfo(timestamp = Instant.parse("2023-01-01T00:00:00Z"))
                )
            })
            edge(callTool forwardTo nodeFinish transformed { input -> input.content })
        }

        val messageProcessor = TestFeatureMessageWriter()

        val agent = createAgent(
            strategy = strategy
        ) {
            install(Tracing) {
                addMessageProcessor(messageProcessor)
            }
        }

        val throwable = assertFails { agent.run("") }
        assertEquals(
            "Tool \"there is no tool with this name\" is not defined",
            throwable.message
        )
    }

    @Test
    fun `test existing tool call`() = runTest {

        val strategy = strategy<String, String>("tracing-tool-call-test") {
            val callTool by nodeExecuteTool("Tool call")
            edge(nodeStart forwardTo callTool transformed { _ ->
                Message.Tool.Call(
                    id = "0",
                    tool = DummyTool().name,
                    content = "{}",
                    metaInfo = ResponseMetaInfo(timestamp = Instant.parse("2023-01-01T00:00:00Z"))
                )
            })
            edge(callTool forwardTo nodeFinish transformed { input -> input.content })
        }

        val messageProcessor = TestFeatureMessageWriter()

        val agent = createAgent(
            strategy = strategy
        ) {
            install(Tracing) {
                addMessageProcessor(messageProcessor)
            }
        }

        agent.run("")

        val toolCallsStartEvent = messageProcessor.messages.filterIsInstance<ToolCallEvent>().toList()
        assertEquals(1, toolCallsStartEvent.size, "Tool call start event for existing tool")

        val toolCallsEndEvent = messageProcessor.messages.filterIsInstance<ToolCallEvent>().toList()
        assertEquals(1, toolCallsEndEvent.size, "Tool call end event for existing tool")
    }

    @Test
    fun `test recursive tool call`() = runTest {

        val strategy = strategy<String, String>("recursive-tool-call-test") {
            val callTool by nodeExecuteTool("Tool call")
            edge(nodeStart forwardTo callTool transformed { _ ->
                Message.Tool.Call(
                    id = "0",
                    tool = RecursiveTool().name,
                    content = "{}",
                    metaInfo = ResponseMetaInfo(timestamp = Instant.parse("2023-01-01T00:00:00Z"))
                )
            })
            edge(callTool forwardTo nodeFinish transformed { input -> input.content })
        }

        val messageProcessor = TestFeatureMessageWriter()

        val agent = createAgent(
            strategy = strategy,
        ) {
            install(Tracing) {
                addMessageProcessor(messageProcessor)
            }
        }.apply {
            toolRegistry.add(RecursiveTool())
        }

        agent.run("")

        val toolCallsStartEvent = messageProcessor.messages.filterIsInstance<ToolCallEvent>().toList()
        assertEquals(1, toolCallsStartEvent.size, "Tool call start event for existing tool")
    }

    @Test
    fun `test llm tool call`() = runTest {

        val strategy = strategy<String, String>("llm-tool-call-test") {
            val callTool by nodeExecuteTool("Tool call")
            edge(nodeStart forwardTo callTool transformed { _ ->
                Message.Tool.Call(
                    id = "0",
                    tool = LLMCallTool().name,
                    content = "{}",
                    metaInfo = ResponseMetaInfo(timestamp = Instant.parse("2023-01-01T00:00:00Z"))
                )
            })
            edge(callTool forwardTo nodeFinish transformed { input -> input.content })
        }

        val messageProcessor = TestFeatureMessageWriter()

        val agent = createAgent(
            strategy = strategy,
        ) {
            install(Tracing) {
                addMessageProcessor(messageProcessor)
            }
        }.apply {
            toolRegistry.add(LLMCallTool())
        }

        agent.run("")

        val toolCallsStartEvent = messageProcessor.messages.filterIsInstance<ToolCallEvent>().toList()
        assertEquals(1, toolCallsStartEvent.size, "Tool call start event for existing tool")

        val toolCallsEndEvent = messageProcessor.messages.filterIsInstance<ToolCallResultEvent>().toList()
        assertEquals(1, toolCallsEndEvent.size, "Tool call end event for existing tool")
    }

    private class RecursiveTool : SimpleTool<RecursiveTool.Args>() {
        @Serializable
        data class Args(val dummy: String = "") : ToolArgs

        override val argsSerializer = Args.serializer()

        override val descriptor = ToolDescriptor(
            name = "recursive",
            description = "Recursive tool for testing",
            requiredParameters = emptyList()
        )

        override suspend fun doExecute(args: Args): String {
            return "Dummy tool result: ${DummyTool().doExecute(DummyTool.Args())}"
        }
    }

    private class LLMCallTool : SimpleTool<LLMCallTool.Args>() {

        @Serializable
        data class Args(val dummy: String = "") : ToolArgs

        val executor = TestLLMExecutor()

        override val argsSerializer = Args.serializer()

        override val descriptor = ToolDescriptor(
            name = "recursive",
            description = "Recursive tool for testing",
            requiredParameters = emptyList()
        )

        override suspend fun doExecute(args: Args): String {
            val prompt = Prompt.build("test") {
                system("You are a helpful assistant that uses tools.")
                user("Set the color to blue")
            }
            return executor.execute(
                prompt, OllamaModels.Meta.LLAMA_3_2, emptyList()
            ).first().content
        }
    }
}