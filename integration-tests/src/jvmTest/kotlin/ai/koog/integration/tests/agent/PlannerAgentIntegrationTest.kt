package ai.koog.integration.tests.agent

import ai.koog.agents.planner.AIAgentPlannerStrategy
import ai.koog.agents.planner.PlannerAIAgent
import ai.koog.agents.planner.goap.GoapAgentState
import ai.koog.integration.tests.utils.TestCredentials
import ai.koog.integration.tests.utils.annotations.Retry
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

/**
 * Integration test for Planner Agent with GOAP subtask-based actions.
 */
class PlannerAgentIntegrationTest {

    /**
     * State for a simple problem-solving agent.
     */
    data class ProblemState(
        val problem: String,
        val hasSolution: Boolean = false,
        val solution: String = ""
    ) : GoapAgentState<String, String>(problem) {
        override fun provideOutput(): String = solution
    }

    /**
     * Structured output for the solution.
     */
    @Serializable
    data class SolutionResult(
        val solution: String,
        val confidence: String
    )

    @Test
    @Retry
    fun integration_testSubtaskActionWithBuilderAPI() = runTest(timeout = 180.seconds) {
        val openAIClient = OpenAILLMClient(TestCredentials.readTestOpenAIKeyFromEnv())
        val executor = MultiLLMPromptExecutor(openAIClient)

        executor.use {
            val strategy = AIAgentPlannerStrategy.builder("subtask-builder-test")
                .goap(::ProblemState)
                .goal(
                    name = "Problem solved",
                    condition = { state -> state.hasSolution }
                )
                .action("Solve problem") { builder ->
                    builder
                        .description("Use LLM to solve the problem with builder API")
                        .precondition { state -> !state.hasSolution }
                        .belief { state -> state.copy(hasSolution = true) }
                        .structuredOutputClass(SolutionResult::class)
                        .updateState { state, result ->
                            state.copy(
                                hasSolution = true,
                                solution = result.solution
                            )
                        }
                        .taskDescription { state ->
                            "Solve the following problem: ${state.problem}. " +
                                "Provide the solution and your confidence level (low/medium/high)."
                        }
                }
                .build()

            val agent = PlannerAIAgent.builder(strategy)
                .promptExecutor(executor)
                .llmModel(OpenAIModels.Chat.GPT4o)
                .systemPrompt("You are a helpful assistant.")
                .build()

            val result = agent.run("What is the capital of France?")

            result shouldContain "Paris"
        }
    }
}
