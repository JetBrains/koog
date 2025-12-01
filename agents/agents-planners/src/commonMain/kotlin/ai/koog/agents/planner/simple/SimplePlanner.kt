package ai.koog.agents.planner.simple

import ai.koog.agents.core.agent.context.AIAgentGraphContextBase
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphBuilderBase
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphDelegate
import ai.koog.agents.core.dsl.extension.replaceHistoryWithTLDR
import ai.koog.agents.memory.feature.history.RetrieveFactsFromHistory
import ai.koog.agents.memory.model.Concept
import ai.koog.agents.memory.model.FactType
import ai.koog.agents.planner.PlanningAIAgentStrategy
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.markdown.markdown
import ai.koog.prompt.message.Message
import kotlinx.serialization.Serializable

/**
 * A simple planning strategy that uses LLM requests to build a plan.
 *
 * @property name The name of the strategy.
 */
@OptIn(InternalAgentsApi::class)
public open class SimplePlanner(
    name: String
) : PlanningAIAgentStrategy<String, SimplePlanner.SimplePlan>(name) {

    override fun defineBuildPlan(builder: AIAgentSubgraphBuilderBase<*, *>):
        AIAgentSubgraphDelegate<Pair<String, SimplePlan?>, SimplePlan> =
        builder.singleNodeSubgraph { (state, plan) -> buildPlan(this, state, plan) }

    override fun defineExecuteStep(builder: AIAgentSubgraphBuilderBase<*, *>):
        AIAgentSubgraphDelegate<Pair<String, SimplePlan>, String> =
        builder.singleNodeSubgraph { (state, plan) -> executeStep(this, state, plan) }

    override fun defineIsPlanCompleted(builder: AIAgentSubgraphBuilderBase<*, *>):
        AIAgentSubgraphDelegate<Pair<String, SimplePlan>, Boolean> =
        builder.singleNodeSubgraph { (state, plan) -> completed(this, state, plan) }

    protected open suspend fun buildPlan(
        context: AIAgentGraphContextBase,
        state: String,
        plan: SimplePlan?
    ): SimplePlan {
        val planAssessment = assessPlan(context, state, plan)
        if (planAssessment is PlanAssessment.Continue) {
            return planAssessment.currentPlan
        }

        val shouldReplan = planAssessment is PlanAssessment.Replan

        val newPlan = context.llm.writeSession {
            replaceHistoryWithTLDR(
                strategy = RetrieveFactsFromHistory(
                    Concept(
                        keyword = "achievements",
                        description = "What important milestones have been achieved",
                        factType = FactType.MULTIPLE
                    ),
                    Concept(
                        keyword = "struggles",
                        description = "What major problems have been encountered throughout the course of the task, and how they have been resolved",
                        factType = FactType.MULTIPLE
                    )
                )
            )

            rewritePrompt { oldPrompt ->
                prompt("planner") {
                    system {
                        markdown {
                            h1("Main Goal -- Create a Plan")
                            textWithNewLine("You are a planning agent. Your task is to create a detailed plan with steps.")

                            if (shouldReplan) {
                                h1("Previous Plan (failed)")

                                textWithNewLine("Previously it was attempted to solve the problem with another plan, but it has failed")
                                textWithNewLine("Below you'll see the previous plan with the reason for replan")

                                h2("Previous Plan Overview")

                                textWithNewLine("Previously, the following plan has been tried:")

                                h3("Previous Plan Goal")
                                textWithNewLine("The goal of the previous plan was:")
                                textWithNewLine(planAssessment.currentPlan.goal)

                                h3("Previous Plan Steps")
                                textWithNewLine("The previous plan consisted of the following consecutive steps:")

                                bulleted {
                                    planAssessment.currentPlan.steps.forEach {
                                        if (it.isCompleted) {
                                            item("[COMPLETED!] ${it.description}")
                                        } else {
                                            item(it.description)
                                        }
                                    }
                                }

                                h2("Reason(s) to Replan")

                                textWithNewLine("The previous plan needs to be revised for the following reason")

                                blockquote(planAssessment.reason)
                            }

                            h1("What to do next?")

                            textWithNewLine("You need to create a new plan with steps that will solve the user's problem:")

                            blockquote(state)

                            if (shouldReplan) {
                                bold("Note: Below you'll see some observations from the ")
                            }
                        }
                    }

                    if (shouldReplan) {
                        oldPrompt.messages.filter { it !is Message.System }.forEach {
                            message(it)
                        }
                    }
                }
            }

            val structuredPlanResult = requestLLMStructured(
                serializer = SimplePlan.serializer(),
                examples = listOf(
                    SimplePlan(
                        goal = "The main goal to be achieved by the system",
                        steps = mutableListOf(
                            PlanStep("First step description", isCompleted = true),
                            PlanStep("Second step description", isCompleted = true),
                            PlanStep("Some other action", isCompleted = false),
                            PlanStep("Action to be performed on the step 4", isCompleted = false),
                            PlanStep("Next high-level goal (5)", isCompleted = false),
                        )
                    )
                )
            ).getOrThrow()

            structuredPlanResult.data
        }

        context.llm.writeSession {
            rewritePrompt { oldPrompt ->
                prompt("agent") {
                    system {
                        markdown {
                            h1("Plan")

                            textWithNewLine("You must follow the following plan to solve the problem:")

                            h2("Main Goal:")

                            textWithNewLine(newPlan.goal)

                            h2("Plan Steps:")

                            numbered {
                                newPlan.steps.forEach {
                                    if (it.isCompleted) {
                                        item("[COMPLETED!] ${it.description}")
                                    } else {
                                        item(it.description)
                                    }
                                }
                            }
                        }
                    }

                    oldPrompt.messages.filter { it !is Message.System }.forEach {
                        message(it)
                    }
                }
            }
        }

        // Create a SimplePlan with the generated steps
        return SimplePlan(goal = newPlan.goal, steps = newPlan.steps.toMutableList())
    }

    protected open suspend fun assessPlan(
        context: AIAgentGraphContextBase,
        state: String,
        plan: SimplePlan?
    ): PlanAssessment<SimplePlan> {
        // Simple implementation always continues with the current plan
        return if (plan == null) PlanAssessment.NoPlan() else PlanAssessment.Continue(plan)
    }

    protected open suspend fun executeStep(
        context: AIAgentGraphContextBase,
        state: String,
        plan: SimplePlan
    ): String {
        val currentStep = plan.steps.firstOrNull { !it.isCompleted } ?: return "All steps of the plan are completed!"

        // Execute the step using LLM
        val result = context.llm.writeSession {
            appendPrompt {
                system("You are executing a step in a plan. The goal is: $plan.goal")
                user("Execute the following step: ${currentStep.description}")
                user("Current state: $state")
            }

            requestLLMWithoutTools()
        }

        // Mark the step as completed
        val stepIndex = plan.steps.indexOf(currentStep)
        plan.steps[stepIndex] = currentStep.copy(isCompleted = true)

        return result.content
    }

    protected open fun completed(context: AIAgentGraphContextBase, state: String, plan: SimplePlan): Boolean =
        plan.steps.all { it.isCompleted }

    /**
     * Represents a step in the plan.
     *
     * @property description The description of the step.
     * @property isCompleted Whether the step has been completed.
     */
    @Serializable
    public data class PlanStep(
        val description: String, val isCompleted: Boolean = false
    )

    /**
     * Represents a structured plan with steps.
     *
     * @property goal The goal of the plan.
     * @property steps The steps to achieve the goal.
     */
    @Serializable
    public data class SimplePlan(
        val goal: String,
        val steps: MutableList<PlanStep>,
    )

    /**
     * Represents an assessment of a plan's execution, indicating whether to continue with the current plan or replan.
     */
    public sealed interface PlanAssessment<Plan> {

        /**
         * Indicates that the plan should be replanned based on the current state.
         *
         * @property reason The reason for replanning.
         */
        public class Replan<Plan>(
            public val currentPlan: Plan, public val reason: String
        ) : PlanAssessment<Plan>

        /**
         * Indicates that the plan should continue execution without replanning.
         */
        public class Continue<Plan>(public val currentPlan: Plan) : PlanAssessment<Plan>

        /**
         *
         */
        public class NoPlan<Plan> : PlanAssessment<Plan>
    }
}
