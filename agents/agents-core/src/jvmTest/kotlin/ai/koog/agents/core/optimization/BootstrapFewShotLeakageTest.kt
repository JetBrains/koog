package ai.koog.agents.core.optimization

import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.optimization.core.Demonstration
import ai.koog.agents.core.optimization.core.Example
import ai.koog.agents.core.optimization.core.Metric
import ai.koog.agents.core.optimization.optimizers.BootstrapFewShot
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Regression test for BootstrapFewShot data leakage during bootstrapping.
 *
 * In the reference implementation (DSPy / koog-auto-agent-optimization), when bootstrapping
 * example N, the teacher's prompt is filtered to REMOVE labeled demos matching example N.
 * This prevents the teacher from simply parroting the ground truth instead of generating
 * an independent bootstrap trace.
 *
 * The current implementation at BootstrapFewShot.kt:292 does:
 *   val filteredConfig = teacherConfig
 * i.e., NO filtering — the teacher sees the ground truth as a demo.
 *
 * This test catches that regression by using a custom executor that returns different
 * responses depending on whether the ground truth demo is present in the prompt.
 */
class BootstrapFewShotLeakageTest {

    private val trainset = listOf(
        Example(
            data = mapOf("question" to "Question 0", "thinking" to "Thinking 0", "answer" to "Answer 0"),
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

    private val exactMatch: Metric<String> = { expected, actual -> if (expected == actual) 1.0 else 0.0 }

    /**
     * Creates a custom executor that detects data leakage during bootstrapping.
     *
     * For the thinking node:
     * - If the prompt contains "Thinking 0" as an assistant demo message (ground truth leaked),
     *   returns "Thinking 0 (parroted)" — simulating the teacher parroting the answer.
     * - If the prompt does NOT contain the ground truth demo,
     *   returns "Thinking 0 (bootstrapped)" — simulating genuine independent generation.
     *
     * For the answer node: always returns "Answer 0" so the metric passes.
     *
     * @param capturedPrompts Optional list to collect all prompts for debugging.
     */
    private fun createLeakageDetectingExecutor(
        capturedPrompts: MutableList<Prompt>? = null,
    ): PromptExecutor {
        // Base executor only needed for interface delegation (executeStreaming, moderate, close, etc.)
        return object : PromptExecutor by getMockExecutor(init = { }) {
            private fun respond(prompt: Prompt): List<Message.Response> {
                capturedPrompts?.add(prompt)
                val systemContent = prompt.messages.firstOrNull()?.content ?: ""
                val metaInfo = ResponseMetaInfo.create(Clock.System)

                if ("Think about the question" in systemContent) {
                    // Check if ground truth "Thinking 0" appears as an assistant message in the demos.
                    // Prompt structure: system, [user(demo.input), assistant(demo.output)]*, user(input)
                    // Demos are all messages between system (first) and final user input (last).
                    val demoMessages = prompt.messages.subList(1, prompt.messages.size - 1)
                    val hasGroundTruthInDemos = demoMessages.any {
                        it is Message.Assistant && it.content == "Thinking 0"
                    }

                    val response = if (hasGroundTruthInDemos) {
                        "Thinking 0 (parroted)"  // Regression: teacher sees ground truth
                    } else {
                        "Thinking 0 (bootstrapped)"  // Correct: teacher generates independently
                    }
                    return listOf(Message.Assistant(response, metaInfo))
                }

                if ("Answer the question" in systemContent) {
                    return listOf(Message.Assistant("Answer 0", metaInfo))
                }

                return listOf(Message.Assistant("Default", metaInfo))
            }

            override suspend fun execute(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): List<Message.Response> = respond(prompt)
            override suspend fun execute(prompt: Prompt, model: LLModel): List<Message.Response> = respond(prompt)
        }
    }

    /**
     * Tests that bootstrapping does NOT leak labeled demos into the teacher prompt.
     *
     * Setup: single training example (Question 0 → Thinking 0 → Answer 0).
     * With maxLabeledDemos=1, the teacher gets pre-optimized with LabeledFewShot,
     * giving it the demo: user("Question 0") → assistant("Thinking 0").
     *
     * When bootstrapping example 0, the teacher runs on "Question 0".
     * - CORRECT (reference impl): The demo for example 0 is filtered out.
     *   Teacher prompt has NO demos → executor returns "Thinking 0 (bootstrapped)".
     * - REGRESSION (current): filteredConfig = teacherConfig (no filtering).
     *   Teacher prompt contains the demo → executor returns "Thinking 0 (parroted)".
     *
     * The bootstrapped trace should contain "(bootstrapped)", NOT "(parroted)".
     */
    @Test
    fun testTeacherPromptDoesNotContainLabeledDemoForCurrentExample() {
        val capturedPrompts = mutableListOf<Prompt>()
        val executor = createLeakageDetectingExecutor(capturedPrompts)

        val result = runBlocking {
            val optimizer = BootstrapFewShot(
                maxBootstrappedDemos = 1,
                maxLabeledDemos = 1,  // Enable LabeledFewShot so teacher has demos to potentially leak
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

        // Verify the thinking node has bootstrapped demos in the result
        val thinkingDemos = result.config.demonstrations["thinking"]
        assertNotNull(thinkingDemos, "Should have thinking node demos in optimization result")

        val bootstrappedDemos = thinkingDemos.filter { it.isBootstrapped }
        assertTrue(bootstrappedDemos.isNotEmpty(), "Should have at least one bootstrapped thinking demo")

        // KEY ASSERTION: The teacher should NOT have seen the ground truth "Thinking 0"
        // as a demo when bootstrapping example 0. If it did (regression at BootstrapFewShot.kt:292),
        // our executor would have returned "Thinking 0 (parroted)" instead of "Thinking 0 (bootstrapped)".
        for (demo in bootstrappedDemos) {
            assertEquals(
                "Thinking 0 (bootstrapped)", demo.output,
                "Teacher should generate thinking independently, not parrot the ground truth. " +
                    "Got '${demo.output}' — labeled example leaked into teacher prompt. " +
                    "See BootstrapFewShot.kt:292 (filteredConfig = teacherConfig, no filtering)"
            )
        }
    }

    /**
     * Verifies that the thinking node's bootstrapped prompt during optimization
     * does NOT include the labeled example as a demonstration.
     *
     * Directly inspects the captured prompts to confirm the teacher's prompt structure.
     */
    @Test
    fun testTeacherPromptStructureDuringBootstrap() {
        val capturedPrompts = mutableListOf<Prompt>()
        val executor = createLeakageDetectingExecutor(capturedPrompts)

        runBlocking {
            val optimizer = BootstrapFewShot(
                maxBootstrappedDemos = 1,
                maxLabeledDemos = 1,
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

        // Find the thinking node prompt (system message contains "Think about the question")
        val thinkingPrompts = capturedPrompts.filter { prompt ->
            prompt.messages.firstOrNull()?.content?.contains("Think about the question") == true
        }
        assertTrue(thinkingPrompts.isNotEmpty(), "Should have captured at least one thinking node prompt")

        // The teacher's thinking prompt should NOT contain "Thinking 0" as an assistant demo
        for (prompt in thinkingPrompts) {
            val demoMessages = prompt.messages.subList(1, prompt.messages.size - 1)
            val assistantDemos = demoMessages.filterIsInstance<Message.Assistant>()
            val leakedDemos = assistantDemos.filter { it.content == "Thinking 0" }

            assertTrue(
                leakedDemos.isEmpty(),
                "Teacher's thinking prompt should NOT contain ground truth 'Thinking 0' as a demo. " +
                    "Found ${leakedDemos.size} leaked demo(s). Full prompt messages: " +
                    prompt.messages.joinToString("\n") { "[${it.role}] ${it.content}" }
            )
        }
    }
}
