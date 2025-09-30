package ai.koog.integration.tests.agent

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.ToolCalls
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.singleRunStrategy
import ai.koog.agents.core.dsl.builder.ParallelNodeExecutionResult
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.HistoryCompressionStrategy
import ai.koog.agents.core.dsl.extension.nodeLLMCompressHistory
import ai.koog.agents.core.dsl.extension.nodeLLMRequest
import ai.koog.agents.core.dsl.extension.onAssistantMessage
import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.ext.agent.reActStrategy
import ai.koog.agents.features.eventHandler.feature.EventHandler
import ai.koog.agents.features.eventHandler.feature.EventHandlerConfig
import ai.koog.agents.snapshot.feature.Persistency
import ai.koog.agents.snapshot.feature.withPersistency
import ai.koog.agents.snapshot.providers.InMemoryPersistencyStorageProvider
import ai.koog.agents.snapshot.providers.file.JVMFilePersistencyStorageProvider
import ai.koog.integration.tests.utils.Models
import ai.koog.integration.tests.utils.RetryUtils.withRetry
import ai.koog.integration.tests.utils.TestUtils.CalculatorTool
import ai.koog.integration.tests.utils.TestUtils.DelayTool
import ai.koog.integration.tests.utils.TestUtils.readAwsAccessKeyIdFromEnv
import ai.koog.integration.tests.utils.TestUtils.readAwsSecretAccessKeyFromEnv
import ai.koog.integration.tests.utils.TestUtils.readAwsSessionTokenFromEnv
import ai.koog.integration.tests.utils.TestUtils.readTestAnthropicKeyFromEnv
import ai.koog.integration.tests.utils.TestUtils.readTestGoogleAIKeyFromEnv
import ai.koog.integration.tests.utils.TestUtils.readTestOpenAIKeyFromEnv
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.clients.bedrock.BedrockModels
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
import ai.koog.prompt.executor.llms.all.simpleAnthropicExecutor
import ai.koog.prompt.executor.llms.all.simpleBedrockExecutor
import ai.koog.prompt.executor.llms.all.simpleGoogleAIExecutor
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.params.LLMParams
import ai.koog.prompt.params.LLMParams.ToolChoice
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Base64
import java.util.stream.Stream
import kotlin.io.path.readBytes
import kotlin.reflect.typeOf
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

@Execution(ExecutionMode.SAME_THREAD)
class AIAgentIntegrationTest {

    companion object {
        private lateinit var testResourcesDir: Path

        @JvmStatic
        @BeforeAll
        fun setup() {
            testResourcesDir =
                Paths.get(AIAgentIntegrationTest::class.java.getResource("/media")!!.toURI())
        }

        @JvmStatic
        fun reasoningIntervals(): Stream<Int> {
            return listOf(1, 2, 3).stream()
        }

        @JvmStatic
        fun openAIModels(): Stream<LLModel> {
            return Models.openAIModels()
        }

        @JvmStatic
        fun anthropicModels(): Stream<LLModel> {
            return Models.anthropicModels()
        }

        @JvmStatic
        fun anthropicModels4_0(): Stream<LLModel> {
            return listOf(
                AnthropicModels.Opus_4,
                AnthropicModels.Opus_4_1,
                AnthropicModels.Sonnet_4,
                AnthropicModels.Sonnet_4_5,
            ).stream()
        }

        @JvmStatic
        fun googleModels(): Stream<LLModel> {
            return Models.googleModels()
        }

        @JvmStatic
        fun bedrockModels(): Stream<LLModel> {
            return Models.bedrockModels()
        }

        @JvmStatic
        fun openRouterModels(): Stream<LLModel> {
            return Models.openRouterModels()
        }

        @JvmStatic
        fun modelsWithVisionCapability(): Stream<Arguments> {
            return Models.modelsWithVisionCapability()
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
                // ToDo uncomment when KG-311 is fully fixed
                // Arguments.of(HistoryCompressionStrategy.Chunked(2), "Chunked(2)")
            )
        }

