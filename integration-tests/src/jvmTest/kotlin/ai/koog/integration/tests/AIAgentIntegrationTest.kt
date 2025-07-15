package ai.koog.integration.tests

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.ToolCalls
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.singleRunStrategy
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeLLMRequest
import ai.koog.agents.core.dsl.extension.onAssistantMessage
import ai.koog.agents.core.tools.*
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
import ai.koog.integration.tests.utils.TestUtils.readTestAnthropicKeyFromEnv
import ai.koog.integration.tests.utils.TestUtils.readTestGoogleAIKeyFromEnv
import ai.koog.integration.tests.utils.TestUtils.readTestOpenAIKeyFromEnv
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
import ai.koog.prompt.executor.llms.all.simpleAnthropicExecutor
import ai.koog.prompt.executor.llms.all.simpleGoogleAIExecutor
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.params.LLMParams
import ai.koog.prompt.params.LLMParams.ToolChoice
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.nio.file.Path
import java.nio.file.Paths
import java.util.stream.Stream
import kotlin.io.path.readBytes
import kotlin.reflect.typeOf
import kotlin.test.AfterTest
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class AIAgentIntegrationTest {
    val systemPrompt = "You are a helpful assistant."

    @Serializable
    data class GetTransactionsArgs(
        val startDate: String,
        val endDate: String
    ) : ToolArgs

    object GetTransactionsTool : SimpleTool<GetTransactionsArgs>() {
        override val argsSerializer = GetTransactionsArgs.serializer()

        override val descriptor = ToolDescriptor(
            name = "get_transactions",
            description = "Get all transactions between two dates",
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "startDate",
                    description = "Start date in format YYYY-MM-DD",
                    type = ToolParameterType.String
                ),
                ToolParameterDescriptor(
                    name = "endDate",
                    description = "End date in format YYYY-MM-DD",
                    type = ToolParameterType.String
                )
            )
        )

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
        val amounts: List<Double>
    ) : ToolArgs

    object CalculateSumTool : SimpleTool<CalculateSumArgs>() {
        override val argsSerializer = CalculateSumArgs.serializer()

        override val descriptor = ToolDescriptor(
            name = "calculate_sum",
            description = "Calculate the sum of a list of amounts",
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "amounts",
                    description = "List of amounts to sum",
                    type = ToolParameterType.List(ToolParameterType.Float)
                )
            )
        )

        override suspend fun doExecute(args: CalculateSumArgs): String {
            val sum = args.amounts.sum()
            return sum.toString()
        }
    }

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
            return listOf(AnthropicModels.Opus_4, AnthropicModels.Sonnet_4).stream()
        }

        @JvmStatic
        fun googleModels(): Stream<LLModel> {
            return Models.googleModels()
        }

        @JvmStatic
        fun modelsWithVisionCapability(): Stream<Arguments> {
            return Models.modelsWithVisionCapability()
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
                        temperature = 0.0,
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

    private var reasoningCallsCount = 0

    val eventHandlerConfig: EventHandlerConfig.() -> Unit = {
        onAgentFinished { eventContext ->
            results.add(eventContext.result)
        }

        onAgentRunError { eventContext ->
            errors.add(eventContext.throwable)
        }

        onBeforeLLMCall { eventContext ->
            if (eventContext.tools.isEmpty() &&
                eventContext.prompt.params.toolChoice == null
            ) {
                reasoningCallsCount++
            }
        }

        onBeforeNode { eventContext ->
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

        onToolCall { eventContext ->
            actualToolCalls.add(eventContext.tool.name)
            toolExecutionCounter.add(eventContext.tool.name)
        }
    }

    val parallelToolCalls = mutableListOf<ToolCallInfo>()
    val singleToolCalls = mutableListOf<ToolCallInfo>()

    data class ToolCallInfo(
        val id: String?,
        val tool: String,
        val content: String,
        val metaInfo: ResponseMetaInfo,
    )

    val actualToolCalls = mutableListOf<String>()
    val errors = mutableListOf<Throwable>()
    val results = mutableListOf<Any?>()
    val toolExecutionCounter = mutableListOf<String>()

    @AfterTest
    fun teardown() {
        toolExecutionCounter.clear()
        actualToolCalls.clear()
        errors.clear()
        results.clear()
        parallelToolCalls.clear()
        singleToolCalls.clear()
        reasoningCallsCount = 0
    }

    private fun runMultipleToolsTest(model: LLModel, runMode: ToolCalls) = runBlocking {
        assumeTrue(model.capabilities.contains(LLMCapability.Tools), "Model $model does not support tools")

        /* Some models are not calling tools in parallel:
        * see https://youtrack.jetbrains.com/issue/KG-115
        */

        withRetry {
            val multiToolAgent =
                getSingleRunAgentWithRunMode(model, runMode, eventHandlerConfig = eventHandlerConfig)
            multiToolAgent.run(twoToolsPrompt)

            assertTrue(
                parallelToolCalls.size == 2,
                "There should be exactly 2 tool calls in a Multiple tool calls scenario"
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

            assertTrue(firstCall.tool == CalculatorTool.name, "First tool call should be ${CalculatorTool.name}")
            assertTrue(secondCall.tool == DelayTool.name, "Second tool call should be ${DelayTool.name}")
        }
    }

    @ParameterizedTest
    @MethodSource("openAIModels", "anthropicModels", "googleModels")
    fun integration_AIAgentShouldNotCallToolsByDefault(model: LLModel) = runBlocking {
        withRetry {
            val executor = when (model.provider) {
                is LLMProvider.Anthropic -> simpleAnthropicExecutor(readTestAnthropicKeyFromEnv())
                is LLMProvider.Google -> simpleGoogleAIExecutor(readTestGoogleAIKeyFromEnv())
                else -> simpleOpenAIExecutor(readTestOpenAIKeyFromEnv())
            }

            val agent = AIAgent(
                executor = executor,
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

    @ParameterizedTest
    @MethodSource("openAIModels", "anthropicModels", "googleModels")
    fun integration_AIAgentShouldCallCustomTool(model: LLModel) = runBlocking {
        val systemPromptForSmallLLM = systemPrompt + "You MUST use tools."
        assumeTrue(model.capabilities.contains(LLMCapability.Tools), "Model $model does not support tools")

        val toolRegistry = ToolRegistry {
            tool(CalculatorTool)
        }

        withRetry {
            val executor = when (model.provider) {
                is LLMProvider.Anthropic -> simpleAnthropicExecutor(readTestAnthropicKeyFromEnv())
                is LLMProvider.Google -> simpleGoogleAIExecutor(readTestGoogleAIKeyFromEnv())
                else -> simpleOpenAIExecutor(readTestOpenAIKeyFromEnv())
            }

            val agent = AIAgent(
                executor = executor,
                systemPrompt = if (model.id == OpenAIModels.CostOptimized.O4Mini.id) systemPromptForSmallLLM else systemPrompt,
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

    @ParameterizedTest
    @MethodSource("modelsWithVisionCapability")
    fun integration_AIAgentWithImageCapabilityTest(model: LLModel) = runTest(timeout = 120.seconds) {
        assumeTrue(model.capabilities.contains(LLMCapability.Vision.Image), "Model must support vision capability")

        val imageFile = testResourcesDir.resolve("test.png")

        val imageBytes = imageFile.readBytes()
        val base64Image = java.util.Base64.getEncoder().encodeToString(imageBytes)

        val promptWithImage = """
            I'm sending you an image encoded in base64 format.

            data:image/png,$base64Image

            Please analyze this image and identify the image format if possible.
        """.trimIndent()

        withRetry {
            val executor = when (model.provider) {
                is LLMProvider.Anthropic -> simpleAnthropicExecutor(readTestAnthropicKeyFromEnv())
                is LLMProvider.Google -> simpleGoogleAIExecutor(readTestGoogleAIKeyFromEnv())
                else -> simpleOpenAIExecutor(readTestOpenAIKeyFromEnv())
            }

            val agent = AIAgent(
                executor = executor,
                systemPrompt = "You are a helpful assistant that can analyze images.",
                llmModel = model,
                temperature = 0.7,
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
    @MethodSource("openAIModels", "anthropicModels", "googleModels")
    fun integration_testRequestLLMWithoutToolsTest(model: LLModel) = runTest(timeout = 120.seconds) {
        assumeTrue(model.capabilities.contains(LLMCapability.Tools), "Model $model does not support tools")

        val executor = when (model.provider) {
            is LLMProvider.Anthropic -> simpleAnthropicExecutor(readTestAnthropicKeyFromEnv())
            is LLMProvider.Google -> simpleGoogleAIExecutor(readTestGoogleAIKeyFromEnv())
            else -> simpleOpenAIExecutor(readTestOpenAIKeyFromEnv())
        }

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
    @MethodSource("openAIModels", "anthropicModels", "googleModels")
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
    @MethodSource("openAIModels", "anthropicModels", "googleModels")
    fun integration_AIAgentSingleRunNoParallelToolsTest(model: LLModel) = runTest(timeout = 300.seconds) {
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
                singleToolCalls.size == 2,
                "There should be exactly 2 single tool calls in a Sequential single run scenario"
            )
            assertTrue(
                singleToolCalls.first().tool == CalculatorTool.name,
                "First tool call should be ${CalculatorTool.name}"
            )
        }
    }

    @ParameterizedTest
    @MethodSource("reasoningIntervals")
    fun integration_AIAgentWithReActStrategyTest(interval: Int) = runTest(timeout = 300.seconds) {
        // As we're checking the strategy, we're not passing each model and use one "default" profile to build an agent
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
                            temperature = 0.0,
                            toolChoice = ToolChoice.Auto,
                        )
                    ) {},
                    model = model,
                    maxAgentIterations = 10,
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

            assertTrue(
                reasoningCallsCount == expectedReasoningCalls,
                "With reasoningInterval=$interval and ${toolExecutionCounter.size} tool calls, " +
                        "expected $expectedReasoningCalls reasoning calls but got $reasoningCallsCount"
            )
        }
    }

    @ParameterizedTest
    @MethodSource("openAIModels", "anthropicModels", "googleModels")
    fun integration_AgentCreateAndRestoreTest(model: LLModel) = runTest(timeout = 120.seconds) {
        val checkpointStorageProvider = InMemoryPersistencyStorageProvider("integration_AgentCreateAndRestoreTest")
        val sayHello = "Hello World!"
        val hello = "Hello"
        val savedMessage = "Saved the state – the agent is ready to work!"
        val save = "Save"
        val sayBye = "Bye Bye World!"
        val bye = "Bye"

        val checkpointStrategy = strategy("checkpoint-strategy") {
            val nodeHello by node<String, String>(hello) { input ->
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

            val nodeBye by node<String, String>(bye) { input ->
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
    @MethodSource("openAIModels", "anthropicModels", "googleModels")
    fun integration_AgentCheckpointRollbackTest(model: LLModel) = runTest(timeout = 120.seconds) {
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
            val nodeHello by node<String, String>(hello) { input ->
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

            val nodeBye by node<String, String>(bye) { input ->
                executionLog.append(sayByeLog)
                sayBye
            }

            val rollbackNode by node<String, String>(rollback) { input ->
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
    @MethodSource("openAIModels", "anthropicModels", "googleModels")
    fun integration_AgentCheckpointContinuousPersistenceTest(model: LLModel) = runTest(timeout = 120.seconds) {
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
            val nodeHello by node<String, String>(hello) { input ->
                sayHello
            }

            val nodeWorld by node<String, String>(world) { input ->
                sayWorld
            }

            val node3 by node<String, String>(bye) { input ->
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

    @TempDir
    lateinit var tempDir: Path

    @ParameterizedTest
    @MethodSource("openAIModels", "anthropicModels", "googleModels")
    fun integration_AgentCheckpointStorageProvidersTest(model: LLModel) = runTest(timeout = 120.seconds) {
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
            val nodeHello by node<String, String>(hello) { input ->
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

        // Verify that a checkpoint was created and saved to the file system
        val checkpoints = fileStorageProvider.getCheckpoints()
        assertTrue(checkpoints.isNotEmpty(), noCheckpointsError)
        assertEquals(bye, checkpoints.first().nodeId, incorrectNodeIdError)
    }
}