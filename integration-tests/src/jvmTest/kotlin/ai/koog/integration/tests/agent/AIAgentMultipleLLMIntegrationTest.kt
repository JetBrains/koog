package ai.koog.integration.tests.agent

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.context.agentInput
import ai.koog.agents.core.agent.exception.AIAgentException
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeExecuteTool
import ai.koog.agents.core.dsl.extension.nodeLLMCompressHistory
import ai.koog.agents.core.dsl.extension.nodeLLMRequest
import ai.koog.agents.core.dsl.extension.nodeLLMSendToolResult
import ai.koog.agents.core.dsl.extension.onAssistantMessage
import ai.koog.agents.core.dsl.extension.onToolCall
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.ext.agent.subgraphWithTask
import ai.koog.agents.features.eventHandler.feature.EventHandler
import ai.koog.agents.features.eventHandler.feature.EventHandlerConfig
import ai.koog.integration.tests.agent.ReportingLLMLLMClient.Event
import ai.koog.integration.tests.utils.Models
import ai.koog.integration.tests.utils.RetryUtils.withRetry
import ai.koog.integration.tests.utils.TestUtils.readTestAnthropicKeyFromEnv
import ai.koog.integration.tests.utils.TestUtils.readTestOpenAIKeyFromEnv
import ai.koog.integration.tests.utils.annotations.Retry
import ai.koog.prompt.dsl.ModerationResult
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
import ai.koog.prompt.markdown.markdown
import ai.koog.prompt.message.Message
import ai.koog.prompt.params.LLMParams
import ai.koog.prompt.streaming.StreamFrame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.io.File
import java.util.Base64
import java.util.stream.Stream
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration.Companion.minutes