        val twoToolsRegistry = ToolRegistry {
            tool(CalculatorTool)
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

        fun getExecutor(model: LLModel): SingleLLMPromptExecutor = when (model.provider) {
            is LLMProvider.Anthropic -> simpleAnthropicExecutor(readTestAnthropicKeyFromEnv())
            is LLMProvider.Google -> simpleGoogleAIExecutor(readTestGoogleAIKeyFromEnv())
            is LLMProvider.Bedrock -> simpleBedrockExecutor(
                readAwsAccessKeyIdFromEnv(),
                readAwsSecretAccessKeyFromEnv(),
                readAwsSessionTokenFromEnv()
            )

            else -> simpleOpenAIExecutor(readTestOpenAIKeyFromEnv())
        }

        fun getSingleRunAgentWithRunMode(
            model: LLModel,
            runMode: ToolCalls,
            toolRegistry: ToolRegistry = twoToolsRegistry,
            eventHandlerConfig: EventHandlerConfig.() -> Unit,
        ) = AIAgent(
            promptExecutor = getExecutor(model),
            strategy = singleRunStrategy(runMode),
            agentConfig = AIAgentConfig(
                prompt = prompt(
                    id = "multiple-tool-calls-agent",
                    params = LLMParams(
                        temperature = 1.0,
                        toolChoice = ToolChoice.Auto,
                    )
                ) {
                    system("You are a helpful assistant.")
                },
                model = model,
                maxAgentIterations = 10,
            ),
            toolRegistry = toolRegistry,
            installFeatures = { install(EventHandler.Feature, eventHandlerConfig) },
        )
    }

    @Serializable
    private object CalculatorToolNoArgs : SimpleTool<Unit>() {
        override val argsSerializer = Unit.serializer()

        override val name: String = "calculator"
        override val description: String =
            "A simple calculator that performs basic calculations. No parameters needed."

        override suspend fun doExecute(args: Unit): String {
            return "The result of 123 + 456 is 579"
        }
    }

    @Serializable
    data class GetTransactionsArgs(
        @property:LLMDescription("Start date in format YYYY-MM-DD")
        val startDate: String,
        @property:LLMDescription("End date in format YYYY-MM-DD")
        val endDate: String
    )

    object GetTransactionsTool : SimpleTool<GetTransactionsArgs>() {
        override val argsSerializer = GetTransactionsArgs.serializer()

        override val name: String = "get_transactions"
        override val description: String = "Get all transactions between two dates"

        override suspend fun doExecute(args: GetTransactionsArgs): String {
            // Simulate returning transactions
            return """
            [
              {date: "${args.startDate}", amount: -100.00, description: "Grocery Store"},
              {date: "${args.startDate}", amount: +1000.00, description: "Salary Deposit"},
              {date: "${args.endDate}", amount: -500.00, description: "Rent Payment"},
              {date: "${args.endDate}", amount: -200.00, description: "Utilities"}
            ]
            """.trimIndent()
        }
    }

    @Serializable
    data class CalculateSumArgs(
        @property:LLMDescription("List of amounts to sum")
        val amounts: List<Double>
    )

    object CalculateSumTool : SimpleTool<CalculateSumArgs>() {
        override val argsSerializer = CalculateSumArgs.serializer()

        override val name: String = "calculate_sum"
        override val description: String = "Calculate the sum of a list of amounts"

        override suspend fun doExecute(args: CalculateSumArgs): String {
            val sum = args.amounts.sum()
            return sum.toString()
        }
    }

    val systemPrompt = "You are a helpful assistant."
    private var reasoningCallsCount = 0
    val actualToolCalls = mutableListOf<String>()
    val errors = mutableListOf<Throwable>()
    val results = mutableListOf<Any?>()
    val toolExecutionCounter = mutableListOf<String>()
    val parallelToolCalls = mutableListOf<ToolCallInfo>()
    val singleToolCalls = mutableListOf<ToolCallInfo>()

