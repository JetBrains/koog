@file:OptIn(ExperimentalUuidApi::class)

package ai.koog.integration.tests

import ai.koog.integration.tests.ReportingLLMLLMClient.Event
import ai.koog.integration.tests.utils.TestUtils.readTestAnthropicKeyFromEnv
import ai.koog.integration.tests.utils.TestUtils.readTestOpenAIKeyFromEnv
import ai.koog.integration.tests.utils.TestLogPrinter
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.AIAgentException
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.*
import ai.koog.agents.core.tools.*
import ai.koog.agents.features.eventHandler.feature.EventHandler
import ai.koog.agents.features.eventHandler.feature.EventHandlerConfig
import ai.koog.agents.features.tracing.feature.Tracing
import ai.koog.integration.tests.utils.Models
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.executor.llms.all.simpleAnthropicExecutor
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.params.LLMParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.io.File
import java.util.stream.Stream
import kotlin.coroutines.coroutineContext
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi

internal class ReportingLLMLLMClient(
    private val eventsChannel: Channel<Event>,
    private val underlyingClient
    : LLMClient
) : LLMClient {
    sealed interface Event {
        data class Message(
            val llmClient: String,
            val method: String,
            val prompt: Prompt,
            val tools: List<String>,
            val model: LLModel
        ) : Event

        data object Termination : Event
    }

    override suspend fun execute(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ): List<Message.Response> {
        CoroutineScope(coroutineContext).launch {
            eventsChannel.send(
                Event.Message(
                    llmClient = underlyingClient::class.simpleName ?: "null",
                    method = "execute",
                    prompt = prompt,
                    tools = tools.map { it.name },
                    model = model
                )
            )
        }
        return underlyingClient.execute(prompt, model, tools)
    }

    override suspend fun executeStreaming(prompt: Prompt, model: LLModel): Flow<String> {
        CoroutineScope(coroutineContext).launch {
            eventsChannel.send(
                Event.Message(
                    llmClient = underlyingClient::class.simpleName ?: "null",
                    method = "execute",
                    prompt = prompt,
                    tools = emptyList(),
                    model = model
                )
            )
        }
        return underlyingClient.executeStreaming(prompt, model)
    }
}

internal fun LLMClient.reportingTo(
    eventsChannel: Channel<Event>
) = ReportingLLMLLMClient(eventsChannel, this)

@Suppress("SSBasedInspection")
class AIAgentMultipleLLMIntegrationTest {

    companion object {
        private lateinit var testResourcesDir: File

        @JvmStatic
        @BeforeAll
        fun setupTestResources() {
            testResourcesDir = File("src/jvmTest/resources/media")
            testResourcesDir.mkdirs()

            val markdownFile = File(testResourcesDir, "test.md")
            markdownFile.writeText(
                """
                # Test Markdown File

                This is a test markdown file for integration testing.

                ## Features
                - Support for markdown files
                - Integration with LLM models
                - Testing capabilities

                ## Usage
                - Run the `integration_test` Gradle task to run the tests.
                - Run the `integrationTest` Maven goal to run the tests.

                ## License
                This project is licensed under the Apache License 2.0.
            """.trimIndent()
            )

            val textFile = File(testResourcesDir, "test.txt")
            textFile.writeText("This is a simple text file for testing document handling.")

            val imageFile = File(testResourcesDir, "test.png")
            imageFile.writeBytes(
                byteArrayOf(
                    -119, 80, 78, 71, 13, 10, 26, 10, 0, 0, 0, 13,
                    73, 72, 68, 82, 0, 0, 0, 1, 0, 0, 0, 1, 8, 6,
                    0, 0, 0, 31, 21, -60, -119, 0, 0, 0, 10, 73, 68, 65,
                    84, 120, -100, 99, 0, 1, 0, 0, 5, 0, 1, 13, 10, 45,
                    -76, 0, 0, 0, 0, 73, 69, 78, 68, -82, 66, 96, -126
                )
            )

            val audioFile = File(testResourcesDir, "test.wav")
            audioFile.writeBytes(
                byteArrayOf(
                    82, 73, 70, 70, 36, 0, 0, 0, 87, 65, 86, 69, 102, 109, 116, 32,
                    16, 0, 0, 0, 1, 0, 1, 0, 68, -84, 0, 0, -120, 88, 1, 0,
                    2, 0, 16, 0, 100, 97, 116, 97, 0, 0, 0, 0
                )
            )

            // Create a simple PDF file for testing
            val pdfFile = File(testResourcesDir, "test.pdf")
            pdfFile.writeText(
                """%PDF-1.4
                        1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj
                        2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj
                        3 0 obj<</Type/Page/Contents 4 0 R>>endobj
                        4 0 obj<</Length 44>>stream
                        BT/F1 12 Tf 100 700 Td(Test PDF for Koog)Tj ET
                        endstream endobj
                        xref 0 5
                        0000000000 65535 f 
                        0000000010 00000 n 
                        0000000074 00000 n 
                        0000000142 00000 n 
                        0000000210 00000 n 
                        trailer<</Size 5/Root 1 0 R>>startxref 300 %%EOF""".trimIndent()
            )
        }

        @JvmStatic
        fun modelWithVisionCapability(): Stream<Arguments> {
            val openAIClient = OpenAILLMClient(readTestOpenAIKeyFromEnv())
            val anthropicClient = AnthropicLLMClient(readTestAnthropicKeyFromEnv())

            return Stream.concat(
                Models.openAIModels()
                    .filter { model ->
                        model.capabilities.contains(LLMCapability.Vision.Image)
                    }
                    .map { model -> Arguments.of(model, openAIClient) },

                Models.anthropicModels()
                    .filter { model ->
                        model.capabilities.contains(LLMCapability.Vision.Image)
                    }
                    .map { model -> Arguments.of(model, anthropicClient) }
            )
        }
    }

