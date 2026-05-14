package ai.koog.agents.core.planner;

import ai.koog.agents.annotations.JavaAPI;
import ai.koog.agents.core.agent.context.AIAgentPlannerContext;
import ai.koog.serialization.TypeToken;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * AIAgentPlanner implementation for Java.
 */
@JavaAPI
public abstract class JavaAIAgentPlanner<Input, Output, State, Plan> extends AIAgentPlanner<Input, Output, State, Plan> {

    /**
     * Constructor for JavaAIAgentPlanner.
     *
     * @param stateType The type of the state.
     * @param planType The type of the plan.
     */
    public JavaAIAgentPlanner(Class<State> stateType, Class<Plan> planType) {
        super(TypeToken.of(stateType), TypeToken.of(planType));
    }

    /**
     * Simplified constructor for JavaAIAgentPlanner.
     * note: if you use features which require serialization, you should use the other constructor
     * and provide the type tokens for the state and plan types.
     */
    public JavaAIAgentPlanner() {
        super();
    }

    abstract protected Plan buildPlan(
        AIAgentPlannerContext context,
        State state,
        @Nullable Plan plan
    );

    abstract protected State executeStep(
        AIAgentPlannerContext context,
            State state,
            Plan plan
    );

    abstract protected Boolean isPlanCompleted(
        AIAgentPlannerContext context,
        State state,
        Plan plan
    );

    @Override
    protected final Plan buildPlan(
        AIAgentPlannerContext context,
        State state,
        @Nullable Plan plan,
        @NotNull Continuation<? super Plan> continuation
    ) {
        return buildPlan(context, state, plan);
    }

    @Override
    protected final State executeStep(
        AIAgentPlannerContext context,
        State state,
        Plan plan,
        @NotNull Continuation<? super State> continuation
    ) {
        return executeStep(context, state, plan);
    }

    @Override
    protected final Boolean isPlanCompleted(
        AIAgentPlannerContext context,
        State state,
        Plan plan,
        @NotNull Continuation<? super Boolean> continuation
    ) {
        return isPlanCompleted(context, state, plan);
    }
}