    val eventHandlerConfig: EventHandlerConfig.() -> Unit = {
        onAgentCompleted { eventContext ->
            results.add(eventContext.result)
        }

        onAgentExecutionFailed { eventContext ->
            errors.add(eventContext.throwable)
        }

        onLLMCallStarting { eventContext ->
            if (eventContext.tools.isEmpty() &&
                eventContext.prompt.params.toolChoice == null
            ) {
                reasoningCallsCount++
            }
        }

        onNodeExecutionStarting { eventContext ->
            val input = eventContext.input

            if (input is List<*>) {
                input.filterIsInstance<Message.Tool.Call>().forEach { call ->
                    parallelToolCalls.add(
                        ToolCallInfo(
                            id = call.id,
                            tool = call.tool,
                            content = call.content,
                            metaInfo = call.metaInfo,
                        )
                    )
                }
            } else if (input is Message.Tool.Call) {
                singleToolCalls.add(
                    ToolCallInfo(
                        id = input.id,
                        tool = input.tool,
                        content = input.content,
                        metaInfo = input.metaInfo,
                    )
                )
            }
        }

        onToolExecutionStarting { eventContext ->
            actualToolCalls.add(eventContext.tool.name)
            toolExecutionCounter.add(eventContext.tool.name)
        }
    }

    data class ToolCallInfo(
        val id: String?,
        val tool: String,
        val content: String,
        val metaInfo: ResponseMetaInfo,
    )

    fun cleanUp() {
        toolExecutionCounter.clear()
        actualToolCalls.clear()
        singleToolCalls.clear()
        errors.clear()
        results.clear()
        parallelToolCalls.clear()
        reasoningCallsCount = 0
    }

    @BeforeTest
    fun setupTest() = runTest {
        cleanUp()
    }

    @AfterTest
    fun teardownTest() = runTest {
        cleanUp()
    }

    @TempDir
    lateinit var tempDir: Path

    private fun runMultipleToolsTest(model: LLModel, runMode: ToolCalls) = runTest(timeout = 300.seconds) {
        Models.assumeAvailable(model.provider)
        assumeTrue(model.capabilities.contains(LLMCapability.Tools), "Model $model does not support tools")

        /* Some models are not calling tools in parallel:
         * see https://youtrack.jetbrains.com/issue/KG-115
         */

        withRetry {
            val multiToolAgent =
                getSingleRunAgentWithRunMode(model, runMode, eventHandlerConfig = eventHandlerConfig)
            multiToolAgent.run(twoToolsPrompt)

            assertTrue(
                parallelToolCalls.size >= 2,
                "There should be at least 2 tool calls in a Multiple tool calls scenario"
            )
            assertTrue(
                singleToolCalls.isEmpty(),
                "There should be no single tool calls in a Multiple tool calls scenario"
            )

            val firstCall = parallelToolCalls.first()
            val secondCall = parallelToolCalls.last()

            if (runMode == ToolCalls.PARALLEL) {
                assertTrue(
                    firstCall.metaInfo.timestamp == secondCall.metaInfo.timestamp ||
                        firstCall.metaInfo.totalTokensCount == secondCall.metaInfo.totalTokensCount ||
                        firstCall.metaInfo.inputTokensCount == secondCall.metaInfo.inputTokensCount ||
                        firstCall.metaInfo.outputTokensCount == secondCall.metaInfo.outputTokensCount,
                    "At least one of the metadata should be equal for parallel tool calls"
                )
            }

            assertEquals(CalculatorTool.name, firstCall.tool, "First tool call should be ${CalculatorTool.name}")
            assertEquals(DelayTool.name, secondCall.tool, "Second tool call should be ${DelayTool.name}")
        }
    }

    @ParameterizedTest
    @MethodSource("openAIModels", "anthropicModels", "googleModels", "bedrockModels")
    fun integration_AIAgentShouldNotCallToolsByDefault(model: LLModel) = runTest {
        Models.assumeAvailable(model.provider)
        withRetry {
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
            assertTrue(actualToolCalls.isEmpty(), "No tools should be called for model $model")
        }
    }

    // ToDo: uncomment anthropicModels when KG-317 is fixed
    @ParameterizedTest
    @MethodSource("openAIModels", /*"anthropicModels",*/ "googleModels")
    fun integration_AIAgentNoSystemMessage(model: LLModel) = runTest {
        Models.assumeAvailable(model.provider)
        withRetry {
            val executor = getExecutor(model)

            val agent = AIAgent(
                promptExecutor = executor,
                llmModel = model,
                temperature = 1.0,
                maxIterations = 10,
                installFeatures = { install(EventHandler.Feature, eventHandlerConfig) },
            )
            agent.run("Repeat what I say: hello, I'm good.")
            assertTrue(
                errors.isEmpty(),
                "No errors were expected during the run, got:\n[${errors.joinToString("\n")}]"
            )
        }
    }

