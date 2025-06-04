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
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.executor.llms.all.simpleAnthropicExecutor
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
class KotlinAIAgentWithMultipleLLMIntegrationTest {

    // API keys for testing
    private val openAIApiKey: String get() = readTestOpenAIKeyFromEnv()
    private val anthropicApiKey: String get() = readTestAnthropicKeyFromEnv()

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
    fun integration_testKotlinAIAgentWithOpenAIAndAnthropic() = runTest(timeout = 600.seconds) {
        // Create the clients
        val eventsChannel = Channel<Event>(Channel.UNLIMITED)
        val fs = MockFileSystem()
        val eventHandlerConfig: EventHandlerConfig.() -> Unit = {
            onToolCall = { tool, arguments ->
                println(
                    "Calling tool ${tool.name} with arguments ${
                        arguments.toString().lines().first().take(100)
                    }"
                )
            }

            onAgentFinished = { _, _ ->
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
            onToolCall = { tool, arguments ->
                println(
                    "Calling tool ${tool.name} with arguments ${
                        arguments.toString().lines().first().take(100)
                    }"
                )
            }

            onAgentFinished = { _, _ ->
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
            onToolCall = { tool, arguments ->
                println(
                    "Calling tool ${tool.name} with arguments ${
                        arguments.toString().lines().first().take(100)
                    }"
                )
            }

            onAgentFinished = { _, _ ->
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
            onToolCall = { tool, arguments ->
                println(
                    "Calling tool ${tool.name} with arguments ${
                        arguments.toString().lines().first().take(100)
                    }"
                )
            }

            onAgentFinished = { _, _ ->
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
                        onAgentRunError = { _, _, e ->
                            println("error: ${e.javaClass.simpleName}(${e.message})\n${e.stackTraceToString()}")
                            true
                        }
                        onToolCall = { tool, arguments ->
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
}
