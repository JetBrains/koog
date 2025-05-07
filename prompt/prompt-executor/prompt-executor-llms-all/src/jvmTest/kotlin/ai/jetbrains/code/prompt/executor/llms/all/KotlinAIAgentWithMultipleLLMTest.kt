package ai.jetbrains.code.prompt.executor.llms.all

import ai.grazie.code.agents.core.event.EventHandler
import ai.grazie.code.agents.core.tools.*
import ai.grazie.code.agents.local.KotlinAIAgent
import ai.grazie.code.agents.local.agent.ContextTransitionPolicy
import ai.grazie.code.agents.local.agent.LocalAgentConfig
import ai.grazie.code.agents.local.dsl.builders.forwardTo
import ai.grazie.code.agents.local.dsl.builders.strategy
import ai.grazie.code.agents.local.dsl.extensions.*
import ai.grazie.code.agents.local.features.tracing.feature.TraceFeature
import ai.jetbrains.code.prompt.dsl.Prompt
import ai.jetbrains.code.prompt.dsl.prompt
import ai.jetbrains.code.prompt.executor.clients.DirectLLMClient
import ai.jetbrains.code.prompt.executor.clients.anthropic.AnthropicModels
import ai.jetbrains.code.prompt.executor.clients.anthropic.AnthropicDirectLLMClient
import ai.jetbrains.code.prompt.executor.clients.openai.OpenAIModels
import ai.jetbrains.code.prompt.executor.clients.openai.OpenAIDirectLLMClient
import ai.jetbrains.code.prompt.executor.llms.MultiLLMPromptExecutor
import ai.jetbrains.code.prompt.executor.llms.all.ReportingLLMLLMClient.Event
import ai.jetbrains.code.prompt.llm.LLMProvider
import ai.jetbrains.code.prompt.message.Message
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.newFixedThreadPoolContext
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Disabled
import kotlin.coroutines.coroutineContext
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

internal class ReportingLLMLLMClient(
    private val eventsChannel: Channel<Event>,
    private val underlyingClient
    : DirectLLMClient
) : DirectLLMClient {
    sealed interface Event {
        data class Message(
            val llmClient: String,
            val method: String,
            val prompt: Prompt,
            val tools: List<String>
        ) : Event

        data object Termination : Event
    }

    override suspend fun execute(
        prompt: Prompt,
        tools: List<ToolDescriptor>
    ): List<Message.Response> {
        CoroutineScope(coroutineContext).launch {
            eventsChannel.send(
                Event.Message(
                    llmClient = underlyingClient::class.simpleName ?: "null",
                    method = "execute",
                    prompt = prompt,
                    tools = tools.map { it.name }
                )
            )
        }
        return underlyingClient.execute(prompt, tools)
    }

    override suspend fun executeStreaming(prompt: Prompt): Flow<String> {
        CoroutineScope(coroutineContext).launch {
            eventsChannel.send(
                Event.Message(
                    llmClient = underlyingClient::class.simpleName ?: "null",
                    method = "execute",
                    prompt = prompt,
                    tools = emptyList()
                )
            )
        }
        return underlyingClient.executeStreaming(prompt)
    }
}

internal fun DirectLLMClient.reportingTo(
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

    @Disabled("This test requires valid API keys")
    @OptIn(DelicateCoroutinesApi::class)
    @Test
    fun testKotlinAIAgentWithOpenAIAndAnthropic() = runTest(timeout = 600.seconds) {
        // TODO: pass the `OPEN_AI_API_TEST_KEY` and `ANTHROPIC_API_TEST_KEY`
        return@runTest

        // Create the clients
        val eventsChannel = Channel<Event>()

        val openAIClient = OpenAIDirectLLMClient(openAIApiKey).reportingTo(eventsChannel)
        val anthropicClient = AnthropicDirectLLMClient(anthropicApiKey).reportingTo(eventsChannel)

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
                        rewritePrompt {
                            prompt(AnthropicModels.Sonnet_3_7, "test") { //JetBrainsAIModels.Anthropic.Sonnet_3_7
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
                        rewritePrompt {
                            prompt(OpenAIModels.GPT4o, "test") { //  JetBrainsAIModels.OpenAI.GPT4o
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

        val fs = MockFileSystem()

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
        val agent = KotlinAIAgent(
            toolRegistry = tools,
            strategy = strategy,
            eventHandler = EventHandler {
                onToolCall { stage, tool, arguments ->
                    println(
                        "[$stage] Calling tool ${tool.name} with arguments ${
                            arguments.toString().lines().first().take(100)
                        }"
                    )
                }

                handleResult {
                    eventsChannel.send(Event.Termination)
                }
            },
            agentConfig = LocalAgentConfig(prompt(OpenAIModels.GPT4o, "test") {}, 15),
            promptExecutor = executor,
            cs = CoroutineScope(newFixedThreadPoolContext(2, "TestAgent"))
        ) {
            install(TraceFeature) {
                addMessageProcessor(TestLogPrinter())
            }
        }

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
            messages.any { it.llmClient == "AnthropicSuspendableDirectClient" },
            "At least one message must be delegated to Anthropic client"
        )

        assertTrue(
            messages.any { it.llmClient == "OpenAISuspendableDirectClient" },
            "At least one message must be delegated to OpenAI client"
        )

        assertTrue(
            messages
                .filter { it.llmClient == "AnthropicSuspendableDirectClient" }
                .all { it.prompt.model.provider == LLMProvider.Anthropic },
            "All prompts with Anthropic model must be delegated to Anthropic client"
        )

        assertTrue(
            messages
                .filter { it.llmClient == "OpenAISuspendableDirectClient" }
                .all { it.prompt.model.provider == LLMProvider.OpenAI },
            "All prompts with OpenAI model must be delegated to OpenAI client"
        )
    }
}