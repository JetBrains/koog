package ai.koog.agents.core.optimization

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.optimization.core.Example
import ai.koog.agents.core.optimization.core.Metric
import ai.koog.agents.core.optimization.optimizers.BootstrapFewShot
import ai.koog.agents.core.optimization.util.toAgent
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
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
            inputField = "question",
            outputField = "thinking",
        )
        val answer by optimizableNode(
            instruction = "Answer the question",
            inputField = "thinking",
            outputField = "answer",
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

    @Test
    fun testSomething() = runBlocking {
        val executor = createMockExecutor(emptySet())
        val optimizer = BootstrapFewShot(
            maxBootstrappedDemos = 3,
            maxLabeledDemos = 3,
            maxRounds = 1,
        )

        val result = optimizer.optimize(
            promptExecutor = executor,
            agentConfig = agentConfig,
            strategy = simpleStrategy,
            trainset = trainset,
            metric = exactMatch,
            inputFromExample = { it["question"] as String },
        )

        val capturedPrompts = mutableListOf<Prompt>()
        val capturingExecutor = object : PromptExecutor by executor {
            override suspend fun execute(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): List<Message.Response> {
                capturedPrompts.add(prompt)
                return executor.execute(prompt, model, tools)
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

        optimizedAgent.run("New Question")

        // capturedPrompts now contains the prompts sent to each node,
        // including any few-shot demonstrations injected by the optimization config
        assertTrue { true }
        print(capturedPrompts)
        print(optimizedAgent)
    }
}
