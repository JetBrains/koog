package ai.koog.agents.planner;

import ai.koog.agents.annotations.JavaAPI;
import ai.koog.agents.core.agent.context.AIAgentFunctionalContext;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@JavaAPI
public abstract class JavaAIAgentPlanner<State, Plan> extends AIAgentPlanner<State, Plan> {

    public JavaAIAgentPlanner() {
        super(null);
    }

    abstract protected Plan buildPlan(
        AIAgentFunctionalContext context,
        State state,
        @Nullable Plan plan
    );

    abstract protected State executeStep(
        AIAgentFunctionalContext context,
        State state,
        Plan plan
    );

    abstract protected Boolean isPlanCompleted(
        AIAgentFunctionalContext context,
        State state,
        Plan plan
    );

    @Override
    protected final Plan buildPlan(
        AIAgentFunctionalContext context,
        State state,
        @Nullable Plan plan,
        @NotNull Continuation<? super Plan> continuation
    ) {
        return buildPlan(context, state, plan);
    }

    @Override
    protected final State executeStep(
        AIAgentFunctionalContext context,
        State state,
        Plan plan,
        @NotNull Continuation<? super State> continuation
    ) {
        return executeStep(context, state, plan);
    }

    @Override
    protected final Boolean isPlanCompleted(
        AIAgentFunctionalContext context,
        State state,
        Plan plan,
        @NotNull Continuation<? super Boolean> continuation
    ) {
        return isPlanCompleted(context, state, plan);
    }
}
