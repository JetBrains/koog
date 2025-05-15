package ai.jetbrains.code.prompt.executor.llms.all

import ai.grazie.code.agents.core.agent.AIAgent
import ai.grazie.code.agents.core.agent.AIAgentException
import ai.grazie.code.agents.core.agent.config.AIAgentConfig
import ai.grazie.code.agents.core.agent.entity.ContextTransitionPolicy
import ai.grazie.code.agents.core.api.simpleSingleRunAgent
import ai.grazie.code.agents.core.dsl.builder.forwardTo
import ai.grazie.code.agents.core.dsl.builder.strategy
import ai.grazie.code.agents.core.dsl.extension.*
import ai.grazie.code.agents.core.tools.*
import ai.grazie.code.agents.local.features.eventHandler.feature.EventHandler
import ai.grazie.code.agents.local.features.eventHandler.feature.EventHandlerConfig
import ai.grazie.code.agents.local.features.tracing.feature.Tracing
import ai.jetbrains.code.prompt.dsl.Prompt
import ai.jetbrains.code.prompt.dsl.prompt
import ai.jetbrains.code.prompt.executor.clients.LLMClient
import ai.jetbrains.code.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.jetbrains.code.prompt.executor.clients.anthropic.AnthropicModels
import ai.jetbrains.code.prompt.executor.clients.openai.OpenAILLMClient
import ai.jetbrains.code.prompt.executor.clients.openai.OpenAIModels
import ai.jetbrains.code.prompt.executor.llms.MultiLLMPromptExecutor
import ai.jetbrains.code.prompt.executor.llms.all.ReportingLLMLLMClient.Event
import ai.jetbrains.code.prompt.llm.LLMProvider
import ai.jetbrains.code.prompt.llm.LLModel
import ai.jetbrains.code.prompt.message.Message
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Disabled
import kotlin.coroutines.coroutineContext
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

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
class KotlinAIAgentWithMultipleLLMTest {

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