    @ParameterizedTest
    @MethodSource("openAIModels", "anthropicModels", "googleModels", "bedrockModels")
    fun integration_AIAgentShouldCallCustomTool(model: LLModel) = runTest {
        Models.assumeAvailable(model.provider)
        assumeTrue(model.capabilities.contains(LLMCapability.Tools), "Model $model does not support tools")

        val toolRegistry = ToolRegistry {
            tool(CalculatorTool)
        }

        withRetry {
            val executor = getExecutor(model)

            val agent = AIAgent(
                promptExecutor = executor,
                systemPrompt = systemPrompt + "You MUST use tools.",
                llmModel = model,
                temperature = 1.0,
                toolRegistry = toolRegistry,
                maxIterations = 10,
                installFeatures = { install(EventHandler.Feature, eventHandlerConfig) },
            )

            agent.run("How much is 3 times 5?")
            assertTrue(actualToolCalls.isNotEmpty(), "No tools were called for model $model")
            assertTrue(
                actualToolCalls.contains(CalculatorTool.name),
                "The ${CalculatorTool.name} tool was not called for model $model"
            )
        }
    }

    @Test
    fun integration_BedrockNovaAgentShouldCallTools() = runTest {
        val model = BedrockModels.AmazonNovaLite

        Models.assumeAvailable(model.provider)
        assumeTrue(model.capabilities.contains(LLMCapability.Tools), "Model $model does not support tools")

        val toolRegistry = ToolRegistry {
            tool(CalculatorTool)
        }

        withRetry {
            val executor = getExecutor(model)

            val agent = AIAgent(
                promptExecutor = executor,
                systemPrompt = systemPrompt + "You MUST use tools.",
                llmModel = model,
                strategy = singleRunStrategy(ToolCalls.PARALLEL),
                temperature = 1.0,
                toolRegistry = toolRegistry,
                maxIterations = 10,
                installFeatures = { install(EventHandler.Feature, eventHandlerConfig) },
            )

            agent.run("How much is 3 times 5?")
            assertTrue(actualToolCalls.isNotEmpty(), "No tools were called for Bedrock Nova model $model")
            assertTrue(
                actualToolCalls.contains(CalculatorTool.name),
                "The ${CalculatorTool.name} tool was not called for Bedrock Nova model $model"
            )
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

            assertTrue(errors.isEmpty(), "There should be no errors")
            assertTrue(results.isNotEmpty(), "There should be results")

            val result = results.first() as String
            assertNotNull(result, "Result should not be null")
            assertTrue(result.isNotBlank(), "Result should not be empty or blank")
            assertTrue(result.length > 20, "Result should contain more than 20 characters")

            val resultLowerCase = result.lowercase()
            assertFalse(resultLowerCase.contains("error processing"), "Result should not contain error messages")
            assertFalse(
                resultLowerCase.contains("unable to process"),
                "Result should not indicate inability to process"
            )
            assertFalse(resultLowerCase.contains("cannot process"), "Result should not indicate inability to process")
        }
    }

    @ParameterizedTest
    @MethodSource("openAIModels", "anthropicModels", "googleModels", "bedrockModels")
    fun integration_testRequestLLMWithoutToolsTest(model: LLModel) = runTest(timeout = 180.seconds) {
        Models.assumeAvailable(model.provider)
        assumeTrue(model.capabilities.contains(LLMCapability.Tools), "Model $model does not support tools")

        val executor = getExecutor(model)

        val toolRegistry = ToolRegistry {
            tool(CalculatorTool)
        }

        val customStrategy = strategy("test-without-tools") {
            val callLLM by nodeLLMRequest(name = "callLLM", allowToolCalls = false)
            edge(nodeStart forwardTo callLLM)
            edge(callLLM forwardTo nodeFinish onAssistantMessage { true })
        }

        val agent = AIAgent(
            promptExecutor = executor,
            strategy = customStrategy,
            agentConfig = AIAgentConfig(
                prompt("test-without-tools") {},
                model,
                maxAgentIterations = 10,
            ),
            toolRegistry = toolRegistry,
        )

        withRetry(times = 3, testName = "integration_testRequestLLMWithoutTools[${model.id}]") {
            val result = agent.run("What is 123 + 456?")

            assertNotNull(result, "Result should not be null")
            assertTrue(result.isNotEmpty(), "Result should not be empty")
            assertTrue(actualToolCalls.isEmpty(), "No tools should be called for model $model")

            assertTrue(
                result.contains("579"),
                "Result should contain the correct answer (579)"
            )
        }
    }

