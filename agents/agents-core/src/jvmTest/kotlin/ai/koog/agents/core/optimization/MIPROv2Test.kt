package ai.koog.agents.core.optimization

import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.optimization.core.Demonstration
import ai.koog.agents.core.optimization.core.Example
import ai.koog.agents.core.optimization.core.Metric
import ai.koog.agents.core.optimization.optimizers.mipro.AutoRunMode
import ai.koog.agents.core.optimization.optimizers.mipro.MIPROv2
import ai.koog.agents.core.optimization.optimizers.mipro.MIPROv2Config
import ai.koog.agents.core.optimization.optimizers.mipro.InstructionProposerConfig
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.model.PromptExecutor
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MIPROv2Test {

    private val trainset = (0..19).map { i ->
        Example(input = "Question $i", label = "Answer $i")
    }

    private val valset = (20..29).map { i ->
        Example(input = "Question $i", label = "Answer $i")
    }

    private val simpleStrategy = strategy("test") {
        val thinking by optimizableNode(
            instruction = "Think about the question",
            demonstrations = (0..4).map { Demonstration("Question $it", "Thinking $it", false) }
        )
        val answer by optimizableNode(
            instruction = "Answer the question",
            demonstrations = (0..4).map { Demonstration("Thinking $it", "Answer $it", false) }
        )

        edge(nodeStart forwardTo thinking)
        edge(thinking forwardTo answer)
        edge(answer forwardTo nodeFinish)
    }

    private val agentConfig = AIAgentConfig(
        Prompt.Empty,
        OpenAIModels.Chat.GPT5Nano,
        10
    )

    /**
     * Creates a mock executor that handles both:
     * 1. Task execution (questions → thinking → answers)
     * 2. Meta-prompting (dataset summarization, instruction proposal)
     */
    private fun createMockExecutor(): PromptExecutor = getMockExecutor {
        // Task execution: answers for all question indices
        for (i in 0..29) {
            mockLLMAnswer("Thinking $i (bootstrapped)") onCondition { "Question $i" in it && "Think" in it }
            mockLLMAnswer("Answer $i") onCondition { "Thinking $i" in it && "Answer" in it }
        }

        // Meta-prompting: dataset summarizer returns a simple summary
        mockLLMAnswer("This dataset contains question-answer pairs for testing.") onCondition {
            "observations" in it.lowercase() || "patterns" in it.lowercase() || "summary" in it.lowercase()
        }

        // Meta-prompting: instruction proposals
        mockLLMAnswer("Carefully think about the question step by step.") onCondition {
            "instruction" in it.lowercase() || "propose" in it.lowercase()
        }

        // Meta-prompting: program/module descriptions
        mockLLMAnswer("A question-answering pipeline.") onCondition {
            "program" in it.lowercase() && "description" in it.lowercase()
        }

        // Default fallback
        mockLLMAnswer("Default answer")
    }

    private val exactMatch: Metric<String> = { expected, actual -> if (expected == actual) 1.0 else 0.0 }

    @Test
    fun testEndToEndAutoLight() = runBlocking {
        val executor = createMockExecutor()
        val mipro = MIPROv2(
            MIPROv2Config(
                promptModel = OpenAIModels.Chat.GPT5Nano,
                auto = AutoRunMode.LIGHT,
                maxBootstrappedDemos = 2,
                maxLabeledDemos = 2,
                seed = 42L,
                proposerConfig = InstructionProposerConfig(
                    useDatasetSummary = false,
                    programAware = false,
                ),
            )
        )

        val result = mipro.optimize(
            promptExecutor = executor,
            agentConfig = agentConfig,
            createStrategy = { simpleStrategy },
            trainset = trainset,
            metric = exactMatch,
            valset = valset,
        )

        assertNotNull(result.config, "Result should have a config")
        assertTrue(result.iterations > 0, "Should have performed at least one trial")
        assertTrue(result.score >= 0.0, "Score should be non-negative")
        assertTrue(result.metadata.isNotEmpty(), "Metadata should not be empty")
        assertEquals("MIPROv2", result.metadata["optimizer"], "Optimizer should be MIPROv2")
    }

    @Test
    fun testEndToEndManualConfig() = runBlocking {
        val executor = createMockExecutor()
        val mipro = MIPROv2(
            MIPROv2Config(
                promptModel = OpenAIModels.Chat.GPT5Nano,
                auto = null,
                numCandidates = 4,
                numTrials = 5,
                maxBootstrappedDemos = 2,
                maxLabeledDemos = 2,
                seed = 42L,
                minibatch = false,
                proposerConfig = InstructionProposerConfig(
                    useDatasetSummary = false,
                    programAware = false,
                ),
            )
        )

        val result = mipro.optimize(
            promptExecutor = executor,
            agentConfig = agentConfig,
            createStrategy = { simpleStrategy },
            trainset = trainset,
            metric = exactMatch,
            valset = valset,
        )

        assertNotNull(result.config)
        assertEquals(5, result.iterations, "Should have performed exactly 5 trials")
    }

    @Test
    fun testConfigValidationAutoAndNumCandidates() {
        assertFailsWith<IllegalArgumentException>(
            message = "Should throw when both auto and numCandidates are set"
        ) {
            MIPROv2(
                MIPROv2Config(
                    promptModel = OpenAIModels.Chat.GPT5Nano,
                    auto = AutoRunMode.LIGHT,
                    numCandidates = 10,
                )
            )
        }
    }

    @Test
    fun testConfigValidationNoAutoNoNumCandidates() {
        assertFailsWith<IllegalArgumentException>(
            message = "Should throw when auto is null and numCandidates is not provided"
        ) {
            MIPROv2(
                MIPROv2Config(
                    promptModel = OpenAIModels.Chat.GPT5Nano,
                    auto = null,
                    numCandidates = null,
                )
            )
        }
    }

    @Test
    fun testConfigValidationMissingNumTrials() = runBlocking {
        val executor = createMockExecutor()
        val mipro = MIPROv2(
            MIPROv2Config(
                promptModel = OpenAIModels.Chat.GPT5Nano,
                auto = null,
                numCandidates = 4,
                numTrials = null,
            )
        )

        assertFailsWith<IllegalArgumentException>(
            message = "Should throw when auto is null and numTrials is not provided"
        ) {
            mipro.optimize(
                promptExecutor = executor,
                agentConfig = agentConfig,
                createStrategy = { simpleStrategy },
                trainset = trainset,
                metric = exactMatch,
                valset = valset,
            )
        }
    }

    @Test
    fun testResultContainsMetadata() = runBlocking {
        val executor = createMockExecutor()
        val mipro = MIPROv2(
            MIPROv2Config(
                promptModel = OpenAIModels.Chat.GPT5Nano,
                auto = null,
                numCandidates = 3,
                numTrials = 3,
                maxBootstrappedDemos = 2,
                maxLabeledDemos = 2,
                seed = 42L,
                minibatch = false,
                proposerConfig = InstructionProposerConfig(
                    useDatasetSummary = false,
                    programAware = false,
                ),
            )
        )

        val result = mipro.optimize(
            promptExecutor = executor,
            agentConfig = agentConfig,
            createStrategy = { simpleStrategy },
            trainset = trainset,
            metric = exactMatch,
            valset = valset,
        )

        assertEquals("MIPROv2", result.metadata["optimizer"])
        assertTrue(result.metadata.containsKey("baselineScore"), "Metadata should contain baselineScore")
        assertTrue(result.metadata.containsKey("numTrials"), "Metadata should contain numTrials")
        assertTrue(result.metadata.containsKey("numModules"), "Metadata should contain numModules")
        assertEquals(3, result.metadata["numTrials"])
        assertEquals(2, result.metadata["numModules"])
    }

    @Test
    fun testZeroShotMode() = runBlocking {
        val executor = createMockExecutor()
        val mipro = MIPROv2(
            MIPROv2Config(
                promptModel = OpenAIModels.Chat.GPT5Nano,
                auto = null,
                numCandidates = 3,
                numTrials = 3,
                maxBootstrappedDemos = 0,
                maxLabeledDemos = 0,
                seed = 42L,
                minibatch = false,
                proposerConfig = InstructionProposerConfig(
                    useDatasetSummary = false,
                    programAware = false,
                ),
            )
        )

        val result = mipro.optimize(
            promptExecutor = executor,
            agentConfig = agentConfig,
            createStrategy = { simpleStrategy },
            trainset = trainset,
            metric = exactMatch,
            valset = valset,
        )

        assertNotNull(result.config)
        // In zero-shot mode, the best config should have empty demonstrations
        assertTrue(result.config.demonstrations.isEmpty() ||
            result.config.demonstrations.values.all { it.isEmpty() },
            "Zero-shot mode should produce empty demonstrations")
    }

    @Test
    fun testValsetSplitFromTrainset() = runBlocking {
        val executor = createMockExecutor()
        val mipro = MIPROv2(
            MIPROv2Config(
                promptModel = OpenAIModels.Chat.GPT5Nano,
                auto = null,
                numCandidates = 3,
                numTrials = 3,
                maxBootstrappedDemos = 2,
                maxLabeledDemos = 2,
                seed = 42L,
                minibatch = false,
                proposerConfig = InstructionProposerConfig(
                    useDatasetSummary = false,
                    programAware = false,
                ),
            )
        )

        // Pass null valset — it should be split from trainset
        val result = mipro.optimize(
            promptExecutor = executor,
            agentConfig = agentConfig,
            createStrategy = { simpleStrategy },
            trainset = trainset,
            metric = exactMatch,
            valset = null,
        )

        assertNotNull(result.config, "Should succeed with null valset (auto-split from trainset)")
        assertTrue(result.iterations > 0)
    }

    @Test
    fun testParallelEvaluation() = runBlocking {
        val executor = createMockExecutor()
        val mipro = MIPROv2(
            MIPROv2Config(
                promptModel = OpenAIModels.Chat.GPT5Nano,
                auto = null,
                numCandidates = 3,
                numTrials = 3,
                maxBootstrappedDemos = 2,
                maxLabeledDemos = 2,
                seed = 42L,
                minibatch = false,
                evalParallelism = 4,
                proposerConfig = InstructionProposerConfig(
                    useDatasetSummary = false,
                    programAware = false,
                ),
            )
        )

        val result = mipro.optimize(
            promptExecutor = executor,
            agentConfig = agentConfig,
            createStrategy = { simpleStrategy },
            trainset = trainset,
            metric = exactMatch,
            valset = valset,
        )

        assertNotNull(result.config, "Parallel evaluation should produce a result")
        assertTrue(result.iterations > 0, "Should have performed at least one trial")
        assertTrue(result.score >= 0.0, "Score should be non-negative")
        assertEquals("MIPROv2", result.metadata["optimizer"])
    }
}