    // API keys for testing
    private val openAIApiKey: String get() = readTestOpenAIKeyFromEnv()
    private val anthropicApiKey: String get() = readTestAnthropicKeyFromEnv()

    @BeforeEach
    fun setup() {
        assertTrue(testResourcesDir.exists(), "Test resources directory should exist")
    }

    sealed interface OperationResult<T> {
        class Success<T>(val result: T) : OperationResult<T>
        class Failure<T>(val error: String) : OperationResult<T>
    }

    class MockFileSystem {
        private val fileContents: MutableMap<String, String> = mutableMapOf()

        fun create(path: String, content: String): OperationResult<Unit> {
            if (path in fileContents) return OperationResult.Failure("File already exists")
            fileContents[path] = content
            return OperationResult.Success(Unit)
        }

        fun delete(path: String): OperationResult<Unit> {
            if (path !in fileContents) return OperationResult.Failure("File does not exist")
            fileContents.remove(path)
            return OperationResult.Success(Unit)
        }

        fun read(path: String): OperationResult<String> {
            if (path !in fileContents) return OperationResult.Failure("File does not exist")
            return OperationResult.Success(fileContents[path]!!)
        }

        fun ls(path: String): OperationResult<List<String>> {
            if (path in fileContents) {
                return OperationResult.Failure("Path $path points to a file, but not a directory!")
            }
            val matchingFiles = fileContents
                .filter { (filePath, _) -> filePath.startsWith(path) }
                .map { (filePath, _) -> filePath }

            if (matchingFiles.isEmpty()) {
                return OperationResult.Failure("No files in the directory. Directory doesn't exist or is empty.")
            }
            return OperationResult.Success(matchingFiles)
        }

        fun fileCount(): Int = fileContents.size
    }

    class CreateFile(private val fs: MockFileSystem) : Tool<CreateFile.Args, CreateFile.Result>() {
        @Serializable
        data class Args(val path: String, val content: String) : Tool.Args

        @Serializable
        data class Result(
            val successful: Boolean,
            val message: String? = null
        ) : ToolResult.JSONSerializable<Result> {
            override fun getSerializer() = serializer()
        }

        override val argsSerializer = Args.serializer()