    @ParameterizedTest
    @MethodSource("openAIModels", "anthropicModels", "googleModels", "bedrockModels")
    fun integration_AIAgentSingleRunWithSequentialToolsTest(model: LLModel) = runTest(timeout = 300.seconds) {
        runMultipleToolsTest(model, ToolCalls.SEQUENTIAL)
    }

    @ParameterizedTest
    @MethodSource("openAIModels", "anthropicModels4_0", "googleModels")
    fun integration_AIAgentSingleRunWithParallelToolsTest(model: LLModel) = runTest(timeout = 300.seconds) {
        assumeTrue(model.id != OpenAIModels.Reasoning.O1.id, "The model fails to call tools in parallel, see KG-115")
        assumeTrue(model.id != OpenAIModels.Reasoning.O3.id, "The model fails to call tools in parallel, see KG-115")
        assumeTrue(
            model.id != OpenAIModels.Reasoning.O3Mini.id,
            "The model fails to call tools in parallel, see KG-115"
        )
        assumeTrue(
            model.id != OpenAIModels.CostOptimized.O4Mini.id,
            "The model fails to call tools in parallel, see KG-115"
        )

        runMultipleToolsTest(model, ToolCalls.PARALLEL)
    }

    @ParameterizedTest
    @MethodSource("openAIModels", "anthropicModels", "googleModels", "bedrockModels")
    fun integration_AIAgentSingleRunNoParallelToolsTest(model: LLModel) = runTest(timeout = 300.seconds) {
        Models.assumeAvailable(model.provider)
        assumeTrue(model.capabilities.contains(LLMCapability.Tools), "Model $model does not support tools")
        assumeTrue(model.id != OpenAIModels.Audio.GPT4oAudio.id, "See KG-124")

        withRetry {
            val sequentialAgent = getSingleRunAgentWithRunMode(
                model,
                ToolCalls.SINGLE_RUN_SEQUENTIAL,
                eventHandlerConfig = eventHandlerConfig,
            )
            sequentialAgent.run(twoToolsPrompt)

            assertTrue(
                parallelToolCalls.isEmpty(),
                "There should be no parallel tool calls in a Sequential single run scenario"
            )
            assertTrue(
                singleToolCalls.isNotEmpty(),
                "There should be exactly 2 single tool calls in a Sequential single run scenario"
            )
            assertEquals(
                CalculatorTool.name,
                singleToolCalls.first().tool,
                "First tool call should be ${CalculatorTool.name}"
            )
        }
    }

    @ParameterizedTest
    @MethodSource("reasoningIntervals")
    fun integration_AIAgentWithReActStrategyTest(interval: Int) = runTest(timeout = 300.seconds) {
        val model = OpenAIModels.Chat.GPT4o

        withRetry {
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

            assertTrue(errors.isEmpty(), "There should be no errors")
            assertTrue(results.isNotEmpty(), "There should be results")
            assertTrue(
                actualToolCalls.contains(GetTransactionsTool.descriptor.name),
                "The ${GetTransactionsTool.descriptor.name} tool should be called"
            )
            assertTrue(
                actualToolCalls.contains(CalculateSumTool.descriptor.name),
                "The ${CalculateSumTool.descriptor.name} tool should be called"
            )

            val getTransactionsIndex = actualToolCalls.indexOf(GetTransactionsTool.descriptor.name)
            val calculateSumIndex = actualToolCalls.indexOf(CalculateSumTool.descriptor.name)
            assertTrue(
                getTransactionsIndex < calculateSumIndex,
                "The ${GetTransactionsTool.descriptor.name} tool should be called before the ${CalculateSumTool.descriptor.name} tool"
            )

            assertTrue(
                reasoningCallsCount > 0,
                "Should have at least one reasoning call for the ReAct strategy."
            )

            // Count how many times the reasoning step would trigger based on the interval
            var expectedReasoningCalls = 1 // Start with 1 for the initial reasoning
            for (i in 0 until toolExecutionCounter.size) {
                if (i % interval == 0) {
                    expectedReasoningCalls++
                }
            }

            assertEquals(
                expectedReasoningCalls,
                reasoningCallsCount,
                "With reasoningInterval=$interval and ${toolExecutionCounter.size} tool calls, " +
                    "expected $expectedReasoningCalls reasoning calls but got $reasoningCallsCount"
            )
        }
    }

