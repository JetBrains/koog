package ai.koog.agents.planner

import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.agent.context.AIAgentPlannerContext
import ai.koog.agents.core.agent.context.getPlannerAgentContextData
import ai.koog.agents.core.agent.context.removePlannerAgentContextData
import ai.koog.agents.core.agent.context.with
import ai.koog.agents.core.agent.exception.AIAgentMaxNumberOfIterationsReachedException
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.serialization.TypeToken
import io.github.oshai.kotlinlogging.KotlinLogging

/**
 * An abstract base planner component, which can be used to implement different types of AI agent planner execution flows.
 *
 * An entry point is an [execute] method, which accepts an initial arbitrary [State] and returns the final [State] after the execution.
 *
 * Planner flow works as follows:
 * 1. Build a plan: [buildPlan]
 * 2. Execute a step in the plan: [executeStep]
 * 3. Repeat steps 1 and 2 until the plan is considered completed. Then the final [State] is returned.
 *
 * @param stateType [TypeToken] of the [State].
 * @param planType [TypeToken] of the [Plan].
 */
public abstract class AIAgentPlanner<State : Any, Plan : Any>(
    public val stateType: TypeToken? = null,
    public val planType: TypeToken? = null,
) {

    private companion object {
        private val logger = KotlinLogging.logger { }
    }

    /**
     * Builds a plan
     */
    protected abstract suspend fun buildPlan(
        context: AIAgentPlannerContext,
        state: State,
        plan: Plan?
    ): Plan

    /**
     * Executes a step in the plan.
     */
    protected abstract suspend fun executeStep(
        context: AIAgentPlannerContext,
        state: State,
        plan: Plan
    ): State

    /**
     * Checks if the plan is completed.
     */
    protected abstract suspend fun isPlanCompleted(
        context: AIAgentPlannerContext,
        state: State,
        plan: Plan
    ): Boolean

    /**
     * Executes the main loop for the planner, which involves building and executing plans iteratively until
     * the plan is considered successfully completed or a max number of iterations is reached.
     *
     * @param context AI Agent's context
     * @param input The initial state to be used as the starting point for the execution process.
     * @return The final state after the execution of the plans.
     * @throws AIAgentMaxNumberOfIterationsReachedException If the maximum number of iterations defined in the agent's
     * configuration is exceeded.
     */
    @OptIn(InternalAgentsApi::class)
    internal suspend fun execute(
        context: AIAgentPlannerContext,
        input: State
    ): State {
        logger.debug { formatLog(context, "Starting planner execution") }
        var state = input
        var plan: Plan? = null

        val contextData = context.getPlannerAgentContextData()
        context.removePlannerAgentContextData()
        var executionPoint = contextData?.executionPoint
        if (contextData != null) {
            state = context.config.serializer.decodeFromJSONElement(contextData.state, stateType!!)
            plan = context.config.serializer.decodeFromJSONElement(contextData.plan, planType!!)
        }

        while (true) {
            if (executionPoint is PlannerAgentExecutionPoint.PlanCompletionEvaluated) {
                if (executionPoint.isCompleted) {
                    break
                } else {
                    executionPoint = null
                }
            }

            val stepIndex = context.stateManager.withStateLock { state ->
                state.iterations
            }

            if (executionPoint == null) {
                context.with(partName = "buildPlan-${stepIndex + 1}") { executionInfo, eventId ->
                    context.pipeline.onPlanCreationStarting(eventId, executionInfo, context, state, stateType, plan, planType, stepIndex + 1)
                    val newPlan = buildPlan(context, state, plan)
                    context.pipeline.onPlanCreationCompleted(eventId, executionInfo, context, state, stateType, plan, planType, stepIndex + 1, newPlan)
                    plan = newPlan
                }
            }

            if (executionPoint is PlannerAgentExecutionPoint.PlanCreated) {
                executionPoint = null
            }

            logger.debug { formatLog(context, "Executing plan step #${stepIndex + 1}") }

            if (executionPoint == null) {
                // Execute step
                context.with(partName = "executeStep-${stepIndex + 1}") { stepExecutionInfo, stepEventId ->
                    context.pipeline.onStepExecutionStarting(stepEventId, stepExecutionInfo, context, state, stateType, plan!!, planType, stepIndex + 1)
                    state = executeStep(context, state, plan)
                    context.pipeline.onStepExecutionCompleted(stepEventId, stepExecutionInfo, context, state, stateType, plan, planType, stepIndex + 1)
                }
            }

            if (executionPoint is PlannerAgentExecutionPoint.StepExecuted) {
                executionPoint = null
            }

            logger.debug { formatLog(context, "Finished executing plan step #${stepIndex + 1}") }

            // Check if plan is completed
            val isCompleted = context.with(partName = "isPlanCompleted-${stepIndex + 1}") { executionInfo, eventId ->
                context.pipeline.onPlanCompletionEvaluationStarting(eventId, executionInfo, context, state, stateType, plan!!, planType, stepIndex + 1)
                val completed = isPlanCompleted(context, state, plan)
                context.pipeline.onPlanCompletionEvaluationCompleted(eventId, executionInfo, context, state, stateType, plan, planType, stepIndex + 1, completed)
                completed
            }

            context.stateManager.withStateLock { state ->
                if (++state.iterations > context.config.maxAgentIterations) {
                    logger.error {
                        formatLog(
                            context,
                            "Max iterations limit (${context.config.maxAgentIterations}) reached"
                        )
                    }
                    throw AIAgentMaxNumberOfIterationsReachedException(context.config.maxAgentIterations)
                }
            }

            if (isCompleted) break
        }

        logger.debug { formatLog(context, "Finished planner execution") }
        return state
    }

    private fun formatLog(context: AIAgentContext, message: String): String =
        "$message [${context.strategyName}, ${context.runId}]"
}
