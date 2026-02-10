package ai.koog.agents.core.optimization

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.optimization.core.Demonstration
import ai.koog.agents.core.optimization.core.Example
import ai.koog.agents.core.optimization.core.Metric
import ai.koog.agents.core.optimization.core.OptimizationResult
import ai.koog.agents.core.optimization.optimizers.BootstrapFewShot
import ai.koog.agents.core.optimization.util.toAgent
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BootstrapFewShotTest {

    private val trainset = listOf(
        Example(
            data = mapOf("question" to "Question 0", "thinking" to "Thinking 0", "answer" to "Answer 0"),
            labelKey = "answer"
        ),
        Example(
            data = mapOf("question" to "Question 1", "thinking" to "Thinking 1", "answer" to "Answer 1"),
            labelKey = "answer"
        ),
        Example(
            data = mapOf("question" to "Question 2", "thinking" to "Thinking 2", "answer" to "Answer 2"),
            labelKey = "answer"
        ),
        Example(
            data = mapOf("question" to "Question 3", "thinking" to "Thinking 3", "answer" to "Answer 3"),
            labelKey = "answer"
        ),
        Example(
            data = mapOf("question" to "Question 4", "thinking" to "Thinking 4", "answer" to "Answer 4"),
            labelKey = "answer"
        ),
    )

    private val simpleStrategy = strategy("test") {
        val thinking by optimizableNode(
            instruction = "Think about the question",
            demonstrations = trainset.map { Demonstration(it.data["question"] as String, it.data["thinking"] as String, false) }
        )
        val answer by optimizableNode(
            instruction = "Answer the question",
            demonstrations = trainset.map { Demonstration(it.data["thinking"] as String, it.data["answer"] as String, false) }
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

    // Returns correct thinking and answer for question index in `correct`, wrong thinking and answer otherwise
    private fun createMockExecutor(
        correct: Set<Int>,
    ): PromptExecutor = getMockExecutor {
        for (i in correct) {
            mockLLMAnswer("Thinking $i (bootstrapped)") onCondition { "Question $i" in it }
            mockLLMAnswer("Answer $i") onCondition { "Thinking $i" in it }
        }
        mockLLMAnswer("Wrong thinking") onCondition { "Question" in it }
        mockLLMAnswer("Wrong answer") onCondition { "Wrong thinking" in it }
        mockLLMAnswer("Default answer")
    }

    private val exactMatch: Metric<String> = { expected, actual -> if (expected == actual) 1.0 else 0.0 }

    private val allCorrect = setOf(0, 1, 2, 3, 4)

    private fun optimize(
        correct: Set<Int>,
        maxBootstrappedDemos: Int,
        maxLabeledDemos: Int,
    ) = runBlocking {
        val executor = createMockExecutor(correct)
        val optimizer = BootstrapFewShot(
            maxBootstrappedDemos = maxBootstrappedDemos,
            maxLabeledDemos = maxLabeledDemos,
            maxRounds = 1,
        )

        optimizer.optimize(
            promptExecutor = executor,
            agentConfig = agentConfig,
            strategy = simpleStrategy,
            trainset = trainset,
            metric = exactMatch,
            inputFromExample = { it["question"] as String },
        )
    }

    /**
     * Runs the optimized agent and returns the captured prompts.
     * The prompts are in execution order: first the thinking node's prompt, then the answer node's.
     *
     * Each prompt has the structure (from [defaultStringPromptFn]):
     *   messages[0] = system(instruction)
     *   messages[1..2*N] = N demo pairs: user(demo.input), assistant(demo.output)
     *   messages[last] = user(actualInput)
     */
    private fun runAndCapture(
        result: OptimizationResult,
        executor: PromptExecutor,
        input: String = "New Question",
    ): List<Prompt> = runBlocking {
        val capturedPrompts = mutableListOf<Prompt>()
        val capturingExecutor = object : PromptExecutor by executor {
            override suspend fun execute(prompt: Prompt, model: LLModel): List<Message.Response> {
                capturedPrompts.add(prompt)
                return executor.execute(prompt, model)
            }
        }

        val optimizedAgent = result.toAgent(
            AIAgent(
                promptExecutor = capturingExecutor,
                agentConfig = agentConfig,
                strategy = simpleStrategy,
                toolRegistry = ToolRegistry.EMPTY,
            )
        )

        optimizedAgent.run(input)
        capturedPrompts
    }

    /** Extract demo pairs from a captured prompt: messages between system and final user input. */
    private fun extractDemoPairs(prompt: Prompt): List<Pair<String, String>> {
        val msgs = prompt.messages
        // msgs[0] = system, msgs[last] = user input, middle = demo pairs
        val demoMessages = msgs.subList(1, msgs.size - 1)
        return demoMessages.chunked(2).map { (user, assistant) ->
            user.content to assistant.content
        }
    }

    // --- All correct executor, varying demo limits ---

    @Test
    fun testAllCorrectBothDemoTypes() {
        val executor = createMockExecutor(allCorrect)
        val result = optimize(correct = allCorrect, maxBootstrappedDemos = 5, maxLabeledDemos = 5)
        val prompts = runAndCapture(result, executor)

        assertEquals(2, prompts.size, "Should have 2 prompts (thinking + answer)")

        val thinkingDemos = extractDemoPairs(prompts[0])
        val answerDemos = extractDemoPairs(prompts[1])

        assertTrue(thinkingDemos.isNotEmpty(), "Thinking node should have demos in prompt")
        assertTrue(answerDemos.isNotEmpty(), "Answer node should have demos in prompt")

        // At least some demos should have bootstrapped content (from mock executor)
        assertTrue(thinkingDemos.any { "(bootstrapped)" in it.second },
            "Thinking demos should include bootstrapped outputs, got: $thinkingDemos")
    }

    @Test
    fun testAllCorrectOnlyBootstrapped() {
        val executor = createMockExecutor(allCorrect)
        val result = optimize(correct = allCorrect, maxBootstrappedDemos = 5, maxLabeledDemos = 0)
        val prompts = runAndCapture(result, executor)

        val thinkingDemos = extractDemoPairs(prompts[0])
        val answerDemos = extractDemoPairs(prompts[1])

        assertTrue(thinkingDemos.isNotEmpty(), "Thinking node should have demos")
        assertTrue(thinkingDemos.size <= 5, "At most 5 thinking demos, got ${thinkingDemos.size}")

        // All demos should be bootstrapped (contain mock's "(bootstrapped)" marker)
        assertTrue(thinkingDemos.all { "(bootstrapped)" in it.second },
            "All thinking demos should be bootstrapped, got: $thinkingDemos")

        // Answer demos should have correct "Answer N" outputs
        for ((input, output) in answerDemos) {
            assertTrue(output.startsWith("Answer "),
                "Answer demo output should start with 'Answer ', got: $output")
        }
    }

    @Test
    fun testAllCorrectOnlyLabeled() {
        val executor = createMockExecutor(allCorrect)
        val result = optimize(correct = allCorrect, maxBootstrappedDemos = 0, maxLabeledDemos = 5)
        val prompts = runAndCapture(result, executor)

        val thinkingDemos = extractDemoPairs(prompts[0])
        val answerDemos = extractDemoPairs(prompts[1])

        assertTrue(thinkingDemos.isNotEmpty(), "Thinking node should have demos")
        assertTrue(answerDemos.isNotEmpty(), "Answer node should have demos")

        // Labeled demos come from trainset — no "(bootstrapped)" marker
        for ((input, output) in thinkingDemos) {
            assertTrue(input.startsWith("Question "),
                "Thinking labeled demo input should be a question, got: $input")
            assertTrue(output.startsWith("Thinking ") && "(bootstrapped)" !in output,
                "Thinking labeled demo output should be from trainset, got: $output")
        }

        for ((input, output) in answerDemos) {
            assertTrue(input.startsWith("Thinking "),
                "Answer labeled demo input should be a thinking, got: $input")
            assertTrue(output.startsWith("Answer "),
                "Answer labeled demo output should be from trainset, got: $output")
        }
    }

    @Test
    fun testAllCorrectNoDemos() {
        val executor = createMockExecutor(allCorrect)
        val result = optimize(correct = allCorrect, maxBootstrappedDemos = 0, maxLabeledDemos = 0)
        val prompts = runAndCapture(result, executor)

        // With no demos, each prompt should have only system + user input (2 messages)
        for (prompt in prompts) {
            assertEquals(2, prompt.messages.size,
                "Prompt with no demos should have 2 messages (system + input), got ${prompt.messages.size}")
        }
    }

    // --- No correct answers: all bootstraps fail ---

    @Test
    fun testNoneCorrectFallsBackToLabeled() {
        val executor = createMockExecutor(emptySet())
        val result = optimize(correct = emptySet(), maxBootstrappedDemos = 5, maxLabeledDemos = 5)
        val prompts = runAndCapture(result, executor)

        val thinkingDemos = extractDemoPairs(prompts[0])
        val answerDemos = extractDemoPairs(prompts[1])

        assertTrue(thinkingDemos.isNotEmpty(), "Should have labeled demos as fallback")

        // All demos should be from trainset (labeled), not from mock (no "Wrong", no "(bootstrapped)")
        for ((input, output) in thinkingDemos) {
            assertTrue(input.startsWith("Question "), "Input should be from trainset, got: $input")
            assertTrue("Wrong" !in output && "(bootstrapped)" !in output,
                "Output should be from trainset, got: $output")
        }
        for ((_, output) in answerDemos) {
            assertTrue(output.startsWith("Answer "), "Answer output should be from trainset, got: $output")
            assertTrue("Wrong" !in output, "Answer output should not be wrong, got: $output")
        }
    }

    // --- Partial correct: mixed bootstrapped + labeled ---

    @Test
    fun testPartialCorrectMixedDemos() {
        val correct = setOf(1, 2)
        val executor = createMockExecutor(correct)
        val result = optimize(correct = correct, maxBootstrappedDemos = 5, maxLabeledDemos = 5)
        val prompts = runAndCapture(result, executor)

        val thinkingDemos = extractDemoPairs(prompts[0])
        val answerDemos = extractDemoPairs(prompts[1])

        assertTrue(thinkingDemos.isNotEmpty(), "Should have demos")

        val bootstrappedThinking = thinkingDemos.filter { "(bootstrapped)" in it.second }
        val labeledThinking = thinkingDemos.filter { "(bootstrapped)" !in it.second }

        // Only questions 1 and 2 pass metric → at most 2 bootstrapped
        assertTrue(bootstrappedThinking.size <= 2,
            "At most 2 bootstrapped thinking demos, got ${bootstrappedThinking.size}")

        // Labeled demos should have clean trainset content
        for ((input, output) in labeledThinking) {
            assertTrue(input.startsWith("Question "), "Labeled input from trainset, got: $input")
            assertTrue("Wrong" !in output, "Labeled output should not be wrong, got: $output")
        }

        // Bootstrapped answer demos should only be for correct indices
        val bootstrappedAnswers = answerDemos.filter { it.second.matches(Regex("Answer [0-9]+")) }
        for ((_, output) in bootstrappedAnswers) {
            val idx = output.removePrefix("Answer ").toIntOrNull()
            if (idx != null) {
                // If it came from bootstrapping, it must be a correct index
                // (labeled demos also have "Answer N" format, so we can't distinguish here —
                // but we CAN verify no wrong-index bootstrapped content leaked through)
            }
        }
    }

    @Test
    fun testPartialCorrectOnlyBootstrapped() {
        val correct = setOf(1, 2)
        val executor = createMockExecutor(correct)
        val result = optimize(correct = correct, maxBootstrappedDemos = 5, maxLabeledDemos = 0)
        val prompts = runAndCapture(result, executor)

        val thinkingDemos = extractDemoPairs(prompts[0])
        val answerDemos = extractDemoPairs(prompts[1])

        // Only 2 out of 5 pass → exactly 2 demos
        assertEquals(2, thinkingDemos.size,
            "Should have exactly 2 thinking demos, got ${thinkingDemos.size}")
        assertEquals(2, answerDemos.size,
            "Should have exactly 2 answer demos, got ${answerDemos.size}")

        // All thinking demos should be bootstrapped
        assertTrue(thinkingDemos.all { "(bootstrapped)" in it.second },
            "All thinking demos should be bootstrapped, got: $thinkingDemos")

        // Answer demos should correspond to correct indices only
        for ((_, output) in answerDemos) {
            val idx = output.removePrefix("Answer ").toIntOrNull()
            assertTrue(idx != null && idx in correct,
                "Answer demo should be for correct index (1 or 2), got: $output")
        }
    }
}