    @ParameterizedTest
    @MethodSource("openAIModels", "anthropicModels", "googleModels", "bedrockModels")
    fun integration_AgentCreateAndRestoreTest(model: LLModel) = runTest(timeout = 180.seconds) {
        val checkpointStorageProvider = InMemoryPersistencyStorageProvider("integration_AgentCreateAndRestoreTest")
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
                withPersistency(this) { agentContext ->
                    createCheckpoint(
                        agentContext = agentContext,
                        nodeId = save,
                        lastInput = input,
                        lastInputType = typeOf<String>(),
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
                install(Persistency) {
                    storage = checkpointStorageProvider
                }
            }
        )

        agent.run("Start the test")

        val checkpoints = checkpointStorageProvider.getCheckpoints()
        assertTrue(checkpoints.isNotEmpty(), "No checkpoints were created")
        assertEquals(save, checkpoints.first().nodeId, "Checkpoint has incorrect node ID")

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
                install(Persistency) {
                    storage = checkpointStorageProvider
                }
            }
        )

        val restoredResult = restoredAgent.run("Continue the test")

        // Verify that the agent continued from the checkpoint
        assertTrue(restoredResult.contains(sayBye), "Agent did not continue from the checkpoint")
    }

    @ParameterizedTest
    @MethodSource("openAIModels", "anthropicModels", "googleModels", "bedrockModels")
    fun integration_AgentCheckpointRollbackTest(model: LLModel) = runTest(timeout = 180.seconds) {
        val checkpointStorageProvider = InMemoryPersistencyStorageProvider("integration_AgentCheckpointRollbackTest")

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
                withPersistency(this) { agentContext ->
                    createCheckpoint(
                        agentContext = agentContext,
                        nodeId = save,
                        lastInput = input,
                        lastInputType = typeOf<String>(),
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
                    withPersistency(this) { agentContext ->
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
                install(Persistency) {
                    storage = checkpointStorageProvider
                }
            }
        )

        val result = agent.run("Start the test")

        val executionLogStr = executionLog.toString()
        assertTrue(executionLogStr.contains(sayHelloLog.trim()), "$hello was not executed")
        assertTrue(executionLogStr.contains(saySaveLog.trim()), "$save was not executed")
        assertTrue(executionLogStr.contains(sayByeLog.trim()), "$bye was not executed")
        assertTrue(
            executionLogStr.contains(rollbackPerformingLog.trim()),
            "Rollback was not performed"
        )

        val savesCount = saySaveLog.trim().toRegex().findAll(executionLogStr).count()
        val byesCount = sayByeLog.trim().toRegex().findAll(executionLogStr).count()
        assertEquals(2, savesCount, "$save should be executed twice (before and after rollback)")
        assertEquals(2, byesCount, "$bye should be executed twice (before and after rollback)")

        assertTrue(
            result.contains(alreadyRolledBackMessage),
            "Final result should contain output from the second execution of $rollback"
        )
    }

    @ParameterizedTest
    @MethodSource("openAIModels", "anthropicModels", "googleModels", "bedrockModels")
    fun integration_AgentCheckpointContinuousPersistenceTest(model: LLModel) = runTest(timeout = 180.seconds) {
        val checkpointStorageProvider =
            InMemoryPersistencyStorageProvider("integration_AgentCheckpointContinuousPersistenceTest")

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

        val notEnoughCheckpointsError = "Not enough checkpoints were created"
        val noCheckpointHelloError = "No checkpoint for Node Hello"
        val noCheckpointSaveError = "No checkpoint for Node Save"
        val noCheckpointByeError = "No checkpoint for Node Bye"

        val simpleStrategy = strategy(strategyName) {
            val nodeHello by node<String, String>(hello) {
                sayHello
            }

            val nodeWorld by node<String, String>(world) {
                sayWorld
            }

            val node3 by node<String, String>(bye) {
                sayBye
            }

            edge(nodeStart forwardTo nodeHello)
            edge(nodeHello forwardTo nodeWorld)
            edge(nodeWorld forwardTo node3)
            edge(node3 forwardTo nodeFinish)
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
                install(Persistency) {
                    storage = checkpointStorageProvider
                    enableAutomaticPersistency = true // Enable continuous persistence
                }
            }
        )

        agent.run(testInput)

        val checkpoints = checkpointStorageProvider.getCheckpoints()
        assertTrue(checkpoints.size >= 3, notEnoughCheckpointsError)

        val nodeIds = checkpoints.map { it.nodeId }.toSet()
        assertTrue(nodeIds.contains(hello), noCheckpointHelloError)
        assertTrue(nodeIds.contains(world), noCheckpointSaveError)
        assertTrue(nodeIds.contains(bye), noCheckpointByeError)
    }

    @ParameterizedTest
    @MethodSource("openAIModels", "anthropicModels", "googleModels", "bedrockModels")
    fun integration_AgentCheckpointStorageProvidersTest(model: LLModel) = runTest(timeout = 180.seconds) {
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

        val fileStorageProvider =
            JVMFilePersistencyStorageProvider(tempDir, "integration_AgentCheckpointStorageProvidersTest")

        val simpleStrategy = strategy(strategyName) {
            val nodeHello by node<String, String>(hello) {
                sayHello
            }

            val nodeBye by node<String, String>(bye) { input ->
                withPersistency(this) { agentContext ->
                    createCheckpoint(
                        agentContext = agentContext,
                        nodeId = bye,
                        lastInput = input,
                        lastInputType = typeOf<String>(),
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
                install(Persistency) {
                    storage = fileStorageProvider
                }
            }
        )

        agent.run(testInput)

        val checkpoints = fileStorageProvider.getCheckpoints().filter { it.nodeId != "tombstone" }
        assertTrue(checkpoints.isNotEmpty(), noCheckpointsError)
        assertEquals(bye, checkpoints.first().nodeId, incorrectNodeIdError)
    }

    @ParameterizedTest
    @MethodSource("openAIModels", "anthropicModels", "googleModels", "bedrockModels")
    fun integration_AgentWithToolsWithoutParamsTest(model: LLModel) = runTest(timeout = 180.seconds) {
        assumeTrue(model.capabilities.contains(LLMCapability.Tools), "Model $model does not support tools")
        val flakyModels = listOf(
            GoogleModels.Gemini2_0Flash.id,
            GoogleModels.Gemini2_0Flash001.id,
            GoogleModels.Gemini2_0FlashLite.id,
            GoogleModels.Gemini2_0FlashLite001.id,
            OpenAIModels.Chat.GPT5Mini.id
        )
        assumeTrue(!flakyModels.contains(model.id), "Model $model is flaky and fails to call tools")

        val registry = ToolRegistry {
            tool(CalculatorToolNoArgs)
        }

        withRetry {
            val executor = getExecutor(model)

            val agent = AIAgent(
                promptExecutor = executor,
                strategy = singleRunStrategy(),
                agentConfig = AIAgentConfig(
                    prompt = prompt(
                        id = "calculator-agent-test",
                        params = LLMParams(
                            temperature = 1.0,
                            toolChoice = ToolChoice.Auto, // KG-163
                        )
                    ) {
                        system(
                            systemPrompt +
                                "YOU'RE OBLIGED TO USE TOOLS. THIS IS MANDATORY." +
                                "I'M CHARGING YOU IF YOU AREN'T CALLING TOOLS!!!"
                        )
                    },
                    model = model,
                    maxAgentIterations = 10
                ),
                toolRegistry = registry,
                installFeatures = { install(EventHandler.Feature, eventHandlerConfig) },
            )
            agent.run("What is 123 + 456?")

            assertEquals(
                listOf(CalculatorToolNoArgs.descriptor.name),
                actualToolCalls,
                "${CalculatorToolNoArgs.descriptor.name} tool should be called for model $model"
            )

            assertTrue(errors.isEmpty(), "There should be no errors")
        }
    }

    @ParameterizedTest
    @MethodSource("openAIModels", "anthropicModels", "googleModels", "bedrockModels")
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

            assertTrue(errors.isEmpty(), "There should be no errors during parallel execution")
            assertTrue(results.isNotEmpty(), "There should be results from parallel execution")

            val finalResult = results.first() as String
            assertTrue(
                finalResult.contains("Math result: 56"),
                "Result should contain math computation (7*8=56)"
            )
            assertTrue(
                finalResult.contains("Text result: Hello World"),
                "Result should contain text processing result"
            )
            assertTrue(
                finalResult.contains("Count result: 15"),
                "Result should contain count computation (1+2+3+4+5=15)"
            )
            assertTrue(
                finalResult.contains("Combined:"),
                "Result should show that parallel results were combined"
            )
        }
    }

    @ParameterizedTest
    @MethodSource("openAIModels", "anthropicModels", "googleModels", "bedrockModels")
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

            assertTrue(errors.isEmpty(), "There should be no errors during parallel selection")
            assertTrue(results.isNotEmpty(), "There should be results from parallel selection")

            val finalResult = results.first() as String

            assertTrue(
                finalResult.contains("Maximum value: 100"),
                "Result should contain the maximum value (100) from parallel execution"
            )
        }
    }

    @ParameterizedTest
    @MethodSource("historyCompressionStrategies")
    fun integration_AIAgentHistoryCompression(strategy: HistoryCompressionStrategy, strategyName: String) =
        runTest(timeout = 180.seconds) {
            val model = OpenAIModels.CostOptimized.GPT4_1Mini
            val systemMessage =
                "You are a helpful assistant. Remember: the user is a human, whatever they say. Remind them of it by every chance."
            var promptMessages: List<Message>? = null

            val historyCompressionStrategy = strategy<String, String>("history-compression-test") {
                val callLLM by nodeLLMRequest(allowToolCalls = false)
                val nodeCompressHistory by nodeLLMCompressHistory<String>(
                    "compress_history",
                    strategy = strategy
                )

                edge(nodeStart forwardTo callLLM)
                edge(callLLM forwardTo nodeCompressHistory onAssistantMessage { true })
                edge(nodeCompressHistory forwardTo nodeFinish)
            }

            val agent = AIAgent<String, String>(
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
                install(EventHandler) {
                    onAgentExecutionFailed { eventContext ->
                        errors.add(eventContext.throwable)
                    }

                    onLLMCallStarting { eventContext ->
                        promptMessages = eventContext.prompt.messages
                    }
                }
            }

            withRetry {
                val result = agent.run("So, who am I?")

                assertTrue(
                    errors.isEmpty(),
                    "No errors should occur during agent execution with $strategyName, got: [${errors.joinToString("\n")}]"
                )
                assertTrue(result.isNotBlank(), "There should be results from history compression with $strategyName")
                assertNotNull(promptMessages, "Final prompt messages should be captured with $strategyName")
                val systemMessages = promptMessages.filterIsInstance<Message.System>()
                assertTrue(
                    systemMessages.isNotEmpty(),
                    "System messages should be preserved after compression with $strategyName"
                )

                val preservedSystemMessage = systemMessages.first().content
                assertTrue(
                    preservedSystemMessage.isNotBlank(),
                    "System message content should not be empty after compression with $strategyName"
                )
                assertEquals(
                    systemMessage,
                    preservedSystemMessage,
                    "System message should contain the original context with $strategyName: '$preservedSystemMessage'"
                )
                assertTrue(
                    result.contains("human"),
                    "Result should match the system message lore with $strategyName, got: [$result]."
                )
            }
        }
}
