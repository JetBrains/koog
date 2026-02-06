package ai.koog.agents.planner

import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.planner.llm.PlanStep
import ai.koog.agents.planner.llm.SimpleLLMPlanner
import ai.koog.agents.planner.llm.SimplePlan
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.llm.OllamaModels
import ai.koog.prompt.text.text
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertTrue

class SimpleLLMPlannerAgentTest {
    @Test
    fun testSimplePlannerCreatesAndExecutesPlan() = runTest {
        val initialState = "Organize a team meeting"

        val initialPlan = SimplePlan(
            goal = initialState,
            steps = mutableListOf(
                PlanStep("Find available time slots", isCompleted = false),
                PlanStep("Send calendar invites", isCompleted = false),
                PlanStep("Prepare meeting agenda", isCompleted = false)
            )
        )

        val firstStepResult = "Found three available time slots: 2pm-3pm, 3pm-4pm, and 4pm-5pm"
        val secondStepResult = "Calendar invites sent to all team members"
        val thirdStepResult = "Meeting agenda prepared with discussion topics"

        // Counts plan execution iterations
        var planIteration = 0

        val mockExecutor = getMockExecutor {
            // Mock initial plan creation request
            mockLLMAnswer(Json.encodeToString(initialPlan)) onRequestContains "Main Goal -- Create a Plan"

            // Mock step execution responses that return updated state
            fun onPlanStep(request: String, iteration: Int): Boolean {
                val condition = "Current state: " in request && planIteration == iteration
                if (condition) planIteration++
                return condition
            }

            mockLLMAnswer(
                text {
                    +firstStepResult
                }
            ).onCondition { request ->
                onPlanStep(request, 0)
            }

            mockLLMAnswer(
                text {
                    +firstStepResult
                    +secondStepResult
                }
            ).onCondition { request ->
                onPlanStep(request, 1)
            }

            mockLLMAnswer(
                text {
                    +firstStepResult
                    +secondStepResult
                    +thirdStepResult
                }
            ).onCondition { request ->
                onPlanStep(request, 2)
            }
        }

        val planner = SimpleLLMPlanner()
        val strategy = AIAgentPlannerStrategy(
            name = "simple-planner-test",
            planner = planner
        )

        val agentConfig = AIAgentConfig(
            prompt = prompt("planner") {
                system("You are a helpful planning assistant.")
            },
            model = OllamaModels.Meta.LLAMA_3_2,
            maxAgentIterations = 50
        )

        val agent = PlannerAIAgent(
            promptExecutor = mockExecutor,
            strategy = strategy,
            agentConfig = agentConfig
        )

        val result = agent.run(initialState, null)

        // Verify that we got a final state back (should contain results from step executions)
        assertTrue(
            firstStepResult in result &&
                secondStepResult in result &&
                thirdStepResult in result
        )
    }
}