internal class ReportingLLMLLMClient(
    private val eventsChannel: Channel<Event>,
    private val underlyingClient: LLMClient
) : LLMClient {

    override fun llmProvider(): LLMProvider = underlyingClient.llmProvider()
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
        CoroutineScope(currentCoroutineContext()).launch {
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

    override fun executeStreaming(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ): Flow<StreamFrame> = flow {
        coroutineScope {
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
        underlyingClient.executeStreaming(prompt, model, tools)
            .collect(this)
    }

    override suspend fun moderate(
        prompt: Prompt,
        model: LLModel
    ): ModerationResult {
        throw NotImplementedError("Moderation not needed for this test")
    }

    override fun close() {
        underlyingClient.close()
        eventsChannel.close()
    }
}

internal fun LLMClient.reportingTo(
    eventsChannel: Channel<Event>
) = ReportingLLMLLMClient(eventsChannel, this)

class AIAgentMultipleLLMIntegrationTest {

    companion object {
        private lateinit var testResourcesDir: File

        @JvmStatic
        fun getModels(): Stream<LLModel> = Stream.of(
            AnthropicModels.Sonnet_4_5,
            OpenAIModels.Chat.GPT5,
        )

        @JvmStatic
        @BeforeAll
        fun setup() {
            testResourcesDir = File("src/jvmTest/resources/media")
            testResourcesDir.mkdirs()
            assertTrue(testResourcesDir.exists(), "Test resources directory should exist")
        }

        @JvmStatic
        fun modelsWithVisionCapability(): Stream<Arguments> {
            return Models.modelsWithVisionCapability()
        }
    }

    // API keys for testing
    private val openAIApiKey: String get() = readTestOpenAIKeyFromEnv()
    private val anthropicApiKey: String get() = readTestAnthropicKeyFromEnv()

    @Serializable
    enum class CalculatorOperation {
        ADD,
        SUBTRACT,
        MULTIPLY,
        DIVIDE
    }

    object CalculatorTool : Tool<CalculatorTool.Args, Int>() {
        @Serializable
        data class Args(
            @property:LLMDescription("The operation to perform.")
            val operation: CalculatorOperation,
            @property:LLMDescription("The first argument (number)")
            val a: Int,
            @property:LLMDescription("The second argument (number)")
            val b: Int
        )

        override val argsSerializer = Args.serializer()
        override val resultSerializer: KSerializer<Int> = Int.serializer()

        override val name: String = "calculator"
        override val description: String =
            "A simple calculator that can add, subtract, multiply, and divide two numbers."

        override suspend fun execute(args: Args): Int = when (args.operation) {
            CalculatorOperation.ADD -> args.a + args.b
            CalculatorOperation.SUBTRACT -> args.a - args.b
            CalculatorOperation.MULTIPLY -> args.a * args.b
            CalculatorOperation.DIVIDE -> args.a / args.b
        }
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
        data class Args(
            @property:LLMDescription("The path to create the file")
            val path: String,
            @property:LLMDescription("The content to create the file")
            val content: String
        )

        @Serializable
        data class Result(val successful: Boolean, val message: String? = null)

        override val argsSerializer = Args.serializer()
        override val resultSerializer: KSerializer<Result> = Result.serializer()

        override val name: String = "create_file"
        override val description: String =
            "Create a file and writes the given text content to it"

        override suspend fun execute(args: Args): Result {
            return when (val res = fs.create(args.path, args.content)) {
                is OperationResult.Success -> Result(successful = true)
                is OperationResult.Failure -> Result(successful = false, message = res.error)
            }
        }
    }

    class DeleteFile(private val fs: MockFileSystem) : Tool<DeleteFile.Args, DeleteFile.Result>() {
        @Serializable
        data class Args(
            @property:LLMDescription("The path of the file to be deleted")
            val path: String
        )

        @Serializable
        data class Result(val successful: Boolean, val message: String? = null)

        override val argsSerializer = Args.serializer()
        override val resultSerializer: KSerializer<Result> = Result.serializer()

        override val name: String = "delete_file"
        override val description: String = "Deletes a file"

        override suspend fun execute(args: Args): Result {
            return when (val res = fs.delete(args.path)) {
                is OperationResult.Success -> Result(successful = true)
                is OperationResult.Failure -> Result(successful = false, message = res.error)
            }
        }
    }

    class ReadFile(private val fs: MockFileSystem) : Tool<ReadFile.Args, ReadFile.Result>() {
        @Serializable
        data class Args(
            @property:LLMDescription("The path of the file to read")
            val path: String
        )

        @Serializable
        data class Result(
            val successful: Boolean,
            val message: String? = null,
            val content: String? = null
        )

        override val argsSerializer = Args.serializer()
        override val resultSerializer: KSerializer<Result> = Result.serializer()

        override val name: String = "read_file"
        override val description: String = "Reads a file"

        override suspend fun execute(args: Args): Result {
            return when (val res = fs.read(args.path)) {
                is OperationResult.Success<String> -> Result(successful = true, content = res.result)
                is OperationResult.Failure -> Result(successful = false, message = res.error)
            }
        }
    }

    class ListFiles(private val fs: MockFileSystem) : Tool<ListFiles.Args, ListFiles.Result>() {
        @Serializable
        data class Args(
            @property:LLMDescription("The path of the directory")
            val path: String
        )

        @Serializable
        data class Result(
            val successful: Boolean,
            val message: String? = null,
            val children: List<String>? = null
        )

        override val argsSerializer = Args.serializer()
        override val resultSerializer: KSerializer<Result> = Result.serializer()

        override val name: String = "list_files"
        override val description: String = "List all files inside the given path of the directory"

        override suspend fun execute(args: Args): Result {
            return when (val res = fs.ls(args.path)) {
                is OperationResult.Success<List<String>> -> Result(successful = true, children = res.result)
                is OperationResult.Failure -> Result(successful = false, message = res.error)
            }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun createTestMultiLLMAgent(
        fs: MockFileSystem,
        eventHandlerConfig: EventHandlerConfig.() -> Unit,
        maxAgentIterations: Int,
        prompt: Prompt = prompt("test") {},
        initialExecutor: MultiLLMPromptExecutor? = null,
    ): AIAgent<String, String> {
        var executor: MultiLLMPromptExecutor
        if (initialExecutor == null) {
            val openAIClient = OpenAILLMClient(openAIApiKey)
            val anthropicClient = AnthropicLLMClient(anthropicApiKey)
            executor = MultiLLMPromptExecutor(
                LLMProvider.OpenAI to openAIClient,
                LLMProvider.Anthropic to anthropicClient
            )
        } else {
            executor = initialExecutor
        }
        val strategy = strategy<String, String>("test") {
            val anthropicSubgraph by subgraph<String, Unit>("anthropic") {
                val definePromptAnthropic by node<Unit, Unit> {
                    llm.writeSession {
                        model = AnthropicModels.Haiku_4_5
                        rewritePrompt {
                            prompt("test", params = LLMParams(toolChoice = LLMParams.ToolChoice.Auto)) {
                                system {
                                    +"You are a helpful assistant. You need to solve my task. "
                                    +"JUST CALL TOOLS. NO QUESTIONS ASKED."
                                }
                            }
                        }
                    }
                }

                val callLLM by nodeLLMRequest(allowToolCalls = true)
                val callTool by nodeExecuteTool()
                val sendToolResult by nodeLLMSendToolResult()

                edge(nodeStart forwardTo definePromptAnthropic transformed {})
                edge(definePromptAnthropic forwardTo callLLM transformed { agentInput<String>() })
                edge(callLLM forwardTo callTool onToolCall { true })
                edge(callLLM forwardTo nodeFinish onAssistantMessage { true } transformed {})
                edge(callTool forwardTo sendToolResult)
                edge(sendToolResult forwardTo callTool onToolCall { true })
                edge(sendToolResult forwardTo nodeFinish onAssistantMessage { true } transformed {})
            }

            val openaiSubgraph by subgraph("openai") {
                val definePromptOpenAI by node<Unit, Unit> {
                    llm.writeSession {
                        model = OpenAIModels.Chat.GPT5
                        rewritePrompt {
                            prompt("test", params = LLMParams(toolChoice = LLMParams.ToolChoice.Auto)) {
                                system(
                                    """
                                    You are a helpful assistant. You need to verify that the task is solved correctly.
                                    Please analyze the whole produced solution, and check that it is valid.
                                    Write concise verification result.
                                    JUST CALL TOOLS. NO QUESTIONS ASKED.
                                    """.trimIndent()
                                )
                            }
                        }
                    }
                }

                val callLLM by nodeLLMRequest(allowToolCalls = true)
                val callTool by nodeExecuteTool()
                val sendToolResult by nodeLLMSendToolResult()

                edge(nodeStart forwardTo definePromptOpenAI)
                edge(definePromptOpenAI forwardTo callLLM transformed { agentInput<String>() })
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

        return AIAgent(
            promptExecutor = executor,
            strategy = strategy,
            agentConfig = AIAgentConfig(prompt, OpenAIModels.Chat.GPT5, maxAgentIterations),
            toolRegistry = tools,
        ) {
            install(EventHandler, eventHandlerConfig)
        }
    }

    private fun createTestAgentWithToolsInSubgraph(
        fs: MockFileSystem,
        eventHandlerConfig: EventHandlerConfig.() -> Unit = {},
        model: LLModel,
        emptyAgentRegistry: Boolean = true,
    ): AIAgent<String, String> {
        val openAIClient = OpenAILLMClient(openAIApiKey)
        val anthropicClient = AnthropicLLMClient(anthropicApiKey)

        val executor = MultiLLMPromptExecutor(
            LLMProvider.OpenAI to openAIClient,
            LLMProvider.Anthropic to anthropicClient
        )

        val subgraphTools = listOf(
            CreateFile(fs),
            ReadFile(fs),
            ListFiles(fs),
            DeleteFile(fs),
        )

        val strategy = strategy<String, String>("test-subgraph-only-tools") {
            val fileOperationsSubgraph by subgraphWithTask<String, String>(
                tools = subgraphTools,
                llmModel = model,
                llmParams = LLMParams(toolChoice = LLMParams.ToolChoice.Required)
            ) { input ->
                "You are a helpful assistant that can perform file operations. Use the available tools to complete the following task: $input. Make sure to use tools when needed and provide clear feedback about what you've done."
            }

            nodeStart then fileOperationsSubgraph then nodeFinish
        }

        val toolRegistry = if (emptyAgentRegistry) {
            ToolRegistry {}
        } else {
            ToolRegistry {
                subgraphTools.forEach { tool(it) }
            }
        }

        return AIAgent(
            promptExecutor = executor,
            strategy = strategy,
            agentConfig = AIAgentConfig(
                prompt = prompt("test") {
                    system("You are a helpful assistant.")
                },
                model,
                maxAgentIterations = 20,
            ),
            toolRegistry = toolRegistry,
        ) {
            install(EventHandler, eventHandlerConfig)
        }
    }

    @Test
    @Retry(5)
    fun integration_testOpenAIAnthropicAgent() = runTest(timeout = 10.minutes) {
        Models.assumeAvailable(LLMProvider.OpenAI)
        Models.assumeAvailable(LLMProvider.Anthropic)

        val fs = MockFileSystem()
        val eventsChannel = Channel<Event>(Channel.UNLIMITED)
        val eventHandlerConfig: EventHandlerConfig.() -> Unit = {
            onAgentCompleted { _ ->
                eventsChannel.send(Event.Termination)
            }
        }

        val openAIClient = OpenAILLMClient(openAIApiKey).reportingTo(eventsChannel)
        val anthropicClient = AnthropicLLMClient(anthropicApiKey).reportingTo(eventsChannel)
        val reportingExecutor = MultiLLMPromptExecutor(
            LLMProvider.OpenAI to openAIClient,
            LLMProvider.Anthropic to anthropicClient
        )

        val agent = createTestMultiLLMAgent(
            fs,
            eventHandlerConfig,
            maxAgentIterations = 50,
            initialExecutor = reportingExecutor,
        )

        val result = agent.run(
            "Generate me a project in Ktor that has a GET endpoint that returns the capital of France. Write a test"
        )
        eventsChannel.close()

        assertNotNull(result)

        assertTrue(
            fs.fileCount() > 0,
            "Agent must have created at least one file"
        )

        val messages = mutableListOf<Event.Message>()
        for (msg in eventsChannel) {
            if (msg is Event.Message) {
                messages.add(msg)
            } else {
                break
            }
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

    @ParameterizedTest
    @MethodSource("getModels")
    fun `integration_test agent with not registered subgraph tool result fails`(model: LLModel) =
        runTest(timeout = 10.minutes) {
            Models.assumeAvailable(LLMProvider.OpenAI)
            Models.assumeAvailable(LLMProvider.Anthropic)

            val fs = MockFileSystem()
            val agent = createTestAgentWithToolsInSubgraph(fs = fs, model = model)

            try {
                val result = agent.run(
                    "Create a simple file called 'test.txt' with content 'Hello from subgraph tools!' and then read it back to verify it was created correctly."
                )
                fail("Expected AIAgentException but got result: $result")
            } catch (e: IllegalArgumentException) {
                assertContains(e.message ?: "", "Tool \"create_file\" is not defined")
            }
        }

    @ParameterizedTest
    @MethodSource("getModels")
    fun `integration_test agent with registered subgraph tool result runs`(model: LLModel) =
        runTest(timeout = 10.minutes) {
            Models.assumeAvailable(LLMProvider.OpenAI)
            Models.assumeAvailable(LLMProvider.Anthropic)

            val fs = MockFileSystem()
            val calledTools = mutableListOf<String>()
            val eventHandlerConfig: EventHandlerConfig.() -> Unit = {
                onToolCallStarting { eventContext ->
                    calledTools.add(eventContext.tool.name)
                }
            }

            val agent = createTestAgentWithToolsInSubgraph(fs, eventHandlerConfig, model, false)

            val result = agent.run(
                "Create a simple file called 'test.txt' with content 'Hello from subgraph tools!' and then read it back to verify it was created correctly."
            )

            assertNotNull(result)
            assertTrue(result.isNotEmpty(), "Agent result should not be empty")

            assertTrue(
                fs.fileCount() > 0,
                "Agent must have created at least one file using subgraph tools"
            )

            when (val readResult = fs.read("test.txt")) {
                is OperationResult.Success -> {
                    assertTrue(
                        readResult.result.contains("Hello from subgraph tools!"),
                        "File should contain the expected content"
                    )
                }

                is OperationResult.Failure -> {
                    fail("Failed to read file: ${readResult.error}")
                }
            }

            assertTrue(
                calledTools.any { it == "create_file" },
                "At least one LLM call must have tools available"
            )
        }

    @Test
    fun integration_testTerminationOnIterationsLimitExhaustion() = runTest(timeout = 10.minutes) {
        Models.assumeAvailable(LLMProvider.OpenAI)
        Models.assumeAvailable(LLMProvider.Anthropic)

        val fs = MockFileSystem()
        var errorMessage: String? = null
        val steps = 10
        val agent = createTestMultiLLMAgent(
            fs,
            { },
            maxAgentIterations = steps,
        )

        try {
            val result = agent.run(
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

    @Test
    fun integration_testAnthropicAgentEnumSerialization() {
        runTest(timeout = 10.minutes) {
            val llmModel = AnthropicModels.Sonnet_4_5
            Models.assumeAvailable(llmModel.provider)
            val agent = AIAgent(
                promptExecutor = simpleAnthropicExecutor(anthropicApiKey),
                llmModel = llmModel,
                systemPrompt = "You are a calculator with access to the calculator tools. Please call tools!!!",
                toolRegistry = ToolRegistry {
                    tool(CalculatorTool)
                },
                installFeatures = {
                    install(EventHandler) {
                        onAgentExecutionFailed { eventContext ->
                            println(
                                "error: ${eventContext.throwable.javaClass.simpleName}(${eventContext.throwable.message})\n${eventContext.throwable.stackTraceToString()}"
                            )
                        }
                        onToolCallStarting { eventContext ->
                            println(
                                "Calling tool ${eventContext.tool.name} with arguments ${
                                    eventContext.toolArgs.toString().lines().first().take(100)
                                }"
                            )
                        }
                    }
                }
            )

            val result = agent.run("calculate 10 plus 15, and then subtract 8")
            println("result = $result")
            assertNotNull(result)
            assertContains(result, "17")
        }
    }

    @ParameterizedTest
    @MethodSource("modelsWithVisionCapability")
    fun integration_testAgentWithImageCapability(model: LLModel) = runTest(timeout = 2.minutes) {
        Models.assumeAvailable(model.provider)
        val fs = MockFileSystem()

        val imageFile = File(testResourcesDir, "test.png")
        assertTrue(imageFile.exists(), "Image test file should exist")

        val imageBytes = imageFile.readBytes()
        val base64Image = Base64.getEncoder().encodeToString(imageBytes)

        withRetry {
            val agent = createTestMultiLLMAgent(
                fs,
                { },
                maxAgentIterations = 20,
            )

            val result = agent.run(
                """
            I'm sending you an image encoded in base64 format.

            data:image/png,$base64Image

            Please analyze this image and identify the image format if possible.
            """
            )

            assertNotNull(result, "Result should not be null")
            assertTrue(result.isNotBlank(), "Result should not be empty or blank")
            assertTrue(result.length > 20, "Result should contain more than 20 characters")

            val resultLowerCase = result.lowercase()
            assertFalse(
                resultLowerCase.contains("error processing"),
                "Result should not contain error messages"
            )
            assertFalse(
                resultLowerCase.contains("unable to process"),
                "Result should not indicate inability to process"
            )
            assertFalse(
                resultLowerCase.contains("cannot process"),
                "Result should not indicate inability to process"
            )
        }
    }

    @ParameterizedTest
    @MethodSource("modelsWithVisionCapability")
    fun integration_testAgentWithImageCapabilityUrl(model: LLModel) = runTest(timeout = 10.minutes) {
        Models.assumeAvailable(model.provider)

        val fs = MockFileSystem()
        val eventHandlerConfig: EventHandlerConfig.() -> Unit = {
            onToolCallStarting { eventContext ->
                println(
                    "Calling tool ${eventContext.tool.name} with arguments ${
                        eventContext.toolArgs.toString().lines().first().take(100)
                    }"
                )
            }
        }

        val imageFile = File(testResourcesDir, "test.png")
        assertTrue(imageFile.exists(), "Image test file should exist")

        val prompt = prompt("example-prompt") {
            system("You are a professional helpful assistant.")

            user {
                markdown {
                    +"I'm sending you an image."
                    br()
                    +"Please analyze this image and identify the image format if possible."
                }
                image("https://upload.wikimedia.org/wikipedia/commons/thumb/d/dd/Gfp-wisconsin-madison-the-nature-boardwalk.jpg/2560px-Gfp-wisconsin-madison-the-nature-boardwalk.jpg")
            }
        }

        withRetry(3) {
            val agent = createTestMultiLLMAgent(
                fs,
                eventHandlerConfig,
                maxAgentIterations = 50,
                prompt = prompt,
            )

            val result = agent.run("Hi! Please analyse my image.")
            assertNotNull(result, "Result should not be null")
            assertTrue(result.isNotBlank(), "Result should not be empty or blank")
            assertTrue(result.length > 20, "Result should contain more than 20 characters")

            val resultLowerCase = result.lowercase()
            assertFalse(resultLowerCase.contains("error processing"), "Result should not contain error messages")
            assertFalse(
                resultLowerCase.contains("unable to process"),
                "Result should not indicate inability to process"
            )
            assertFalse(
                resultLowerCase.contains("cannot process"),
                "Result should not indicate inability to process"
            )
        }
    }
}