    @Disabled("Todo fix")
    @Test
    fun integration_testKotlinAIAgentWithOpenAIAndAnthropic() = runTest(timeout = 600.seconds) {
        // Create the clients
        val eventsChannel = Channel<Event>(Channel.UNLIMITED)
        val fs = MockFileSystem()
        val eventHandlerConfig: EventHandlerConfig.() -> Unit = {
            onToolCall = { stage, tool, arguments ->
                println(
                    "[$stage] Calling tool ${tool.name} with arguments ${
                        arguments.toString().lines().first().take(100)
                    }"
                )
            }

            onAgentFinished = { _, _ ->
                eventsChannel.send(Event.Termination)
            }
        }
        val agent = createTestAgent(eventsChannel, fs, eventHandlerConfig, maxAgentIterations = 42)

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
            onToolCall = { stage, tool, arguments ->
                println(
                    "[$stage] Calling tool ${tool.name} with arguments ${
                        arguments.toString().lines().first().take(100)
                    }"
                )
            }

            onAgentFinished = { _, _ ->
                eventsChannel.send(Event.Termination)
            }
        }
        val steps = 10
        val agent = createTestAgent(eventsChannel, fs, eventHandlerConfig, maxAgentIterations = steps)

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
    private fun createTestAgent(
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
        val strategy = strategy("test", llmHistoryTransitionPolicy = ContextTransitionPolicy.CLEAR_LLM_HISTORY) {
            stage("anthropic") {
                val definePromptAnthropic by node<Unit, Unit> {
                    llm.writeSession {
                        model = AnthropicModels.Sonnet_3_7
                        rewritePrompt {
                            prompt("test") {
                                system("You are a helpful assistant. You need to solve my task. CALL TOOLS!!! DO NOT SEND MESSAGES!!!!! ONLY SEND THE FINAL MESSAGE WHEN YOU ARE FINISHED AND EVERYTING IS DONE AFTER CALLING THE TOOLS!")
                            }
                        }
                    }
                }

                val callLLM by nodeLLMRequest(allowToolCalls = true)
                val callTool by nodeExecuteTool()
                val sendToolResult by nodeLLMSendToolResult()


                edge(nodeStart forwardTo definePromptAnthropic)
                edge(definePromptAnthropic forwardTo callLLM transformed { stageInput })
                edge(callLLM forwardTo callTool onToolCall { true })
                edge(callLLM forwardTo nodeFinish onAssistantMessage { true })
                edge(callTool forwardTo sendToolResult)
                edge(sendToolResult forwardTo callTool onToolCall { true })
                edge(sendToolResult forwardTo nodeFinish onAssistantMessage { true })
            }
            stage("openai") {
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
                edge(definePromptOpenAI forwardTo callLLM transformed { stageInput })
                edge(callLLM forwardTo callTool onToolCall { true })
                edge(callLLM forwardTo nodeFinish onAssistantMessage { true })
                edge(callTool forwardTo sendToolResult)
                edge(sendToolResult forwardTo callTool onToolCall { true })
                edge(sendToolResult forwardTo nodeFinish onAssistantMessage { true })
            }
        }

        val tools = ToolRegistry {
            stage("anthropic") {
                tool(CreateFile(fs))
                tool(DeleteFile(fs))
                tool(ReadFile(fs))
            }
            stage("openai") {
                tool(ReadFile(fs))
                tool(ListFiles(fs))
            }
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
    fun integration_testOpenAIAnthropicAgent() = runTest {
        val openAIClient = OpenAILLMClient(openAIApiKey)
        val anthropicClient = AnthropicLLMClient(anthropicApiKey)

        val executor = MultiLLMPromptExecutor(
            LLMProvider.OpenAI to openAIClient, LLMProvider.Anthropic to anthropicClient
        )

        val strategy = strategy("test", llmHistoryTransitionPolicy = ContextTransitionPolicy.CLEAR_LLM_HISTORY) {
            stage("anthropic") {
                val definePromptAnthropic by node<Unit, Unit> {
                    llm.writeSession {
                        model = AnthropicModels.Sonnet_3_7
                        rewritePrompt {
                            prompt("test") {
                                system("You are a helpful assistant. You need to solve my task.")
                            }
                        }
                    }
                }

                val callLLM by nodeLLMRequest(allowToolCalls = true)


                edge(nodeStart forwardTo definePromptAnthropic)
                edge(definePromptAnthropic forwardTo callLLM transformed { stageInput })
                edge(callLLM forwardTo nodeFinish onAssistantMessage { true })
            }
            stage("openai") {
                val definePromptOpenAI by node<Unit, Unit> {
                    llm.writeSession {
                        model = OpenAIModels.Chat.GPT4o
                        rewritePrompt {
                            prompt("test") {
                                system(
                                    "You are a helpful assistant. You need to verify that the task is solved correctly. " + "Please analyze the whole produced solution, and check that it is valid." + "Write concise verification result."
                                )
                            }
                        }
                    }
                }

                val callLLM by nodeLLMRequest(allowToolCalls = true)

                edge(nodeStart forwardTo definePromptOpenAI)
                edge(definePromptOpenAI forwardTo callLLM transformed { stageInput })
                edge(callLLM forwardTo nodeFinish onAssistantMessage { true })
            }
            stage("anthropic") {
                val definePromptAnthropic by node<Unit, Unit> {
                    llm.writeSession {
                        model = AnthropicModels.Sonnet_3_7
                        rewritePrompt {
                            prompt("test") {
                                system("Add some joke at the end of the solution.")
                            }
                        }
                    }
                }

                val callLLM by nodeLLMRequest(allowToolCalls = true)


                edge(nodeStart forwardTo definePromptAnthropic)
                edge(definePromptAnthropic forwardTo callLLM transformed { stageInput })
                edge(callLLM forwardTo nodeFinish onAssistantMessage { true })
            }
        }

        val tools = ToolRegistry {}

        val agent = AIAgent(
            promptExecutor = executor,
            strategy = strategy,
            agentConfig = AIAgentConfig(prompt("test") {}, OpenAIModels.Chat.GPT4o, 15),
            toolRegistry = tools,
        ) {
            install(Tracing) {
                addMessageProcessor(TestLogPrinter())
            }

            install(EventHandler) {
                onToolCall = { stage, tool, arguments ->
                    println(
                        "[$stage] Calling tool ${tool.name} with arguments ${
                            arguments.toString().lines().first().take(100)
                        }"
                    )
                }

                onAgentFinished = { _, _ ->
                    println(Event.Termination)
                }
            }
        }

        val result = agent.runAndGetResult(
            "Name me a capital of France"
        )

        assertNotNull(result)
    }

    @Test
    fun integration_testOpenAIAgent() = runTest {
        val openAIClient = OpenAILLMClient(openAIApiKey)

        val executor = MultiLLMPromptExecutor(
            LLMProvider.OpenAI to openAIClient
        )

        val strategy = strategy("test", llmHistoryTransitionPolicy = ContextTransitionPolicy.CLEAR_LLM_HISTORY) {
            stage("openai_initial") {
                val defineInitialPrompt by node<Unit, Unit> {
                    llm.writeSession {
                        model = OpenAIModels.Chat.GPT4o
                        rewritePrompt {
                            prompt("test") {
                                system("You are a helpful assistant. You need to solve my task.")
                            }
                        }
                    }
                }

                val callLLM by nodeLLMRequest(allowToolCalls = true)


                edge(nodeStart forwardTo defineInitialPrompt)
                edge(defineInitialPrompt forwardTo callLLM transformed { stageInput })
                edge(callLLM forwardTo nodeFinish onAssistantMessage { true })
            }
            stage("openai_judge") {
                val defineLLMasAJudgePrompt by node<Unit, Unit> {
                    llm.writeSession {
                        model = OpenAIModels.Chat.GPT4o
                        rewritePrompt {
                            prompt("test") {
                                system(
                                    "You are a helpful assistant. You need to verify that the task is solved correctly. " + "Please analyze the whole produced solution, and check that it is valid." + "Write concise verification result."
                                )
                            }
                        }
                    }
                }

                val callLLM by nodeLLMRequest(allowToolCalls = true)

                edge(nodeStart forwardTo defineLLMasAJudgePrompt)
                edge(defineLLMasAJudgePrompt forwardTo callLLM transformed { stageInput })
                edge(callLLM forwardTo nodeFinish onAssistantMessage { true })
            }
        }

        val tools = ToolRegistry {}

        val agent = AIAgent(
            promptExecutor = executor,
            strategy = strategy,
            agentConfig = AIAgentConfig(prompt("test") {}, OpenAIModels.Chat.GPT4o, 15),
            toolRegistry = tools
        ) {
            install(Tracing) {
                addMessageProcessor(TestLogPrinter())
            }

            install(EventHandler) {
                onToolCall = { stage, tool, arguments ->
                    println(
                        "[$stage] Calling tool ${tool.name} with arguments ${
                            arguments.toString().lines().first().take(100)
                        }"
                    )
                }

                onAgentFinished = { _, _ ->
                    println(Event.Termination)
                }
            }
        }

        val result = agent.runAndGetResult(
            "Name me a capital of France"
        )

        assertNotNull(result)
    }

    @Test
    fun integration_testAnthropicAgent() = runTest {
        val anthropicClient = AnthropicLLMClient(anthropicApiKey)
        val executor = MultiLLMPromptExecutor(
            LLMProvider.Anthropic to anthropicClient
        )

        val strategy = strategy("test", llmHistoryTransitionPolicy = ContextTransitionPolicy.CLEAR_LLM_HISTORY) {
            stage("openai_initial") {
                val defineInitialPrompt by node<Unit, Unit> {
                    llm.writeSession {
                        model = AnthropicModels.Sonnet_3_5
                        rewritePrompt {
                            prompt("test") {
                                system("You are a helpful assistant. You need to solve my task.")
                            }
                        }
                    }
                }

                val callLLM by nodeLLMRequest(allowToolCalls = true)


                edge(nodeStart forwardTo defineInitialPrompt)
                edge(defineInitialPrompt forwardTo callLLM transformed { stageInput })
                edge(callLLM forwardTo nodeFinish onAssistantMessage { true })
            }
            stage("openai_judge") {
                val defineLLMasAJudgePrompt by node<Unit, Unit> {
                    llm.writeSession {
                        model = AnthropicModels.Sonnet_3_7
                        rewritePrompt {
                            prompt("test") {
                                system(
                                    "You are a helpful assistant. You need to verify that the task is solved correctly. " + "Please analyze the whole produced solution, and check that it is valid." + "Write concise verification result."
                                )
                            }
                        }
                    }
                }

                val callLLM by nodeLLMRequest(allowToolCalls = true)

                edge(nodeStart forwardTo defineLLMasAJudgePrompt)
                edge(defineLLMasAJudgePrompt forwardTo callLLM transformed { stageInput })
                edge(callLLM forwardTo nodeFinish onAssistantMessage { true })
            }
        }

        val tools = ToolRegistry {}

        val agent = AIAgent(
            promptExecutor = executor,
            strategy = strategy,
            agentConfig = AIAgentConfig(prompt("test") {}, AnthropicModels.Sonnet_3_7, 15),
            toolRegistry = tools
        ) {
            install(Tracing) {
                addMessageProcessor(TestLogPrinter())
            }

            install(EventHandler) {
                onToolCall = { stage, tool, arguments ->
                    println(
                        "[$stage] Calling tool ${tool.name} with arguments ${
                            arguments.toString().lines().first().take(100)
                        }"
                    )
                }

                onAgentFinished = { _, _ ->
                    println(Event.Termination)
                }
            }
        }

        val result = agent.runAndGetResult(
            "Name me a capital of France"
        )

        assertNotNull(result)
    }

    @Disabled("Todo fix")
    @Test
    fun integration_testOpenAIAnthropicAgentWithTools() = runTest(timeout = 300.seconds) {
        val openAIClient = OpenAILLMClient(openAIApiKey)
        val anthropicClient = AnthropicLLMClient(anthropicApiKey)

        val executor = MultiLLMPromptExecutor(
            LLMProvider.OpenAI to openAIClient, LLMProvider.Anthropic to anthropicClient
        )

        val strategy = strategy("test-tools", llmHistoryTransitionPolicy = ContextTransitionPolicy.CLEAR_LLM_HISTORY) {
            stage("anthropic-calculator") {
                val definePromptAnthropic by node<Unit, Unit> {
                    llm.writeSession {
                        model = AnthropicModels.Sonnet_3_7
                        rewritePrompt {
                            prompt("test-tools") {
                                system("You are a helpful assistant. You need to solve a math problem using the calculator tool.")
                            }
                        }
                    }
                }

                val callLLM by nodeLLMRequest(allowToolCalls = true)
                val callTool by nodeExecuteTool()
                val sendToolResult by nodeLLMSendToolResult()

                edge(nodeStart forwardTo definePromptAnthropic)
                edge(definePromptAnthropic forwardTo callLLM transformed { stageInput })
                edge(callLLM forwardTo callTool onToolCall { true })
                edge(callLLM forwardTo nodeFinish onAssistantMessage { true })
                edge(callTool forwardTo sendToolResult)
                edge(sendToolResult forwardTo nodeFinish onAssistantMessage { true })
            }
            stage("openai-color-picker") {
                val definePromptOpenAI by node<Unit, Unit> {
                    llm.writeSession {
                        model = OpenAIModels.Chat.GPT4o
                        rewritePrompt {
                            prompt("test-tools") {
                                system(
                                    "You are a helpful assistant. You need to pick some colors using the colorPicker tool. " +
                                            "Please pick at least 3 colors."
                                )
                            }
                        }
                    }
                }

                val callLLM by nodeLLMRequest(allowToolCalls = true)
                val callTool by nodeExecuteTool()
                val sendToolResult by nodeLLMSendToolResult()

                edge(nodeStart forwardTo definePromptOpenAI)
                edge(definePromptOpenAI forwardTo callLLM transformed { stageInput })
                edge(callLLM forwardTo callTool onToolCall { true })
                edge(callLLM forwardTo nodeFinish onAssistantMessage { true })
                edge(callTool forwardTo sendToolResult)
                edge(sendToolResult forwardTo nodeFinish onAssistantMessage { true })
            }
            stage("anthropic-summary") {
                val definePromptAnthropic by node<Unit, Unit> {
                    llm.writeSession {
                        model = AnthropicModels.Sonnet_3_7
                        rewritePrompt {
                            prompt("test-tools") {
                                system("Summarize the results of the previous operations and add a joke at the end.")
                            }
                        }
                    }
                }

                val callLLM by nodeLLMRequest(allowToolCalls = true)
                val callTool by nodeExecuteTool()
                val sendToolResult by nodeLLMSendToolResult()

                edge(nodeStart forwardTo definePromptAnthropic)
                edge(definePromptAnthropic forwardTo callLLM transformed { stageInput })
                edge(callLLM forwardTo callTool onToolCall { true })
                edge(callLLM forwardTo nodeFinish onAssistantMessage { true })
                edge(callTool forwardTo sendToolResult)
                edge(sendToolResult forwardTo nodeFinish onAssistantMessage { true })
            }
        }

        val tools = ToolRegistry {
            stage("anthropic-calculator") {
                tool(TestUtils.CalculatorTool())
            }
            stage("openai-color-picker") {
                tool(TestUtils.ColorPickerTool())
            }
            stage("anthropic-summary") {
                tool(TestUtils.SummaryTool())
            }
        }

        val agent = AIAgent(
            promptExecutor = executor,
            strategy = strategy,
            agentConfig = AIAgentConfig(prompt("test-tools") {}, OpenAIModels.Chat.GPT4o, 15),
            toolRegistry = tools
        ) {
            install(Tracing) {
                addMessageProcessor(TestLogPrinter())
            }

            install(EventHandler) {
                onToolCall = { stage, tool, arguments ->
                    println(
                        "[$stage] Calling tool ${tool.name} with arguments ${
                            arguments.toString().lines().first().take(100)
                        }"
                    )
                }

                onAgentFinished = { _, _ ->
                    println(Event.Termination)
                }
            }
        }

        val result = agent.runAndGetResult(
            "Calculate 42 + 58 and then pick some nice colors for my website"
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
            val a = simpleSingleRunAgent(
                executor = simpleAnthropicExecutor(anthropicApiKey),
                llmModel = AnthropicModels.Sonnet_3_7,
                systemPrompt = "You are a calculator with access to the calculator tools. Please call tools!!!",
                toolRegistry = SimpleToolRegistry {
                    tool(CalculatorTool)
                },
                installFeatures = {
                    install(EventHandler) {
                        onAgentRunError = { _, e ->
                            println("error: ${e.javaClass.simpleName}(${e.message})\n${e.stackTraceToString()}")
                            true
                        }
                        onToolCall = { stage, tool, arguments ->
                            println(
                                "[${stage.name}] Calling tool ${tool.name} with arguments ${
                                    arguments.toString().lines().first().take(100)
                                }"
                            )
                        }
                    }
                }
            )

            val result = a.runAndGetResult("calculate 10 plus 15, and then subtract 8")
            println("result = $result")
            assertNotNull(result)
            assertContains(result, "17")
        }
    }
}
