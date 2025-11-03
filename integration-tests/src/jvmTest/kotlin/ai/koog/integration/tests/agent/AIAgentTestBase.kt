package ai.koog.integration.tests.agent

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.context.agentInput
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeExecuteTool
import ai.koog.agents.core.dsl.extension.nodeLLMCompressHistory
import ai.koog.agents.core.dsl.extension.nodeLLMRequest
import ai.koog.agents.core.dsl.extension.nodeLLMSendToolResult
import ai.koog.agents.core.dsl.extension.onAssistantMessage
import ai.koog.agents.core.dsl.extension.onToolCall
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.ext.agent.subgraphWithTask
import ai.koog.agents.features.eventHandler.feature.EventHandler
import ai.koog.agents.features.eventHandler.feature.EventHandlerConfig
import ai.koog.integration.tests.utils.Models
import ai.koog.integration.tests.utils.TestUtils.CreateFile
import ai.koog.integration.tests.utils.TestUtils.DeleteFile
import ai.koog.integration.tests.utils.TestUtils.ListFiles
import ai.koog.integration.tests.utils.TestUtils.MockFileSystem
import ai.koog.integration.tests.utils.TestUtils.ReadFile
import ai.koog.integration.tests.utils.TestUtils.readTestAnthropicKeyFromEnv
import ai.koog.integration.tests.utils.TestUtils.readTestOpenAIKeyFromEnv
import ai.koog.integration.tests.utils.getLLMClientForProvider
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.params.LLMParams
import ai.koog.prompt.streaming.StreamFrame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.params.provider.Arguments
import java.nio.file.Path
import java.nio.file.Paths
import java.util.stream.Stream

open class AIAgentTestBase {
    companion object {
        @JvmStatic
        lateinit var testResourcesDir: Path

        @JvmStatic
        fun getLatestModels(): Stream<LLModel> = Stream.of(
            AnthropicModels.Sonnet_4_5,
            OpenAIModels.Chat.GPT5,
        )

        @JvmStatic
        @BeforeAll
        fun setup() {
            testResourcesDir = Paths.get(AIAgentTestBase::class.java.getResource("/media")!!.toURI())
        }

        @JvmStatic
        fun allModels(): Stream<LLModel> = Models.allCompletionModels()

        @JvmStatic
        fun modelsWithVisionCapability(): Stream<Arguments> = Models.modelsWithVisionCapability()
    }

    protected val testScope = TestScope()

    @AfterEach
    fun cleanup() {
        testScope.cancel()
    }

    val systemPrompt = "You are a helpful assistant."

    fun getExecutor(model: LLModel): SingleLLMPromptExecutor =
        SingleLLMPromptExecutor(getLLMClientForProvider(model.provider))

    protected class State(
        var reasoningCallsCount: Int = 0,
        val actualToolCalls: MutableList<String> = mutableListOf(),
        val errors: MutableList<Throwable> = mutableListOf(),
        val results: MutableList<Any?> = mutableListOf(),
        val toolExecutionCounter: MutableList<String> = mutableListOf(),
        val parallelToolCalls: MutableList<ToolCallInfo> = mutableListOf(),
        val singleToolCalls: MutableList<ToolCallInfo> = mutableListOf(),
    )

    protected suspend fun runWithTracking(
        action: suspend (
            eventHandlerConfig: EventHandlerConfig.() -> Unit,
            state: State,
        ) -> Unit,
    ) {
        val state = State()

        val eventHandlerConfig: EventHandlerConfig.() -> Unit = {
            onAgentCompleted { eventContext ->
                state.results.add(eventContext.result)
            }

            onAgentExecutionFailed { eventContext ->
                state.errors.add(eventContext.throwable)
            }

            onLLMCallStarting { eventContext ->
                if (eventContext.tools.isEmpty() &&
                    eventContext.prompt.params.toolChoice == null
                ) {
                    state.reasoningCallsCount++
                }
            }

            onNodeExecutionStarting { eventContext ->
                val input = eventContext.input

                if (input is List<*>) {
                    input.filterIsInstance<Message.Tool.Call>().forEach { call ->
                        state.parallelToolCalls.add(
                            ToolCallInfo(
                                id = call.id,
                                tool = call.tool,
                                content = call.content,
                                metaInfo = call.metaInfo,
                            )
                        )
                    }
                } else if (input is Message.Tool.Call) {
                    state.singleToolCalls.add(
                        ToolCallInfo(
                            id = input.id,
                            tool = input.tool,
                            content = input.content,
                            metaInfo = input.metaInfo,
                        )
                    )
                }
            }

            onToolCallStarting { eventContext ->
                state.actualToolCalls.add(eventContext.tool.name)
                state.toolExecutionCounter.add(eventContext.tool.name)
            }
        }

        action(eventHandlerConfig, state)
    }

    data class ToolCallInfo(
        val id: String?,
        val tool: String,
        val content: String,
        val metaInfo: ResponseMetaInfo,
    )

    protected class ReportingLLMClient(
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

    protected fun LLMClient.reportingTo(
        eventsChannel: Channel<ReportingLLMClient.Event>
    ) = ReportingLLMClient(eventsChannel, this)

    @OptIn(DelicateCoroutinesApi::class)
    protected fun createTestMultiLLMAgent(
        fs: MockFileSystem,
        eventHandlerConfig: EventHandlerConfig.() -> Unit,
        maxAgentIterations: Int,
        prompt: Prompt = prompt("test") {},
        initialExecutor: MultiLLMPromptExecutor? = null,
    ): AIAgent<String, String> {
        val executor = if (initialExecutor == null) {
            val openAIClient = OpenAILLMClient(readTestOpenAIKeyFromEnv())
            val anthropicClient = AnthropicLLMClient(readTestAnthropicKeyFromEnv())
            MultiLLMPromptExecutor(
                LLMProvider.OpenAI to openAIClient,
                LLMProvider.Anthropic to anthropicClient
            )
        } else {
            initialExecutor
        }
        val strategy = strategy<String, String>("test") {
            val anthropicSubgraph by subgraph<String, Unit>("anthropic") {
                val definePromptAnthropic by node<Unit, Unit> {
                    llm.writeSession {
                        model = AnthropicModels.Haiku_4_5
                        rewritePrompt {
                            prompt("test") {
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
                            prompt("test") {
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

    protected fun createTestAgentWithToolsInSubgraph(
        fs: MockFileSystem,
        eventHandlerConfig: EventHandlerConfig.() -> Unit = {},
        model: LLModel,
        emptyAgentRegistry: Boolean = true,
    ): AIAgent<String, String> {
        val openAIClient = OpenAILLMClient(readTestOpenAIKeyFromEnv())
        val anthropicClient = AnthropicLLMClient(readTestAnthropicKeyFromEnv())

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
                maxAgentIterations = 50,
            ),
            toolRegistry = toolRegistry,
        ) {
            install(EventHandler, eventHandlerConfig)
        }
    }
}
