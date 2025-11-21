package ai.koog.integration.tests.agent

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.ToolCalls
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.singleRunStrategy
import ai.koog.agents.core.dsl.builder.ParallelNodeExecutionResult
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.HistoryCompressionStrategy
import ai.koog.agents.core.dsl.extension.nodeExecuteTool
import ai.koog.agents.core.dsl.extension.nodeLLMCompressHistory
import ai.koog.agents.core.dsl.extension.nodeLLMRequest
import ai.koog.agents.core.dsl.extension.nodeLLMSendToolResult
import ai.koog.agents.core.dsl.extension.onAssistantMessage
import ai.koog.agents.core.dsl.extension.onToolCall
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.ext.agent.reActStrategy
import ai.koog.agents.features.eventHandler.feature.EventHandler
import ai.koog.agents.features.eventHandler.feature.EventHandlerConfig
import ai.koog.agents.snapshot.feature.Persistence
import ai.koog.agents.snapshot.feature.withPersistence
import ai.koog.agents.snapshot.providers.InMemoryPersistenceStorageProvider
import ai.koog.agents.snapshot.providers.file.JVMFilePersistenceStorageProvider
import ai.koog.integration.tests.utils.Models
import ai.koog.integration.tests.utils.RetryUtils.withRetry
import ai.koog.integration.tests.utils.tools.CalculateSumTool
import ai.koog.integration.tests.utils.tools.CalculatorToolNoArgs
import ai.koog.integration.tests.utils.tools.DelayTool
import ai.koog.integration.tests.utils.tools.GetTransactionsTool
import ai.koog.integration.tests.utils.tools.SimpleCalculatorTool
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.params.LLMParams
import ai.koog.prompt.params.LLMParams.ToolChoice
import io.kotest.assertions.withClue
import io.kotest.inspectors.shouldForAny
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.contain
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotBeBlank
import io.kotest.matchers.string.shouldNotBeEmpty
import io.kotest.matchers.string.shouldNotContain
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.nio.file.Path
import java.util.Base64
import java.util.stream.Stream
import kotlin.io.path.readBytes
import kotlin.reflect.typeOf
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class AIAgentIntegrationTest : AIAgentTestBase() {

    companion object {
        private lateinit var testResourcesDir: Path

        @JvmStatic
        @BeforeAll
        fun setup() {
            AIAgentTestBase.setup()
            testResourcesDir = AIAgentTestBase.testResourcesDir
        }

        @JvmStatic
        fun allModels(): Stream<LLModel> = AIAgentTestBase.allModels()

        @JvmStatic
        fun modelsWithVisionCapability(): Stream<Arguments> = AIAgentTestBase.modelsWithVisionCapability()

        @JvmStatic
        fun reasoningIntervals(): Stream<Int> {
            return listOf(1, 2, 3).stream()
        }

        @JvmStatic
        fun historyCompressionStrategies(): Stream<Arguments> {
            return Stream.of(
                Arguments.of(HistoryCompressionStrategy.WholeHistory, "WholeHistory"),
                Arguments.of(
                    HistoryCompressionStrategy.WholeHistoryMultipleSystemMessages,
                    "WholeHistoryMultipleSystemMessages"
                ),
                Arguments.of(HistoryCompressionStrategy.FromLastNMessages(1), "FromLastNMessages(1)"),
                Arguments.of(
                    HistoryCompressionStrategy.FromTimestamp(Clock.System.now().minus(1.seconds)),
                    "FromTimestamp"
                ),
                Arguments.of(HistoryCompressionStrategy.Chunked(2), "Chunked(2)")
            )
        }
    }

    val twoToolsRegistry = ToolRegistry {
        tool(SimpleCalculatorTool)
        tool(DelayTool)
    }

    val bankingToolsRegistry = ToolRegistry {
        tool(GetTransactionsTool)
        tool(CalculateSumTool)
    }

    val twoToolsPrompt = """
        I need you to perform two operations:
        1. Calculate 7 times 2
        2. Wait for 500 milliseconds

        Respond briefly after completing both tasks. DO NOT EXCEED THE LIMIT OF 20 WORDS.
    """.trimIndent()

    private fun getSingleRunAgentWithRunMode(
        model: LLModel,
        runMode: ToolCalls,
        toolRegistry: ToolRegistry = twoToolsRegistry,
        eventHandlerConfig: EventHandlerConfig.() -> Unit,
    ) = AIAgent(
        promptExecutor = getExecutor(model),
        strategy = singleRunStrategy(runMode),
        agentConfig = AIAgentConfig(
            prompt = prompt(
                id = "single-run-agent",
                params = LLMParams(
                    temperature = 1.0,
                    toolChoice = ToolChoice.Auto,
                )
            ) {
                system {
                    +"You are a helpful assistant. "
                    +"JUST CALL THE TOOLS, NO QUESTIONS ASKED."
                }
            },
            model = model,
            maxAgentIterations = 10,
        ),
        toolRegistry = toolRegistry,
        installFeatures = {
            install(EventHandler.Feature, eventHandlerConfig)
        },
    )

    private fun buildHistoryCompressionWithToolsStrategy(
        strategy: HistoryCompressionStrategy,
        compressBeforeToolResult: Boolean,
    ) = strategy<String, Pair<String, List<Message>>>("history-compression-with-tools-test") {
        val callLLM by nodeLLMRequest(name = "callLLM", allowToolCalls = true)
        val executeTool by nodeExecuteTool("execute_tool")
        val compressResponse by nodeLLMCompressHistory<Message.Response>(
            name = "compress_history",
            strategy = strategy
        )
        val compressToolResult by nodeLLMCompressHistory<ai.koog.agents.core.environment.ReceivedToolResult>(
            name = "compress_history",
            strategy = strategy
        )
        val sendToolResult by nodeLLMSendToolResult("send_tool_result")

        edge(nodeStart forwardTo callLLM)
        if (compressBeforeToolResult) {
            edge(callLLM forwardTo executeTool onToolCall { true })
            executeTool then compressToolResult then sendToolResult
            edge(sendToolResult forwardTo executeTool onToolCall (SimpleCalculatorTool))
            edge(sendToolResult forwardTo nodeFinish onAssistantMessage { true } transformed { it to llm.prompt.messages })
        } else {
            callLLM then compressResponse
            edge(compressResponse forwardTo executeTool onToolCall (SimpleCalculatorTool))
            edge(compressResponse forwardTo nodeFinish onAssistantMessage { true } transformed { it to llm.prompt.messages })
            executeTool then sendToolResult then compressResponse
        }
    }

    private fun assertHistoryCompressionWithTools(
        errors: Collection<Any>,
        actualToolCalls: Collection<String>,
        result: String,
        promptMessages: List<Message>?,
        strategyName: String,
    ) {
        withClue("No errors should occur during agent execution with $strategyName, got: [${errors.joinToString("\n")}]") {
            errors.shouldBeEmpty()
        }
        withClue("The ${SimpleCalculatorTool.name} tool was not called with $strategyName") {
            actualToolCalls shouldContain SimpleCalculatorTool.name
        }
        result.shouldNotBeBlank()
        promptMessages shouldNotBeNull {
            withClue("System messages should be preserved after compression with $strategyName") {
                filterIsInstance<Message.System>().shouldNotBeEmpty()
            }
            withClue("System message content should not be empty after compression with $strategyName") {
                first().content.shouldNotBeBlank()
            }
        }
    }

    private fun runMultipleToolsTest(model: LLModel, runMode: ToolCalls) = runTest(timeout = 300.seconds) {
        Models.assumeAvailable(model.provider)
        assumeTrue(model.capabilities.contains(LLMCapability.Tools), "Model $model does not support tools")

        /* Some models are not calling tools in parallel:
         * see https://youtrack.jetbrains.com/issue/KG-115
         */
        assumeTrue(model.id !== OpenAIModels.Chat.O1.id, "Model $model flaks when calling parallel tools")
        assumeTrue(model.id !== GoogleModels.Gemini2_5Flash.id, "Model $model flaks when calling parallel tools")

        withRetry(5) {
            runWithTracking { eventHandlerConfig, state ->
                val multiToolAgent =
                    getSingleRunAgentWithRunMode(model, runMode, eventHandlerConfig = eventHandlerConfig)
                multiToolAgent.run(twoToolsPrompt)

                with(state) {
                    withClue("There should be at least 2 tool calls in a Multiple tool calls scenario") {
                        parallelToolCalls.size shouldBeGreaterThanOrEqual 2
                    }

                    withClue("There should be no single tool calls in a Multiple tool calls scenario") {
                        singleToolCalls.shouldBeEmpty()
                    }

                    val firstCall = parallelToolCalls.first()
                    val secondCall = state.parallelToolCalls.last()

                    if (runMode == ToolCalls.PARALLEL) {
                        withClue("At least one of the metadata should be equal for parallel tool calls") {
                            (
                                firstCall.metaInfo.timestamp == secondCall.metaInfo.timestamp ||
                                    firstCall.metaInfo.totalTokensCount == secondCall.metaInfo.totalTokensCount ||
                                    firstCall.metaInfo.inputTokensCount == secondCall.metaInfo.inputTokensCount ||
                                    firstCall.metaInfo.outputTokensCount == secondCall.metaInfo.outputTokensCount
                                ).shouldBeTrue()
                        }
                    }

                    withClue("First tool call should be ${SimpleCalculatorTool.name}") {
                        firstCall.tool shouldBe SimpleCalculatorTool.name
                    }
                    withClue("Second tool call should be ${DelayTool.name}") {
                        secondCall.tool shouldBe DelayTool.name
                    }
                }
            }
        }
    }

    @ParameterizedTest
    @MethodSource("allModels")
    fun integration_AIAgentShouldNotCallToolsByDefault(model: LLModel) = runTest {
        Models.assumeAvailable(model.provider)
        withRetry {
            runWithTracking { eventHandlerConfig, state ->
                val executor = getExecutor(model)

                val agent = AIAgent(
                    promptExecutor = executor,
                    systemPrompt = systemPrompt,
                    llmModel = model,
                    temperature = 1.0,
                    maxIterations = 10,
                    installFeatures = { install(EventHandler.Feature, eventHandlerConfig) },
                )
                agent.run("Repeat what I say: hello, I'm good.")
                // by default, AIAgent has no tools underneath
                withClue("No tools should be called for model $model") {
                    state.actualToolCalls.shouldBeEmpty()
                }
            }
        }
    }

    @ParameterizedTest
    @MethodSource("allModels")
    fun integration_AIAgentNoSystemMessage(model: LLModel) = runTest {
        Models.assumeAvailable(model.provider)
        withRetry {
            runWithTracking { eventHandlerConfig, state ->
                val executor = getExecutor(model)

                val agent = AIAgent(
                    promptExecutor = executor,
                    llmModel = model,
                    temperature = 1.0,
                    maxIterations = 10,
                    installFeatures = { install(EventHandler.Feature, eventHandlerConfig) },
                )
                agent.run("Repeat what I say: hello, I'm good.")
                withClue("No errors were expected during the run, got:\\n[${state.errors.joinToString("\n")}]") {
                    state.errors.shouldBeEmpty()
                }
            }
        }
    }

    @ParameterizedTest
    @MethodSource("allModels")
    fun integration_AIAgentShouldCallCustomTool(model: LLModel) = runTest {
        Models.assumeAvailable(model.provider)
        assumeTrue(model.capabilities.contains(LLMCapability.Tools), "Model $model does not support tools")

        val toolRegistry = ToolRegistry {
            tool(SimpleCalculatorTool)
        }

        withRetry {
            runWithTracking { eventHandlerConfig, state ->
                val executor = getExecutor(model)

                val agent = AIAgent.invoke(
                    promptExecutor = executor,
                    systemPrompt = systemPrompt + "JUST CALL THE TOOLS, NO QUESTIONS ASKED!",
                    strategy = singleRunStrategy(ToolCalls.SEQUENTIAL),
                    llmModel = model,
                    temperature = 1.0,
                    toolRegistry = toolRegistry,
                    maxIterations = 10,
                    installFeatures = { install(EventHandler.Feature, eventHandlerConfig) },
                )

                agent.run("How much is 3 times 5?")
                with(state) {
                    withClue("No tools were called for model $model") { actualToolCalls.shouldNotBeEmpty() }
                    withClue("The ${SimpleCalculatorTool.name} tool was not called for model $model") {
                        actualToolCalls shouldContain SimpleCalculatorTool.name
                    }
                }
            }
        }
    }

    @ParameterizedTest
    @MethodSource("modelsWithVisionCapability")
    fun integration_AIAgentWithImageCapabilityTest(model: LLModel) = runTest(timeout = 300.seconds) {
        Models.assumeAvailable(model.provider)
        assumeTrue(model.capabilities.contains(LLMCapability.Vision.Image), "Model must support vision capability")

        val imageFile = testResourcesDir.resolve("test.png")

        val imageBytes = imageFile.readBytes()
        val base64Image = Base64.getEncoder().encodeToString(imageBytes)

        val promptWithImage = """
            I'm sending you an image encoded in base64 format.

            data:image/png,$base64Image

            Please analyze this image and identify the image format if possible.
        """.trimIndent()

        withRetry {
            runWithTracking { eventHandlerConfig, state ->
                val executor = getExecutor(model)

                val agent = AIAgent(
                    promptExecutor = executor,
                    systemPrompt = "You are a helpful assistant that can analyze images.",
                    llmModel = model,
                    temperature = 1.0,
                    maxIterations = 10,
                    installFeatures = { install(EventHandler.Feature, eventHandlerConfig) },
                )

                agent.run(promptWithImage)

                with(state) {
                    errors.shouldBeEmpty()
                    results.shouldNotBeEmpty()
                    results.first() as String shouldNotBeNull {
                        shouldNotBeBlank()
                        length shouldBeGreaterThan 20
                        lowercase()
                            .shouldNotContain("error processing")
                            .shouldNotContain("unable to process")
                            .shouldNotContain("cannot process")
                    }
                }
            }
        }
    }

    @ParameterizedTest
    @MethodSource("allModels")
    fun integration_testRequestLLMWithoutToolsTest(model: LLModel) = runTest(timeout = 180.seconds) {
        Models.assumeAvailable(model.provider)
        assumeTrue(model.capabilities.contains(LLMCapability.Tools), "Model $model does not support tools")

        val executor = getExecutor(model)

        val toolRegistry = ToolRegistry {
            tool(SimpleCalculatorTool)
        }

        val customStrategy = strategy("test-without-tools") {
            val callLLM by nodeLLMRequest(name = "callLLM", allowToolCalls = false)
            edge(nodeStart forwardTo callLLM)
            edge(callLLM forwardTo nodeFinish onAssistantMessage { true })
        }

        withRetry(times = 3, testName = "integration_testRequestLLMWithoutTools[${model.id}]") {
            runWithTracking { eventHandlerConfig, state ->
                val agent = AIAgent(
                    promptExecutor = executor,
                    strategy = customStrategy,
                    agentConfig = AIAgentConfig(
                        prompt("test-without-tools") {},
                        model,
                        maxAgentIterations = 10,
                    ),
                    toolRegistry = toolRegistry,
                    installFeatures = { install(EventHandler.Feature, eventHandlerConfig) },
                )

                agent.run("What is 123 + 456?") shouldNotBeNull {
                    shouldNotBeBlank()
                    shouldContain("579")
                }

                state.actualToolCalls.shouldBeEmpty()
            }
        }
    }

    @ParameterizedTest
    @MethodSource("allModels")
    fun integration_AIAgentSingleRunWithSequentialToolsTest(model: LLModel) = runTest(timeout = 300.seconds) {
        runMultipleToolsTest(model, ToolCalls.SEQUENTIAL)
    }

    @ParameterizedTest
    @MethodSource("allModels")
    fun integration_AIAgentSingleRunWithParallelToolsTest(model: LLModel) = runTest(timeout = 300.seconds) {
        assumeTrue(
            model !in listOf(
                OpenAIModels.Chat.O1,
            ),
            "The model fails to call tools in parallel or flaky, see KG-115"
        )

        runMultipleToolsTest(model, ToolCalls.PARALLEL)
    }

    @ParameterizedTest
    @MethodSource("allModels")
    fun integration_AIAgentSingleRunNoParallelToolsTest(model: LLModel) = runTest(timeout = 300.seconds) {
        Models.assumeAvailable(model.provider)
        assumeTrue(model.capabilities.contains(LLMCapability.Tools), "Model $model does not support tools")

        withRetry {
            runWithTracking { eventHandlerConfig, state ->
                val sequentialAgent = getSingleRunAgentWithRunMode(
                    model,
                    ToolCalls.SINGLE_RUN_SEQUENTIAL,
                    eventHandlerConfig = eventHandlerConfig,
                )
                sequentialAgent.run(twoToolsPrompt)
                with(state) {
                    withClue("There should be no parallel tool calls in a Sequential single run scenario") {
                        parallelToolCalls.shouldBeEmpty()
                    }
                    withClue("There should be more or equal than 2 single tool calls in a Sequential single run scenario") {
                        singleToolCalls.size shouldBeGreaterThanOrEqual 2
                    }
                    withClue("First tool call should be ${SimpleCalculatorTool.name}") {
                        singleToolCalls.first().tool shouldBe SimpleCalculatorTool.name
                    }
                }
            }
        }
    }

    @ParameterizedTest
    @MethodSource("reasoningIntervals")
    fun integration_AIAgentWithReActStrategyTest(interval: Int) = runTest(timeout = 300.seconds) {
        val model = OpenAIModels.Chat.GPT4o

        withRetry {
            runWithTracking { eventHandlerConfig, state ->
                val executor = getExecutor(model)
                val agent = AIAgent(
                    promptExecutor = executor,
                    strategy = reActStrategy(reasoningInterval = interval),
                    agentConfig = AIAgentConfig(
                        prompt = prompt(
                            id = "react-agent-test",
                            params = LLMParams(
                                temperature = 1.0,
                                toolChoice = ToolChoice.Auto,
                            )
                        ) {},
                        model = model,
                        maxAgentIterations = 20,
                    ),
                    toolRegistry = bankingToolsRegistry,
                    installFeatures = { install(EventHandler.Feature, eventHandlerConfig) },
                )

                agent.run("How much did I spend last month?")

                with(state) {
                    errors.shouldBeEmpty()
                    results.shouldNotBeEmpty()
                    withClue("The ${GetTransactionsTool.descriptor.name} tool should be called") {
                        actualToolCalls shouldContain GetTransactionsTool.descriptor.name
                    }
                    withClue("The ${CalculateSumTool.descriptor.name} tool should be called") {
                        actualToolCalls shouldContain CalculateSumTool.descriptor.name
                    }
                    withClue("The ${GetTransactionsTool.descriptor.name} tool should be called before the ${CalculateSumTool.descriptor.name} tool") {
                        actualToolCalls.indexOf(GetTransactionsTool.descriptor.name) shouldBeLessThan actualToolCalls.indexOf(
                            CalculateSumTool.descriptor.name
                        )
                    }

                    withClue("Should have at least one reasoning call for the ReAct strategy.") {
                        reasoningCallsCount shouldBeGreaterThan 0
                    }

                    // Count how many times the reasoning step would trigger based on the interval
                    var expectedReasoningCalls = 1 // Start with 1 for the initial reasoning
                    for (i in toolExecutionCounter.indices) {
                        if (i % interval == 0) {
                            expectedReasoningCalls++
                        }
                    }

                    withClue(
                        "With reasoningInterval=$interval and ${toolExecutionCounter.size} tool calls, " +
                            "expected $expectedReasoningCalls reasoning calls but got $reasoningCallsCount"
                    ) {
                        reasoningCallsCount shouldBe expectedReasoningCalls
                    }
                }
            }
        }
    }

    @ParameterizedTest
    @MethodSource("allModels")
    fun integration_AgentCreateAndRestoreTest(model: LLModel) = runTest(timeout = 180.seconds) {
        val checkpointStorageProvider = InMemoryPersistenceStorageProvider()
        val sayHello = "Hello World!"
        val hello = "Hello"
        val savedMessage = "Saved the state – the agent is ready to work!"
        val save = "Save"
        val sayBye = "Bye Bye World!"
        val bye = "Bye"

        val checkpointStrategy = strategy("checkpoint-strategy") {
            val nodeHello by node<String, String>(hello) {
                sayHello
            }

            val nodeSave by node<String, String>(save) { input ->
                // Create a checkpoint
                withPersistence { agentContext ->
                    val parent = getLatestCheckpoint(agentContext.agentId)
                    createCheckpoint(
                        agentContext = agentContext,
                        nodeId = save,
                        lastInput = input,
                        lastInputType = typeOf<String>(),
                        version = parent?.version?.plus(1) ?: 0
                    )
                }
                savedMessage
            }

            val nodeBye by node<String, String>(bye) {
                sayBye
            }

            edge(nodeStart forwardTo nodeHello)
            edge(nodeHello forwardTo nodeSave)
            edge(nodeSave forwardTo nodeBye)
            edge(nodeBye forwardTo nodeFinish)
        }

        val agent = AIAgent(
            promptExecutor = getExecutor(model),
            strategy = checkpointStrategy,
            agentConfig = AIAgentConfig(
                prompt = prompt("checkpoint-test") {
                    system("You are a helpful assistant.")
                },
                model = model,
                maxAgentIterations = 10
            ),
            toolRegistry = ToolRegistry {},
            installFeatures = {
                install(Persistence) {
                    storage = checkpointStorageProvider
                }
            }
        )

        agent.run("Start the test")

        with(checkpointStorageProvider.getCheckpoints(agent.id)) {
            shouldNotBeEmpty()
            first().nodeId shouldBe save
        }

        val restoredAgent = AIAgent(
            promptExecutor = getExecutor(model),
            strategy = checkpointStrategy,
            agentConfig = AIAgentConfig(
                prompt = prompt("checkpoint-test") {
                    system("You are a helpful assistant.")
                },
                model = model,
                maxAgentIterations = 10
            ),
            toolRegistry = ToolRegistry {},
            id = agent.id, // Use the same ID to access the checkpoints
            installFeatures = {
                install(Persistence) {
                    storage = checkpointStorageProvider
                }
            }
        )

        // Verify that the agent continued from the checkpoint
        restoredAgent.run("Continue the test") shouldContain sayBye
    }

    @ParameterizedTest
    @MethodSource("allModels")
    fun integration_AgentCheckpointRollbackTest(model: LLModel) = runTest(timeout = 180.seconds) {
        val checkpointStorageProvider = InMemoryPersistenceStorageProvider()

        val hello = "Hello"
        val save = "Save"
        val bye = "Bye-bye"
        val rollback = "Rollback"

        val sayHello = "Hello World!"
        val saySave = "Saved the day"
        val sayBye = "Bye World!"

        val sayHelloLog = "sayHello executed\n"
        val saySaveLog = "saySave executed\n"
        val sayByeLog = "sayBye executed\n"
        val rollbackPerformingLog = "Rollback executed - performing rollback\n"
        val rollbackAlreadyLog = "Rollback executed - already rolled back\n"

        val rolledBackMessage = "Rolled back to the latest checkpoint"
        val alreadyRolledBackMessage = "Already rolled back, continuing to finish"

        var hasRolledBack = false

        // Shared result string to track node executions across rollbacks
        val executionLog = StringBuilder()

        val rollbackStrategy = strategy("rollback-strategy") {
            val nodeHello by node<String, String>(hello) {
                executionLog.append(sayHelloLog)
                sayHello
            }

            val nodeSave by node<String, String>(save) { input ->
                withPersistence { agentContext ->
                    val parent = getLatestCheckpoint(agentContext.agentId)
                    createCheckpoint(
                        agentContext = agentContext,
                        nodeId = save,
                        lastInput = input,
                        lastInputType = typeOf<String>(),
                        version = parent?.version?.plus(1) ?: 0
                    )
                }
                executionLog.append(saySaveLog)
                saySave
            }

            val nodeBye by node<String, String>(bye) {
                executionLog.append(sayByeLog)
                sayBye
            }

            val rollbackNode by node<String, String>(rollback) {
                // Use a shared variable to prevent infinite rollbacks
                // Only roll back once, then continue
                if (!hasRolledBack) {
                    hasRolledBack = true
                    executionLog.append(rollbackPerformingLog)
                    withPersistence { agentContext ->
                        rollbackToLatestCheckpoint(agentContext)
                    }
                    rolledBackMessage
                } else {
                    executionLog.append(rollbackAlreadyLog)
                    alreadyRolledBackMessage
                }
            }

            edge(nodeStart forwardTo nodeHello)
            edge(nodeHello forwardTo nodeSave)
            edge(nodeSave forwardTo nodeBye)
            edge(nodeBye forwardTo rollbackNode)
            edge(rollbackNode forwardTo nodeFinish)
        }

        val agent = AIAgent(
            promptExecutor = getExecutor(model),
            strategy = rollbackStrategy,
            agentConfig = AIAgentConfig(
                prompt = prompt("rollback-test") {
                    system("You are a helpful assistant.")
                },
                model = model,
                maxAgentIterations = 50
            ),
            toolRegistry = ToolRegistry {},
            installFeatures = {
                install(Persistence) {
                    storage = checkpointStorageProvider
                }
            }
        )

        withClue("Final result should contain output from the second execution of $rollback") {
            agent.run("Start the test") shouldContain alreadyRolledBackMessage
        }

        with(executionLog.toString()) {
            shouldNotBeEmpty()
            shouldContain(sayHelloLog.trim())
            shouldContain(saySaveLog.trim())
            shouldContain(sayByeLog.trim())
            shouldContain(rollbackPerformingLog.trim())
            saySaveLog.trim().toRegex().findAll(this).count() shouldBe 2
            sayByeLog.trim().toRegex().findAll(this).count() shouldBe 2
        }
    }

    @ParameterizedTest
    @MethodSource("allModels")
    fun integration_AgentCheckpointContinuousPersistenceTest(model: LLModel) = runTest(timeout = 180.seconds) {
        val checkpointStorageProvider =
            InMemoryPersistenceStorageProvider()

        val strategyName = "continuous-persistence-strategy"

        val hello = "Hello"
        val world = "Save"
        val bye = "Bye-bye"

        val sayHello = "Hello World!"
        val sayWorld = "World, hello!"
        val sayBye = "Bye World!"

        val promptName = "continuous-persistence-test"
        val systemMessage = "You are a helpful assistant."
        val testInput = "Start the test"

        val simpleStrategy = strategy(strategyName) {
            val nodeHello by node<String, String>(hello) {
                sayHello
            }

            val nodeWorld by node<String, String>(world) {
                sayWorld
            }

            val nodeBye by node<String, String>(bye) {
                sayBye
            }

            edge(nodeStart forwardTo nodeHello)
            edge(nodeHello forwardTo nodeWorld)
            edge(nodeWorld forwardTo nodeBye)
            edge(nodeBye forwardTo nodeFinish)
        }

        val agent = AIAgent(
            promptExecutor = getExecutor(model),
            strategy = simpleStrategy,
            agentConfig = AIAgentConfig(
                prompt = prompt(promptName) {
                    system(systemMessage)
                },
                model = model,
                maxAgentIterations = 10
            ),
            toolRegistry = ToolRegistry {},
            installFeatures = {
                install(Persistence) {
                    storage = checkpointStorageProvider
                    enableAutomaticPersistence = true // Enable continuous persistence
                }
            }
        )

        agent.run(testInput)

        with(checkpointStorageProvider.getCheckpoints(agent.id)) {
            size shouldBeGreaterThanOrEqual 3
            map { it.nodeId }.toSet() shouldNotBeNull {
                shouldContain(hello)
                shouldContain(world)
                shouldContain(bye)
            }
        }
    }

    @ParameterizedTest
    @MethodSource("allModels")
    fun integration_AgentCheckpointStorageProvidersTest(
        model: LLModel,
        @TempDir tempDir: Path,
    ) = runTest(timeout = 180.seconds) {
        val strategyName = "storage-providers-strategy"

        val hello = "Hello"
        val bye = "Bye-bye"

        val sayHello = "Hello World!"
        val sayBye = "Bye World!"

        val promptName = "storage-providers-test"
        val systemMessage = "You are a helpful assistant."
        val testInput = "Start the test"

        val noCheckpointsError = "No checkpoints were created"
        val incorrectNodeIdError = "Checkpoint has incorrect node ID"

        val fileStorageProvider = JVMFilePersistenceStorageProvider(tempDir)

        val simpleStrategy = strategy(strategyName) {
            val nodeHello by node<String, String>(hello) {
                sayHello
            }

            val nodeBye by node<String, String>(bye) { input ->
                withPersistence { agentContext ->
                    val parent = getLatestCheckpoint(agentContext.agentId)
                    createCheckpoint(
                        agentContext = agentContext,
                        nodeId = bye,
                        lastInput = input,
                        lastInputType = typeOf<String>(),
                        version = parent?.version?.plus(1) ?: 0
                    )
                }
                sayBye
            }

            edge(nodeStart forwardTo nodeHello)
            edge(nodeHello forwardTo nodeBye)
            edge(nodeBye forwardTo nodeFinish)
        }

        val agent = AIAgent(
            promptExecutor = getExecutor(model),
            strategy = simpleStrategy,
            agentConfig = AIAgentConfig(
                prompt = prompt(promptName) {
                    system(systemMessage)
                },
                model = model,
                maxAgentIterations = 10
            ),
            toolRegistry = ToolRegistry {},
            installFeatures = {
                install(Persistence) {
                    storage = fileStorageProvider
                }
            }
        )

        agent.run(testInput)

        with(fileStorageProvider.getCheckpoints(agent.id).filter { it.nodeId != "tombstone" }) {
            withClue(noCheckpointsError) {
                isNotEmpty() shouldBe true
            }
            withClue(incorrectNodeIdError) {
                first().nodeId shouldBe bye
            }
        }
    }

    @ParameterizedTest
    @MethodSource("allModels")
    @Disabled("KG-499 Infinite loop on an attempt to serialize input for checkpoint creation for nodeSendToolResult")
    fun integration_AgentCheckpointWithToolCallsTest(model: LLModel) = runTest(timeout = 180.seconds) {
        assumeTrue(model.capabilities.contains(LLMCapability.Tools), "Model $model does not support tools")

        val storageProvider = InMemoryPersistenceStorageProvider()
        val registry = ToolRegistry {
            tool(SimpleCalculatorTool)
        }

        withRetry {
            runWithTracking { eventHandlerConfig, state ->
                val executor = getExecutor(model)

                val agent = AIAgent(
                    promptExecutor = executor,
                    strategy = singleRunStrategy(ToolCalls.SEQUENTIAL),
                    agentConfig = AIAgentConfig(
                        prompt = prompt(
                            id = "calculator-agent-persistence-test",
                            params = LLMParams(
                                temperature = 1.0,
                                toolChoice = ToolChoice.Required,
                            )
                        ) {
                            system {
                                +systemPrompt
                                +"Always use the calculator tool once to answer math questions."
                                +"JUST CALL THE TOOL, NO QUESTIONS ASKED."
                            }
                        },
                        model = model,
                        maxAgentIterations = 10
                    ),
                    toolRegistry = registry,
                    installFeatures = {
                        install(EventHandler.Feature, eventHandlerConfig)
                        install(Persistence) {
                            storage = storageProvider
                            enableAutomaticPersistence = true
                        }
                    },
                )

                agent.run("What is 12 + 34?")

                with(state) {
                    actualToolCalls shouldBe listOf(SimpleCalculatorTool.descriptor.name)
                    withClue("${SimpleCalculatorTool.descriptor.name} tool should be called for model $model with persistence") {
                        errors.shouldBeEmpty()
                    }
                }

                withClue("Checkpoint message history should contain a tool call to '${SimpleCalculatorTool.name}'") {
                    storageProvider.getCheckpoints(agent.id).filter { it.nodeId != "tombstone" }
                        .shouldNotBeEmpty()
                        .shouldForAny { cp ->
                            cp.messageHistory.any { msg ->
                                msg is Message.Tool.Call && msg.tool == SimpleCalculatorTool.name
                            }
                        }
                }
            }
        }
    }

    @ParameterizedTest
    @MethodSource("allModels")
    fun integration_AgentWithToolsWithoutParamsTest(model: LLModel) = runTest(timeout = 180.seconds) {
        assumeTrue(model.capabilities.contains(LLMCapability.Tools), "Model $model does not support tools")

        val registry = ToolRegistry {
            tool(CalculatorToolNoArgs)
        }

        withRetry {
            runWithTracking { eventHandlerConfig, state ->
                val executor = getExecutor(model)

                val agent = AIAgent(
                    promptExecutor = executor,
                    strategy = singleRunStrategy(ToolCalls.SEQUENTIAL),
                    agentConfig = AIAgentConfig(
                        prompt = prompt(
                            id = "calculator-agent-test",
                            params = LLMParams(
                                temperature = 1.0,
                                toolChoice = ToolChoice.Auto, // KG-163
                            )
                        ) {
                            system {
                                +systemPrompt
                                +"YOU'RE OBLIGED TO USE TOOLS. THIS IS MANDATORY."
                                +"JUST CALL THE TOOL ONE TIME, NO QUESTIONS ASKED."
                            }
                        },
                        model = model,
                        maxAgentIterations = 10
                    ),
                    toolRegistry = registry,
                    installFeatures = { install(EventHandler.Feature, eventHandlerConfig) },
                )
                agent.run("What is 123 + 456?")

                with(state) {
                    withClue("${CalculatorToolNoArgs.descriptor.name} tool should be called for model $model") {
                        actualToolCalls shouldBe listOf(CalculatorToolNoArgs.descriptor.name)
                    }

                    errors.shouldBeEmpty()
                }
            }
        }
    }

    @ParameterizedTest
    @MethodSource("allModels")
    fun integration_ParallelNodesExecutionTest(model: LLModel) = runTest(timeout = 180.seconds) {
        Models.assumeAvailable(model.provider)

        val parallelStrategy = strategy<String, String>("parallel-nodes-strategy") {
            // Create three nodes that process different computations
            val mathNode by node<Unit, String>("math") {
                "Math result: ${7 * 8}"
            }

            val textNode by node<Unit, String>("text") {
                "Text result: Hello World"
            }

            val countNode by node<Unit, String>("count") {
                "Count result: ${(1..5).sum()}"
            }

            val parallelNode by parallel(
                mathNode,
                textNode,
                countNode,
                name = "parallelProcessor"
            ) {
                val combinedResult = fold("") { acc, result ->
                    if (acc.isEmpty()) result else "$acc | $result"
                }
                ParallelNodeExecutionResult("Combined: ${combinedResult.output}", this)
            }

            edge(nodeStart forwardTo parallelNode transformed { })
            edge(parallelNode forwardTo nodeFinish)
        }

        withRetry {
            runWithTracking { eventHandlerConfig, state ->
                val agent = AIAgent<String, String>(
                    promptExecutor = getExecutor(model),
                    strategy = parallelStrategy,
                    agentConfig = AIAgentConfig(
                        prompt = prompt("parallel-test") {
                            system("You are a helpful assistant.")
                        },
                        model = model,
                        maxAgentIterations = 10
                    ),
                    toolRegistry = ToolRegistry {},
                    installFeatures = { install(EventHandler.Feature, eventHandlerConfig) }
                )

                agent.run("Hi")

                with(state) {
                    errors.shouldBeEmpty() // There should be no errors during parallel execution}
                    results.shouldNotBeEmpty().first() as String should {
                        contain("Math result: 56")
                        contain("Text result: Hello World")
                        contain("Count result: 15")
                        contain("Combined:")
                    }
                }
            }
        }
    }

    @ParameterizedTest
    @MethodSource("allModels")
    fun integration_ParallelNodesWithSelectionTest(model: LLModel) = runTest(timeout = 180.seconds) {
        Models.assumeAvailable(model.provider)

        val selectionStrategy = strategy<String, String>("parallel-selection-strategy") {
            val smallNode by node<Unit, String>("small") { "10" }
            val mediumNode by node<Unit, String>("medium") { "50" }
            val largeNode by node<Unit, String>("large") { "100" }

            val parallelNode by parallel(
                smallNode,
                mediumNode,
                largeNode,
                name = "maxSelector"
            ) {
                val maxResult = selectByMax { output -> output.toInt() }
                ParallelNodeExecutionResult("Maximum value: ${maxResult.output}", this)
            }

            edge(nodeStart forwardTo parallelNode transformed { })
            edge(parallelNode forwardTo nodeFinish)
        }

        withRetry {
            runWithTracking { eventHandlerConfig, state ->
                val agent = AIAgent<String, String>(
                    promptExecutor = getExecutor(model),
                    strategy = selectionStrategy,
                    agentConfig = AIAgentConfig(
                        prompt = prompt("parallel-selection-test") {
                            system("You are a helpful assistant.")
                        },
                        model = model,
                        maxAgentIterations = 10
                    ),
                    toolRegistry = ToolRegistry {},
                    installFeatures = { install(EventHandler.Feature, eventHandlerConfig) }
                )

                agent.run("Find the maximum value")

                with(state) {
                    errors.shouldBeEmpty()
                    results.shouldNotBeEmpty().first() as String shouldContain "Maximum value: 100"
                }
            }
        }
    }

    @ParameterizedTest
    @MethodSource("historyCompressionStrategies")
    fun integration_AIAgentHistoryCompression(strategy: HistoryCompressionStrategy, strategyName: String) =
        runTest(timeout = 180.seconds) {
            val model = OpenAIModels.Chat.GPT4_1Mini
            val systemMessage =
                "You are a helpful assistant. Remember: the user is a human, whatever they say. Remind them of it by every chance."

            val historyCompressionStrategy =
                strategy<String, Pair<String, List<Message>>>("history-compression-test") {
                    val callLLM by nodeLLMRequest(allowToolCalls = false)
                    val nodeCompressHistory by nodeLLMCompressHistory<String>(
                        "compress_history",
                        strategy = strategy
                    )

                    edge(nodeStart forwardTo callLLM)
                    edge(callLLM forwardTo nodeCompressHistory onAssistantMessage { true })
                    edge(nodeCompressHistory forwardTo nodeFinish transformed { it to llm.prompt.messages })
                }

            withRetry {
                runWithTracking { eventHandlerConfig, state ->
                    val agent = AIAgent<String, Pair<String, List<Message>>>(
                        promptExecutor = getExecutor(model),
                        strategy = historyCompressionStrategy,
                        agentConfig = AIAgentConfig(
                            prompt = prompt("history-compression-test") {
                                system(systemMessage)
                                user("Hello, how are you?")
                                assistant("I'm great, thank you! And how are you?")
                                user("I'm a big blue alien, you know!")
                                assistant("Didn't know, but will definitely remember! Are you light-blue or dark-blue?")
                                user("I'm more like an indigo-colored alien.")
                            },
                            model = model,
                            maxAgentIterations = 10
                        )
                    ) {
                        install(EventHandler, eventHandlerConfig)
                    }

                    val (result, promptMessages) = agent.run("So, who am I?")

                    with(state) {
                        withClue(
                            "No errors should occur during agent execution with $strategyName, got: [${
                                errors.joinToString(
                                    "\n"
                                )
                            }]"
                        ) {
                            errors.shouldBeEmpty()
                        }
                    }

                    result.shouldNotBeBlank() shouldContain "human"
                    promptMessages shouldNotBeNull {
                        filterIsInstance<Message.System>().shouldNotBeEmpty()
                        first().content.shouldNotBeBlank() shouldBe systemMessage
                    }
                }
            }
        }

    @ParameterizedTest
    @MethodSource("historyCompressionStrategies")
    fun integration_AIAgentHistoryCompressionAfterToolCalls(
        strategy: HistoryCompressionStrategy,
        strategyName: String
    ) = runTest(timeout = 10.minutes) {
        val model = OpenAIModels.Chat.GPT5_1
        val systemMessage = "You are a helpful assistant. JUST CALL THE TOOLS, NO QUESTIONS ASKED."

        val historyCompressionStrategy = buildHistoryCompressionWithToolsStrategy(
            strategy = strategy,
            compressBeforeToolResult = false,
        )

        withRetry {
            runWithTracking { eventHandlerConfig, state ->
                val agent = AIAgent<String, Pair<String, List<Message>>>(
                    promptExecutor = getExecutor(model),
                    strategy = historyCompressionStrategy,
                    agentConfig = AIAgentConfig(
                        prompt = prompt(
                            "history-compression-with-tools-test",
                            params = LLMParams(toolChoice = ToolChoice.Auto)
                        ) {
                            system(systemMessage)
                            user(
                                "Please calculate 7 times 2 using the calculator tool. " +
                                    "Reply concisely after executing the tool."
                            )
                        },
                        model = model,
                        maxAgentIterations = 10
                    ),
                    toolRegistry = twoToolsRegistry,
                ) {
                    install(EventHandler, eventHandlerConfig)
                }

                val (result, promptMessages) = agent.run("Proceed with the task.")

                assertHistoryCompressionWithTools(
                    errors = state.errors,
                    actualToolCalls = state.actualToolCalls,
                    result = result,
                    promptMessages = promptMessages,
                    strategyName = strategyName
                )
            }
        }
    }

    @ParameterizedTest
    @MethodSource("historyCompressionStrategies")
    fun integration_AIAgentHistoryCompressionBeforeToolResult(
        strategy: HistoryCompressionStrategy,
        strategyName: String
    ) = runTest(timeout = 10.minutes) {
        val model = OpenAIModels.Chat.GPT5_1
        val systemMessage = "You are a helpful assistant. JUST CALL THE TOOLS, NO QUESTIONS ASKED."

        val historyCompressionStrategy = buildHistoryCompressionWithToolsStrategy(
            strategy = strategy,
            compressBeforeToolResult = true,
        )

        withRetry {
            runWithTracking { eventHandlerConfig, state ->
                val agent = AIAgent<String, Pair<String, List<Message>>>(
                    promptExecutor = getExecutor(model),
                    strategy = historyCompressionStrategy,
                    agentConfig = AIAgentConfig(
                        prompt = prompt(
                            "history-compression-with-tools-test",
                            params = LLMParams(toolChoice = ToolChoice.Auto)
                        ) {
                            system(systemMessage)
                            user(
                                "Please calculate 7 times 2 using the calculator tool. " +
                                    "Reply concisely after executing the tool."
                            )
                        },
                        model = model,
                        maxAgentIterations = 10
                    ),
                    toolRegistry = twoToolsRegistry,
                ) {
                    install(EventHandler, eventHandlerConfig)
                }

                val (result, promptMessages) = agent.run("Proceed with the task.")

                assertHistoryCompressionWithTools(
                    errors = state.errors,
                    actualToolCalls = state.actualToolCalls,
                    result = result,
                    promptMessages = promptMessages,
                    strategyName = strategyName
                )
            }
        }
    }
}
