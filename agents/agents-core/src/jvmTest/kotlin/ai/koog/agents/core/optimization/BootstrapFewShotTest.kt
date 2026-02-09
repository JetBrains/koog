package ai.koog.agents.core.optimization

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.optimization.core.Demonstration
import ai.koog.agents.core.optimization.core.Example
import ai.koog.agents.core.optimization.core.Metric
import ai.koog.agents.core.optimization.core.OptimizationConfig
import ai.koog.agents.core.optimization.optimizers.BootstrapFewShot
import ai.koog.agents.core.optimization.util.findOptimizableModules
import ai.koog.agents.core.optimization.util.toAgent
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.testing.feature.withTesting
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.model.PromptExecutor
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BootstrapFewShotTest {

    private val trainset = listOf(
        Example(
            data = mapOf("question" to "What is 2+2?", "thinking" to "Simple addition", "answer" to "4"),
            labelKey = "answer"
        ),
        Example(
            data = mapOf("question" to "What is 3+3?", "thinking" to "Simple addition", "answer" to "6"),
            labelKey = "answer"
        ),
        Example(
            data = mapOf("question" to "What is 5+5?", "thinking" to "Simple addition", "answer" to "10"),
            labelKey = "answer"
        ),
        Example(
            data = mapOf("question" to "What is 7+7?", "thinking" to "Simple addition", "answer" to "14"),
            labelKey = "answer"
        ),
        Example(
            data = mapOf("question" to "What is 9+9?", "thinking" to "Simple addition", "answer" to "18"),
            labelKey = "answer"
        ),
    )

    private val testStrategy = strategy("test") {
        val process by optimizableNode(
            instruction = "Process the input",
            inputField = "question",
            outputField = "thinking",
        )
        val answer by optimizableNode(
            instruction = "Answer the question",
            inputField = "thinking",
            outputField = "answer",
        )

        edge(nodeStart forwardTo process)
        edge(process forwardTo answer)
        edge(answer forwardTo nodeFinish)
    }

    private val agentConfig = AIAgentConfig(
        Prompt.Empty,
        OpenAIModels.Chat.GPT4oMini,
        10
    )

    private fun createMockExecutor(
        processResponse: String = "mock thinking",
        answerResponse: String = "mock answer",
    ): PromptExecutor = getMockExecutor {
        mockLLMAnswer(processResponse) onRequestContains "Process"
        mockLLMAnswer(answerResponse) onRequestContains "Answer"
        mockLLMAnswer("default response").asDefaultResponse
    }

    private val alwaysPass: Metric<String> = { _, _ -> 1.0 }

    private val alwaysFail: Metric<String> = { _, _ -> 0.0 }

    @Test
    fun testAllBootstrapsSucceedNoMetric() = runBlocking {
        val executor = createMockExecutor()
        val optimizer = BootstrapFewShot(
            maxBootstrappedDemos = 4,
            maxLabeledDemos = 0,
            maxRounds = 1,
        )

        val result = optimizer.optimize(
            promptExecutor = executor,
            agentConfig = agentConfig,
            strategy = testStrategy,
            trainset = trainset,
            metric = null,
            inputFromExample = { it["question"] as String },
        )

        // No metric → all bootstraps accepted
        val config = result.config
        val modules = testStrategy.findOptimizableModules()
        assertTrue(modules.isNotEmpty())

        // Each module should have bootstrapped demos
        for (module in modules) {
            val demos = config.getTypedDemonstrations<Any?, Any?>(module.name)
            assertTrue(demos != null && demos.isNotEmpty(), "Module ${module.name} should have demos")
            // All should be bootstrapped (isBootstrapped=true from trace collection)
            assertTrue(demos.all { it.isBootstrapped }, "All demos for ${module.name} should be bootstrapped")
        }

        assertEquals("BootstrapFewShot", result.metadata["optimizer"])
    }

    @Test
    fun testAllBootstrapsSucceedWithPassingMetric() = runBlocking {
        val executor = createMockExecutor()
        val optimizer = BootstrapFewShot(
            maxBootstrappedDemos = 3,
            maxLabeledDemos = 0,
            maxRounds = 1,
        )

        val result = optimizer.optimize(
            promptExecutor = executor,
            agentConfig = agentConfig,
            strategy = testStrategy,
            trainset = trainset,
            metric = alwaysPass,
            inputFromExample = { it["question"] as String },
        )

        val config = result.config
        for (module in testStrategy.findOptimizableModules()) {
            val demos = config.getTypedDemonstrations<Any?, Any?>(module.name)
            assertTrue(demos != null && demos.isNotEmpty(),
                "Module ${module.name} should have bootstrapped demos")
        }
    }

    @Test
    fun testAllFailMetricOnlyLabeledDemos() = runBlocking {
        val executor = createMockExecutor()
        val optimizer = BootstrapFewShot(
            maxBootstrappedDemos = 4,
            maxLabeledDemos = 8,
            maxRounds = 1,
        )

        val result = optimizer.optimize(
            promptExecutor = executor,
            agentConfig = agentConfig,
            strategy = testStrategy,
            trainset = trainset,
            metric = alwaysFail,
            inputFromExample = { it["question"] as String },
        )

        val config = result.config
        for (module in testStrategy.findOptimizableModules()) {
            val demos = config.getTypedDemonstrations<Any?, Any?>(module.name)
            assertTrue(demos != null, "Module ${module.name} should have demos")
            // All demos should be labeled (not bootstrapped) since all bootstraps failed
            assertTrue(demos.all { !it.isBootstrapped },
                "All demos for ${module.name} should be labeled (non-bootstrapped) since metric failed")
        }
    }

    @Test
    fun testMixedSuccessAndFailure() = runBlocking {
        // Use a metric that passes for specific answers
        var callCount = 0
        val sometimesPass: Metric<String> = { _, _ ->
            callCount++
            if (callCount % 2 == 1) 1.0 else 0.0
        }

        val executor = createMockExecutor()
        val optimizer = BootstrapFewShot(
            maxBootstrappedDemos = 4,
            maxLabeledDemos = 4,
            maxRounds = 1,
        )

        val result = optimizer.optimize(
            promptExecutor = executor,
            agentConfig = agentConfig,
            strategy = testStrategy,
            trainset = trainset,
            metric = sometimesPass,
            inputFromExample = { it["question"] as String },
        )

        val config = result.config
        for (module in testStrategy.findOptimizableModules()) {
            val demos = config.getTypedDemonstrations<Any?, Any?>(module.name)
            assertTrue(demos != null && demos.isNotEmpty(),
                "Module ${module.name} should have demos (bootstrapped + labeled)")
        }
    }

    @Test
    fun testMaxErrorsStopsEarly() = runBlocking {
        val executor = createMockExecutor()
        var inputCallCount = 0

        val optimizer = BootstrapFewShot(
            maxBootstrappedDemos = 4,
            maxLabeledDemos = 0,
            maxRounds = 1,
            maxErrors = 2,
        )

        val result = optimizer.optimize(
            promptExecutor = executor,
            agentConfig = agentConfig,
            strategy = testStrategy,
            trainset = trainset,
            metric = null,
            inputFromExample = {
                inputCallCount++
                throw RuntimeException("Simulated input conversion error")
            },
        )

        // Should stop after 2 errors
        assertTrue(inputCallCount <= 2, "Should stop after maxErrors=2, but called inputFromExample $inputCallCount times")

        // No bootstrapped demos because all executions threw exceptions
        val config = result.config
        for (module in testStrategy.findOptimizableModules()) {
            val demos = config.getTypedDemonstrations<Any?, Any?>(module.name)
            assertTrue(demos == null || demos.isEmpty() || demos.all { !it.isBootstrapped },
                "Module ${module.name} should have no bootstrapped demos after maxErrors reached")
        }
    }

    @Test
    fun testEmptyTrainsetThrows() = runBlocking {
        val executor = createMockExecutor()
        val optimizer = BootstrapFewShot()

        try {
            optimizer.optimize(
                promptExecutor = executor,
                agentConfig = agentConfig,
                strategy = testStrategy,
                trainset = emptyList(),
                metric = null,
                inputFromExample = { it["question"] as String },
            )
            assertTrue(false, "Should have thrown IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("trainset") == true)
        }
    }

    @Test
    fun testNoOptimizableModulesReturnsEmpty() = runBlocking {
        val plainStrategy = strategy("plain") {
            val node by node<String, String> { input -> input }
            edge(nodeStart forwardTo node)
            edge(node forwardTo nodeFinish)
        }

        val executor = createMockExecutor()

        val optimizer = BootstrapFewShot()
        val result = optimizer.optimize(
            promptExecutor = executor,
            agentConfig = agentConfig,
            strategy = plainStrategy,
            trainset = trainset,
            metric = null,
            inputFromExample = { it["question"] as String },
        )

        assertEquals(0, result.iterations)
        assertTrue(result.config.demonstrations.isEmpty())
    }

    @Test
    fun testTeacherPreOptimization() = runBlocking {
        val executor = createMockExecutor()
        val optimizer = BootstrapFewShot(
            maxBootstrappedDemos = 2,
            maxLabeledDemos = 4, // > 0 triggers LabeledFewShot pre-optimization
            maxRounds = 1,
        )

        val result = optimizer.optimize(
            promptExecutor = executor,
            agentConfig = agentConfig,
            strategy = testStrategy,
            trainset = trainset,
            metric = alwaysPass,
            inputFromExample = { it["question"] as String },
        )

        // Should succeed with teacher pre-optimization providing labeled demos
        val config = result.config
        for (module in testStrategy.findOptimizableModules()) {
            val demos = config.getTypedDemonstrations<Any?, Any?>(module.name)
            assertTrue(demos != null && demos.isNotEmpty(),
                "Module ${module.name} should have demos with teacher pre-optimization")
        }
    }

    @Test
    fun testSelectTraceSingleTrace() {
        val trace = Demonstration<Any?, Any?>("input", "output", isBootstrapped = true)
        val selected = BootstrapFewShot.selectTrace(listOf(trace), Random(42L))
        assertEquals(trace, selected)
    }

    @Test
    fun testSelectTraceMultipleTraces() {
        val traces = listOf(
            Demonstration<Any?, Any?>("in1", "out1", isBootstrapped = true),
            Demonstration<Any?, Any?>("in2", "out2", isBootstrapped = true),
            Demonstration<Any?, Any?>("in3", "out3", isBootstrapped = true),
        )

        // Should be deterministic — same traces always give same selection
        val selected1 = BootstrapFewShot.selectTrace(traces, Random(42L))
        val selected2 = BootstrapFewShot.selectTrace(traces, Random(42L))
        assertEquals(selected1, selected2, "Selection should be deterministic for same input")

        // Selected trace should be one of the input traces
        assertTrue(selected1 in traces, "Selected trace should be from the input list")
    }

    @Test
    fun testMaxBootstrappedDemosRespected() = runBlocking {
        val executor = createMockExecutor()
        val optimizer = BootstrapFewShot(
            maxBootstrappedDemos = 2, // Only 2 bootstrapped demos even though 5 examples
            maxLabeledDemos = 0,
            maxRounds = 1,
        )

        val result = optimizer.optimize(
            promptExecutor = executor,
            agentConfig = agentConfig,
            strategy = testStrategy,
            trainset = trainset,
            metric = null,
            inputFromExample = { it["question"] as String },
        )

        val config = result.config
        for (module in testStrategy.findOptimizableModules()) {
            val demos = config.getTypedDemonstrations<Any?, Any?>(module.name)
            assertTrue(demos != null, "Module ${module.name} should have demos")
            assertTrue(demos.size <= 2,
                "Module ${module.name} should have at most 2 demos, got ${demos.size}")
        }
    }

    @Test
    fun testToAgentCreatesOptimizedCopy() = runBlocking {
        val executor = createMockExecutor()

        // First, optimize
        val optimizer = BootstrapFewShot(
            maxBootstrappedDemos = 2,
            maxLabeledDemos = 0,
            maxRounds = 1,
        )
        val result = optimizer.optimize(
            promptExecutor = executor,
            agentConfig = agentConfig,
            strategy = testStrategy,
            trainset = trainset,
            metric = null,
            inputFromExample = { it["question"] as String },
        )

        // Create a user agent
        val userAgent = AIAgent(
            promptExecutor = executor,
            agentConfig = agentConfig,
            strategy = testStrategy,
            toolRegistry = ToolRegistry.EMPTY,
            installFeatures = {
                withTesting()
            },
        )

        // Create optimized copy
        val optimizedAgent = result.toAgent(userAgent)

        // Verify the optimization config is accessible when running the optimized agent
        // (we can't easily run the agent without full mock setup, but we can verify the result is valid)
        assertTrue(result.config.demonstrations.isNotEmpty(), "Optimization result should have demonstrations")

        // Verify the optimized agent is a different instance
        assertTrue(optimizedAgent !== userAgent, "Optimized agent should be a different instance")
    }

    @Test
    fun testToAgentConfigIsAccessible() = runBlocking {
        // Verify that OptimizationConfig from result is structured correctly for toAgent
        val config = OptimizationConfig(
            instructions = mapOf("process" to "optimized instruction"),
            demonstrations = mapOf(
                "process" to listOf(Demonstration("q", "a", isBootstrapped = true))
            ),
        )

        // Verify the config can be used in coroutine context
        withContext(config) {
            val retrieved = kotlin.coroutines.coroutineContext[OptimizationConfig]
            assertEquals(config, retrieved)
            assertEquals("optimized instruction", retrieved?.getInstruction("process"))
        }
    }
}