        override val descriptor: ToolDescriptor = ToolDescriptor(
            name = "create_file",
            description = "Create a file and writes the given text content to it",
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "path",
                    description = "The path to create the file",
                    type = ToolParameterType.String
                ),
                ToolParameterDescriptor(
                    name = "content",
                    description = "The content to create the file",
                    type = ToolParameterType.String
                )
            )
        )

        override suspend fun execute(args: Args): Result {
            val res = fs.create(args.path, args.content)
            return when (res) {
                is OperationResult.Success -> Result(successful = true)
                is OperationResult.Failure -> Result(successful = false, message = res.error)
            }
        }
    }

    class DeleteFile(private val fs: MockFileSystem) : Tool<DeleteFile.Args, DeleteFile.Result>() {
        @Serializable
        data class Args(val path: String) : Tool.Args

        @Serializable
        data class Result(
            val successful: Boolean,
            val message: String? = null
        ) : ToolResult {
            override fun toStringDefault(): String = "successful: $successful, message: \"$message\""
        }

        override val argsSerializer = Args.serializer()

        override val descriptor: ToolDescriptor = ToolDescriptor(
            name = "delete_file",
            description = "Deletes a file",
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "path",
                    description = "The path of the file to be deleted",
                    type = ToolParameterType.String
                )
            )
        )

        override suspend fun execute(args: Args): Result {
            val res = fs.delete(args.path)
            return when (res) {
                is OperationResult.Success -> Result(successful = true)
                is OperationResult.Failure -> Result(successful = false, message = res.error)
            }
        }
    }

    class ReadFile(private val fs: MockFileSystem) : Tool<ReadFile.Args, ReadFile.Result>() {
        @Serializable
        data class Args(val path: String) : Tool.Args

        @Serializable
        data class Result(
            val successful: Boolean,
            val message: String? = null,
            val content: String? = null
        ) : ToolResult.JSONSerializable<Result> {
            override fun getSerializer() = serializer()
        }

        override val argsSerializer = Args.serializer()

        override val descriptor: ToolDescriptor = ToolDescriptor(
            name = "read_file",
            description = "Reads a file",
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "path",
                    description = "The path of the file to read",
                    type = ToolParameterType.String
                )
            )
        )

        override suspend fun execute(args: Args): Result {
            val res = fs.read(args.path)
            return when (res) {
                is OperationResult.Success<String> -> Result(successful = true, content = res.result)
                is OperationResult.Failure -> Result(successful = false, message = res.error)
            }
        }
    }

    class ListFiles(private val fs: MockFileSystem) : Tool<ListFiles.Args, ListFiles.Result>() {
        @Serializable
        data class Args(val path: String) : Tool.Args

        @Serializable
        data class Result(
            val successful: Boolean,
            val message: String? = null,
            val children: List<String>? = null
        ) : ToolResult {
            override fun toStringDefault(): String =
                "successful: $successful, message: \"$message\", children: ${children?.joinToString()}"
        }

        override val argsSerializer = Args.serializer()

        override val descriptor: ToolDescriptor = ToolDescriptor(
            name = "list_files",
            description = "List all files inside the given path of the directory",
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "path",
                    description = "The path of the directory",
                    type = ToolParameterType.String
                )
            )
        )

        override suspend fun execute(args: Args): Result {
            val res = fs.ls(args.path)
            return when (res) {
                is OperationResult.Success<List<String>> -> Result(successful = true, children = res.result)
                is OperationResult.Failure -> Result(successful = false, message = res.error)
            }
        }
    }

    @Test
    fun integration_testAIAgentOpenAIAndAnthropic() = runTest(timeout = 600.seconds) {
        // Create the clients
        val eventsChannel = Channel<Event>(Channel.UNLIMITED)
        val fs = MockFileSystem()
        val eventHandlerConfig: EventHandlerConfig.() -> Unit = {
            onToolCall { tool, arguments ->
                println(
                    "Calling tool ${tool.name} with arguments ${
                        arguments.toString().lines().first().take(100)
                    }"
                )
            }

            onAgentFinished { _, _ ->
                eventsChannel.send(Event.Termination)
            }
        }
        val agent = createTestOpenaiAnthropicAgent(eventsChannel, fs, eventHandlerConfig, maxAgentIterations = 42)

        val result = agent.runAndGetResult(
            "Generate me a project in Ktor that has a GET endpoint that returns the capital of France. Write a test"
        )

        assertNotNull(result)

        assertTrue(
            fs.fileCount() > 0,
            "Agent must have created at least one file"
        )

        val messages = mutableListOf<Event.Message>()
        for (msg in eventsChannel) {
            if (msg is Event.Message) messages.add(msg)
            else break
        }

        assertTrue(
            messages.any { it.llmClient == "AnthropicLLMClient" },
            "At least one message must be delegated to Anthropic client"
        )

        assertTrue(
            messages.any { it.llmClient == "OpenAILLMClient" },
            "At least one message must be delegated to OpenAI client"
        )

        assertTrue(
            messages
                .filter { it.llmClient == "AnthropicLLMClient" }
                .all { it.model.provider == LLMProvider.Anthropic },
            "All prompts with Anthropic model must be delegated to Anthropic client"
        )

        assertTrue(
            messages
                .filter { it.llmClient == "OpenAILLMClient" }
                .all { it.model.provider == LLMProvider.OpenAI },
            "All prompts with OpenAI model must be delegated to OpenAI client"
        )
    }

    @Test
    fun integration_testTerminationOnIterationsLimitExhaustion() = runTest(timeout = 600.seconds) {
        val eventsChannel = Channel<Event>(Channel.UNLIMITED)
        val fs = MockFileSystem()
        var errorMessage: String? = null
        val eventHandlerConfig: EventHandlerConfig.() -> Unit = {
            onToolCall { tool, arguments ->
                println(
                    "Calling tool ${tool.name} with arguments ${
                        arguments.toString().lines().first().take(100)
                    }"
                )
            }

            onAgentFinished { _, _ ->
                eventsChannel.send(Event.Termination)
            }
        }
        val steps = 10
        val agent = createTestOpenaiAnthropicAgent(eventsChannel, fs, eventHandlerConfig, maxAgentIterations = steps)

        try {
            val result = agent.runAndGetResult(
                "Generate me a project in Ktor that has a GET endpoint that returns the capital of France. Write a test"
            )
            assertNull(result)
        } catch (e: AIAgentException) {
            errorMessage = e.message
        } finally {
            assertEquals(
                "AI Agent has run into a problem: Agent couldn't finish in given number of steps ($steps). " +
                        "Please, consider increasing `maxAgentIterations` value in agent's configuration",
                errorMessage
            )
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun createTestOpenaiAnthropicAgent(
        eventsChannel: Channel<Event>,
        fs: MockFileSystem,
        eventHandlerConfig: EventHandlerConfig.() -> Unit,
        maxAgentIterations: Int
    ): AIAgent {
        val openAIClient = OpenAILLMClient(openAIApiKey).reportingTo(eventsChannel)
        val anthropicClient = AnthropicLLMClient(anthropicApiKey).reportingTo(eventsChannel)

        val executor = MultiLLMPromptExecutor(
            LLMProvider.OpenAI to openAIClient,
            LLMProvider.Anthropic to anthropicClient
        )

        val strategy = strategy("test") {
            val anthropicSubgraph by subgraph<String, Unit>("anthropic") {
                val definePromptAnthropic by node<Unit, Unit> {
                    llm.writeSession {
                        model = AnthropicModels.Sonnet_3_7
                        rewritePrompt {
                            prompt("test", params = LLMParams(toolChoice = LLMParams.ToolChoice.Auto)) {
                                system(
                                    "You are a helpful assistant. You need to solve my task. " +
                                            "CALL TOOLS!!! DO NOT SEND MESSAGES!!!!! ONLY SEND THE FINAL MESSAGE " +
                                            "WHEN YOU ARE FINISHED AND EVERYTING IS DONE AFTER CALLING THE TOOLS!"
                                )
                            }
                        }
                    }
                }

                val callLLM by nodeLLMRequest(allowToolCalls = true)
                val callTool by nodeExecuteTool()
                val sendToolResult by nodeLLMSendToolResult()


                edge(nodeStart forwardTo definePromptAnthropic transformed {})
                edge(definePromptAnthropic forwardTo callLLM transformed { agentInput })
                edge(callLLM forwardTo callTool onToolCall { true })
                edge(callLLM forwardTo nodeFinish onAssistantMessage { true } transformed {})
                edge(callTool forwardTo sendToolResult)
                edge(sendToolResult forwardTo callTool onToolCall { true })
                edge(sendToolResult forwardTo nodeFinish onAssistantMessage { true } transformed {})
            }

            val openaiSubgraph by subgraph("openai") {
                val definePromptOpenAI by node<Unit, Unit> {
                    llm.writeSession {
                        model = OpenAIModels.Chat.GPT4o
                        rewritePrompt {
                            prompt("test", params = LLMParams(toolChoice = LLMParams.ToolChoice.Auto)) {
                                system(
                                    "You are a helpful assistant. You need to verify that the task is solved correctly. " +
                                            "Please analyze the whole produced solution, and check that it is valid." +
                                            "Write concise verification result." +
                                            "CALL TOOLS!!! DO NOT SEND MESSAGES!!!!! " +
                                            "ONLY SEND THE FINAL MESSAGE WHEN YOU ARE FINISHED AND EVERYTING IS DONE " +
                                            "AFTER CALLING THE TOOLS!"
                                )
                            }
                        }
                    }
                }

                val callLLM by nodeLLMRequest(allowToolCalls = true)
                val callTool by nodeExecuteTool()
                val sendToolResult by nodeLLMSendToolResult()


                edge(nodeStart forwardTo definePromptOpenAI)
                edge(definePromptOpenAI forwardTo callLLM transformed { agentInput })
                edge(callLLM forwardTo callTool onToolCall { true })
                edge(callLLM forwardTo nodeFinish onAssistantMessage { true })
                edge(callTool forwardTo sendToolResult)
                edge(sendToolResult forwardTo callTool onToolCall { true })
                edge(sendToolResult forwardTo nodeFinish onAssistantMessage { true })
            }

            val compressHistoryNode by nodeLLMCompressHistory<Unit>("compress_history")

            nodeStart then anthropicSubgraph then compressHistoryNode then openaiSubgraph then nodeFinish
        }

        val tools = ToolRegistry {
            tool(CreateFile(fs))
            tool(DeleteFile(fs))
            tool(ReadFile(fs))
            tool(ListFiles(fs))
        }

        // Create the agent
        return AIAgent(
            promptExecutor = executor,
            strategy = strategy,
            agentConfig = AIAgentConfig(prompt("test") {}, OpenAIModels.Chat.GPT4o, maxAgentIterations),
            toolRegistry = tools,
        ) {
            install(Tracing) {
                addMessageProcessor(TestLogPrinter())
            }

            install(EventHandler, eventHandlerConfig)
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun createTestOpenaiAgent(
        eventsChannel: Channel<Event>,
        fs: MockFileSystem,
        eventHandlerConfig: EventHandlerConfig.() -> Unit,
        maxAgentIterations: Int
    ): AIAgent {
        val openAIClient = OpenAILLMClient(openAIApiKey).reportingTo(eventsChannel)
        val anthropicClient = AnthropicLLMClient(anthropicApiKey).reportingTo(eventsChannel)

        // Create the executor
        val executor = //grazieExecutor
            MultiLLMPromptExecutor(
                LLMProvider.OpenAI to openAIClient,
                LLMProvider.Anthropic to anthropicClient
            )

        // Create a simple agent strategy
        val strategy = strategy("test") {
            val openaiSubgraphFirst by subgraph<String, Unit>("openai0") {
                val definePromptOpenAI by node<Unit, Unit> {
                    llm.writeSession {
                        model = OpenAIModels.Chat.GPT4o
                        rewritePrompt {
                            prompt("test") {
                                system(
                                    "You are a helpful assistant. You need to verify that the task is solved correctly. " +
                                            "Please analyze the whole produced solution, and check that it is valid." +
                                            "Write concise verification result." +
                                            "CALL TOOLS!!! DO NOT SEND MESSAGES!!!!! ONLY SEND THE FINAL MESSAGE WHEN YOU ARE FINISHED AND EVERYTING IS DONE AFTER CALLING THE TOOLS!"
                                )
                            }
                        }
                    }
                }


                val callLLM by nodeLLMRequest(allowToolCalls = true)
                val callTool by nodeExecuteTool()
                val sendToolResult by nodeLLMSendToolResult()


                edge(nodeStart forwardTo definePromptOpenAI transformed {})
                edge(definePromptOpenAI forwardTo callLLM transformed { agentInput })
                edge(callLLM forwardTo callTool onToolCall { true })
                edge(callLLM forwardTo nodeFinish onAssistantMessage { true } transformed {})
                edge(callTool forwardTo sendToolResult)
                edge(sendToolResult forwardTo callTool onToolCall { true })
                edge(sendToolResult forwardTo nodeFinish onAssistantMessage { true } transformed {})
            }

            val openaiSubgraphSecond by subgraph("openai1") {
                val definePromptOpenAI by node<Unit, Unit> {
                    llm.writeSession {
                        model = OpenAIModels.Chat.GPT4o
                        rewritePrompt {
                            prompt("test") {
                                system(
                                    "You are a helpful assistant. You need to verify that the task is solved correctly. " +
                                            "Please analyze the whole produced solution, and check that it is valid." +
                                            "Write concise verification result." +
                                            "CALL TOOLS!!! DO NOT SEND MESSAGES!!!!! ONLY SEND THE FINAL MESSAGE WHEN YOU ARE FINISHED AND EVERYTING IS DONE AFTER CALLING THE TOOLS!"
                                )
                            }
                        }
                    }
                }

                val callLLM by nodeLLMRequest(allowToolCalls = true)
                val callTool by nodeExecuteTool()
                val sendToolResult by nodeLLMSendToolResult()


                edge(nodeStart forwardTo definePromptOpenAI)
                edge(definePromptOpenAI forwardTo callLLM transformed { agentInput })
                edge(callLLM forwardTo callTool onToolCall { true })
                edge(callLLM forwardTo nodeFinish onAssistantMessage { true })
                edge(callTool forwardTo sendToolResult)
                edge(sendToolResult forwardTo callTool onToolCall { true })
                edge(sendToolResult forwardTo nodeFinish onAssistantMessage { true })
            }

            val compressHistoryNode by nodeLLMCompressHistory<Unit>("compress_history")

            nodeStart then openaiSubgraphFirst then compressHistoryNode then openaiSubgraphSecond then nodeFinish
        }

        val tools = ToolRegistry {
            tool(CreateFile(fs))
            tool(DeleteFile(fs))
            tool(ReadFile(fs))
            tool(ListFiles(fs))
        }

        // Create the agent
        return AIAgent(
            promptExecutor = executor,
            strategy = strategy,
            agentConfig = AIAgentConfig(prompt("test") {}, OpenAIModels.Chat.GPT4o, maxAgentIterations),
            toolRegistry = tools,
        ) {
            install(Tracing) {
                addMessageProcessor(TestLogPrinter())
            }

            install(EventHandler, eventHandlerConfig)
        }
    }


    @Test
    fun integration_testAnthropicAgent() = runTest {
        val eventsChannel = Channel<Event>(Channel.UNLIMITED)
        val fs = MockFileSystem()
        val eventHandlerConfig: EventHandlerConfig.() -> Unit = {
            onToolCall { tool, arguments ->
                println(
                    "Calling tool ${tool.name} with arguments ${
                        arguments.toString().lines().first().take(100)
                    }"
                )
            }

            onAgentFinished { _, _ ->
                eventsChannel.send(Event.Termination)
            }
        }
        val agent = createTestOpenaiAnthropicAgent(eventsChannel, fs, eventHandlerConfig, maxAgentIterations = 42)
        val result = agent.runAndGetResult(
            "Name me a capital of France"
        )

        assertNotNull(result)
    }

    @Test
    fun integration_testOpenAIAnthropicAgentWithTools() = runTest(timeout = 300.seconds) {
        val eventsChannel = Channel<Event>(Channel.UNLIMITED)
        val fs = MockFileSystem()
        val eventHandlerConfig: EventHandlerConfig.() -> Unit = {
            onToolCall { tool, arguments ->
                println(
                    "Calling tool ${tool.name} with arguments ${
                        arguments.toString().lines().first().take(100)
                    }"
                )
            }

            onAgentFinished { _, _ ->
                eventsChannel.send(Event.Termination)
            }
        }
        val agent = createTestOpenaiAgent(eventsChannel, fs, eventHandlerConfig, maxAgentIterations = 42)

        val result = agent.runAndGetResult(
            "Name me a capital of France"
        )

        assertNotNull(result)
    }

    @Serializable
    enum class CalculatorOperation {
        ADD, SUBTRACT, MULTIPLY, DIVIDE
    }

    object CalculatorTool : Tool<CalculatorTool.Args, ToolResult.Number>() {
        @Serializable
        data class Args(val operation: CalculatorOperation, val a: Int, val b: Int) : Tool.Args

        override val argsSerializer = Args.serializer()

        override val descriptor: ToolDescriptor = ToolDescriptor(
            name = "calculator",
            description = "A simple calculator that can add, subtract, multiply, and divide two numbers.",
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "operation",
                    description = "The operation to perform.",
                    type = ToolParameterType.Enum(CalculatorOperation.entries.map { it.name }.toTypedArray())
                ),
                ToolParameterDescriptor(
                    name = "a",
                    description = "The first argument (number)",
                    type = ToolParameterType.Integer
                ),
                ToolParameterDescriptor(
                    name = "b",
                    description = "The second argument (number)",
                    type = ToolParameterType.Integer
                )
            )
        )

        override suspend fun execute(args: Args): ToolResult.Number = when (args.operation) {
            CalculatorOperation.ADD -> args.a + args.b
            CalculatorOperation.SUBTRACT -> args.a - args.b
            CalculatorOperation.MULTIPLY -> args.a * args.b
            CalculatorOperation.DIVIDE -> args.a / args.b
        }.let(ToolResult::Number)
    }

    @Test
    fun integration_testAnthropicAgentEnumSerialization() {
        runBlocking {
            val agent = AIAgent(
                executor = simpleAnthropicExecutor(anthropicApiKey),
                llmModel = AnthropicModels.Sonnet_3_7,
                systemPrompt = "You are a calculator with access to the calculator tools. Please call tools!!!",
                toolRegistry = ToolRegistry {
                    tool(CalculatorTool)
                },
                installFeatures = {
                    install(EventHandler) {
                        onAgentRunError { _, _, e ->
                            println("error: ${e.javaClass.simpleName}(${e.message})\n${e.stackTraceToString()}")
                            true
                        }
                        onToolCall { tool, arguments ->
                            println(
                                "Calling tool ${tool.name} with arguments ${
                                    arguments.toString().lines().first().take(100)
                                }"
                            )
                        }
                    }
                }
            )

            val result = agent.runAndGetResult("calculate 10 plus 15, and then subtract 8")
            println("result = $result")
            assertNotNull(result)
            assertContains(result, "17")
        }
    }

    @ParameterizedTest
    @MethodSource("modelWithVisionCapability")
    fun integration_testAgentWithImageCapability(model: LLModel) = runTest(timeout = 120.seconds) {
        val eventsChannel = Channel<Event>(Channel.UNLIMITED)
        val fs = MockFileSystem()
        val eventHandlerConfig: EventHandlerConfig.() -> Unit = {
            onToolCall { tool, arguments ->
                println(
                    "Calling tool ${tool.name} with arguments ${
                        arguments.toString().lines().first().take(100)
                    }"
                )
            }

            onAgentFinished { _, _ ->
                eventsChannel.send(Event.Termination)
            }
        }

        val imageFile = File(testResourcesDir, "test.png")
        assertTrue(imageFile.exists(), "Image test file should exist")

        val imageBytes = imageFile.readBytes()
        val base64Image = java.util.Base64.getEncoder().encodeToString(imageBytes)

        val agent = when (model.provider) {
            is LLMProvider.Anthropic -> createTestOpenaiAnthropicAgent(
                eventsChannel,
                fs,
                eventHandlerConfig,
                maxAgentIterations = 20
            )

            else -> createTestOpenaiAgent(eventsChannel, fs, eventHandlerConfig, maxAgentIterations = 20)
        }

        val result = agent.runAndGetResult(
            """
            I'm sending you an image encoded in base64 format.

            data:image/png,$base64Image

            Please analyze this image and describe what you see.
            """
        )

        assertNotNull(result, "Result should not be null")
    }
}
